package by.list.reader;

import android.app.Instrumentation;
import android.content.*;
import android.os.Bundle;
import java.io.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Separate test APK; fixture tokens and transport never enter the production UI. */
public class YandexDeviceChecks extends Instrumentation {
    int count;void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;Bundle b=new Bundle();b.putString("check",name);sendStatus(0,b);}
    @Override public void onCreate(Bundle args){super.onCreate(args);start();}
    @Override public void onStart(){MainActivity a=null;File root=null;try{
        a=(MainActivity)startActivitySync(new Intent(getTargetContext(),MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));waitForIdleSync();root=new File(getTargetContext().getCacheDir(),"yandex-device-checks-"+UUID.randomUUID());root.mkdirs();
        final String secret="y0_fixture_encryption_only_not_a_real_token";final String[] account={"first"};final boolean[] failQuotes={false},cancel={false};final YandexAccount[] current={null};final int[] libraryCalls={0};
        YandexAccount.Transport fixture=(method,url,body,token)->{
            if(!secret.equals(token))throw new AssertionError("Token was changed");if(!YandexProtocol.trustedApi(url))throw new AssertionError("Untrusted transport");
            if(url.endsWith("/profile"))return new JSONObject().put("user",new JSONObject().put("uuid",account[0]).put("name","Fixture").put("login","fixture"));
            if(url.contains("/profile/library_cards")){libraryCalls[0]++;if(cancel[0]){current[0].handle("cancel","yandexCancel",new JSONObject());cancel[0]=false;}JSONArray rows=new JSONArray();if(url.contains("page=1&")){for(int i=0;i<100;i++)rows.put(new JSONObject().put("state","reading").put("reading_progress",i).put("book",new JSONObject().put("uuid","book"+i).put("title","Книга "+i)));}else rows.put(new JSONObject().put("state","finished").put("book",new JSONObject().put("uuid","last-book").put("title","Последняя")));return new JSONObject().put("library_cards",rows);}
            if(url.contains("/profile/bookshelves"))return new JSONObject().put("bookshelves",new JSONArray().put(new JSONObject().put("uuid","shelf").put("title","Полка")));
            if(url.contains("/quotes")){if(failQuotes[0])throw new IOException("Fixture quote endpoint unavailable");return new JSONObject().put("quotes",new JSONArray().put(new JSONObject().put("item_uuid","book0").put("content","Цитата").put("cfi","epubcfi(/6/2)")));}
            if(url.equals(YandexProtocol.GRAPHQL)){if(!"POST".equals(method)||!body.getString("query").startsWith("query "))throw new AssertionError("Only query POST allowed");return new JSONObject("{\"data\":{\"user\":{\"loyalty\":{\"statistics\":{\"daysPerMonth\":8,\"streak\":3,\"streakRecord\":9,\"todayTime\":90}}}}}");}
            if(url.endsWith("/sync_state"))return new JSONObject().put("opaque_state","not-a-write-position");throw new IOException("Unexpected fixture request");
        };
        YandexAccount client=new YandexAccount(a,fixture,root);current[0]=client;Method store=YandexAccount.class.getDeclaredMethod("storeToken",String.class);store.setAccessible(true);Method load=YandexAccount.class.getDeclaredMethod("token");load.setAccessible(true);Method sync=YandexAccount.class.getDeclaredMethod("sync");sync.setAccessible(true);
        store.invoke(client,secret);check(secret.equals(load.invoke(client)),"Android Keystore encryption round trip");String ciphertext=new String(BookParser.read(new FileInputStream(new File(root,"yandex-token.json")),16000),"UTF-8");check(!ciphertext.contains(secret)&&new JSONObject(ciphertext).has("iv"),"Credential file has ciphertext only");
        JSONObject first=(JSONObject)sync.invoke(client);check(first.getJSONArray("books").length()==101&&libraryCalls[0]==2,"Library pagination includes second page");check(first.getJSONArray("quotes").length()==1&&first.getJSONArray("shelves").length()==1,"Quotes and shelves loaded");check(first.getJSONObject("statistics").getInt("streak_record")==9,"GraphQL statistics normalized");check(!first.toString().contains(secret),"Snapshot contains no access token");check(!first.getJSONObject("positionProbe").getBoolean("writeSupported"),"State probe never advertises position write");
        failQuotes[0]=true;JSONObject partial=(JSONObject)sync.invoke(client);check(partial.getJSONArray("books").length()==101&&partial.getJSONArray("quotes").length()==1&&partial.getJSONArray("errors").length()==1,"Partial failure preserves last good quotes");
        account[0]="second";JSONObject switched=(JSONObject)sync.invoke(client);check(!switched.has("quotes")&&switched.getJSONObject("profile").getString("id").equals("second"),"Account switch never reuses previous quotes");
        byte[] before=BookParser.read(new FileInputStream(new File(root,"yandex-snapshot.json")),12*1024*1024);cancel[0]=true;boolean cancelled=false;try{sync.invoke(client);}catch(InvocationTargetException expected){cancelled=true;}check(cancelled,"Cancellation aborts running native sync");byte[] after=BookParser.read(new FileInputStream(new File(root,"yandex-snapshot.json")),12*1024*1024);check(Arrays.equals(before,after),"Cancelled native sync leaves cache unchanged");
        client.handle("logout","yandexDisconnect",new JSONObject());check(!new File(root,"yandex-token.json").exists()&&!new File(root,"yandex-snapshot.json").exists(),"Disconnect deletes credentials and snapshot");
        Bundle result=new Bundle();result.putString("result",count+" native Yandex checks passed (fixture account, no real credentials)");finish(ActivityResult.OK,result);
    }catch(Throwable e){Bundle result=new Bundle();result.putString("failure",e.toString());finish(ActivityResult.ERROR,result);}finally{if(root!=null){File[] files=root.listFiles();if(files!=null)for(File f:files)f.delete();root.delete();}}}
    static class ActivityResult{static final int OK=-1,ERROR=0;}
}
