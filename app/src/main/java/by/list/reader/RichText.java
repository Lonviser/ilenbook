package by.list.reader;

import java.util.*;
import org.w3c.dom.*;
import org.json.*;

/** Publication markup is converted to a tiny, passive vocabulary; no publisher CSS or scripts. */
final class RichText {
    private final Map<String,byte[]> archive;
    private final Map<String,String> binaries;
    private final String path;
    private int imageBytes;
    RichText(Map<String,byte[]> z,Map<String,String> images,String sourcePath){archive=z;binaries=images;path=sourcePath;}
    private static String escape(String s){return s.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;");}
    String html(Node n){StringBuilder out=new StringBuilder();append(n,out);return out.toString();}
    String target(String href){
        if(href.isEmpty()||href.matches("(?i)^[a-z][a-z0-9+.-]*:.*")||href.startsWith("//"))return "";
        try{java.net.URI u=new java.net.URI("https://local/"+path).resolve(href);return u.getPath().substring(1)+(u.getFragment()==null?"":"#"+u.getFragment());}catch(Exception e){return "";}
    }
    private void append(Node n,StringBuilder out){
        if(n.getNodeType()==Node.TEXT_NODE){out.append(escape(n.getNodeValue().replaceAll("\\s+"," ")));return;}if(!(n instanceof Element))return;
        Element e=(Element)n;String original=BookParser.name(e).toLowerCase(Locale.ROOT);
        if(Arrays.asList("script","style","iframe","object","embed","svg","math","head","nav","binary","form","input","button").contains(original))return;
        String tag=original;
        if(tag.equals("strong"))tag="strong";else if(tag.equals("emphasis")||tag.equals("i"))tag="em";else if(tag.equals("b"))tag="strong";else if(tag.equals("strikethrough"))tag="s";else if(tag.equals("title")||tag.equals("subtitle"))tag="h3";else if(tag.equals("v"))tag="p";else if(tag.equals("epigraph")||tag.equals("cite"))tag="blockquote";else if(tag.equals("empty-line"))tag="br";
        if(original.equals("img")||original.equals("image")){
            String source=e.getAttribute("src");if(source.isEmpty())source=e.getAttributeNS("http://www.w3.org/1999/xlink","href");if(source.isEmpty())source=e.getAttribute("href");
            String data="";if(source.startsWith("#")&&binaries!=null)data=binaries.getOrDefault(source.substring(1),"");
            else if(archive!=null){String t=target(source).split("#")[0];byte[] b=archive.get(t);String mime=t.toLowerCase(Locale.ROOT).endsWith(".png")?"image/png":t.toLowerCase(Locale.ROOT).endsWith(".webp")?"image/webp":t.toLowerCase(Locale.ROOT).matches(".*\\.jpe?g")?"image/jpeg":"";if(b!=null&&!mime.isEmpty()&&b.length<2*1024*1024)data="data:"+mime+";base64,"+Base64.getEncoder().encodeToString(b);}
            if(!data.isEmpty()&&(imageBytes+=data.length())<8*1024*1024)out.append("<img src=\"").append(data).append("\" alt=\"").append(escape(e.getAttribute("alt"))).append("\">");return;
        }
        boolean allowed=Arrays.asList("p","div","section","h1","h2","h3","h4","h5","h6","br","em","strong","s","sub","sup","blockquote","ul","ol","li","a","span","pre","code","hr").contains(tag);
        if(allowed){out.append('<').append(tag);String id=e.getAttribute("id");if(!id.isEmpty())out.append(" data-anchor-id=\"").append(escape(id)).append('"');
            if(tag.equals("a")){String href=e.getAttribute("href");if(href.isEmpty())href=e.getAttributeNS("http://www.w3.org/1999/xlink","href");String t=target(href);if(!t.isEmpty())out.append(" href=\"#\" data-target=\"").append(escape(t)).append('"');}
            out.append('>');
        }
        for(Node c=n.getFirstChild();c!=null;c=c.getNextSibling())append(c,out);
        if(allowed&&!Arrays.asList("br","hr").contains(tag))out.append("</").append(tag).append('>');
    }
    void collectFootnotes(Element root,JSONObject result,boolean allSections)throws Exception {
        for(Element e:BookParser.all(root,allSections?"section":"*")){
            String id=e.getAttribute("id"),type=e.getAttributeNS("http://www.idpf.org/2007/ops","type")+" "+e.getAttribute("role");
            if(!id.isEmpty()&&(allSections||type.contains("footnote")||type.contains("endnote")||id.matches("(?i)(note|fn|footnote)[-_]?[0-9]+")))result.put(path+"#"+id,html(e));
        }
    }
    void collectAllFootnotes(Element root,JSONObject result)throws Exception{for(Element e:BookParser.all(root,"*")){String id=e.getAttribute("id");if(id.isEmpty())continue;Element content=e;if(Arrays.asList("a","span","sup").contains(BookParser.name(e))&&e.getParentNode() instanceof Element)content=(Element)e.getParentNode();result.put(path+"#"+id,html(content));}}
}
