package by.list.reader;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.*;
import org.json.*;

public class ArchiveChecks {
    static int count;
    static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);System.out.println("PASS "+message);count++;}
    static void write(File f,String text)throws Exception{f.getParentFile().mkdirs();try(FileOutputStream o=new FileOutputStream(f)){o.write(text.getBytes(StandardCharsets.UTF_8));}}
    static String read(File f)throws Exception{return new String(Files.readAllBytes(f.toPath()),StandardCharsets.UTF_8);}
    static byte[] zip(String... entries)throws Exception{return ParserChecks.zip(entries);}
    public static void main(String[] args)throws Exception{
        File root=new File("build/archive-fixture-"+System.nanoTime());root.mkdirs();
        String prefs="{\"books\":{\"book-1\":{\"notes\":[{\"id\":\"note-1\",\"text\":\"Заметка\"}],\"anchor\":{\"block\":2,\"char\":14}}},\"settings\":{\"font\":\"lora\"},\"schema\":4}";
        JSONObject custom=new JSONObject(prefs);custom.getJSONObject("books").getJSONObject("book-1").put("readingOverrides",new JSONObject().put("font","sourcesans").put("size",26));custom.put("readingProfiles",new JSONArray().put(new JSONObject().put("id","profile-test").put("name","Вечером").put("values",new JSONObject().put("theme","sepia"))));custom.put("readerControls",new JSONObject().put("toolbar",new JSONArray().put("notes").put("profiles")).put("zones",new JSONArray().put("bookmark").put("menu").put("next")));prefs=custom.toString();
        String book="{\"id\":\"book-1\",\"title\":\"Книга\",\"chapters\":[{\"title\":\"Глава\",\"text\":\"Оригинальный текст\"}]}";
        write(new File(root,"books/book-1.json"),book);write(new File(root,"originals/book-1"),"Исходник");write(new File(root,"dictionary.json"),"{\"слово\":{\"text\":\"Толкование\"}}");
        write(new File(root,"yandex-token.json"),"fixture-credential-must-not-export");write(new File(root,"yandex-snapshot.json"),"fixture-private-account-cache");
        ByteArrayOutputStream out=new ByteArrayOutputStream();LibraryArchive.write(root,out,prefs,true);byte[] backup=out.toByteArray();check(backup.length>0,"Archive produced");
        boolean accountData=false;try(ZipInputStream z=new ZipInputStream(new ByteArrayInputStream(backup))){ZipEntry entry;while((entry=z.getNextEntry())!=null)if(entry.getName().contains("yandex"))accountData=true;}check(!accountData,"Credentials and account cache excluded from book backup");
        write(new File(root,"books/book-2.json"),book.replace("book-1","book-2"));write(new File(root,"preferences.json"),"{\"changed\":true}");write(new File(root,"preferences.json.bak"),"{\"stale\":true}");
        String restored=LibraryArchive.restore(root,new ByteArrayInputStream(backup));check(new JSONObject(restored).getJSONObject("books").getJSONObject("book-1").getJSONArray("notes").getJSONObject(0).getString("text").equals("Заметка"),"Notes and anchors round-trip");
        JSONObject restoredCustom=new JSONObject(restored);check(restoredCustom.getJSONObject("books").getJSONObject("book-1").getJSONObject("readingOverrides").getInt("size")==26&&restoredCustom.getJSONArray("readingProfiles").getJSONObject(0).getString("name").equals("Вечером"),"Per-book text and named profiles survive backup");check(restoredCustom.getJSONObject("readerControls").getJSONArray("toolbar").getString(1).equals("profiles")&&restoredCustom.getJSONObject("readerControls").getJSONArray("zones").getString(0).equals("bookmark"),"Toolbar and tap actions survive backup");
        check(read(new File(root,"originals/book-1")).equals("Исходник"),"Original preserved");check(!new File(root,"books/book-2.json").exists(),"Restore replaces active library");check(!new File(root,"preferences.json.bak").exists(),"Stale atomic backup cannot override restored preferences");check(new File(root,"dictionary.json").isFile(),"Offline dictionary restored");
        boolean rejected=false;try{LibraryArchive.restore(root,new ByteArrayInputStream(zip("../escaped.txt","bad")));}catch(Exception expected){rejected=true;}check(rejected&&!new File(root.getParentFile(),"escaped.txt").exists(),"Reject path traversal");check(read(new File(root,"books/book-1.json")).equals(book),"Invalid archive leaves active books intact");
        rejected=false;try{LibraryArchive.restore(root,new ByteArrayInputStream(zip("manifest.json","{\"format\":\"ilenbook-backup\",\"version\":1}","preferences.json","{}","books/book-1.json",book)));}catch(Exception expected){rejected=true;}check(rejected&&read(new File(root,"preferences.json")).equals(prefs),"Reject missing settings before replacement");
        LibraryArchive.restore(root,new ByteArrayInputStream(backup));check(new File(root,"books").list().length==1,"Repeated restore is idempotent");
        System.out.println(count+" archive checks passed");
    }
}
