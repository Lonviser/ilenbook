package by.list.reader;

import android.app.Activity;
import android.content.Intent;
import android.security.keystore.*;
import android.util.*;
import android.util.Base64;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;

/** Private credential storage and read-only API client, outside the book backup. */
final class YandexAccount {
    private final MainActivity activity;private static final String KEY="ilenbook-yandex-token-v1";
    interface Transport{JSONObject request(String method,String address,JSONObject body,String token)throws Exception;}
    private final Transport transport;private volatile long deadline;
    private String loginRequest;private long loginGeneration;private boolean syncing;private final AtomicLong generation=new AtomicLong();
    private final File credentials,cache;
    YandexAccount(MainActivity a){this(a,null);}
    YandexAccount(MainActivity a,Transport t){this(a,t,a.getNoBackupFilesDir());}
    YandexAccount(MainActivity a,Transport t,File directory){activity=a;transport=t;credentials=new File(directory,"yandex-token.json");cache=new File(directory,"yandex-snapshot.json");}
    boolean handle(String id,String action,JSONObject p)throws Exception{
        switch(action){
            case "yandexSnapshot":activity.reply(id,snapshot(),null);return true;
            case "yandexConnect":activity.runOnUiThread(()->{if(loginRequest!=null){activity.reply(id,null,"Вход уже открыт");return;}loginRequest=id;loginGeneration=generation.incrementAndGet();byte[] random=new byte[32];new SecureRandom().nextBytes(random);String state=Base64.encodeToString(random,Base64.URL_SAFE|Base64.NO_WRAP|Base64.NO_PADDING);try{activity.startActivityForResult(new Intent(activity,YandexLoginActivity.class).putExtra("state",state).putExtra("started",System.currentTimeMillis()),610);}catch(Exception e){loginRequest=null;activity.reply(id,null,"Не удалось открыть вход");}});return true;
            case "yandexSync":try{activity.reply(id,sync(),null);}catch(Exception e){throw new IOException(message(e));}return true;
            case "yandexCancel":generation.incrementAndGet();activity.reply(id,true,null);return true;
            case "yandexDisconnect":synchronized(this){generation.incrementAndGet();new AtomicFile(credentials).delete();new AtomicFile(cache).delete();}android.webkit.CookieManager.getInstance().removeAllCookies(null);activity.reply(id,new JSONObject().put("connected",false),null);return true;
            default:return false;
        }
    }
    boolean result(int request,int code,Intent data){if(request!=610)return false;final String id=loginRequest;final long epoch=loginGeneration;loginRequest=null;if(id==null)return true;if(code!=Activity.RESULT_OK||data==null){activity.reply(id,false,null);return true;}final String token=data.getStringExtra("token");activity.background(()->{try{JSONObject profile=YandexProtocol.profile(request("GET",YandexProtocol.REST+"/profile",null,token));synchronized(this){if(epoch!=generation.get())throw new IOException("Подключение отменено");storeToken(token);new AtomicFile(cache).delete();JSONObject snapshot=new JSONObject().put("connected",true).put("profile",profile).put("books",new JSONArray()).put("updated",0);write(cache,snapshot);activity.reply(id,snapshot,null);}}catch(Exception e){activity.reply(id,null,message(e));}});return true;}
    private synchronized JSONObject snapshot()throws Exception{JSONObject s=new JSONObject();try{s=read(cache,12*1024*1024);}catch(Exception ignored){}s.put("connected",credentials.isFile());return s;}
    private synchronized String token()throws Exception{if(!credentials.isFile())throw new IOException("Сначала подключите Яндекс Книги");try{JSONObject o=read(credentials,16000);Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");cipher.init(Cipher.DECRYPT_MODE,key(),new GCMParameterSpec(128,Base64.decode(o.getString("iv"),Base64.NO_WRAP)));cipher.updateAAD("ilenbook-yandex-v1".getBytes(StandardCharsets.UTF_8));return new String(cipher.doFinal(Base64.decode(o.getString("data"),Base64.NO_WRAP)),StandardCharsets.UTF_8);}catch(Exception e){throw new IOException("Не удалось открыть сохранённый вход. Подключите аккаунт заново.");}}
    private SecretKey key()throws Exception{KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);if(!store.containsAlias(KEY)){KeyGenerator g=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");g.init(new KeyGenParameterSpec.Builder(KEY,KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT).setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build());return g.generateKey();}return (SecretKey)store.getKey(KEY,null);}
    private void storeToken(String token)throws Exception{if(token==null||!token.matches("[A-Za-z0-9._~+-]{16,4096}"))throw new IOException("Неверный ответ входа");Cipher c=Cipher.getInstance("AES/GCM/NoPadding");c.init(Cipher.ENCRYPT_MODE,key());c.updateAAD("ilenbook-yandex-v1".getBytes(StandardCharsets.UTF_8));write(credentials,new JSONObject().put("iv",Base64.encodeToString(c.getIV(),Base64.NO_WRAP)).put("data",Base64.encodeToString(c.doFinal(token.getBytes(StandardCharsets.UTF_8)),Base64.NO_WRAP)));}
    private static JSONObject read(File f,int max)throws Exception{return new JSONObject(new String(BookParser.read(new AtomicFile(f).openRead(),max),StandardCharsets.UTF_8));}
    private static void write(File f,JSONObject o)throws Exception{byte[] bytes=o.toString().getBytes(StandardCharsets.UTF_8);if(bytes.length>12*1024*1024)throw new IOException("Слишком много данных Яндекса");AtomicFile a=new AtomicFile(f);FileOutputStream out=null;try{out=a.startWrite();out.write(bytes);a.finishWrite(out);}catch(Exception e){if(out!=null)a.failWrite(out);throw e;}}
    private JSONObject sync()throws Exception{
        final long epoch;final String token;synchronized(this){if(syncing)throw new IOException("Синхронизация уже идёт");if(loginRequest!=null)throw new IOException("Завершите вход");token=token();epoch=generation.get();syncing=true;deadline=System.currentTimeMillis()+90000;}
        try{
            JSONObject profile=YandexProtocol.profile(request("GET",YandexProtocol.REST+"/profile",null,token));JSONObject old=snapshot();if(!profile.getString("id").equals(old.optJSONObject("profile")==null?"":old.getJSONObject("profile").optString("id")))old=new JSONObject();
            JSONObject result=new JSONObject().put("connected",true).put("profile",profile).put("updated",System.currentTimeMillis());JSONArray errors=new JSONArray();
            check(epoch);
            try{JSONObject page=pages("/profile/library_cards","library_cards",token,epoch,50);result.put("books",YandexProtocol.cards(page.getJSONArray("rows"))).put("booksTruncated",page.getBoolean("truncated")).put("booksUpdated",System.currentTimeMillis());}catch(AuthFailure e){throw e;}catch(Exception e){copy(old,result,"books","booksUpdated","booksTruncated");errors.put(new JSONObject().put("part","books").put("message",message(e)));}
            check(epoch);
            try{JSONObject page=pages("/profile/bookshelves","bookshelves",token,epoch,10);JSONArray shelves=new JSONArray();for(int i=0;i<page.getJSONArray("rows").length();i++){JSONObject s=(JSONObject)YandexProtocol.normalize(page.getJSONArray("rows").getJSONObject(i));shelves.put(new JSONObject().put("id",YandexProtocol.text(s,"uuid",100)).put("name",YandexProtocol.text(s,"title",200).isEmpty()?YandexProtocol.text(s,"name",200):YandexProtocol.text(s,"title",200)));}result.put("shelves",shelves).put("shelvesUpdated",System.currentTimeMillis()).put("shelvesTruncated",page.getBoolean("truncated"));}catch(AuthFailure e){throw e;}catch(Exception e){copy(old,result,"shelves","shelvesUpdated","shelvesTruncated");errors.put(new JSONObject().put("part","shelves").put("message",message(e)));}
            check(epoch);
            try{String login=profile.getString("login");if(login.isEmpty())throw new IOException("В профиле нет логина для цитат");JSONObject page=pages("/users/"+URLEncoder.encode(login,"UTF-8")+"/quotes","quotes",token,epoch,20);result.put("quotes",YandexProtocol.quotes(page.getJSONArray("rows"))).put("quotesUpdated",System.currentTimeMillis()).put("quotesTruncated",page.getBoolean("truncated"));}catch(AuthFailure e){throw e;}catch(Exception e){copy(old,result,"quotes","quotesUpdated","quotesTruncated");errors.put(new JSONObject().put("part","quotes").put("message",message(e)));}
            check(epoch);
            try{JSONObject body=new JSONObject().put("query",YandexProtocol.STATISTICS).put("operationName","Statistics").put("variables",new JSONObject().put("requestTime",java.time.Instant.now().toString()));JSONObject response=(JSONObject)YandexProtocol.normalize(request("POST",YandexProtocol.GRAPHQL,body,token));if(response.optJSONArray("errors")!=null&&response.getJSONArray("errors").length()>0)throw new IOException("Статистика временно недоступна");JSONObject statistics=response.getJSONObject("data").getJSONObject("user").getJSONObject("loyalty").getJSONObject("statistics");result.put("statistics",statistics).put("statisticsUpdated",System.currentTimeMillis());}catch(AuthFailure e){throw e;}catch(Exception e){copy(old,result,"statistics","statisticsUpdated");errors.put(new JSONObject().put("part","statistics").put("message",message(e)));}
            // Probe a documented-by-client read endpoint only. Never guess a position mutation.
            try{check(epoch);JSONObject info=request("GET",YandexProtocol.REST+"/profile/sync_state",null,token);JSONArray keys=new JSONArray();for(Iterator<String> i=info.keys();i.hasNext();)keys.put(i.next());result.put("positionProbe",new JSONObject().put("status","read-response").put("keys",keys).put("writeSupported",false));}catch(Exception e){result.put("positionProbe",new JSONObject().put("status","unavailable").put("writeSupported",false));}
            result.put("errors",errors);synchronized(this){if(epoch!=generation.get())throw new IOException("Синхронизация отменена");write(cache,result);}return result;
        }finally{synchronized(this){syncing=false;}}
    }
    private static void copy(JSONObject from,JSONObject to,String... keys)throws Exception{for(String k:keys)if(from.has(k))to.put(k,from.get(k));}
    private void check(long epoch)throws IOException{if(epoch!=generation.get())throw new IOException("Синхронизация отменена");if(System.currentTimeMillis()>deadline)throw new IOException("Обновление заняло слишком много времени. Повторите позже.");}
    private JSONObject pages(String path,String array,String token,long epoch,int maxPages)throws Exception{JSONArray all=new JSONArray();Set<String> seen=new HashSet<>();boolean truncated=false;for(int page=1;page<=maxPages;page++){check(epoch);JSONObject response=(JSONObject)YandexProtocol.normalize(request("GET",YandexProtocol.REST+path+"?page="+page+"&per_page=100",null,token));JSONArray rows=response.optJSONArray(array);if(rows==null)throw new IOException("Сервис изменил формат ответа");String fingerprint=rows.toString();if(!seen.add(fingerprint)&&rows.length()>0){truncated=true;break;}for(int i=0;i<rows.length();i++)all.put(rows.get(i));if(rows.length()<100)break;if(page==maxPages)truncated=true;}return new JSONObject().put("rows",all).put("truncated",truncated);}
    private JSONObject request(String method,String address,JSONObject body,String token)throws Exception{
        if(!YandexProtocol.trustedApi(address))throw new IOException("Недоверенный адрес API");if(token==null||token.length()>4096)throw new IOException("Нужен вход в аккаунт");
        if(transport!=null)return transport.request(method,address,body,token);
        HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();try{c.setInstanceFollowRedirects(false);c.setConnectTimeout(9000);c.setReadTimeout(14000);c.setRequestMethod(method);c.setRequestProperty("Auth-Token",token);c.setRequestProperty("Accept","application/json");c.setRequestProperty("App-Language","ru");c.setRequestProperty("App-Locale","ru");c.setRequestProperty("App-Platform","android");c.setRequestProperty("Bookmate-Version","20200305");c.setRequestProperty("Device-Os","Android");c.setRequestProperty("User-Agent","ilenbook/0.7.2");if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json");try(OutputStream out=c.getOutputStream()){out.write(body.toString().getBytes(StandardCharsets.UTF_8));}}int code=c.getResponseCode();if(code==401)throw new AuthFailure();if(code==403)throw new IOException("Нет доступа к этому разделу аккаунта (403)");if(code>=300&&code<400)throw new IOException("Перенаправление API отклонено");if(code==429)throw new IOException("Слишком много запросов. Повторите позже.");if(code!=200)throw new IOException("Сервис временно недоступен ("+code+")");return new JSONObject(new String(BookParser.read(c.getInputStream(),6*1024*1024),StandardCharsets.UTF_8));}finally{c.disconnect();}
    }
    private static String message(Exception e){if(e instanceof AuthFailure)return "Вход истёк или доступ отклонён. Подключите аккаунт заново.";if(e instanceof java.net.SocketTimeoutException)return "Яндекс не ответил вовремя. Повторите синхронизацию.";if(e instanceof JSONException)return "Сервис изменил формат данных. Сохранённая копия остаётся доступна.";return e.getMessage()==null?"Не удалось подключиться к Яндексу":e.getMessage();}
    private static final class AuthFailure extends IOException{}
}
