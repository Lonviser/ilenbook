package by.list.reader;

import java.io.IOException;
import java.net.URL;
import java.util.Arrays;
import org.json.*;

/** Release metadata is untrusted until both its origin and APK identity are checked. */
final class UpdatePolicy {
    static final String REPOSITORY="https://github.com/Lonviser/ilenbook";
    static final String API="https://api.github.com/repos/Lonviser/ilenbook/releases/latest";
    static final String CERTIFICATE="8598bb8e626eb31bc75347f26ecc5a641e20a90e1c128f6db4c2d5a8fbf2e601";
    static final long MAX_APK=150L*1024*1024;
    static String version(String value)throws IOException {
        String v=value.startsWith("v")?value.substring(1):value;
        if(!v.matches("(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})\\.(0|[1-9][0-9]{0,5})"))throw new IOException("Неподдерживаемый номер версии");
        return v;
    }
    static int compare(String a,String b)throws IOException {
        String[] x=version(a).split("\\."),y=version(b).split("\\.");
        for(int i=0;i<3;i++){int n=Integer.compare(Integer.parseInt(x[i]),Integer.parseInt(y[i]));if(n!=0)return n;}return 0;
    }
    static boolean shouldCheck(long now,long success,long attempt){return (success==0||now<success||now-success>=24L*60*60*1000)&&(attempt==0||now<attempt||now-attempt>=15L*60*1000);}
    static JSONObject release(JSONObject input)throws Exception {
        if(input.optBoolean("draft",true)||input.optBoolean("prerelease",true)||input.optString("published_at").isEmpty())throw new IOException("Релиз ещё не опубликован");
        String v=version(input.getString("tag_name")),tag="v"+v,page=REPOSITORY+"/releases/tag/"+tag,name="ilenbook-"+v+".apk",address=REPOSITORY+"/releases/download/"+tag+"/"+name;
        if(!page.equals(input.optString("html_url")))throw new IOException("Неверный источник обновления");
        JSONArray assets=input.getJSONArray("assets");JSONObject found=null;
        for(int i=0;i<assets.length();i++){JSONObject a=assets.getJSONObject(i);if(name.equals(a.optString("name"))){if(found!=null)throw new IOException("Неоднозначный файл обновления");found=a;}}
        if(found==null||!address.equals(found.optString("browser_download_url"))||!"uploaded".equals(found.optString("state")))throw new IOException("В релизе нет подходящего APK");
        long size=found.getLong("size");String digest=found.optString("digest");
        if(size<=0||size>MAX_APK||!digest.matches("sha256:[0-9a-f]{64}"))throw new IOException("Нет корректной контрольной суммы APK");
        String notes=input.optString("body","");if(notes.length()>12000)notes=notes.substring(0,12000)+"…";
        return new JSONObject().put("version",v).put("url",address).put("pageUrl",page).put("size",size).put("sha256",digest.substring(7)).put("notes",notes);
    }
    static boolean transport(String address,boolean apk) {
        try{URL u=new URL(address);if(!u.getProtocol().equals("https")||u.getUserInfo()!=null||u.getPort()!=-1&&u.getPort()!=443||u.getRef()!=null)return false;
            if(!apk)return u.getHost().equals("api.github.com")&&u.getPath().equals("/repos/Lonviser/ilenbook/releases/latest")&&u.getQuery()==null;
            if(Arrays.asList("release-assets.githubusercontent.com","objects.githubusercontent.com").contains(u.getHost()))return true;
            return u.getHost().equals("github.com")&&u.getQuery()==null&&u.getPath().matches("/Lonviser/ilenbook/releases/download/v[0-9.]+/ilenbook-[0-9.]+\\.apk");
        }catch(Exception e){return false;}
    }
    static void identity(String packageName,String apkVersion,long apkCode,String[] apkSigners,String[] installedSigners,String releaseVersion,long installedCode,int minimumSdk,int sdk)throws IOException {
        if(!"by.list.reader".equals(packageName)||!version(apkVersion).equals(version(releaseVersion)))throw new IOException("APK не соответствует обновлению ilenbook");
        if(apkCode<=installedCode)throw new IOException("APK не новее установленной версии");
        if(minimumSdk>sdk)throw new IOException("Обновлению нужна более новая версия Android");
        if(apkSigners.length!=1||installedSigners.length!=1||!CERTIFICATE.equals(apkSigners[0])||!CERTIFICATE.equals(installedSigners[0]))throw new IOException("Подпись APK отличается от релизной подписи ilenbook");
    }
}
