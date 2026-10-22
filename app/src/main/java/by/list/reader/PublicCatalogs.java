package by.list.reader;

import java.io.IOException;
import java.net.*;
import java.util.*;
import org.json.*;

/** Public JSON catalogs are adapted to the same passive book model as OPDS. */
final class PublicCatalogs {
    static boolean supports(String address)throws Exception {
        URL u=new URL(address);
        return u.getProtocol().equals("https")&&u.getPort()==-1&&u.getUserInfo()==null&&
            (u.getHost().equals("ru.wikisource.org")&&u.getPath().equals("/w/api.php")||u.getHost().equals("archive.org")&&u.getPath().equals("/advancedsearch.php"));
    }
    static String detailsProvider(String address)throws Exception {
        URL u=new URL(address);if(!u.getProtocol().equals("https")||u.getPort()!=-1||u.getUserInfo()!=null)throw new IOException("Неверный адрес источника");
        if(u.getHost().equals("archive.org")&&u.getPath().matches("/metadata/[a-zA-Z0-9_.-]{1,200}"))return "archive";
        if(u.getHost().equals("ru.wikisource.org")&&u.getPath().equals("/w/api.php")&&"parse".equals(parameter(address,"action"))&&parameter(address,"pageid").matches("[1-9][0-9]{0,11}"))return "wikisource";
        throw new IOException("Неизвестный источник книги");
    }
    static JSONObject parse(String address,JSONObject body)throws Exception {
        if(body.has("error"))throw new IOException("Источник временно не выполнил поиск");
        if(new URL(address).getHost().equals("ru.wikisource.org"))return wiki(address,body);
        return archive(address,body);
    }
    private static JSONObject wiki(String address,JSONObject body)throws Exception {
        JSONObject query=body.optJSONObject("query");if(query==null)throw new IOException("Нет ответа поиска Викитеки");JSONArray rows=query.optJSONArray("search"),entries=new JSONArray();
        if(rows==null)rows=new JSONArray();
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null||row.optInt("ns",-1)!=0||row.optLong("pageid")<=0)continue;String title=row.optString("title");
            String id=String.valueOf(row.getLong("pageid"));
            entries.put(new JSONObject().put("id","wikisource:"+id).put("kind","book").put("title",title).put("author","").put("language","ru").put("summary",BookParser.strip(row.optString("snippet"))).put("formats",new JSONArray().put("TXT")).put("downloads",new JSONArray()).put("provider","wikisource").put("detailsUrl","https://ru.wikisource.org/w/api.php?action=parse&pageid="+id+"&prop=text%7Crevid&format=json&formatversion=2&disableeditsection=1").put("externalUrl","https://ru.wikisource.org/?curid="+id));
        }
        JSONObject result=new JSONObject().put("url",address).put("title","Викитека · русские тексты").put("entries",entries);JSONObject more=body.optJSONObject("continue");if(more!=null&&more.has("sroffset"))result.put("next",withParameter(address,"sroffset",String.valueOf(more.getInt("sroffset"))));return result;
    }
    private static JSONObject archive(String address,JSONObject body)throws Exception {
        JSONObject response=body.optJSONObject("response");if(response==null)throw new IOException("Нет ответа поиска Internet Archive");JSONArray rows=response.optJSONArray("docs"),entries=new JSONArray();if(rows==null)rows=new JSONArray();
        for(int i=0;i<rows.length();i++){JSONObject row=rows.optJSONObject(i);if(row==null)continue;String id=row.optString("identifier");if(!id.matches("[a-zA-Z0-9_.-]{1,200}")||!freeLicense(row.opt("licenseurl")))continue;
            JSONArray formats=formats(row.opt("format"));if(formats.length()==0)continue;
            entries.put(new JSONObject().put("id","archive:"+id).put("kind","book").put("title",text(row.opt("title"))).put("author",text(row.opt("creator"))).put("language",language(row.opt("language"))).put("summary","Свободное издание · Internet Archive. Для сканов качество распознавания текста может отличаться.").put("formats",formats).put("downloads",new JSONArray()).put("provider","archive").put("detailsUrl","https://archive.org/metadata/"+id).put("externalUrl","https://archive.org/details/"+id).put("coverUrl","https://archive.org/services/img/"+id));
        }
        JSONObject result=new JSONObject().put("url",address).put("title","Internet Archive · свободные тексты").put("entries",entries);int page=1;try{page=Integer.parseInt(parameter(address,"page"));}catch(Exception ignored){}if(rows.length()>0&&response.optLong("start")+rows.length()<response.optLong("numFound"))result.put("next",withParameter(address,"page",String.valueOf(page+1)));return result;
    }
    static JSONObject archiveDetails(String address,JSONObject body)throws Exception {
        detailsProvider(address);JSONObject m=body.optJSONObject("metadata");
        if(m==null||body.optBoolean("is_dark")||m.optBoolean("access-restricted-item")||!freeLicense(m.opt("licenseurl")))throw new IOException("Это издание недоступно для свободной загрузки");
        String id=new URL(address).getPath().substring("/metadata/".length());JSONArray files=body.optJSONArray("files"),epubs=new JSONArray(),texts=new JSONArray();if(files==null)files=new JSONArray();
        for(int i=0;i<files.length();i++){JSONObject f=files.optJSONObject(i);if(f==null||f.optBoolean("private")||"true".equalsIgnoreCase(f.optString("private")))continue;String name=f.optString("name"),format=f.optString("format");long size=f.optLong("size",-1);if(name.isEmpty()||size<1||size>BookParser.MAX_BYTES||name.contains("..")||name.startsWith("/"))continue;
            boolean epub=name.toLowerCase(Locale.ROOT).endsWith(".epub")&&format.equalsIgnoreCase("EPUB"),txt=name.toLowerCase(Locale.ROOT).endsWith(".txt")&&Arrays.asList("djvutxt","text","utf-8","text (utf-8)").contains(format.toLowerCase(Locale.ROOT));if(!epub&&!txt)continue;
            JSONObject d=new JSONObject().put("url","https://archive.org/download/"+id+"/"+encode(name)).put("type",epub?"application/epub+zip":"text/plain").put("label",(epub?"EPUB":"TXT")+" · "+name.substring(0,Math.min(name.length(),100)));if(epub&&epubs.length()<8)epubs.put(d);else if(txt&&texts.length()<8)texts.put(d);
        }
        JSONArray downloads=new JSONArray();for(int i=0;i<epubs.length();i++)downloads.put(epubs.get(i));for(int i=0;i<texts.length();i++)downloads.put(texts.get(i));
        return new JSONObject().put("title",text(m.opt("title"))).put("author",text(m.opt("creator"))).put("summary",BookParser.strip(text(m.opt("description")))).put("downloads",downloads).put("externalUrl","https://archive.org/details/"+id);
    }
    static boolean freeLicense(Object value){for(String s:strings(value))try{URL u=new URL(s);if(Arrays.asList("http","https").contains(u.getProtocol())&&u.getHost().equals("creativecommons.org")&&u.getUserInfo()==null&&(u.getPath().startsWith("/publicdomain/")||u.getPath().startsWith("/licenses/")))return true;}catch(Exception ignored){}return false;}
    private static JSONArray formats(Object value){Set<String> result=new LinkedHashSet<>();for(String s:strings(value)){s=s.toLowerCase(Locale.ROOT);if(s.equals("epub"))result.add("EPUB");else if(Arrays.asList("djvutxt","text","utf-8","text (utf-8)").contains(s))result.add("TXT");}return new JSONArray(result);}
    private static List<String> strings(Object value){List<String> out=new ArrayList<>();if(value instanceof JSONArray){JSONArray a=(JSONArray)value;for(int i=0;i<a.length();i++)if(a.opt(i) instanceof String)out.add(a.optString(i));}else if(value instanceof String)out.add((String)value);return out;}
    private static String text(Object value){return String.join(" · ",strings(value));}
    private static String language(Object value){List<String> values=strings(value);for(String s:values)if(Arrays.asList("ru","rus","russian").contains(s.toLowerCase(Locale.ROOT)))return "ru";if(values.isEmpty())return "";String s=values.get(0).toLowerCase(Locale.ROOT);if(Arrays.asList("eng","english").contains(s))return "en";if(Arrays.asList("fra","fre","french").contains(s))return "fr";if(Arrays.asList("deu","ger","german").contains(s))return "de";return s;}
    private static String encode(String value)throws Exception{return URLEncoder.encode(value,"UTF-8").replace("+","%20");}
    static String parameter(String address,String key)throws Exception{String q=new URL(address).getQuery();if(q!=null)for(String pair:q.split("&")){String[] parts=pair.split("=",2);if(URLDecoder.decode(parts[0],"UTF-8").equals(key))return parts.length==2?URLDecoder.decode(parts[1],"UTF-8"):"";}return "";}
    private static String withParameter(String address,String key,String value)throws Exception{URL u=new URL(address);List<String> parts=new ArrayList<>();if(u.getQuery()!=null)for(String p:u.getQuery().split("&"))if(!URLDecoder.decode(p.split("=",2)[0],"UTF-8").equals(key))parts.add(p);parts.add(encode(key)+"="+encode(value));return u.getProtocol()+"://"+u.getAuthority()+u.getPath()+"?"+String.join("&",parts);}
}
