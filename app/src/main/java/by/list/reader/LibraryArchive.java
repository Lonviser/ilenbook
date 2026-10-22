package by.list.reader;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Versioned, bounded archive. Validate everything before replacing the active library. */
public final class LibraryArchive {
    public static final long LIMIT=256L*1024*1024;
    private static final String[] DIRS={"books","originals","user-fonts"};
    private static final String[] FILES={"preferences.json","preferences.json.bak","preferences.json.new","dictionary.json","dictionary.json.bak","dictionary.json.new"};
    private static void add(ZipOutputStream z,String name,InputStream input)throws IOException {
        z.putNextEntry(new ZipEntry(name));try(InputStream in=input){byte[] b=new byte[16384];int n;while((n=in.read(b))!=-1)z.write(b,0,n);}z.closeEntry();
    }
    public static void write(File root,OutputStream out,String preferences,boolean originals)throws Exception {
        long total=preferences.getBytes(StandardCharsets.UTF_8).length;
        for(String dir:DIRS)if(originals||!dir.equals("originals")){File[] files=new File(root,dir).listFiles();if(files!=null)for(File f:files)if(f.isFile())total+=f.length();}
        File dict=new File(root,"dictionary.json");if(dict.isFile())total+=dict.length();
        if(total>LIMIT)throw new IOException("Резервная копия превышает 256 МБ. Сохраните исходники отдельно.");
        new JSONObject(preferences);
        try(ZipOutputStream z=new ZipOutputStream(out)){
            add(z,"manifest.json",new ByteArrayInputStream(new JSONObject().put("format","ilenbook-backup").put("version",1).put("created",System.currentTimeMillis()).toString().getBytes(StandardCharsets.UTF_8)));
            add(z,"preferences.json",new ByteArrayInputStream(preferences.getBytes(StandardCharsets.UTF_8)));
            for(String dir:DIRS)if(originals||!dir.equals("originals")){File[] files=new File(root,dir).listFiles();if(files!=null)for(File f:files)if(f.isFile()&&!f.getName().endsWith(".tmp"))add(z,dir+"/"+f.getName(),new FileInputStream(f));}
            if(dict.isFile())add(z,"dictionary.json",new FileInputStream(dict));
        }
    }
    public static String restore(File root,InputStream input)throws Exception {
        File stage=new File(root,"restore-"+UUID.randomUUID());if(!stage.mkdirs())throw new IOException("Не удалось подготовить восстановление");
        boolean preserve=false;try{
            Set<String> seen=new HashSet<>();long expanded=0;int count=0;
            try(ZipInputStream z=new ZipInputStream(input)){
                ZipEntry e;while((e=z.getNextEntry())!=null){String n=e.getName();
                    if(e.isDirectory())continue;
                    if(!validName(n)||!seen.add(n)||++count>10000)throw new IOException("Неверная структура резервной копии");
                    File f=new File(stage,n);f.getParentFile().mkdirs();
                    try(FileOutputStream out=new FileOutputStream(f)){byte[] b=new byte[16384];int read;long size=0;while((read=z.read(b))!=-1){expanded+=read;size+=read;if(expanded>LIMIT||size>BookParser.MAX_BYTES)throw new IOException("Слишком большая резервная копия");out.write(b,0,read);}}
                }
            }
            JSONObject manifest=json(new File(stage,"manifest.json"));if(!"ilenbook-backup".equals(manifest.optString("format"))||manifest.optInt("version")!=1)throw new IOException("Неподдерживаемая версия резервной копии");
            File preferences=new File(stage,"preferences.json");JSONObject p=json(preferences);if(p.optJSONObject("books")==null||p.optJSONObject("settings")==null)throw new IOException("В копии нет настроек и позиций книг");
            File[] books=new File(stage,"books").listFiles();if(books==null||books.length==0)throw new IOException("В копии нет библиотеки");
            Set<String> ids=new HashSet<>();
            for(File f:books){JSONObject b=json(f);String id=f.getName().replaceFirst("\\.json$","");JSONArray ch=b.optJSONArray("chapters");if(!id.equals(b.optString("id"))||b.optString("title").isEmpty()||ch==null||ch.length()==0)throw new IOException("Повреждена книга в резервной копии");for(int i=0;i<ch.length();i++)if(!(ch.getJSONObject(i).opt("text") instanceof String))throw new IOException("Повреждён текст книги");ids.add(id);}
            File[] originals=new File(stage,"originals").listFiles();if(originals!=null)for(File f:originals)if(!ids.contains(f.getName()))throw new IOException("Исходник не относится к библиотеке");
            File dictionary=new File(stage,"dictionary.json");if(dictionary.isFile())json(dictionary);
            String restored=new String(BookParser.read(new FileInputStream(preferences),4*1024*1024),StandardCharsets.UTF_8);
            commit(root,stage);return restored;
        }catch(RollbackFailure e){preserve=true;throw e;}finally{if(!preserve)deleteTree(stage);}
    }
    private static boolean validName(String n){return n.equals("manifest.json")||n.equals("preferences.json")||n.equals("dictionary.json")||n.matches("books/[a-zA-Z0-9-]+\\.json")||n.matches("originals/[a-zA-Z0-9-]+")||n.matches("user-fonts/[a-zA-Z0-9-]+\\.(ttf|otf)");}
    private static JSONObject json(File f)throws Exception {return new JSONObject(new String(BookParser.read(new FileInputStream(f),BookParser.MAX_BYTES),StandardCharsets.UTF_8));}
    private static void commit(File root,File stage)throws IOException {
        List<String> moved=new ArrayList<>(),installed=new ArrayList<>();List<String> names=new ArrayList<>();Collections.addAll(names,DIRS);Collections.addAll(names,FILES);
        try{
            for(String name:names){File old=new File(root,name),saved=new File(stage,"previous-"+name);if(old.exists()){if(!old.renameTo(saved))throw new IOException("Не удалось сохранить текущую библиотеку");moved.add(name);}}
            for(String name:names){File next=new File(stage,name);if(next.exists()){if(!next.renameTo(new File(root,name)))throw new IOException("Не удалось установить восстановленную библиотеку");installed.add(name);}}
        }catch(IOException failure){for(String name:installed)deleteTree(new File(root,name));for(String name:moved)if(!new File(stage,"previous-"+name).renameTo(new File(root,name)))throw new RollbackFailure("Не удалось откатить восстановление; сохранена папка "+stage.getName(),failure);throw failure;}
    }
    private static void deleteTree(File f){File[] files=f.listFiles();if(files!=null)for(File c:files)deleteTree(c);f.delete();}
    private static class RollbackFailure extends IOException{RollbackFailure(String message,Throwable cause){super(message,cause);}}
}
