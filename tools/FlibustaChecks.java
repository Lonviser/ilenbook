package by.list.reader;
import java.net.URL;

public final class FlibustaChecks {
    static int checks;
    static void check(boolean ok,String label){if(!ok)throw new AssertionError(label);checks++;System.out.println("PASS "+label);}
    public static void main(String[] args)throws Exception{
        for(String host:new String[]{"flibusta.is","www.flibusta.is","static.flibusta.is",FlibustaAddress.ONION})check(FlibustaAddress.owns(new URL("https://"+host+"/opds")),"Library origin "+host);
        for(String address:new String[]{"https://flibusta.is.evil.example/opds","https://evilflibusta.is/opds","https://flibusta.site/opds","https://flibusta.is@evil.example/opds","https://user:pass@flibusta.is/opds","https://flibusta.is:8080/opds","ftp://flibusta.is/opds","https://unrelated.onion/opds"})check(!FlibustaAddress.owns(new URL(address)),"Reject unrelated origin "+address);
        String path="/opds/search?searchTerm=%D0%9A%D0%B0%D1%88%D1%82%D0%B0%D0%BD%D0%BA%D0%B0&searchType=books";
        check(FlibustaAddress.reserveAddress("https://flibusta.is"+path).equals("http://"+FlibustaAddress.ONION+path),"Search path and encoded Russian query survive Tor routing");
        check(FlibustaAddress.reserveAddress("http://"+FlibustaAddress.ONION+"/opds").equals("http://"+FlibustaAddress.ONION+"/opds"),"Onion redirect is stable");
        check(FlibustaAddress.reserveAddress("http://static.flibusta.is/b.fb2/book.zip").equals("https://static.flibusta.is/b.fb2/book.zip"),"Static file retains TLS over Tor");
        boolean rejected=false;try{FlibustaAddress.reserveAddress("https://example.com/book");}catch(Exception e){rejected=true;}check(rejected,"Tor redirects cannot escape library origins");
        System.out.println(checks+" Flibusta checks passed");
    }
}
