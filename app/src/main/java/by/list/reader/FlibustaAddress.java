package by.list.reader;

import java.io.IOException;
import java.net.URL;
import java.util.Locale;

/** Origins belong to the library; the onion address is advertised by its HTTPS endpoint. */
final class FlibustaAddress {
    static final String ONION="flibustaongezhld6dibs2dps6vm4nvqg2kp7vgowbu76tzopgnhazqd.onion";
    static boolean owns(URL url){
        String host=url.getHost().toLowerCase(Locale.ROOT),scheme=url.getProtocol();
        return (scheme.equals("http")||scheme.equals("https"))
            &&(host.equals("flibusta.is")||host.equals("www.flibusta.is")||host.equals("static.flibusta.is")||host.equals(ONION))
            &&url.getUserInfo()==null&&(url.getPort()==-1||url.getPort()==80||url.getPort()==443);
    }
    static String reserveAddress(String address)throws IOException{
        URL url=new URL(address);if(!owns(url))throw new IOException("Неверный адрес Флибусты");
        return url.getHost().equalsIgnoreCase("static.flibusta.is")?"https://static.flibusta.is"+url.getFile():"http://"+ONION+url.getFile();
    }
}
