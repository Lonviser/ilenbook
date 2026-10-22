package by.list.reader;

import java.io.*;
import java.nio.charset.*;
import java.util.*;
import java.util.zip.*;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.json.*;

/** Reads publications; rich content is reduced to passive tags by RichText. */
public final class BookParser {
    public static final int MAX_BYTES = 40 * 1024 * 1024;
    public static byte[] read(InputStream in, int limit) throws IOException {
        try (InputStream src=in; ByteArrayOutputStream out=new ByteArrayOutputStream()) {
            byte[] b=new byte[8192]; int n;
            while((n=src.read(b))!=-1) { if(out.size()+n>limit) throw new IOException("Файл слишком большой (максимум 40 МБ)"); out.write(b,0,n); }
            return out.toByteArray();
        }
    }
    public static Document xml(byte[] data) throws Exception {
        // Android's bundled XML provider does not expose every SAX security feature.
        // Reject DTDs outright, including UTF-16 declarations, before parsing.
        String declaration=new String(data,StandardCharsets.UTF_8).replace("\u0000","").toUpperCase(Locale.ROOT);
        if(declaration.contains("<!DOCTYPE")||declaration.contains("<!ENTITY"))throw new IOException("XML с DTD или внешними сущностями не поддерживается");
        DocumentBuilderFactory f=DocumentBuilderFactory.newInstance(); f.setNamespaceAware(true);
        for(String feature:new String[]{"http://xml.org/sax/features/external-general-entities","http://xml.org/sax/features/external-parameter-entities"})try{f.setFeature(feature,false);}catch(ParserConfigurationException ignored){}
        try{f.setXIncludeAware(false);}catch(UnsupportedOperationException ignored){} f.setExpandEntityReferences(false);
        DocumentBuilder builder=f.newDocumentBuilder(); builder.setEntityResolver((p,s)->new org.xml.sax.InputSource(new StringReader("")));
        return builder.parse(new ByteArrayInputStream(data));
    }
    public static String name(Node n) { return n.getLocalName()!=null?n.getLocalName():n.getNodeName().replaceFirst("^.*:",""); }
    public static List<Element> all(Element e,String tag) {
        List<Element> r=new ArrayList<>(); NodeList ns=e.getElementsByTagNameNS("*",tag);
        for(int i=0;i<ns.getLength();i++) r.add((Element)ns.item(i)); return r;
    }
    public static String first(Element e,String tag,String fallback) { List<Element> ns=all(e,tag); return ns.isEmpty()?fallback:ns.get(0).getTextContent().trim(); }
    private static JSONObject chapter(String title,String text) throws Exception { return new JSONObject().put("title",title).put("text",text.trim()); }
    public static JSONObject parse(byte[] bytes,String filename) throws Exception {
        int expanded=0;
        String ext=filename.toLowerCase(Locale.ROOT); JSONObject book=new JSONObject(); JSONArray chapters=new JSONArray();JSONObject footnotes=new JSONObject();book.put("footnotes",footnotes);
        book.put("title",filename.replaceAll("(?i)\\.(fb2|epub|txt|zip)$","")).put("author","Неизвестный автор");
        if(bytes.length>3 && bytes[0]=='P' && bytes[1]=='K') {
            Map<String,byte[]> z=new LinkedHashMap<>();
            try(ZipInputStream zin=new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry ent; while((ent=zin.getNextEntry())!=null) {
                    if(ent.isDirectory())continue;
                    ByteArrayOutputStream out=new ByteArrayOutputStream(); byte[] buf=new byte[8192]; int n;
                    while((n=zin.read(buf))!=-1) { expanded+=n; if(expanded>80*1024*1024)throw new IOException("Архив превышает допустимый размер");out.write(buf,0,n); }
                    z.put(ent.getName(),out.toByteArray()); if(z.size()>3000)throw new IOException("Слишком много файлов в архиве");
                }
            }
            if(z.containsKey("META-INF/container.xml")) {
                Element container=xml(z.get("META-INF/container.xml")).getDocumentElement();
                String opfPath=all(container,"rootfile").get(0).getAttribute("full-path");
                if(!z.containsKey(opfPath))throw new IOException("Не найдено описание EPUB");
                Element opf=xml(z.get(opfPath)).getDocumentElement();
                book.put("title",first(opf,"title",book.getString("title"))).put("author",first(opf,"creator","Неизвестный автор")).put("format","EPUB");
                String dir=opfPath.contains("/")?opfPath.substring(0,opfPath.lastIndexOf('/')+1):"";
                Map<String,Element> items=new HashMap<>();
                String coverId=""; for(Element m:all(opf,"meta"))if("cover".equals(m.getAttribute("name")))coverId=m.getAttribute("content");
                for(Element item:all(opf,"item")) {
                    items.put(item.getAttribute("id"),item);
                    if(item.getAttribute("properties").contains("cover-image") || item.getAttribute("id").equals(coverId)) {
                        byte[] img=z.get(resolve(dir,item.getAttribute("href"))); String mime=item.getAttribute("media-type");
                        if(img!=null&&img.length<2*1024*1024&&mime.matches("image/(jpeg|png|webp)"))book.put("cover","data:"+mime+";base64,"+Base64.getEncoder().encodeToString(img));
                    }
                }
                for(Element ref:all(opf,"itemref")) {
                    Element item=items.get(ref.getAttribute("idref")); if(item==null)continue;
                    byte[] html=z.get(resolve(dir,item.getAttribute("href"))); if(html==null)continue;
                    String raw=new String(html,StandardCharsets.UTF_8); String title=""; String text;
                    try {
                        byte[] safeXml=new String(html,StandardCharsets.UTF_8).replaceFirst("(?is)<!DOCTYPE\\s+html\\s+(?:PUBLIC|SYSTEM)\\s+[^\\[>]*>","").getBytes(StandardCharsets.UTF_8);
                        Element doc=xml(safeXml).getDocumentElement();
                        for(String h:new String[]{"h1","h2","h3"}) { title=first(doc,h,""); if(!title.isEmpty())break; }
                        List<Element> bodies=all(doc,"body");Element body=bodies.isEmpty()?doc:bodies.get(0);StringBuilder sb=new StringBuilder(); toText(body,sb); text=sb.toString();
                        String path=resolve(dir,item.getAttribute("href"));RichText rich=new RichText(z,null,path);rich.collectFootnotes(body,footnotes,false);if("no".equals(ref.getAttribute("linear"))){rich.collectAllFootnotes(body,footnotes);continue;}
                        if(!text.trim().isEmpty()){chapters.put(chapter(title.isEmpty()?"Раздел "+(chapters.length()+1):title,text).put("html",rich.html(body)).put("path",path));continue;}
                    } catch(Exception ex) { text=strip(raw); }
                    if(!"no".equals(ref.getAttribute("linear"))&&!text.trim().isEmpty()) chapters.put(chapter(title.isEmpty()?"Раздел "+(chapters.length()+1):title,text).put("path",resolve(dir,item.getAttribute("href"))));
                }
            } else {
                for(Map.Entry<String,byte[]> entry:z.entrySet()) if(entry.getKey().toLowerCase(Locale.ROOT).endsWith(".fb2"))return parse(entry.getValue(),entry.getKey());
                throw new IOException("В архиве нет FB2 или структуры EPUB");
            }
        } else if(ext.endsWith(".fb2") || new String(bytes,0,Math.min(bytes.length,400),StandardCharsets.UTF_8).contains("FictionBook")) {
            Element root=xml(bytes).getDocumentElement();
            if(!"FictionBook".equals(name(root)))throw new IOException("Это не FB2");
            book.put("title",first(root,"book-title",book.getString("title"))).put("format","FB2");
            Map<String,String> images=new HashMap<>();int imageBudget=0;for(Element bin:all(root,"binary")){String type=bin.getAttribute("content-type"),data=bin.getTextContent().replaceAll("\\s","");if(type.matches("image/(jpeg|png|webp)")&&data.matches("[A-Za-z0-9+/=]+")&&data.length()<2800000&&(imageBudget+=data.length())<8*1024*1024)images.put(bin.getAttribute("id"),"data:"+type+";base64,"+data);}
            RichText rich=new RichText(null,images,"");
            for(Element body:all(root,"body"))if("notes".equals(body.getAttribute("name"))||"comments".equals(body.getAttribute("name")))rich.collectFootnotes(body,footnotes,true);
            List<Element> sequences=all(root,"sequence");if(!sequences.isEmpty())book.put("series",sequences.get(0).getAttribute("name")).put("seriesNumber",sequences.get(0).getAttribute("number"));
            List<Element> authors=all(root,"author"); if(!authors.isEmpty()) { Element a=authors.get(0);book.put("author",(first(a,"first-name","")+" "+first(a,"middle-name","")+" "+first(a,"last-name","")).replaceAll(" +"," ").trim()); }
            List<Element> coverpages=all(root,"coverpage");
            if(!coverpages.isEmpty()) { List<Element> imgs=all(coverpages.get(0),"image"); if(!imgs.isEmpty()) {
                String id=imgs.get(0).getAttributeNS("http://www.w3.org/1999/xlink","href").replaceFirst("^#","");
                for(Element bin:all(root,"binary"))if(bin.getAttribute("id").equals(id)&&bin.getAttribute("content-type").matches("image/(jpeg|png|webp)")) {
                    String data=bin.getTextContent().replaceAll("\\s",""); if(data.length()<2800000)book.put("cover","data:"+bin.getAttribute("content-type")+";base64,"+data);
                }
            }}
            for(Element body:all(root,"body")) { if("notes".equals(body.getAttribute("name"))||"comments".equals(body.getAttribute("name")))continue;
                boolean hasSections=false;
                for(Node node=body.getFirstChild();node!=null;node=node.getNextSibling())if(node instanceof Element && name(node).equals("section")) {
                    hasSections=true;StringBuilder sb=new StringBuilder();toText(node,sb); if(sb.toString().trim().length()>0)chapters.put(chapter(first((Element)node,"title","Раздел "+(chapters.length()+1)),sb.toString()).put("html",rich.html(node)).put("path",""));
                }
                if(!hasSections) { StringBuilder sb=new StringBuilder();toText(body,sb);chapters.put(chapter(book.getString("title"),sb.toString()).put("html",rich.html(body)).put("path","")); }
            }
        } else {
            if(!ext.endsWith(".txt"))throw new IOException("Поддерживаются EPUB, FB2, FB2.ZIP и TXT. PDF и DRM пока не поддерживаются.");
            Charset charset=StandardCharsets.UTF_8;
            if(bytes.length>1&&bytes[0]==(byte)0xff&&bytes[1]==(byte)0xfe)charset=StandardCharsets.UTF_16LE;
            else if(bytes.length>1&&bytes[0]==(byte)0xfe&&bytes[1]==(byte)0xff)charset=StandardCharsets.UTF_16BE;
            else try { StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(java.nio.ByteBuffer.wrap(bytes)); } catch(CharacterCodingException ex){charset=Charset.forName("windows-1251");}
            String text=new String(bytes,charset).replace("\uFEFF","");
            if(text.trim().startsWith("<"))throw new IOException("Вместо книги сервер вернул страницу HTML/XML");
            String[] chunks=text.split("(?m)(?=^(?:ГЛАВА|Глава|Часть|CHAPTER)\\s+[А-ЯA-Z0-9IVX])");
            for(String chunk:chunks)if(!chunk.trim().isEmpty())chapters.put(chapter(chunks.length==1?book.getString("title"):chunk.trim().split("\\n",2)[0],chunk));
            book.put("format","TXT");
        }
        if(chapters.length()==0)throw new IOException("В книге не найден текст. Возможно, файл повреждён или защищён DRM.");
        book.put("chapters",chapters).put("richVersion",1);return book;
    }
    private static String resolve(String dir,String path) { try { return new java.net.URI("https://local/"+dir).resolve(path.split("#")[0]).getPath().substring(1); }catch(Exception e){return dir+path;} }
    private static void toText(Node n,StringBuilder sb) {
        String tag=name(n).toLowerCase(Locale.ROOT);
        if(Arrays.asList("script","style","binary","svg","head","nav").contains(tag))return;
        if(n.getNodeType()==Node.TEXT_NODE) { sb.append(n.getNodeValue().replaceAll("\\s+"," "));return; }
        boolean block=Arrays.asList("p","div","section","title","h1","h2","h3","h4","br","li","v","subtitle","empty-line").contains(tag);
        if(block&&sb.length()>0)sb.append("\n\n");
        for(Node c=n.getFirstChild();c!=null;c=c.getNextSibling())toText(c,sb);
        if(block)sb.append("\n\n");
    }
    public static String strip(String html) { return html.replaceAll("(?is)<(script|style).*?</\\1>","").replaceAll("(?i)</?(p|div|br|h[1-6])[^>]*>","\n\n").replaceAll("<[^>]*>","").replace("&nbsp;"," ").replace("&amp;","&").replace("&lt;","<").replace("&gt;",">").trim(); }
}
