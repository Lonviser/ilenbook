package by.list.reader;

import org.json.*;

public class PublicCatalogChecks {
    static int count;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);count++;}
    interface Action{void run()throws Exception;}
    static boolean rejects(Action action){try{action.run();return false;}catch(Exception e){return true;}}
    static final String WIKI="https://ru.wikisource.org/w/api.php?action=query&list=search&srsearch=test&sroffset=0";
    static final String ARCHIVE="https://archive.org/advancedsearch.php?output=json&rows=1&page=1";
    static JSONObject file(String name,String format,long size)throws Exception{return new JSONObject().put("name",name).put("format",format).put("size",size);}
    public static void main(String[] args)throws Exception{
        check(PublicCatalogs.supports(WIKI)&&PublicCatalogs.supports(ARCHIVE),"Known public JSON providers");
        check(!PublicCatalogs.supports("https://archive.org.evil.test/advancedsearch.php")&&!PublicCatalogs.supports("https://archive.org@evil.test/advancedsearch.php")&&!PublicCatalogs.supports("http://archive.org/advancedsearch.php"),"Provider origin boundaries");
        JSONObject wikiBody=new JSONObject().put("query",new JSONObject().put("search",new JSONArray().put(new JSONObject().put("ns",0).put("pageid",12).put("title","Русская книга").put("snippet","Фрагмент <span>текста</span>")).put(new JSONObject().put("ns",102).put("pageid",13).put("title","Страница скана")))).put("continue",new JSONObject().put("sroffset",20));
        JSONObject wiki=PublicCatalogs.parse(WIKI,wikiBody),entry=wiki.getJSONArray("entries").getJSONObject(0);
        check(wiki.getJSONArray("entries").length()==1&&entry.getString("language").equals("ru")&&entry.getString("kind").equals("book"),"Wikisource pages normalized and namespaces filtered");
        check(entry.getString("summary").equals("Фрагмент текста")&&entry.getJSONArray("formats").getString(0).equals("TXT"),"Wikisource snippets passive and text format explicit");
        check(PublicCatalogs.parameter(wiki.getString("next"),"sroffset").equals("20")&&PublicCatalogs.parameter(wiki.getString("next"),"srsearch").equals("test"),"Wikisource continuation retains query");
        check(PublicCatalogs.detailsProvider(entry.getString("detailsUrl")).equals("wikisource")&&rejects(()->PublicCatalogs.detailsProvider("https://ru.wikisource.org/w/api.php?action=parse&pageid=-1")),"Wikisource detail IDs validated");
        check(rejects(()->PublicCatalogs.parse(WIKI,new JSONObject().put("error",new JSONObject()))),"API error is not an empty successful feed");
        JSONObject doc=new JSONObject().put("identifier","book-12").put("title","Книга").put("creator",new JSONArray().put("Первый автор").put("Второй автор")).put("language",new JSONArray().put("rus").put("eng")).put("format",new JSONArray().put("EPUB").put("DjVuTXT")).put("licenseurl","https://creativecommons.org/publicdomain/zero/1.0/");
        JSONObject response=PublicCatalogs.parse(ARCHIVE,new JSONObject().put("response",new JSONObject().put("docs",new JSONArray().put(doc).put(new JSONObject(doc.toString()).put("identifier","denied").put("licenseurl","https://example.test/unknown"))).put("numFound",3).put("start",0)));
        check(response.getJSONArray("entries").length()==1,"Archive excludes items without declared free license");
        JSONObject book=response.getJSONArray("entries").getJSONObject(0);check(book.getString("language").equals("ru")&&book.getString("author").contains("Второй автор")&&book.getJSONArray("formats").length()==2,"Archive multiple authors, language and formats");
        check(book.getString("coverUrl").equals("https://archive.org/services/img/book-12")&&PublicCatalogs.parameter(response.getString("next"),"page").equals("2"),"Archive cover and pagination");
        check(!PublicCatalogs.freeLicense("https://creativecommons.org.evil.test/licenses/by/4.0/")&&!PublicCatalogs.freeLicense("https://creativecommons.org@evil.test/licenses/by/4.0/"),"License host lookalikes rejected");
        JSONObject body=new JSONObject().put("metadata",new JSONObject().put("title","Книга").put("creator","Автор").put("licenseurl","https://creativecommons.org/licenses/by/4.0/")).put("files",new JSONArray().put(file("book.epub","EPUB",1200)).put(file("book_djvu.txt","DjVuTXT",200)).put(file("secret.epub","EPUB",100).put("private",true)).put(file("huge.epub","EPUB",BookParser.MAX_BYTES+1L)).put(file("scan.pdf","Text PDF",100)).put(file("../bad.epub","EPUB",100)));
        JSONObject detail=PublicCatalogs.archiveDetails("https://archive.org/metadata/book-12",body);
        check(detail.getJSONArray("downloads").length()==2&&detail.getJSONArray("downloads").getJSONObject(0).getString("type").contains("epub"),"Download formats exclude private, oversized, traversal and unsupported files");
        JSONObject restricted=new JSONObject(body.toString());restricted.getJSONObject("metadata").put("access-restricted-item",true);check(rejects(()->PublicCatalogs.archiveDetails("https://archive.org/metadata/book-12",restricted)),"Restricted items rejected with Boolean flag");
        JSONObject dark=new JSONObject(body.toString()).put("is_dark",true);check(rejects(()->PublicCatalogs.archiveDetails("https://archive.org/metadata/book-12",dark)),"Unavailable archive item rejected");
        check(rejects(()->PublicCatalogs.detailsProvider("https://archive.org.evil.test/metadata/book-12"))&&rejects(()->PublicCatalogs.detailsProvider("https://archive.org/metadata/book-12/extra")),"Details cannot redirect provider to another origin or endpoint");
        System.out.println(count+" public catalog checks passed");
    }
}
