package by.list.reader;

import android.app.*;
import android.content.*;
import android.database.Cursor;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.AtomicFile;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.json.*;

/** Device operations kept outside the reader UI. All file requests use the Android picker. */
final class ReleaseSupport {
    private final MainActivity activity;
    private String pickerId,pickerKind;
    private File pickerFile;
    private final List<JSONObject> incoming=new ArrayList<>();
    private boolean ready;
    TransferServer transfer;
    ReleaseSupport(MainActivity a){activity=a;}
    synchronized String preferences(){try{return new String(BookParser.read(new AtomicFile(new File(activity.getFilesDir(),"preferences.json")).openRead(),4*1024*1024),StandardCharsets.UTF_8);}catch(Exception ignored){return "";}}
    synchronized void persist(String value)throws Exception {
        if(value.getBytes(StandardCharsets.UTF_8).length>4*1024*1024)throw new IOException("Заметки и настройки превышают 4 МБ. Экспортируйте часть заметок.");
        JSONObject p=new JSONObject(value);if(p.optJSONObject("books")==null||p.optJSONObject("settings")==null)throw new IOException("Неверные настройки");
        AtomicFile file=new AtomicFile(new File(activity.getFilesDir(),"preferences.json"));FileOutputStream out=null;try{out=file.startWrite();out.write(value.getBytes(StandardCharsets.UTF_8));file.finishWrite(out);activity.getPreferences(0).edit().putBoolean("volumePaging",p.getJSONObject("settings").optBoolean("volumePaging",false)).apply();}catch(Exception e){if(out!=null)file.failWrite(out);throw e;}
    }
    synchronized void ready(){ready=true;for(JSONObject e:incoming)activity.event("incoming",e);incoming.clear();}
    synchronized void event(JSONObject value){if(ready)activity.event("incoming",value);else incoming.add(value);}
    boolean handle(String id,String action,JSONObject p)throws Exception {
        switch(action){
            case "preferences": activity.reply(id,new JSONObject(preferences().isEmpty()?"{}":preferences()),null);return true;
            case "backup": {
                File f=new File(activity.getCacheDir(),"backup-"+UUID.randomUUID()+".zip");
                try{synchronized(activity){synchronized(this){String prefs=preferences();if(prefs.isEmpty())throw new IOException("Настройки ещё не сохранены");try(OutputStream out=new FileOutputStream(f)){LibraryArchive.write(activity.getFilesDir(),out,prefs,p.optBoolean("originals",true));}}}}catch(Exception e){f.delete();throw e;}
                pick(id,"save",f,"ilenbook-backup.zip","application/zip");return true;
            }
            case "restore": pick(id,"restore",null,null,"application/zip");return true;
            case "exportText": {
                String text=p.getString("text"),name=p.optString("name","ilenbook-notes.md").replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]","_");if(text.length()>2*1024*1024)throw new IOException("Экспорт слишком большой");
                File f=File.createTempFile("notes-",".txt",activity.getCacheDir());try(FileOutputStream out=new FileOutputStream(f)){out.write(text.getBytes(StandardCharsets.UTF_8));}String mime=name.toLowerCase(Locale.ROOT).endsWith(".csv")?"text/csv":name.toLowerCase(Locale.ROOT).endsWith(".md")?"text/markdown":"text/plain";pick(id,"save",f,name,mime);return true;
            }
            case "importFont": pick(id,"font",null,null,"*/*");return true;
            case "fonts": activity.reply(id,fonts(),null);return true;
            case "importDictionary": pick(id,"dictionary",null,null,"application/json");return true;
            case "dictionary": activity.reply(id,definition(p.getString("word"),p.optBoolean("online",false)),null);return true;
            case "openLink": {
                String address=validYandex(p.getString("url"));activity.runOnUiThread(()->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(address)));activity.reply(id,true,null);}catch(Exception e){activity.reply(id,null,"Нет приложения для открытия ссылки");}});return true;
            }
            case "openCatalogLink": {
                String address=p.getString("url");java.net.URL target=new java.net.URL(address);if(!target.getProtocol().equals("https")||target.getUserInfo()!=null||target.getPort()!=-1||!java.util.Arrays.asList("ru.wikisource.org","archive.org").contains(target.getHost()))throw new IOException("Неверная ссылка источника");activity.runOnUiThread(()->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(address)));activity.reply(id,true,null);}catch(Exception e){activity.reply(id,null,"Нет приложения для открытия ссылки");}});return true;
            }
            case "transferStart": synchronized(this){if(transfer!=null)transfer.close();transfer=new TransferServer(activity);activity.reply(id,transfer.start(),null);}return true;
            case "transferStop": synchronized(this){if(transfer!=null)transfer.close();transfer=null;activity.reply(id,true,null);}return true;
            default:return false;
        }
    }
    private void pick(String id,String kind,File file,String name,String mime){activity.runOnUiThread(()->{
        if(pickerId!=null){if(file!=null)file.delete();activity.reply(id,null,"Уже открыт выбор файла");return;}
        pickerId=id;pickerKind=kind;pickerFile=file;
        Intent intent=new Intent(kind.equals("save")?Intent.ACTION_CREATE_DOCUMENT:Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType(mime);if(name!=null)intent.putExtra(Intent.EXTRA_TITLE,name);
        try{activity.startActivityForResult(intent,200);}catch(Exception e){pickerId=null;pickerKind=null;pickerFile=null;if(file!=null)file.delete();activity.reply(id,null,"Не удалось открыть выбор файла");}
    });}
    boolean result(int request,int code,Intent data){
        if(request!=200)return false;final String id=pickerId,kind=pickerKind;final File file=pickerFile;pickerId=null;pickerKind=null;pickerFile=null;if(id==null)return true;
        if(code!=Activity.RESULT_OK||data==null||data.getData()==null){if(file!=null)file.delete();activity.reply(id,false,null);return true;}
        Uri uri=data.getData();activity.background(()->{try{
            Object value=true;
            if(kind.equals("save")){try(InputStream in=new FileInputStream(file);OutputStream out=activity.getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("Нет доступа к выбранному файлу");byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
            else if(kind.equals("restore")){synchronized(activity){synchronized(this){activity.newStorageGeneration();String restored=LibraryArchive.restore(activity.getFilesDir(),activity.getContentResolver().openInputStream(uri));value=new JSONObject(restored);}}}
            else if(kind.equals("font"))value=importFont(uri);
            else if(kind.equals("dictionary"))value=importDictionary(uri);
            activity.reply(id,value,null);
        }catch(Exception e){activity.reply(id,null,e.getMessage());}finally{if(file!=null)file.delete();}});return true;
    }
    static String validYandex(String input)throws Exception {
        String s=input.trim();java.util.regex.Matcher m=java.util.regex.Pattern.compile("https://[^\\s<>]+",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s);if(m.find())s=m.group();
        URL u=new URL(s);String host=u.getHost().toLowerCase(Locale.ROOT);if(!u.getProtocol().equals("https")||u.getUserInfo()!=null||u.getPort()!=-1||!Arrays.asList("books.yandex.ru","bookmate.ru","www.bookmate.ru").contains(host))throw new IOException("Нужна HTTPS-ссылка на книгу books.yandex.ru или bookmate.ru");return u.toString();
    }
    void incoming(Intent intent){if(intent==null)return;String action=intent.getAction();if(!Intent.ACTION_SEND.equals(action)&&!Intent.ACTION_VIEW.equals(action))return;
        Uri uri=Intent.ACTION_SEND.equals(action)?intent.getParcelableExtra(Intent.EXTRA_STREAM):intent.getData();
        if(uri!=null&&"content".equals(uri.getScheme()))activity.background(()->{try{JSONObject b=importUri(uri);event(new JSONObject().put("book",b));}catch(Exception e){try{event(new JSONObject().put("error",e.getMessage()));}catch(Exception ignored){}}});
        else if(Intent.ACTION_SEND.equals(action)){String text=intent.getStringExtra(Intent.EXTRA_TEXT);if(text!=null)try{event(new JSONObject().put("url",validYandex(text)));}catch(Exception e){try{event(new JSONObject().put("error",e.getMessage()));}catch(Exception ignored){}}}
    }
    private String filename(Uri uri)throws Exception {String name="Книга.txt";try(Cursor c=activity.getContentResolver().query(uri,null,null,null,null)){if(c!=null&&c.moveToFirst()){int col=c.getColumnIndex(OpenableColumns.DISPLAY_NAME);if(col>=0)name=c.getString(col);}}return name;}
    JSONObject importUri(Uri uri)throws Exception {String name=filename(uri);byte[] bytes=BookParser.read(activity.getContentResolver().openInputStream(uri),BookParser.MAX_BYTES);return activity.importBytes(bytes,name,"С устройства");}
    private JSONObject importFont(Uri uri)throws Exception {
        String name=filename(uri),lower=name.toLowerCase(Locale.ROOT);if(!lower.endsWith(".ttf")&&!lower.endsWith(".otf"))throw new IOException("Выберите шрифт TTF или OTF");
        byte[] bytes=BookParser.read(activity.getContentResolver().openInputStream(uri),8*1024*1024);String id=UUID.randomUUID().toString()+lower.substring(lower.lastIndexOf('.'));File dir=new File(activity.getFilesDir(),"user-fonts");dir.mkdirs();File file=new File(dir,id);
        try{try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);}Typeface.createFromFile(file);return new JSONObject().put("id",id).put("name",name).put("url","https://list.local/user-fonts/"+id);}catch(Exception e){file.delete();throw new IOException("Не удалось открыть шрифт",e);}
    }
    JSONArray fonts()throws Exception {JSONArray a=new JSONArray();File[] files=new File(activity.getFilesDir(),"user-fonts").listFiles();if(files!=null)for(File f:files)if(f.getName().matches("[a-zA-Z0-9-]+\\.(ttf|otf)"))a.put(new JSONObject().put("id",f.getName()).put("name",f.getName().substring(0,8)+" · свой шрифт").put("url","https://list.local/user-fonts/"+f.getName()));return a;}
    private static String word(String w){return w.toLowerCase(Locale.ROOT).replace('ё','е').replaceAll("^[^\\p{L}]+|[^\\p{L}-]+$","").trim();}
    private synchronized JSONObject dictionary()throws Exception {File f=new File(activity.getFilesDir(),"dictionary.json");return f.isFile()?new JSONObject(new String(BookParser.read(new FileInputStream(f),8*1024*1024),StandardCharsets.UTF_8)):new JSONObject();}
    private synchronized void writeDictionary(JSONObject d)throws Exception {byte[] bytes=d.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>8*1024*1024)throw new IOException("Словарь превышает 8 МБ");AtomicFile f=new AtomicFile(new File(activity.getFilesDir(),"dictionary.json"));FileOutputStream out=null;try{out=f.startWrite();out.write(bytes);f.finishWrite(out);}catch(Exception e){if(out!=null)f.failWrite(out);throw e;}}
    private JSONObject importDictionary(Uri uri)throws Exception {
        JSONObject source=new JSONObject(new String(BookParser.read(activity.getContentResolver().openInputStream(uri),8*1024*1024),StandardCharsets.UTF_8));JSONObject entries=source.optJSONObject("entries");if(entries==null)throw new IOException("Нужен JSON: {\"entries\": {\"слово\": \"толкование\"}, \"source\": \"источник\"}");
        if(entries.length()>20000)throw new IOException("Не более 20 000 слов в одном словаре");JSONObject out=new JSONObject();Iterator<String> keys=entries.keys();String attribution=source.optString("source","Личный словарь");
        while(keys.hasNext()){String key=keys.next(),value=entries.getString(key);if(key.length()>80||value.length()>6000)throw new IOException("Слишком длинная словарная статья");out.put(word(key),new JSONObject().put("text",value).put("source",attribution.substring(0,Math.min(300,attribution.length()))));}
        writeDictionary(out);return new JSONObject().put("count",out.length());
    }
    private JSONObject definition(String input,boolean online)throws Exception {
        String key=word(input);if(key.isEmpty()||key.length()>80||key.contains(" "))throw new IOException("Выделите одно слово");JSONObject d=dictionary();if(d.has(key)){JSONObject cached=new JSONObject(d.getJSONObject(key).toString());if(cached.optBoolean("online"))cached.put("text",compactDefinition(cached.getString("text")));return cached.put("cached",true);}
        if(!online)throw new IOException("В локальном словаре слова нет. Можно запросить Викисловарь или импортировать свой словарь.");
        String address="https://ru.wiktionary.org/w/api.php?action=query&prop=extracts&explaintext=1&redirects=1&format=json&titles="+URLEncoder.encode(input.toLowerCase(Locale.ROOT),"UTF-8");
        JSONObject response=new JSONObject(new String(activity.fetch(address,2*1024*1024),StandardCharsets.UTF_8));JSONObject pages=response.getJSONObject("query").getJSONObject("pages");Iterator<String> ids=pages.keys();String text="";while(ids.hasNext()){JSONObject p=pages.getJSONObject(ids.next());if(!p.has("missing"))text=p.optString("extract");}
        if(text.trim().isEmpty())throw new IOException("Слово не найдено. Попробуйте начальную форму.");text=compactDefinition(text);if(text.length()>6000)text=text.substring(0,6000)+"…";
        JSONObject entry=new JSONObject().put("text",text).put("source","Русский Викисловарь · CC BY-SA · https://ru.wiktionary.org/wiki/"+URLEncoder.encode(input,"UTF-8")).put("online",true);
        synchronized(this){JSONObject latest=dictionary();if(latest.length()<20000){latest.put(key,entry);writeDictionary(latest);}}return entry;
    }
    private static String compactDefinition(String text){java.util.regex.Matcher m=java.util.regex.Pattern.compile("(?s)={3,5} Значение ={3,5}\\s*(.*?)(?=\\n={2,}|$)").matcher(text);if(m.find())text=m.group(1);return text.replaceAll("(?m)\\s*◆[^\\n]*","").trim();}
    synchronized void close(){if(transfer!=null)transfer.close();}
}
