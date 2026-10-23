package by.list.reader;

import android.content.*;
import android.content.pm.*;
import android.net.*;
import android.os.Build;
import android.provider.Settings;
import android.util.AtomicFile;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.*;
import org.json.*;

/** Foreground checks and an optional Wi-Fi download; installation always uses Android UI. */
final class AppUpdates {
    private final MainActivity activity;
    private final SharedPreferences prefs;
    private final File directory,apk,part;
    private final ExecutorService worker=Executors.newSingleThreadExecutor();
    private final String currentVersion;private final long currentCode;
    private volatile JSONObject latest,downloaded;
    private volatile String phase="idle",error="";
    private volatile boolean busy,cancelled,closed,ready,uiReady,installAfterPermission;
    private volatile long bytes,total;
    private volatile HttpURLConnection connection;
    AppUpdates(MainActivity a){
        activity=a;prefs=a.getSharedPreferences("app-updates",0);directory=new File(a.getNoBackupFilesDir(),"updates");directory.mkdirs();apk=new File(directory,"ready.apk");part=new File(directory,"download.part");
        String version="0.0.0";long code=0;try{PackageInfo p=a.getPackageManager().getPackageInfo(a.getPackageName(),0);version=p.versionName;code=code(p);}catch(Exception ignored){}currentVersion=version;currentCode=code;
        try{latest=UpdatePolicy.release(new JSONObject(prefs.getString("release","{}")));}catch(Exception ignored){}
    }
    private static long code(PackageInfo p){return Build.VERSION.SDK_INT>=28?p.getLongVersionCode():p.versionCode;}
    private boolean available(){try{return latest!=null&&UpdatePolicy.compare(latest.getString("version"),currentVersion)>0;}catch(Exception e){return false;}}
    JSONObject status()throws JSONException {
        return new JSONObject().put("currentVersion",currentVersion).put("currentCode",currentCode).put("phase",phase).put("busy",busy).put("error",error).put("available",available()).put("release",latest==null?JSONObject.NULL:latest).put("ready",ready).put("downloadedVersion",downloaded==null?"":downloaded.optString("version")).put("bytes",bytes).put("total",total).put("lastChecked",prefs.getLong("checked",0)).put("lastAttempt",prefs.getLong("attempt",0)).put("autoCheck",prefs.getBoolean("autoCheck",true)).put("autoDownloadWifi",prefs.getBoolean("autoDownloadWifi",false)).put("installAllowed",activity.getPackageManager().canRequestPackageInstalls());
    }
    private void notifyUi(){if(!closed&&uiReady)try{activity.event("updates",status());}catch(Exception ignored){}}
    void onReady(){if(uiReady)return;uiReady=true;worker.execute(()->{
        part.delete();try{AtomicFile record=new AtomicFile(new File(directory,"ready.json"));try(InputStream in=record.openRead()){downloaded=new JSONObject(new String(BookParser.read(in,32768),StandardCharsets.UTF_8));}verify(apk,downloaded);ready=true;phase="ready";}catch(Exception ignored){downloaded=null;apk.delete();new AtomicFile(new File(directory,"ready.json")).delete();}
        notifyUi();foreground();
    });}
    void foreground(){if(uiReady&&!closed&&prefs.getBoolean("autoCheck",true)&&UpdatePolicy.shouldCheck(System.currentTimeMillis(),prefs.getLong("checked",0),prefs.getLong("attempt",0)))startCheck();}
    boolean handle(String id,String action,JSONObject p)throws Exception {
        if(!action.startsWith("updates"))return false;
        switch(action){
            case "updatesStatus":break;
            case "updatesCheck":startCheck();break;
            case "updatesConfigure":{SharedPreferences.Editor e=prefs.edit();if(p.has("autoCheck"))e.putBoolean("autoCheck",p.getBoolean("autoCheck"));if(p.has("autoDownloadWifi"))e.putBoolean("autoDownloadWifi",p.getBoolean("autoDownloadWifi"));e.apply();if(p.optBoolean("autoDownloadWifi",false)&&available()&&!ready&&wifi())startDownload(true);break;}
            case "updatesDownload":startDownload(false);break;
            case "updatesCancel":cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();break;
            case "updatesInstall":startInstall();break;
            case "updatesOpenRelease":activity.runOnUiThread(()->{try{activity.startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse(UpdatePolicy.REPOSITORY+"/releases/latest")));}catch(Exception ignored){}});break;
            default:return false;
        }
        activity.reply(id,status(),null);notifyUi();return true;
    }
    private synchronized boolean begin(String next){if(busy||closed)return false;busy=true;cancelled=false;error="";phase=next;notifyUi();return true;}
    private void finish(){HttpURLConnection c=connection;if(c!=null)c.disconnect();connection=null;busy=false;notifyUi();}
    private void fail(Exception e){error=cancelled?"Скачивание отменено":e instanceof UnknownHostException?"Не удалось подключиться к GitHub. Проверьте интернет.":e instanceof SocketTimeoutException?"GitHub не ответил вовремя. Повторите попытку.":e.getMessage()==null?"Не удалось обновить приложение":e.getMessage();phase=cancelled?(ready?"ready":available()?"available":"idle"):"error";}
    private void startCheck(){if(!begin("checking"))return;prefs.edit().putLong("attempt",System.currentTimeMillis()).apply();worker.execute(()->{try{
        HttpURLConnection c=open(UpdatePolicy.API,false);JSONObject raw;try(InputStream in=c.getInputStream()){raw=new JSONObject(new String(BookParser.read(in,512*1024),StandardCharsets.UTF_8));}finally{c.disconnect();}
        latest=UpdatePolicy.release(raw);if(ready&&downloaded!=null&&!latest.optString("sha256").equals(downloaded.optString("sha256"))){ready=false;downloaded=null;apk.delete();new AtomicFile(new File(directory,"ready.json")).delete();}prefs.edit().putString("release",raw.toString()).putLong("checked",System.currentTimeMillis()).apply();phase=ready?"ready":available()?"available":"current";
    }catch(Exception e){fail(e);}finally{finish();}if(available()&&!ready&&prefs.getBoolean("autoDownloadWifi",false)&&wifi())try{startDownload(true);}catch(Exception e){fail(e);notifyUi();}});}
    private boolean wifi(){try{ConnectivityManager cm=(ConnectivityManager)activity.getSystemService(Context.CONNECTIVITY_SERVICE);NetworkCapabilities n=cm.getNetworkCapabilities(cm.getActiveNetwork());return n!=null&&n.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)&&!cm.isActiveNetworkMetered();}catch(Exception e){return false;}}
    private void startDownload(boolean automatic)throws Exception {
        if(!available())throw new IOException("Новой версии пока нет");if(automatic&&!wifi())return;if(ready&&downloaded!=null&&downloaded.optString("sha256").equals(latest.optString("sha256")))return;if(!begin("downloading"))return;
        final JSONObject release=new JSONObject(latest.toString());bytes=0;total=release.getLong("size");worker.execute(()->{try{
            part.delete();if(automatic&&!wifi())throw new IOException("Автоскачивание остановлено: Wi-Fi недоступен.");HttpURLConnection c=open(release.getString("url"),true);try(InputStream in=c.getInputStream();FileOutputStream out=new FileOutputStream(part)){byte[] buffer=new byte[65536];int n;long lastWifiCheck=0;while((n=in.read(buffer))!=-1){checkCancelled();if(automatic&&System.currentTimeMillis()-lastWifiCheck>1000){if(!wifi())throw new IOException("Автоскачивание остановлено: Wi-Fi недоступен.");lastWifiCheck=System.currentTimeMillis();}bytes+=n;if(bytes>total||bytes>UpdatePolicy.MAX_APK)throw new IOException("Размер APK отличается от опубликованного");out.write(buffer,0,n);}out.getFD().sync();}finally{c.disconnect();}
            checkCancelled();verify(part,release);checkCancelled();if(apk.exists()&&!apk.delete())throw new IOException("Не удалось заменить файл обновления");if(!part.renameTo(apk))throw new IOException("Не удалось сохранить обновление");
            AtomicFile record=new AtomicFile(new File(directory,"ready.json"));FileOutputStream out=null;try{out=record.startWrite();out.write(release.toString().getBytes(StandardCharsets.UTF_8));record.finishWrite(out);}catch(Exception e){if(out!=null)record.failWrite(out);apk.delete();throw e;}
            downloaded=release;ready=true;phase="ready";
        }catch(Exception e){fail(e);}finally{part.delete();finish();}});
    }
    private void checkCancelled()throws IOException{if(cancelled||closed||Thread.currentThread().isInterrupted())throw new IOException("Скачивание отменено");}
    private HttpURLConnection open(String address,boolean download)throws Exception {
        for(int hop=0;hop<6;hop++){checkCancelled();if(!UpdatePolicy.transport(address,download))throw new IOException("Недопустимый адрес обновления");
            HttpURLConnection c=(HttpURLConnection)new URL(address).openConnection();connection=c;c.setInstanceFollowRedirects(false);c.setConnectTimeout(15000);c.setReadTimeout(download?60000:15000);c.setRequestProperty("User-Agent","ilenbook/"+currentVersion);if(!download)c.setRequestProperty("Accept","application/vnd.github+json");
            int status=c.getResponseCode();if(status>=300&&status<400){String location=c.getHeaderField("Location");c.disconnect();if(location==null)throw new IOException("Пустое перенаправление обновления");address=new URL(new URL(address),location).toString();continue;}
            if(status!=200){c.disconnect();throw new IOException(status==403||status==429?"GitHub временно ограничил запросы. Попробуйте позже.":"Сервер обновлений недоступен ("+status+")");}return c;
        }throw new IOException("Слишком много перенаправлений обновления");
    }
    private static String hex(byte[] bytes){StringBuilder s=new StringBuilder();for(byte b:bytes)s.append(String.format(java.util.Locale.ROOT,"%02x",b&255));return s.toString();}
    private String[] signers(PackageInfo info)throws Exception {
        Signature[] signatures=Build.VERSION.SDK_INT>=28&&info.signingInfo!=null?info.signingInfo.getApkContentsSigners():info.signatures;
        if(signatures==null)return new String[0];String[] hashes=new String[signatures.length];for(int i=0;i<hashes.length;i++)hashes[i]=hex(MessageDigest.getInstance("SHA-256").digest(signatures[i].toByteArray()));return hashes;
    }
    private void verify(File file,JSONObject descriptor)throws Exception {
        if(!file.isFile()||file.length()!=descriptor.getLong("size"))throw new IOException("APK скачан не полностью");
        MessageDigest sha=MessageDigest.getInstance("SHA-256");try(InputStream in=new FileInputStream(file)){byte[] buffer=new byte[65536];int n;while((n=in.read(buffer))!=-1){checkCancelled();sha.update(buffer,0,n);}}
        if(!hex(sha.digest()).equals(descriptor.getString("sha256")))throw new IOException("Контрольная сумма APK не совпадает");
        PackageManager pm=activity.getPackageManager();int flags=Build.VERSION.SDK_INT>=28?PackageManager.GET_SIGNING_CERTIFICATES:PackageManager.GET_SIGNATURES;PackageInfo candidate=pm.getPackageArchiveInfo(file.getAbsolutePath(),flags),installed=pm.getPackageInfo(activity.getPackageName(),flags);
        if(candidate==null||candidate.applicationInfo==null)throw new IOException("Повреждённый APK");
        UpdatePolicy.identity(candidate.packageName,candidate.versionName,code(candidate),signers(candidate),signers(installed),descriptor.getString("version"),currentCode,candidate.applicationInfo.minSdkVersion,Build.VERSION.SDK_INT);
    }
    private void startInstall()throws IOException {
        if(!ready||downloaded==null)throw new IOException("Сначала скачайте обновление");if(!begin("verifying"))return;worker.execute(()->{try{verify(apk,downloaded);activity.runOnUiThread(()->{try{
            if(!activity.getPackageManager().canRequestPackageInstalls()){installAfterPermission=true;activity.startActivityForResult(new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:"+activity.getPackageName())),420);}
            else{Uri uri=Uri.parse("content://by.list.reader.updates/ilenbook.apk");Intent intent=new Intent(Intent.ACTION_VIEW).setDataAndType(uri,"application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);intent.setClipData(ClipData.newRawUri("ilenbook update",uri));activity.startActivity(intent);}
            phase="ready";
        }catch(Exception e){error="Не удалось открыть установку: "+e.getMessage();phase="error";}finally{finish();}});
        }catch(Exception e){ready=false;downloaded=null;apk.delete();new AtomicFile(new File(directory,"ready.json")).delete();fail(e);finish();}});
    }
    boolean result(int request){if(request!=420)return false;if(installAfterPermission){installAfterPermission=false;if(activity.getPackageManager().canRequestPackageInstalls())try{startInstall();}catch(Exception e){fail(e);notifyUi();}else{error="Разрешение на установку не выдано. Его можно включить при следующей попытке.";notifyUi();}}return true;}
    void close(){closed=true;cancelled=true;HttpURLConnection c=connection;if(c!=null)c.disconnect();worker.shutdownNow();}
}
