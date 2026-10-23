package by.list.reader;
import org.json.*;
import java.io.IOException;

public final class UpdateChecks {
    private static int passed;
    interface Check{void run()throws Exception;}
    private static void check(String name,Check test)throws Exception{test.run();passed++;System.out.println("PASS "+name);}
    private static void ok(boolean value){if(!value)throw new AssertionError();}
    private static void rejects(Check test)throws Exception{try{test.run();throw new AssertionError("Expected rejection");}catch(IOException expected){}}
    private static JSONObject fixture()throws Exception{return new JSONObject().put("draft",false).put("prerelease",false).put("published_at","2026-10-23T12:00:00Z").put("tag_name","v0.7.4").put("html_url",UpdatePolicy.REPOSITORY+"/releases/tag/v0.7.4").put("body","Release notes").put("assets",new JSONArray().put(new JSONObject().put("name","ilenbook-0.7.4.apk").put("state","uploaded").put("size",40205279).put("digest","sha256:"+repeat('a',64)).put("browser_download_url",UpdatePolicy.REPOSITORY+"/releases/download/v0.7.4/ilenbook-0.7.4.apk")));}
    private static String repeat(char c,int count){StringBuilder s=new StringBuilder();while(count-->0)s.append(c);return s.toString();}
    private static void identity(String pkg,String version,long code,String[] candidate,String[] installed,int sdk)throws Exception{UpdatePolicy.identity(pkg,version,code,candidate,installed,"0.7.4",10,26,sdk);}
    public static void main(String[] args)throws Exception {
        check("Stable release selects exact APK and digest",()->{JSONObject r=UpdatePolicy.release(fixture());ok(r.getString("version").equals("0.7.4")&&r.getString("sha256").length()==64&&r.getLong("size")==40205279);});
        check("Numeric version ordering 0.7.10 after 0.7.9",()->ok(UpdatePolicy.compare("v0.7.10","0.7.9")>0));
        check("Equal and older versions",()->ok(UpdatePolicy.compare("0.7.4","0.7.4")==0&&UpdatePolicy.compare("0.7.3","0.7.4")<0));
        check("Reject prerelease versions",()->rejects(()->UpdatePolicy.version("0.7.4-beta")));
        check("Reject malformed version and overflow",()->{rejects(()->UpdatePolicy.version("01.7.4"));rejects(()->UpdatePolicy.version("999999999.0.0"));});
        check("Draft is not an update",()->rejects(()->UpdatePolicy.release(fixture().put("draft",true))));
        check("Prerelease is not an update",()->rejects(()->UpdatePolicy.release(fixture().put("prerelease",true))));
        check("Unpublished release rejected",()->rejects(()->UpdatePolicy.release(fixture().put("published_at",""))));
        check("Other repository page rejected",()->rejects(()->UpdatePolicy.release(fixture().put("html_url","https://github.com/other/ilenbook/releases/tag/v0.7.4"))));
        check("APK missing from release rejected",()->rejects(()->UpdatePolicy.release(fixture().put("assets",new JSONArray()))));
        check("Duplicate APK names rejected",()->{JSONObject r=fixture();r.getJSONArray("assets").put(r.getJSONArray("assets").getJSONObject(0));rejects(()->UpdatePolicy.release(r));});
        check("APK source is fixed to release path",()->{JSONObject r=fixture();r.getJSONArray("assets").getJSONObject(0).put("browser_download_url","https://example.org/update.apk");rejects(()->UpdatePolicy.release(r));});
        check("Debug APK is not selected",()->{JSONObject r=fixture();r.getJSONArray("assets").getJSONObject(0).put("name","ilenbook-0.7.4-debug.apk");rejects(()->UpdatePolicy.release(r));});
        check("Missing digest rejected",()->{JSONObject r=fixture();r.getJSONArray("assets").getJSONObject(0).remove("digest");rejects(()->UpdatePolicy.release(r));});
        check("Oversized APK rejected",()->{JSONObject r=fixture();r.getJSONArray("assets").getJSONObject(0).put("size",UpdatePolicy.MAX_APK+1);rejects(()->UpdatePolicy.release(r));});
        check("Empty APK rejected",()->{JSONObject r=fixture();r.getJSONArray("assets").getJSONObject(0).put("size",0);rejects(()->UpdatePolicy.release(r));});
        check("Incomplete uploaded asset rejected",()->{JSONObject r=fixture();r.getJSONArray("assets").getJSONObject(0).put("state","new");rejects(()->UpdatePolicy.release(r));});
        check("Notes are bounded",()->ok(UpdatePolicy.release(fixture().put("body",repeat('x',20000))).getString("notes").length()==12001));
        check("Official API is allowed",()->ok(UpdatePolicy.transport(UpdatePolicy.API,false)));
        check("Official APK and CDN are allowed",()->ok(UpdatePolicy.transport(UpdatePolicy.release(fixture()).getString("url"),true)&&UpdatePolicy.transport("https://release-assets.githubusercontent.com/example?signature=abc",true)));
        check("HTTP, host lookalikes, credentials and ports rejected",()->{for(String u:new String[]{"http://github.com/Lonviser/ilenbook/releases/download/v0.7.4/ilenbook-0.7.4.apk","https://github.com.evil.test/Lonviser/ilenbook/releases/download/v0.7.4/ilenbook-0.7.4.apk","https://a@github.com/Lonviser/ilenbook/releases/download/v0.7.4/ilenbook-0.7.4.apk","https://github.com:8080/Lonviser/ilenbook/releases/download/v0.7.4/ilenbook-0.7.4.apk"})ok(!UpdatePolicy.transport(u,true));});
        check("Other repository and API query rejected",()->ok(!UpdatePolicy.transport("https://github.com/other/ilenbook/releases/download/v0.7.4/ilenbook-0.7.4.apk",true)&&!UpdatePolicy.transport(UpdatePolicy.API+"?url=evil",false)));
        String[] valid={UpdatePolicy.CERTIFICATE},bad={repeat('b',64)};
        check("Own newer APK accepted",()->identity("by.list.reader","0.7.4",11,valid,valid,35));
        check("Wrong package rejected",()->rejects(()->identity("other.app","0.7.4",11,valid,valid,35)));
        check("APK version does not match release",()->rejects(()->identity("by.list.reader","0.7.5",11,valid,valid,35)));
        check("Equal version code and downgrade rejected",()->{rejects(()->identity("by.list.reader","0.7.4",10,valid,valid,35));rejects(()->identity("by.list.reader","0.7.4",9,valid,valid,35));});
        check("Wrong signer and CI key rejected",()->{rejects(()->identity("by.list.reader","0.7.4",11,bad,valid,35));rejects(()->identity("by.list.reader","0.7.4",11,valid,bad,35));});
        check("Multiple signers rejected",()->rejects(()->identity("by.list.reader","0.7.4",11,new String[]{UpdatePolicy.CERTIFICATE,UpdatePolicy.CERTIFICATE},valid,35)));
        check("Unsupported Android rejected",()->rejects(()->identity("by.list.reader","0.7.4",11,valid,valid,25)));
        check("First run checks automatically",()->ok(UpdatePolicy.shouldCheck(1000000,0,0)));
        check("Daily check and error retry backoff",()->{long now=200000000;ok(!UpdatePolicy.shouldCheck(now,now-1000,now-1000)&&!UpdatePolicy.shouldCheck(now,0,now-1000)&&UpdatePolicy.shouldCheck(now,now-90000000,now-1000000));});
        check("Clock moved backwards can recover",()->ok(UpdatePolicy.shouldCheck(1000,2000,2000)));
        System.out.println(passed+" update checks passed");
    }
}
