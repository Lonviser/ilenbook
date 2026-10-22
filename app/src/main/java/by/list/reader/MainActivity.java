package by.list.reader;

import android.app.*;
import android.os.*;
import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.webkit.*;
import android.view.*;
import android.widget.FrameLayout;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;
import org.w3c.dom.*;

public class MainActivity extends Activity {
    private WebView web;
    private final ExecutorService io=Executors.newFixedThreadPool(6);
    private final ExecutorService images=Executors.newFixedThreadPool(2);
    private final ExecutorService controls=Executors.newFixedThreadPool(2);
    private final ExecutorService catalogTransports=Executors.newFixedThreadPool(4);
    private final ExecutorService catalogCacheReads=Executors.newSingleThreadExecutor();
    private final ExecutorService catalogRequests=Executors.newFixedThreadPool(4);
    private final ThreadLocal<String> requestGroup=new ThreadLocal<>();
    private final ConcurrentMap<String,Long> cancelledSearches=new ConcurrentHashMap<>();
    private final ConcurrentMap<String,Set<HttpURLConnection>> searchConnections=new ConcurrentHashMap<>();
    private final ConcurrentMap<String,String> searchTemplates=new ConcurrentHashMap<>();
    private String importRequest;
    private String exportRequest;
    private File exportFile;
    private File library;
    private ReleaseSupport release;
    private YandexAccount yandex;
    private volatile boolean readingActive;
    private final java.util.concurrent.atomic.AtomicLong storageGeneration=new java.util.concurrent.atomic.AtomicLong();
    private final ThreadLocal<Long> operationGeneration=new ThreadLocal<>();
    private static final String ORIGIN="https://list.local/";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state); library=new File(getFilesDir(),"books"); library.mkdirs();
        release=new ReleaseSupport(this);
        yandex=new YandexAccount(this);
        getWindow().setStatusBarColor(0xfff8f7f2);getWindow().setNavigationBarColor(0xfff8f7f2);
        web=new ReaderWebView(this); web.setBackgroundColor(0xfff8f7f2);
        WebView.setWebContentsDebuggingEnabled((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0);
        FrameLayout frame=new FrameLayout(this);frame.setBackgroundColor(0xfff8f7f2);
        frame.addView(web,new FrameLayout.LayoutParams(-1,-1));setContentView(frame);
        // Target 35 uses edge-to-edge: keep text and controls outside system bars.
        frame.setOnApplyWindowInsetsListener((v,insets)->{ FrameLayout.LayoutParams layout=(FrameLayout.LayoutParams)web.getLayoutParams();layout.setMargins(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());web.setLayoutParams(layout);return insets.consumeSystemWindowInsets(); });frame.requestApplyInsets();
        web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
        web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
        web.getSettings().setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.addJavascriptInterface(new Bridge(),"Native");
        web.setWebViewClient(new WebViewClient(){
            @Override public WebResourceResponse shouldInterceptRequest(WebView view,WebResourceRequest request) {
                Uri u=request.getUrl();
                if("list.local".equals(u.getHost())) {
                    String path=u.getPath().equals("/")?"index.html":u.getPath().substring(1);
                    if(path.contains(".."))return denied();
                    try { String mime=path.endsWith(".js")?"text/javascript":path.endsWith(".css")?"text/css":path.endsWith(".ttf")?"font/ttf":path.endsWith(".otf")?"font/otf":path.endsWith(".svg")?"image/svg+xml":path.endsWith(".json")?"application/json":"text/html";
                        InputStream resource=path.matches("user-fonts/[a-zA-Z0-9-]+\\.(ttf|otf)")?new FileInputStream(new File(getFilesDir(),path)):getAssets().open(path);
                        WebResourceResponse r=new WebResourceResponse(mime,path.endsWith(".ttf")||path.endsWith(".otf")?null:"UTF-8",resource);
                        Map<String,String> headers=new HashMap<>();headers.put("Content-Security-Policy","default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; connect-src 'self'; frame-src 'none'; object-src 'none'; base-uri 'none'"); r.setResponseHeaders(headers);return r;
                    }catch(Exception e){return denied();}
                }
                return denied();
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return !"list.local".equals(r.getUrl().getHost());}
        });
        web.loadUrl(ORIGIN);
        release.incoming(getIntent());
    }
    private WebResourceResponse denied(){return new WebResourceResponse("text/plain","UTF-8",new ByteArrayInputStream(new byte[0]));}
    private class ReaderWebView extends WebView {
        ReaderWebView(Context context){super(context);}
        @Override public ActionMode startActionMode(ActionMode.Callback callback,int type){
            return super.startActionMode(new ActionMode.Callback(){
                public boolean onCreateActionMode(ActionMode mode,Menu menu){return callback.onCreateActionMode(mode,menu);}
                public boolean onPrepareActionMode(ActionMode mode,Menu menu){return callback.onPrepareActionMode(mode,menu);}
                public boolean onActionItemClicked(ActionMode mode,MenuItem item){if(item.getItemId()==901||item.getItemId()==902){evaluateJavascript("window.selectionAction('"+(item.getItemId()==901?"note":"translate")+"')",r->mode.finish());return true;}return callback.onActionItemClicked(mode,item);}
                public void onDestroyActionMode(ActionMode mode){callback.onDestroyActionMode(mode);}
            },type);
        }
    }
    public class Bridge {
        @JavascriptInterface public String preferences(){return release.preferences();}
        @JavascriptInterface public String persistPreferences(String value){try{release.persist(value);return "";}catch(Exception e){return e.getMessage();}}
        @JavascriptInterface public void ready(){release.ready();}
        @JavascriptInterface public void cancelSearch(String group){
            if(group==null||group.isEmpty())return;cancelledSearches.put(group,System.currentTimeMillis());Set<HttpURLConnection> connections=searchConnections.get(group);if(connections!=null)controls.execute(()->{for(HttpURLConnection c:connections)c.disconnect();});
            long cutoff=System.currentTimeMillis()-600000;for(Map.Entry<String,Long> entry:cancelledSearches.entrySet())if(entry.getValue()<cutoff)cancelledSearches.remove(entry.getKey());
        }
        @JavascriptInterface public void request(String id,String action,String payload) {
            if("import".equals(action)) { runOnUiThread(()->{
                if(importRequest!=null){reply(id,null,"Уже открыт выбор файла");return;} importRequest=id;
                Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT);intent.addCategory(Intent.CATEGORY_OPENABLE);intent.setType("*/*");
                try { startActivityForResult(intent,100); }catch(Exception e){importRequest=null;reply(id,null,"Не удалось открыть выбор файлов");}
            });return; }
            (action.equals("catalogCache")?catalogCacheReads:action.equals("catalog")||action.equals("publicBook")?catalogRequests:action.equals("cover")||action.equals("findCover")?images:io).execute(()->{try {
                operationGeneration.set(storageGeneration.get());JSONObject p=new JSONObject(payload);requestGroup.set(p.optString("group",""));checkCancelled();Object result;
                if(yandex.handle(id,action,p)||release.handle(id,action,p))return;
                switch(action) {
                    case "list": seed(); result=listBooks();break;
                    case "read": result=readBook(p.getString("id"));break;
                    case "delete": synchronized(MainActivity.this){if(!bookFile(p.getString("id")).delete())throw new IOException("Не удалось удалить книгу");new File(getFilesDir(),"originals/"+p.getString("id")).delete();}result=true;break;
                    case "catalog": result=catalog(p.getString("url"));break;
                    case "catalogCache": result=readCachedCatalog(p.getString("url"),24L*60*60*1000);break;
                    case "publicBook": {
                        String address=p.getString("url"),provider=PublicCatalogs.detailsProvider(address);JSONObject body=new JSONObject(new String(fetch(address,5*1024*1024),StandardCharsets.UTF_8));
                        if(provider.equals("archive"))result=PublicCatalogs.archiveDetails(address,body);
                        else {JSONObject page=body.optJSONObject("parse");if(page==null||page.optString("text").isEmpty())throw new IOException("Не удалось получить текст страницы");result=new JSONObject().put("html",page.getString("text")).put("title",page.optString("title")).put("pageid",page.optLong("pageid")).put("revision",page.optLong("revid"));}break;
                    }
                    case "savePublicText": {
                        String source=p.getString("sourceUrl");URL original=new URL(source);if(!original.getProtocol().equals("https")||!original.getHost().equals("ru.wikisource.org")||original.getUserInfo()!=null||original.getPort()!=-1)throw new IOException("Неверный источник текста");String text=p.getString("text"),title=p.getString("title"),author=p.optString("author","");if(text.length()<20||text.length()>2*1024*1024||title.length()>300||author.length()>300)throw new IOException("Размер текста не поддерживается");
                        byte[] data=(title+"\n"+author+"\n\n"+text+"\n\nИсточник: "+source+"\nАвторство и условия использования: см. страницу источника и историю правок.\n").getBytes(StandardCharsets.UTF_8);JSONObject b=BookParser.parse(data,title+".txt");b.put("title",title).put("author",author.isEmpty()?"Викитека":author).put("source","Викитека").put("sourceUrl",source);result=saveOriginal(b,data);break;
                    }
                    case "cover": result=coverImage(p.getString("url"));break;
                    case "findCover": result=findBookCover(p.getString("id"),p.optBoolean("search",false));break;
                    case "translate": {
                        String text=p.getString("text").trim();String from=p.optString("from","en"),to=p.optString("to","ru");
                        if(text.isEmpty()||text.length()>300)throw new IOException("Выделите слово или фразу до 300 символов");
                        if(!from.matches("[a-z]{2}")||!to.matches("[a-z]{2}"))throw new IOException("Выберите языки перевода");
                        JSONObject response=new JSONObject(new String(fetch("https://api.mymemory.translated.net/get?q="+URLEncoder.encode(text,"UTF-8")+"&langpair="+from+"%7C"+to,1024*1024),StandardCharsets.UTF_8));
                        if(response.optInt("responseStatus",500)!=200)throw new IOException(response.optString("responseDetails","Сервис перевода временно недоступен"));
                        result=new JSONObject().put("text",response.getJSONObject("responseData").getString("translatedText")).put("provider","MyMemory");break;
                    }
                    case "export": case "share": case "openExternal": {
                        JSONObject b=prepareExport(p.getString("id"));File original=new File(getFilesDir(),"originals/"+b.getString("id"));
                        if("export".equals(action)) {runOnUiThread(()->{try{
                            if(exportRequest!=null)throw new IOException("Уже открыто сохранение файла");exportRequest=id;exportFile=original;
                            Intent intent=new Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(BookFileProvider.mime(b.getString("originalName"))).putExtra(Intent.EXTRA_TITLE,b.getString("originalName"));startActivityForResult(intent,101);
                        }catch(Exception e){exportRequest=null;reply(id,null,e.getMessage());}});return;}
                        runOnUiThread(()->{try{Uri uri=new Uri.Builder().scheme("content").authority("by.list.reader.files").appendPath(b.getString("id")).appendPath(b.getString("originalName")).build();
                            Intent intent="openExternal".equals(action)?new Intent(Intent.ACTION_VIEW).setDataAndType(uri,BookFileProvider.mime(b.getString("originalName"))):new Intent(Intent.ACTION_SEND).setType(BookFileProvider.mime(b.getString("originalName"))).putExtra(Intent.EXTRA_STREAM,uri);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("Книга",uri));startActivity(Intent.createChooser(intent,"Открыть или передать книгу"));reply(id,true,null);
                        }catch(Exception e){reply(id,null,e.getMessage());}});return;
                    }
                    case "download": {
                        String type=p.optString("type");String ext=type.contains("epub")?".epub":type.contains("fb2")||type.contains("fictionbook")?".fb2":type.contains("zip")?".zip":".txt";
                        byte[] data=fetch(p.getString("url"),BookParser.MAX_BYTES);
                        JSONObject b=BookParser.parse(data,p.optString("title","Книга")+ext);
                        b.put("source",p.optString("source","OPDS"));if(!p.optString("coverUrl").isEmpty())b.put("coverUrl",p.getString("coverUrl"));result=saveOriginal(b,data);break;
                    }
                    default: throw new IOException("Неизвестная операция");
                }
                reply(id,result,null);
            }catch(Exception ex){String msg=ex instanceof SocketTimeoutException?"Сервер не ответил. Проверьте интернет или адрес каталога.":ex.getMessage(); reply(id,null,msg==null?"Не удалось выполнить операцию":msg);}finally{requestGroup.remove();operationGeneration.remove();}});
        }
        @JavascriptInterface public void readerMode(boolean enabled,boolean dark) {runOnUiThread(()->{
            readingActive=enabled;
            if(enabled)getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            getWindow().setStatusBarColor(dark?0xff202824:0xfff8f7f2);getWindow().setNavigationBarColor(dark?0xff202824:0xfff8f7f2);
            web.setSystemUiVisibility(dark?0:View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        });}
    }
    private File bookFile(String id)throws IOException { if(!id.matches("[a-zA-Z0-9-]+"))throw new IOException("Неверный ID книги");return new File(library,id+".json"); }
    private synchronized JSONObject readBook(String id)throws Exception{
        JSONObject book=new JSONObject(new String(BookParser.read(new FileInputStream(bookFile(id)),BookParser.MAX_BYTES),StandardCharsets.UTF_8));File original=new File(getFilesDir(),"originals/"+id);
        if(book.optInt("richVersion")<1&&original.isFile()&&book.has("originalName"))try{JSONObject fresh=BookParser.parse(BookParser.read(new FileInputStream(original),BookParser.MAX_BYTES),book.getString("originalName"));book.put("chapters",fresh.getJSONArray("chapters")).put("footnotes",fresh.optJSONObject("footnotes")).put("richVersion",1);saveBook(book);}catch(Exception ignored){}
        return book;
    }
    private synchronized JSONObject saveOriginal(JSONObject b,byte[] data)throws Exception {
        checkCancelled();checkGeneration();
        StringBuilder fingerprint=new StringBuilder();for(byte v:java.security.MessageDigest.getInstance("SHA-256").digest(data))fingerprint.append(String.format("%02x",v&255));
        File[] existing=library.listFiles((dir,name)->name.endsWith(".json"));if(existing!=null)for(File f:existing)try{JSONObject old=new JSONObject(new String(BookParser.read(new FileInputStream(f),BookParser.MAX_BYTES),StandardCharsets.UTF_8));if(fingerprint.toString().equals(old.optString("fingerprint")))return metadata(old).put("duplicate",true);}catch(Exception ignored){}
        b.put("fingerprint",fingerprint.toString());
        if(!b.has("id"))b.put("id",UUID.randomUUID().toString());
        String ext=b.getString("format").equals("EPUB")?".epub":b.getString("format").equals("FB2")?(data.length>1&&data[0]=='P'&&data[1]=='K'?".fb2.zip":".fb2"):".txt";
        String name=b.getString("title").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");if(name.length()>100)name=name.substring(0,100);
        b.put("originalName",name+ext);File dir=new File(getFilesDir(),"originals");dir.mkdirs();
        File original=new File(dir,b.getString("id")),temp=new File(dir,b.getString("id")+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(data);}if(!temp.renameTo(original))throw new IOException("Не удалось сохранить исходник");try{return saveBook(b);}catch(Exception e){original.delete();throw e;}
    }
    private synchronized JSONObject prepareExport(String id)throws Exception{
        JSONObject b=new JSONObject(new String(BookParser.read(new FileInputStream(bookFile(id)),BookParser.MAX_BYTES),StandardCharsets.UTF_8));
        File f=new File(getFilesDir(),"originals/"+id);if(f.isFile()&&b.has("originalName"))return b;
        StringBuilder text=new StringBuilder(b.getString("title")+"\n"+b.optString("author")+"\n\n");JSONArray chapters=b.getJSONArray("chapters");for(int i=0;i<chapters.length();i++){JSONObject c=chapters.getJSONObject(i);text.append(c.getString("title")).append("\n\n").append(c.getString("text")).append("\n\n");}
        b.put("originalName",b.getString("title").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_")+".txt");new File(getFilesDir(),"originals").mkdirs();try(FileOutputStream out=new FileOutputStream(f)){out.write(text.toString().getBytes(StandardCharsets.UTF_8));}saveBook(b);return b;
    }
    private synchronized JSONObject saveBook(JSONObject b)throws Exception {
        checkGeneration();
        if(!b.has("id"))b.put("id",UUID.randomUUID().toString());if(!b.has("added"))b.put("added",System.currentTimeMillis());
        File file=bookFile(b.getString("id"));File temp=new File(file.getPath()+".tmp");
        byte[] encoded=b.toString().getBytes(StandardCharsets.UTF_8);if(encoded.length>BookParser.MAX_BYTES)throw new IOException("Книга после разбора превышает 40 МБ");
        try(FileOutputStream out=new FileOutputStream(temp)){out.write(encoded);}
        if(!temp.renameTo(file))throw new IOException("Не удалось сохранить книгу");return metadata(b);
    }
    private JSONObject metadata(JSONObject b)throws Exception {
        JSONObject m=new JSONObject(b.toString());JSONArray chapters=m.getJSONArray("chapters");int length=0;
        for(int i=0;i<chapters.length();i++)length+=chapters.getJSONObject(i).getString("text").length();
        JSONArray lengths=new JSONArray();for(int i=0;i<chapters.length();i++)lengths.put(chapters.getJSONObject(i).getString("text").length());m.remove("chapters");m.remove("footnotes");m.put("chapterLengths",lengths).put("characters",length).put("chapterCount",chapters.length());return m;
    }
    private JSONArray listBooks()throws Exception {
        JSONArray arr=new JSONArray();File[] files=library.listFiles((d,n)->n.endsWith(".json"));if(files==null)return arr;
        Arrays.sort(files,Comparator.comparingLong(File::lastModified).reversed());
        for(File f:files)try {arr.put(metadata(new JSONObject(new String(BookParser.read(new FileInputStream(f),BookParser.MAX_BYTES),StandardCharsets.UTF_8))));}catch(Exception ignored){}
        return arr;
    }
    private void seed()throws Exception {
        if(getPreferences(0).getBoolean("seeded",false))return;
        JSONArray seeds=new JSONArray(new String(BookParser.read(getAssets().open("seed-books.json"),BookParser.MAX_BYTES),StandardCharsets.UTF_8));
        for(int i=0;i<seeds.length();i++){JSONObject b=seeds.getJSONObject(i);if(!bookFile(b.getString("id")).exists())saveBook(b);}
        getPreferences(0).edit().putBoolean("seeded",true).apply();
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(release.result(request,result,data))return;
        if(yandex.result(request,result,data))return;
        if(request==101){String exportId=exportRequest;File source=exportFile;exportRequest=null;exportFile=null;if(exportId==null)return;if(result!=RESULT_OK||data==null){reply(exportId,false,null);return;}Uri dest=data.getData();io.execute(()->{try(InputStream in=new FileInputStream(source);OutputStream out=getContentResolver().openOutputStream(dest,"wt")){if(out==null)throw new IOException("Не удалось записать файл");byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);reply(exportId,true,null);}catch(Exception e){reply(exportId,null,e.getMessage());}});return;}
        if(request!=100)return;String id=importRequest;importRequest=null;
        if(id==null)return;if(result!=RESULT_OK||data==null){reply(id,JSONObject.NULL,null);return;}
        Uri uri=data.getData();io.execute(()->{try {
            String filename="Книга.txt";try(Cursor c=getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int col=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(col>=0)filename=c.getString(col);}}
            byte[] bytes=BookParser.read(getContentResolver().openInputStream(uri),BookParser.MAX_BYTES);JSONObject b=BookParser.parse(bytes,filename);b.put("source","С устройства");reply(id,saveOriginal(b,bytes),null);
        }catch(Exception ex){reply(id,null,ex.getMessage());}});
    }
    void reply(String id,Object value,String error) {
        try { JSONObject r=new JSONObject().put("id",id).put("result",value==null?JSONObject.NULL:value).put("error",error==null?JSONObject.NULL:error);
            String script="window.nativeResult("+r.toString().replace("\u2028","\\u2028").replace("\u2029","\\u2029")+")";
            runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())web.evaluateJavascript(script,null);});
        }catch(Exception ignored){}
    }
    byte[] fetch(String address,int limit)throws Exception {
        checkCancelled();URL url=new URL(address);boolean flibusta=url.getHost().equalsIgnoreCase("flibusta.is")&&url.getProtocol().equals("https");
        if(flibusta&&System.currentTimeMillis()-getPreferences(0).getLong("flibustaHttpWorks",0)<30*60*1000L){
            try{return fetchOnce("http://flibusta.is"+url.getFile(),limit);}catch(IOException error){checkCancelled();getPreferences(0).edit().remove("flibustaHttpWorks").apply();}
        }
        try{return fetchOnce(address,limit);}catch(IOException first){
            checkCancelled();
            if(flibusta){byte[] result=fetchOnce("http://flibusta.is"+url.getFile(),limit);getPreferences(0).edit().putLong("flibustaHttpWorks",System.currentTimeMillis()).apply();return result;}
            throw first;
        }
    }
    private void checkCancelled()throws IOException{String group=requestGroup.get();if(group!=null&&cancelledSearches.containsKey(group))throw new IOException("Поиск отменён");}
    private byte[] fetchOnce(String address,int limit)throws Exception {return fetchOnce(address,limit,null);}
    private byte[] fetchOnce(String address,int limit,CatalogAttempt attempt)throws Exception {
        String current=address;
        for(int hop=0;hop<6;hop++) {
            URL url=new URL(current);if(!Arrays.asList("https","http").contains(url.getProtocol()))throw new IOException("Нужен адрес http:// или https://");
            checkCancelled();if(attempt!=null&&attempt.cancelled)throw new IOException("Запрос отменён");HttpURLConnection c=(HttpURLConnection)url.openConnection();String group=requestGroup.get();Set<HttpURLConnection> active=group==null||group.isEmpty()?null:searchConnections.computeIfAbsent(group,k->ConcurrentHashMap.newKeySet());if(active!=null)active.add(c);if(attempt!=null)attempt.connections.add(c);
            boolean flibusta=url.getHost().equalsIgnoreCase("flibusta.is");c.setConnectTimeout(flibusta?4000:10000);c.setReadTimeout(flibusta?10000:15000);c.setInstanceFollowRedirects(false);
            c.setRequestProperty("User-Agent","IlenbookReader/0.7 (Android; OPDS; personal reader)");c.setRequestProperty("Accept","application/atom+xml, application/epub+zip, application/xml, */*");
            try { if(attempt!=null&&attempt.cancelled)throw new IOException("Запрос отменён");int status=c.getResponseCode();if(status>=300&&status<400) { String loc=c.getHeaderField("Location");if(loc==null)throw new IOException("Неверное перенаправление");current=new URL(url,loc).toString();continue; }
                if(status!=200)throw new IOException("Сервер вернул HTTP "+status+". Проверьте адрес или попробуйте позже.");return BookParser.read(c.getInputStream(),limit);
            }finally{c.disconnect();if(active!=null)active.remove(c);if(attempt!=null)attempt.connections.remove(c);}
        }
        throw new IOException("Слишком много перенаправлений");
    }
    private final class CatalogAttempt implements Callable<byte[]> {
        final String address,group;final int limit;final Set<HttpURLConnection> connections=ConcurrentHashMap.newKeySet();volatile boolean cancelled;
        CatalogAttempt(String a,int l,String g){address=a;limit=l;group=g;}
        public byte[] call()throws Exception{requestGroup.set(group);try{byte[] bytes=fetchOnce(address,limit,this);Element root=BookParser.xml(bytes).getDocumentElement();String kind=BookParser.name(root);if(!"feed".equals(kind)&&!"entry".equals(kind))throw new IOException("Сервер не вернул OPDS-каталог");return bytes;}finally{requestGroup.remove();}}
        void cancel(){cancelled=true;if(!connections.isEmpty())try{controls.execute(()->{for(HttpURLConnection c:connections)c.disconnect();});}catch(RejectedExecutionException ignored){}}
    }
    private byte[] fetchCatalogBytes(String address)throws Exception{
        URL url=new URL(address);if(!url.getHost().equalsIgnoreCase("flibusta.is")||!url.getProtocol().equals("https")||url.getPort()!=-1)return fetch(address,5*1024*1024);
        if(System.currentTimeMillis()-getPreferences(0).getLong("flibustaHttpWorks",0)<30*60*1000L)return fetch(address,5*1024*1024);
        String group=requestGroup.get();CatalogAttempt secure=new CatalogAttempt(address,5*1024*1024,group),fallback=new CatalogAttempt("http://flibusta.is"+url.getFile(),5*1024*1024,group);
        FirstSuccessfulFetch.Result<byte[]> winner=FirstSuccessfulFetch.run(catalogTransports,secure,fallback,650,this::checkCancelled,secure::cancel,fallback::cancel);
        if(winner.alternative)getPreferences(0).edit().putLong("flibustaHttpWorks",System.currentTimeMillis()).apply();return winner.value;
    }
    private File catalogCacheFile(String address)throws Exception{File dir=new File(getFilesDir(),"catalog-cache");dir.mkdirs();String key=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8)));return new File(dir,key+".json");}
    private JSONObject readCachedCatalog(String address,long maxAge)throws Exception{checkCancelled();File file=catalogCacheFile(address);long age=System.currentTimeMillis()-file.lastModified();if(!file.isFile()||age<0||age>=maxAge)return null;try{JSONObject cached=new JSONObject(new String(BookParser.read(new FileInputStream(file),8*1024*1024),StandardCharsets.UTF_8));cached.put("cached",true).put("stale",age>=10*60*1000L).put("cacheAgeMs",age);return cached;}catch(Exception ignored){return null;}}
    private String absolute(String base,String href)throws Exception { // URL allows OPDS template braces before query replacement.
        return new URL(new URL(base),href).toString();
    }
    private String coverImage(String address)throws Exception {
        URL url=new URL(address);if(!Arrays.asList("https","http").contains(url.getProtocol()))throw new IOException("Неверный адрес обложки");
        StringBuilder hash=new StringBuilder();for(byte b:java.security.MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8)))hash.append(String.format("%02x",b&255));
        File dir=new File(getFilesDir(),"cover-cache");dir.mkdirs();File cached=new File(dir,hash+".jpg");
        byte[] data;
        if(cached.isFile())data=BookParser.read(new FileInputStream(cached),2*1024*1024);
        else {
            byte[] input=fetch(address,4*1024*1024);BitmapFactory.Options bounds=new BitmapFactory.Options();bounds.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(input,0,input.length,bounds);
            if(bounds.outWidth<=0||bounds.outHeight<=0||bounds.outWidth>40000||bounds.outHeight>40000)throw new IOException("Не удалось прочитать обложку");
            BitmapFactory.Options options=new BitmapFactory.Options();options.inSampleSize=1;while(Math.max(bounds.outWidth,bounds.outHeight)/options.inSampleSize>1000)options.inSampleSize*=2;
            Bitmap bitmap=BitmapFactory.decodeByteArray(input,0,input.length,options);if(bitmap==null)throw new IOException("Неподдерживаемое изображение");
            float scale=Math.min(1f,640f/Math.max(bitmap.getWidth(),bitmap.getHeight()));Bitmap reduced=Bitmap.createScaledBitmap(bitmap,Math.max(1,Math.round(bitmap.getWidth()*scale)),Math.max(1,Math.round(bitmap.getHeight()*scale)),true);
            ByteArrayOutputStream out=new ByteArrayOutputStream();reduced.compress(Bitmap.CompressFormat.JPEG,85,out);data=out.toByteArray();if(reduced!=bitmap)reduced.recycle();bitmap.recycle();
            File temp=new File(dir,hash+"-"+UUID.randomUUID()+".tmp");try(FileOutputStream file=new FileOutputStream(temp)){file.write(data);}if(!temp.renameTo(cached))temp.delete();
            File[] cache=dir.listFiles((d,n)->n.endsWith(".jpg"));if(cache!=null&&cache.length>300){Arrays.sort(cache,Comparator.comparingLong(File::lastModified));for(int i=0;i<20;i++)cache[i].delete();}
        }
        return "data:image/jpeg;base64,"+Base64.getEncoder().encodeToString(data);
    }
    private String normalized(String value){return java.text.Normalizer.normalize(value,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("[^\\p{L}\\p{N}]+"," ").trim();}
    private JSONObject findBookCover(String id,boolean search)throws Exception{
        JSONObject b=new JSONObject(new String(BookParser.read(new FileInputStream(bookFile(id)),BookParser.MAX_BYTES),StandardCharsets.UTF_8));
        if(b.has("cover")&&!search)return metadata(b);
        if(!search&&System.currentTimeMillis()-b.optLong("coverChecked",0)<7L*24*3600*1000)return metadata(b);
        String address=b.optString("coverUrl","");
        if((address.isEmpty()||search)&&search&&!b.optString("author").isEmpty()&&!id.equals("welcome")){
            JSONObject c=catalog("https://flibusta.is/opds/search?searchType=books&searchTerm="+URLEncoder.encode(b.getString("title"),"UTF-8"));
            JSONArray entries=c.getJSONArray("entries");String wanted=normalized(b.getString("title"));
            for(int i=0;i<entries.length();i++){JSONObject e=entries.getJSONObject(i);if(!normalized(e.getString("title")).equals(wanted)||!e.has("coverUrl"))continue;
                String author=normalized(e.optString("author"));boolean matches=true;for(String token:normalized(b.optString("author")).split(" "))if(token.length()>2&&!author.contains(token)){matches=false;break;}
                if(matches){address=e.getString("coverUrl");break;}
            }
        }
        String cover=address.isEmpty()?null:coverImage(address);
        // Reload before updating: export or other metadata changes may have happened meanwhile.
        JSONObject latest=new JSONObject(new String(BookParser.read(new FileInputStream(bookFile(id)),BookParser.MAX_BYTES),StandardCharsets.UTF_8));
        latest.put("coverChecked",System.currentTimeMillis());if(cover!=null)latest.put("cover",cover).put("coverUrl",address);return saveBook(latest);
    }
    private JSONObject catalog(String address)throws Exception {
        checkCancelled();File cacheDir=new File(getFilesDir(),"catalog-cache");cacheDir.mkdirs();String key=Base64.getUrlEncoder().withoutPadding().encodeToString(java.security.MessageDigest.getInstance("SHA-256").digest(address.getBytes(StandardCharsets.UTF_8)));File file=new File(cacheDir,key+".json");
        if(file.isFile()&&System.currentTimeMillis()-file.lastModified()<10*60*1000L)try{JSONObject cached=new JSONObject(new String(BookParser.read(new FileInputStream(file),8*1024*1024),StandardCharsets.UTF_8));cached.put("cached",true);return cached;}catch(Exception ignored){}
        JSONObject result=catalogNetwork(address);checkCancelled();File temp=new File(cacheDir,key+"-"+UUID.randomUUID()+".tmp");try(FileOutputStream out=new FileOutputStream(temp)){out.write(result.toString().getBytes(StandardCharsets.UTF_8));}if(!temp.renameTo(file))temp.delete();
        File[] files=cacheDir.listFiles((d,n)->n.endsWith(".json"));if(files!=null&&files.length>100){Arrays.sort(files,Comparator.comparingLong(File::lastModified));for(int i=0;i<10;i++)files[i].delete();}return result;
    }
    private JSONObject catalogNetwork(String address)throws Exception {
        if(PublicCatalogs.supports(address))return PublicCatalogs.parse(address,new JSONObject(new String(fetch(address,5*1024*1024),StandardCharsets.UTF_8)));
        Element root=BookParser.xml(fetchCatalogBytes(address.replace(" ","%20"))).getDocumentElement();
        if(!"feed".equals(BookParser.name(root))&&!"entry".equals(BookParser.name(root)))throw new IOException("По этому адресу нет каталога OPDS 1.x. Нужен адрес, заканчивающийся на /opds.");
        JSONObject result=new JSONObject().put("url",address).put("title",BookParser.first(root,"title","Каталог"));JSONArray entries=new JSONArray();
        for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element&&"link".equals(BookParser.name(n))){Element l=(Element)n;String rel=l.getAttribute("rel");
            if(rel.equals("next"))result.put("next",absolute(address,l.getAttribute("href")));
            if(rel.equals("search")&&(l.getAttribute("href").contains("{searchTerms}")||l.getAttribute("href").toLowerCase(Locale.ROOT).contains("%7bsearchterms%7d")))result.put("search",absolute(address,l.getAttribute("href")));
            else if(rel.equals("search")&&l.getAttribute("type").contains("opensearch"))result.put("openSearch",absolute(address,l.getAttribute("href")));
        }
        List<Element> items=new ArrayList<>();if("entry".equals(BookParser.name(root)))items.add(root);else for(Node n=root.getFirstChild();n!=null;n=n.getNextSibling())if(n instanceof Element&&"entry".equals(BookParser.name(n)))items.add((Element)n);
        for(Element e:items) {
            JSONObject item=new JSONObject().put("id",BookParser.first(e,"id","")).put("title",BookParser.first(e,"title","Без названия")).put("author",BookParser.first(e,"name","")).put("language",BookParser.first(e,"language","")).put("summary",BookParser.strip(BookParser.first(e,"summary",BookParser.first(e,"content",""))));
            JSONArray downloads=new JSONArray();
            for(Element link:BookParser.all(e,"link")) {
                String rel=link.getAttribute("rel"),type=link.getAttribute("type"),href=link.getAttribute("href");if(href.isEmpty())continue;
                if(rel.equals("http://opds-spec.org/image")||rel.equals("http://opds-spec.org/image/thumbnail")||rel.equals("x-stanza-cover-image")||rel.equals("x-stanza-cover-image-thumbnail")){
                    if(href.startsWith("data:")){
                        if(!item.has("coverUrl")&&href.length()<500000&&href.matches("data:image/(png|jpeg|webp);base64,[A-Za-z0-9+/=\\r\\n]+"))item.put("cover",href);
                    }else try{if(!item.has("coverUrl")||rel.equals("http://opds-spec.org/image")){String imageUrl=absolute(address,href);if(imageUrl.startsWith("http://")||imageUrl.startsWith("https://")){item.put("coverUrl",imageUrl);item.remove("cover");}}}catch(Exception ignored){}
                }
                if(rel.startsWith("http://opds-spec.org/acquisition")&&!rel.contains("/buy")&&!rel.contains("/borrow")&&!rel.contains("/subscribe")) {
                    if(type.contains("epub")||type.contains("fb2")||type.contains("fictionbook")||type.equals("text/plain")||type.equals("application/zip")) downloads.put(new JSONObject().put("url",absolute(address,href)).put("type",type).put("label",type.contains("epub")?"EPUB":type.contains("zip")?"FB2.ZIP":type.equals("text/plain")?"TXT":"FB2"));
                } else if(type.contains("atom+xml")&&(rel.isEmpty()||rel.equals("subsection")||rel.equals("alternate")||rel.equals("http://opds-spec.org/sort/new")))item.put("url",absolute(address,href));
            }
            item.put("downloads",downloads);entries.put(item);
        }
        result.put("entries",entries);
        // Gutenberg's published OpenSearch descriptor still points at retired m.gutenberg.org.
        String host=new URL(address).getHost();
        if(host.endsWith("gutenberg.org"))result.put("search","https://www.gutenberg.org/ebooks/search.opds/?query={searchTerms}");
        else if(host.equals("flibusta.is"))result.put("search",new URL(address).getProtocol()+"://flibusta.is/opds/search?searchTerm={searchTerms}");
        else if(host.endsWith("wolnelektury.pl"))result.put("search","https://wolnelektury.pl/opds/search/?q={searchTerms}");
        else if(host.endsWith("ebooksgratuits.com"))result.put("search","https://www.ebooksgratuits.com/opds/feed.php?mode=search&query={searchTerms}");
        if(!result.has("search")&&result.has("openSearch"))try {
            String descriptor=result.getString("openSearch");if(searchTemplates.containsKey(descriptor))result.put("search",searchTemplates.get(descriptor));
            else {Element os=BookParser.xml(fetch(descriptor,512*1024)).getDocumentElement();for(Element url:BookParser.all(os,"Url"))if(url.getAttribute("type").contains("atom+xml")) {String template=url.getAttribute("template").replace("{count?}","30").replace("{startIndex?}","0");String resolved=absolute(address,template);result.put("search",resolved);searchTemplates.put(descriptor,resolved);break;}}
        }catch(Exception ignored){}
        return result;
    }
    @Override public void onBackPressed(){web.evaluateJavascript("window.handleBack()",r->{if(!"true".equals(r))finish();});}
    @Override protected void onPause(){web.evaluateJavascript("window.pauseReadingSession ? window.pauseReadingSession() : window.persistReading && window.persistReading()",null);super.onPause();}
    @Override protected void onResume(){super.onResume();if(web!=null)web.evaluateJavascript("window.resumeReadingSession && window.resumeReadingSession()",null);}
    @Override protected void onNewIntent(Intent intent){super.onNewIntent(intent);setIntent(intent);release.incoming(intent);}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(readingActive&&(event.getKeyCode()==KeyEvent.KEYCODE_VOLUME_UP||event.getKeyCode()==KeyEvent.KEYCODE_VOLUME_DOWN)&&getPreferences(0).getBoolean("volumePaging",false)){if(event.getAction()==KeyEvent.ACTION_DOWN)web.evaluateJavascript("window.hardwareTurn && window.hardwareTurn("+(event.getKeyCode()==KeyEvent.KEYCODE_VOLUME_DOWN?1:-1)+")",null);return true;}return super.dispatchKeyEvent(event);}
    void background(Runnable r){io.execute(()->{operationGeneration.set(storageGeneration.get());try{r.run();}finally{operationGeneration.remove();}});}
    void newStorageGeneration(){storageGeneration.incrementAndGet();}
    private void checkGeneration()throws IOException{Long epoch=operationGeneration.get();if(epoch!=null&&epoch.longValue()!=storageGeneration.get())throw new IOException("Операция отменена после восстановления библиотеки");}
    JSONObject importBytes(byte[] data,String filename,String source)throws Exception{JSONObject b=BookParser.parse(data,filename);b.put("source",source);return saveOriginal(b,data);}
    void event(String type,JSONObject data){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed())web.evaluateJavascript("window.nativeEvent && window.nativeEvent("+JSONObject.quote(type)+","+data.toString()+")",null);});}
    void releaseEvent(JSONObject data){release.event(data);}
    @Override protected void onDestroy(){release.close();web.removeJavascriptInterface("Native");web.destroy();io.shutdownNow();images.shutdownNow();controls.shutdownNow();catalogTransports.shutdownNow();catalogCacheReads.shutdownNow();catalogRequests.shutdownNow();super.onDestroy();}
}
