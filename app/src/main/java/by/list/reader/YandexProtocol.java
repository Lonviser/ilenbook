package by.list.reader;

import java.io.IOException;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import org.json.*;

/** Bounded data adapter. No credentials, Android dependencies, or write API calls. */
final class YandexProtocol {
    static final String CLIENT="4483e97bab6e486a9822973109a14d05";
    static final String CALLBACK="yx"+CLIENT+".oauth.yandex.ru";
    static final String REST="https://api.bookmate.yandex.net/api/v5";
    static final String GRAPHQL="https://api-gateway.bookmate.yandex.net/graphql";
    static final String STATISTICS="query Statistics($requestTime: DateTime!) { user(login: null) { loyalty(currentTime: $requestTime) { statistics { daysPerMonth streak streakRecord todayTime } } } }";
    static Object normalize(Object input)throws JSONException {
        if(input instanceof JSONObject){JSONObject result=new JSONObject();JSONObject o=(JSONObject)input;for(Iterator<String> i=o.keys();i.hasNext();){String key=i.next();result.put(key.replaceAll("([a-z0-9])([A-Z])","$1_$2").replace('-','_').toLowerCase(Locale.ROOT),normalize(o.get(key)));}return result;}
        if(input instanceof JSONArray){JSONArray result=new JSONArray();JSONArray a=(JSONArray)input;for(int i=0;i<a.length();i++)result.put(normalize(a.get(i)));return result;}return input;
    }
    static String text(JSONObject o,String key,int max){String s=o==null?"":o.optString(key,"");return "null".equals(s)?"":s.substring(0,Math.min(s.length(),max));}
    static String callbackToken(String value,String state)throws Exception {
        URI uri=new URI(value);if(!"https".equalsIgnoreCase(uri.getScheme())||!CALLBACK.equalsIgnoreCase(uri.getHost())||uri.getUserInfo()!=null||uri.getPort()!=-1)throw new IOException("Неверный адрес возврата Яндекса");
        Map<String,List<String>> p=new HashMap<>();for(String part:new String[]{uri.getRawQuery(),uri.getRawFragment()})if(part!=null)for(String pair:part.split("&")){String[] kv=pair.split("=",2);String k=URLDecoder.decode(kv[0],"UTF-8"),v=kv.length>1?URLDecoder.decode(kv[1],"UTF-8"):"";p.computeIfAbsent(k,x->new ArrayList<>()).add(v);}
        if(state==null||state.length()<24||!p.containsKey("state")||p.get("state").size()!=1||!MessageDigest.isEqual(state.getBytes(StandardCharsets.UTF_8),p.get("state").get(0).getBytes(StandardCharsets.UTF_8)))throw new IOException("Ответ относится к другому входу. Начните заново.");
        if(p.containsKey("error"))throw new IOException("Яндекс не разрешил вход");
        if(!p.containsKey("access_token")||p.get("access_token").size()!=1||!p.get("access_token").get(0).matches("[A-Za-z0-9._~+-]{16,4096}"))throw new IOException("Яндекс не вернул токен доступа");return p.get("access_token").get(0);
    }
    static boolean trustedApi(String value){try{URI u=new URI(value);return "https".equals(u.getScheme())&&u.getUserInfo()==null&&u.getPort()==-1&&Arrays.asList("api.bookmate.yandex.net","api-gateway.bookmate.yandex.net").contains(u.getHost());}catch(Exception e){return false;}}
    static String cover(JSONObject b){JSONObject c=b.optJSONObject("cover");if(c==null)c=b.optJSONObject("image");if(c==null)return "";for(String k:new String[]{"large","url","small"}){String s=text(c,k,1800);try{URI u=new URI(s);if("https".equals(u.getScheme())&&u.getHost()!=null&&u.getUserInfo()==null)return s;}catch(Exception ignored){}}return "";}
    static String author(JSONObject b)throws Exception{JSONArray a=b.optJSONArray("authors_objects");if(a==null)a=b.optJSONArray("authors");if(a!=null){List<String> names=new ArrayList<>();for(int i=0;i<Math.min(a.length(),15);i++){JSONObject p=a.optJSONObject(i);if(p!=null&&!text(p,"name",200).isEmpty())names.add(text(p,"name",200));else if(a.opt(i) instanceof String)names.add(a.getString(i));}if(!names.isEmpty())return String.join(", ",names);}return text(b,"authors",500);}
    static JSONObject card(JSONObject raw)throws Exception {
        JSONObject c=(JSONObject)normalize(raw),b=c.optJSONObject("book");String kind="books";if(b==null){b=c.optJSONObject("audiobook");kind="audiobooks";}if(b==null){b=c.optJSONObject("comicbook");kind="comicbooks";}if(b==null)return null;
        String id=text(b,"uuid",100),title=text(b,"title",400);if(title.isEmpty())title=text(b,"name",400);if(!id.matches("[a-zA-Z0-9-]{1,100}")||title.isEmpty())return null;
        Object progress=c.opt("reading_progress");Double percent=null;if(progress instanceof Number){double n=((Number)progress).doubleValue();if(Double.isFinite(n)&&n>=0&&n<=100)percent=n;}
        return new JSONObject().put("id","yandex-sync-"+id+"-"+kind).put("remoteId",id).put("title",title).put("author",author(b)).put("url","https://books.yandex.ru/"+kind+"/"+id).put("coverUrl",cover(b)).put("format","Яндекс").put("added",c.optLong("added_at",0)).put("remoteState",text(c,"state",40)).put("remoteProgress",percent==null?JSONObject.NULL:percent).put("yandexAccount",true).put("kind",kind);
    }
    static JSONArray cards(JSONArray rows)throws Exception{JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();for(int i=0;i<rows.length();i++){JSONObject raw=rows.optJSONObject(i);if(raw==null)continue;JSONObject c=card(raw);if(c!=null&&seen.add(c.getString("id")))result.put(c);}return result;}
    static JSONObject profile(JSONObject response)throws Exception{JSONObject n=(JSONObject)normalize(response),u=n.optJSONObject("user");if(u==null)throw new IOException("Не удалось получить профиль Яндекс Книг");String id=text(u,"uuid",100);if(id.isEmpty())id=text(u,"login",200);if(id.isEmpty())throw new IOException("Профиль не содержит идентификатор");return new JSONObject().put("id",id).put("name",text(u,"name",200)).put("login",text(u,"login",200));}
    static JSONArray quotes(JSONArray input)throws Exception{JSONArray result=new JSONArray();Set<String> seen=new HashSet<>();for(int i=0;i<Math.min(input.length(),2000);i++){JSONObject raw=input.optJSONObject(i);if(raw==null)continue;JSONObject q=(JSONObject)normalize(raw);if("deleted".equals(q.optString("state")))continue;String content=text(q,"content",12000);if(content.isEmpty())continue;JSONObject b=q.optJSONObject("book");String item=text(q,"item_uuid",100);if(item.isEmpty()&&b!=null)item=text(b,"uuid",100);String cfi=text(q,"cfi",1800);String identity=item+'|'+cfi+'|'+content+'|'+text(q,"created_at",40);String id=UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8)).toString();if(!seen.add(id))continue;result.put(new JSONObject().put("id",id).put("itemId",item).put("quote",content).put("text",text(q,"comment",12000)).put("cfi",cfi).put("title",b==null?"Книга из Яндекса":text(b,"title",400).isEmpty()?text(b,"name",400):text(b,"title",400)).put("author",b==null?text(q,"authors",500):author(b)).put("created",q.opt("created_at")).put("position",new JSONObject().put("startNodeXPath",text(q,"start_node_xpath",1800)).put("startNodeOffset",q.opt("start_node_offset"))));}return result;}
}
