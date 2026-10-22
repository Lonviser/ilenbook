package by.list.reader;
import java.io.*;
import java.nio.charset.*;
import java.util.zip.*;
import org.json.*;

public class ParserChecks {
    static int count=0;
    static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);System.out.println("PASS "+name);count++;}
    static byte[] zip(String... files)throws Exception {ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(ZipOutputStream z=new ZipOutputStream(bytes)){for(int i=0;i<files.length;i+=2){z.putNextEntry(new ZipEntry(files[i]));z.write(files[i+1].getBytes("UTF-8"));z.closeEntry();}}return bytes.toByteArray();}
    static void rejects(byte[] bytes,String filename,String name)throws Exception{boolean failed=false;try{BookParser.parse(bytes,filename);}catch(Exception expected){failed=true;}check(failed,name);}
    public static void main(String[] args)throws Exception {
        JSONObject txt=BookParser.parse("Привет, мир!\n\nВторая строка.".getBytes("UTF-8"),"Проверка.txt");check(txt.getJSONArray("chapters").getJSONObject(0).getString("text").contains("Привет"),"UTF-8 TXT");
        JSONObject cp=BookParser.parse("Проверка кодировки Windows".getBytes("windows-1251"),"test.txt");check(cp.getJSONArray("chapters").getJSONObject(0).getString("text").contains("кодировки"),"Windows-1251 TXT");
        JSONObject utf16=BookParser.parse(("\uFEFF"+"Привет, UTF-16").getBytes("UTF-16LE"),"test.txt");check(utf16.getJSONArray("chapters").getJSONObject(0).getString("text").contains("Привет"),"UTF-16 TXT");
        String fb2="<?xml version='1.0' encoding='UTF-8'?><FictionBook xmlns='http://www.gribuser.ru/xml/fictionbook/2.0'><description><title-info><book-title>Тестовая книга</book-title><author><first-name>Иван</first-name><last-name>Иванов</last-name></author></title-info></description><body><section><title><p>Глава первая</p></title><p>Начало книги.</p></section><section><title><p>Глава вторая</p></title><p>Конец книги.</p></section></body></FictionBook>";
        JSONObject fb=BookParser.parse(fb2.getBytes("UTF-8"),"test.fb2");check(fb.getString("title").equals("Тестовая книга")&&fb.getString("author").equals("Иван Иванов")&&fb.getJSONArray("chapters").length()==2,"FB2 metadata and chapter order");
        JSONObject compressed=BookParser.parse(zip("nested/book.fb2",fb2),"download.zip");check(compressed.getString("title").equals("Тестовая книга"),"FB2.ZIP download");
        String content="Текст главы, достаточно длинный для чтения и проверки. Это настоящий текст тестовой книги, а не разметка.";
        byte[] epub=zip("META-INF/container.xml","<container xmlns='urn:oasis:names:tc:opendocument:xmlns:container'><rootfiles><rootfile full-path='OPS/book.opf'/></rootfiles></container>","OPS/book.opf","<package xmlns='http://www.idpf.org/2007/opf'><metadata xmlns:dc='http://purl.org/dc/elements/1.1/'><dc:title>EPUB проверка</dc:title><dc:creator>Автор</dc:creator></metadata><manifest><item id='b' href='chapters/second.xhtml'/><item id='a' href='chapters/first.xhtml'/></manifest><spine><itemref idref='a'/><itemref idref='b'/></spine></package>","OPS/chapters/second.xhtml","<html xmlns='http://www.w3.org/1999/xhtml'><body><h1>Вторая</h1><p>"+content+"</p></body></html>","OPS/chapters/first.xhtml","<html xmlns='http://www.w3.org/1999/xhtml'><body><h1>Первая</h1><script>BAD_SCRIPT</script><p>"+content+"</p></body></html>");
        JSONObject ep=BookParser.parse(epub,"test.epub");check(ep.getString("title").equals("EPUB проверка")&&ep.getJSONArray("chapters").length()==2,"EPUB container, metadata and relative paths");
        check(ep.getJSONArray("chapters").getJSONObject(0).getString("title").equals("Первая"),"EPUB follows spine instead of zip order");check(!ep.toString().contains("BAD_SCRIPT"),"EPUB scripts removed");
        rejects("<html><body>Access denied</body></html>".getBytes("UTF-8"),"download.txt","Reject HTML masquerading as TXT");
        rejects(zip("file.pdf","not a book"),"file.zip","Reject unsupported archives");rejects(new byte[0],"empty.txt","Reject empty books");
        String xxe="<?xml version='1.0'?><!DOCTYPE FictionBook [<!ENTITY leak SYSTEM 'file:///definitely-not-readable'>]><FictionBook><body><section><p>&leak;</p><p>Обычный текст.</p></section></body></FictionBook>";
        rejects(xxe.getBytes("UTF-8"),"safe.fb2","Reject external XML entity declarations");
        rejects(xxe.getBytes("UTF-16LE"),"safe.fb2","Reject UTF-16 entity declarations");
        String rich=fb2.replace("<p>Начало книги.</p>","<p>Начало <emphasis>курсив</emphasis> и <strong>жирный</strong>. <a xmlns:xlink='http://www.w3.org/1999/xlink' xlink:href='#n1'>1</a></p>").replace("</FictionBook>","<body name='notes'><section id='n1'><p>Текст сноски.</p></section></body></FictionBook>");
        JSONObject rf=BookParser.parse(rich.getBytes("UTF-8"),"rich.fb2");String markup=rf.getJSONArray("chapters").getJSONObject(0).getString("html");check(markup.contains("<em>курсив</em>")&&markup.contains("<strong>жирный</strong>"),"FB2 retains emphasis");check(rf.getJSONObject("footnotes").getString("#n1").contains("Текст сноски"),"FB2 footnote is available separately");check(rf.getJSONArray("chapters").length()==2,"Notes body is not a reading chapter");
        byte[] noteEpub=zip("META-INF/container.xml","<container><rootfiles><rootfile full-path='book.opf'/></rootfiles></container>","book.opf","<package><metadata/><manifest><item id='main' href='main.xhtml'/><item id='notes' href='notes.xhtml'/></manifest><spine><itemref idref='main'/><itemref idref='notes' linear='no'/></spine></package>","main.xhtml","<!DOCTYPE html><html><body><p>Короткая глава <a href='notes.xhtml#n1'>1</a></p></body></html>","notes.xhtml","<html><body><aside id='n1'><p>Отдельная сноска EPUB.</p></aside></body></html>");
        JSONObject ne=BookParser.parse(noteEpub,"notes.epub");check(ne.getJSONArray("chapters").length()==1&&ne.getJSONObject("footnotes").getString("notes.xhtml#n1").contains("Отдельная сноска"),"EPUB non-linear footnotes and short chapter retained");
        ElementProbe.run();
        System.out.println(count+" parser checks passed");
    }
    static class ElementProbe{static void run()throws Exception{
        org.w3c.dom.Element html=BookParser.xml(("<body><p onclick='evil()'>Текст<script>BAD</script><a href='javascript:evil()'>ссылка</a><img src='https://evil.test/x' onerror='evil()'/><strong>смысл</strong></p></body>").getBytes("UTF-8")).getDocumentElement();
        String safe=new RichText(null,null,"OPS/chapter.xhtml").html(html);check(!safe.contains("onclick")&&!safe.contains("javascript")&&!safe.contains("evil.test")&&!safe.contains("BAD")&&safe.contains("<strong>смысл</strong>"),"Rich sanitizer rejects active content and remote images");
    }}
}
