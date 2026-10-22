package by.list.reader;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import org.json.*;

/** Short-lived LAN upload endpoint, available only after the user starts transfer mode. */
final class TransferServer implements Closeable {
    private final MainActivity activity;
    private final String token=UUID.randomUUID().toString().replace("-","");
    private final ExecutorService workers=Executors.newFixedThreadPool(2);
    private ServerSocket server;
    private volatile boolean closed;
    private final Set<Socket> sockets=ConcurrentHashMap.newKeySet();
    private final long expires=System.currentTimeMillis()+10*60*1000;
    TransferServer(MainActivity a){activity=a;}
    JSONObject start()throws Exception {
        JSONArray addresses=new JSONArray();List<String> hosts=new ArrayList<>();
        for(Enumeration<NetworkInterface> n=NetworkInterface.getNetworkInterfaces();n.hasMoreElements();){NetworkInterface iface=n.nextElement();if(!iface.isUp()||iface.isLoopback())continue;for(Enumeration<InetAddress> a=iface.getInetAddresses();a.hasMoreElements();){InetAddress ip=a.nextElement();if(ip instanceof Inet4Address&&ip.isSiteLocalAddress())hosts.add(ip.getHostAddress());}}
        if(hosts.isEmpty())throw new IOException("Подключите электронную книгу и компьютер к одной Wi-Fi сети");
        server=new ServerSocket(0,4);server.setSoTimeout(1000);for(String host:hosts)addresses.put("http://"+host+":"+server.getLocalPort()+"/"+token+"/");
        Thread accept=new Thread(()->{while(!closed&&System.currentTimeMillis()<expires){try{Socket s=server.accept();s.setSoTimeout(30000);if(sockets.size()>=2){s.close();continue;}sockets.add(s);workers.execute(()->serve(s));}catch(SocketTimeoutException ignored){}catch(Exception e){break;}}close();},"ilenbook-transfer");accept.setDaemon(true);accept.start();return new JSONObject().put("addresses",addresses).put("expires",expires);
    }
    private String line(InputStream in)throws IOException {ByteArrayOutputStream b=new ByteArrayOutputStream();int v;while((v=in.read())!=-1){if(v=='\n')break;if(v!='\r')b.write(v);if(b.size()>4096)throw new IOException("Header too long");}return b.toString("UTF-8");}
    private void serve(Socket socket){try(Socket s=socket){InputStream in=s.getInputStream();String[] request=line(in).split(" ");if(request.length!=3)return;Map<String,String> headers=new HashMap<>();int size=0;String h;while(!(h=line(in)).isEmpty()){if((size+=h.length())>16384)throw new IOException("Too many headers");int at=h.indexOf(':');if(at>0)headers.put(h.substring(0,at).toLowerCase(Locale.ROOT),h.substring(at+1).trim());}
        URI uri=new URI(request[1]);String path=uri.getPath();if(!path.equals("/"+token+"/")&&!path.equals("/"+token+"/upload")){send(s,404,"text/plain","Not found");return;}
        String origin=headers.get("origin");if(origin!=null&&!origin.equals("http://"+headers.get("host"))){send(s,403,"text/plain","Forbidden");return;}
        if(request[0].equals("GET")&&path.endsWith("/")){send(s,200,"text/html",PAGE);return;}
        if(!request[0].equals("POST")||!path.endsWith("/upload")){send(s,405,"text/plain","Method not allowed");return;}
        int length;try{length=Integer.parseInt(headers.getOrDefault("content-length","0"));}catch(NumberFormatException e){length=0;}if(length<=0||length>BookParser.MAX_BYTES){send(s,413,"application/json","{\"error\":\"Максимальный размер — 40 МБ\"}");return;}
        String filename="Книга.txt";if(uri.getRawQuery()!=null)for(String q:uri.getRawQuery().split("&"))if(q.startsWith("name="))filename=URLDecoder.decode(q.substring(5),"UTF-8");
        if(filename.length()>200)throw new IOException("Слишком длинное имя файла");ByteArrayOutputStream bytes=new ByteArrayOutputStream();byte[] b=new byte[16384];int left=length;while(left>0){int n=in.read(b,0,Math.min(left,b.length));if(n<0)throw new IOException("Загрузка прервана");bytes.write(b,0,n);left-=n;}if(closed)throw new IOException("Передача остановлена");
        try{JSONObject book=activity.importBytes(bytes.toByteArray(),filename,"По Wi-Fi");activity.releaseEvent(new JSONObject().put("book",book));send(s,200,"application/json",new JSONObject().put("title",book.getString("title")).toString());}catch(Exception e){send(s,400,"application/json",new JSONObject().put("error",e.getMessage()).toString());}
    }catch(Exception ignored){}finally{sockets.remove(socket);}}
    private void send(Socket s,int code,String mime,String body)throws IOException {byte[] data=body.getBytes(StandardCharsets.UTF_8);OutputStream out=s.getOutputStream();out.write(("HTTP/1.1 "+code+" Response\r\nContent-Type: "+mime+"; charset=utf-8\r\nContent-Length: "+data.length+"\r\nCache-Control: no-store\r\nX-Content-Type-Options: nosniff\r\nContent-Security-Policy: default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; connect-src 'self'; base-uri 'none'; frame-ancestors 'none'\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));out.write(data);out.flush();}
    public void close(){closed=true;try{if(server!=null)server.close();}catch(Exception ignored){}for(Socket s:sockets)try{s.close();}catch(Exception ignored){}workers.shutdownNow();}
    private static final String PAGE="<!doctype html><html lang='ru'><meta charset='utf-8'><meta name='viewport' content='width=device-width, initial-scale=1'><title>Передать в ilenbook</title><style>body{font:18px/1.5 sans-serif;max-width:600px;margin:40px auto;padding:20px;color:#15271f;background:#fafbf7}input,button{font:inherit;padding:14px;margin:12px 0;max-width:100%}button{background:#245c45;color:white;border:0;border-radius:8px}li{margin:12px 0}</style><h1>Передать книги в ilenbook</h1><p>EPUB, FB2, FB2.ZIP или TXT, до 40 МБ на файл.</p><input id='files' type='file' multiple accept='.epub,.fb2,.zip,.txt'><br><button id='send'>Передать на устройство</button><ul id='status' aria-live='polite'></ul><script>document.getElementById('send').onclick=async()=>{const button=document.getElementById('send');button.disabled=true;for(const file of document.getElementById('files').files){const item=document.createElement('li');item.textContent=file.name+' — передаём…';document.getElementById('status').appendChild(item);try{const r=await fetch(location.pathname+'upload?name='+encodeURIComponent(file.name),{method:'POST',body:file,headers:{'Content-Type':'application/octet-stream'}});const data=await r.json();if(!r.ok)throw Error(data.error||'Не удалось загрузить');item.textContent=data.title+' — добавлена';}catch(e){item.textContent=file.name+' — '+e.message}}button.disabled=false};</script></html>";
}
