package by.list.reader;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;
import org.json.JSONObject;

/** App-owned Tor child process. Only Flibusta requests use its loopback proxy. */
final class FlibustaConnection implements Closeable {
    interface Guard { void check() throws Exception; }
    static final Set<String> MODES=new HashSet<>(Arrays.asList("auto","direct","tor","snowflake"));
    private final Context context;
    private final SharedPreferences preferences;
    private final ExecutorService startup=Executors.newSingleThreadExecutor();
    private volatile Process process;
    private Future<Proxy> pending;
    private volatile int percent;
    private volatile String phase="idle",error="";
    private volatile boolean closed;
    private int port;
    private long retryAfter;
    private volatile long lastUse;
    private int activeRequests;
    private File currentLog,startupLog;
    private long logPosition;
    private volatile String lastNotice="";
    private volatile String directError="";
    private static final Pattern BOOTSTRAP=Pattern.compile("Bootstrapped (\\d+)%");
    private final ScheduledExecutorService idle=Executors.newSingleThreadScheduledExecutor();

    FlibustaConnection(Context c){context=c.getApplicationContext();preferences=c.getSharedPreferences("flibusta-network",0);idle.scheduleAtFixedRate(()->{synchronized(this){if(phase.equals("ready")&&activeRequests==0&&System.currentTimeMillis()-lastUse>180000)stop();if(phase.equals("ready")&&currentLog!=null&&currentLog.length()>262144)try(FileOutputStream clear=new FileOutputStream(currentLog)){}catch(IOException ignored){}}},30,30,TimeUnit.SECONDS);}
    String mode(){return preferences.getString("mode","auto");}
    boolean preferReserve(){return mode().equals("tor")||mode().equals("snowflake")||mode().equals("auto")&&System.currentTimeMillis()-preferences.getLong("reserveWorks",0)<30*60*1000L;}
    void worked(){preferences.edit().putLong("reserveWorks",System.currentTimeMillis()).apply();error="";}
    synchronized JSONObject status()throws Exception{return new JSONObject().put("mode",mode()).put("phase",phase).put("percent",percent).put("error",error).put("reservePreferred",preferReserve()).put("lastNotice",lastNotice).put("directError",directError);}
    void directFailed(Exception failure){directError=failure.getClass().getSimpleName()+": "+failure.getMessage();}
    synchronized void directWorked(){directError="";if(phase.equals("error")){phase="idle";error="";}if(preferences.contains("reserveWorks"))preferences.edit().remove("reserveWorks").apply();}
    synchronized JSONObject configure(String mode)throws Exception{
        if(!MODES.contains(mode))throw new IOException("Неизвестный способ подключения");
        stop();preferences.edit().putString("mode",mode).remove("reserveWorks").apply();retryAfter=0;error="";return status();
    }
    Proxy proxy(Guard guard)throws Exception{
        Future<Proxy> job;
        synchronized(this){
            if(closed)throw new IOException("Подключение закрыто");
            lastUse=System.currentTimeMillis();if(phase.equals("ready")&&(process==null||!process.isAlive()))stop();
            if(pending==null){
                if(System.currentTimeMillis()<retryAfter)throw new IOException(error.isEmpty()?"Резервное подключение ещё не готово. Повторите через полминуты.":error);
                String requested=mode();pending=startup.submit(()->connect(requested));
            }
            job=pending;
        }
        while(true){guard.check();try{Proxy result=job.get(200,TimeUnit.MILLISECONDS);synchronized(this){if(closed||pending!=job)throw new IOException("Подключение изменено");activeRequests++;lastUse=System.currentTimeMillis();}return result;}catch(TimeoutException ignored){}
            catch(ExecutionException failed){synchronized(this){if(pending==job){pending=null;retryAfter=System.currentTimeMillis()+30000;}}Throwable cause=failed.getCause();throw new IOException(cause==null?"Не удалось подключиться к Флибусте":cause.getMessage(),cause);}
        }
    }
    synchronized void finished(){activeRequests=Math.max(0,activeRequests-1);lastUse=System.currentTimeMillis();}
    private Proxy connect(String requested)throws Exception{
        try{
            if(!requested.equals("snowflake")){
                launch(false);if(waitReady(requested.equals("tor")?80000:18000))return ready();
                destroyProcess();
                if(requested.equals("tor"))throw new IOException("Tor не подключился. Попробуйте Snowflake в настройках Флибусты.");
            }
            launch(true);if(!waitReady(150000))throw new IOException("Резервное соединение с Флибустой недоступно в этой сети. Включите VPN или смените подключение и повторите.");return ready();
        }catch(Exception e){error=e.getMessage()==null?"Не удалось запустить резервное соединение":e.getMessage();phase="error";destroyProcess();throw e;}
    }
    private Proxy ready(){phase="ready";percent=100;return new Proxy(Proxy.Type.SOCKS,new InetSocketAddress("127.0.0.1",port));}
    private void launch(boolean snowflake)throws Exception{
        if(closed||Thread.currentThread().isInterrupted())throw new IOException("Подключение отменено");
        File nativeDir=new File(context.getApplicationInfo().nativeLibraryDir),tor=new File(nativeDir,"libtor.so"),pt=new File(nativeDir,"liblyrebird.so");
        if(!tor.canExecute()||snowflake&&!pt.canExecute())throw new IOException("В сборке отсутствует модуль резервного подключения");
        File dir=new File(context.getFilesDir(),"flibusta-tor/"+(snowflake?"snowflake":"direct"));if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("Не удалось создать папку соединения");
        try(ServerSocket listener=new ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))){port=listener.getLocalPort();}
        currentLog=new File(context.getCacheDir(),"flibusta-tor.log");startupLog=new File(context.getCacheDir(),"flibusta-tor-startup.log");try(FileOutputStream clear=new FileOutputStream(currentLog)){}logPosition=0;lastNotice="";
        String config="ClientOnly 1\nSocksPort 127.0.0.1:"+port+"\nDataDirectory "+dir.getAbsolutePath()+"\nAvoidDiskWrites 1\nSafeLogging 1\nLog notice file "+currentLog.getAbsolutePath()+"\n__OwningControllerProcess "+android.os.Process.myPid()+"\n";
        if(snowflake){
            config+="UseBridges 1\nClientTransportPlugin snowflake exec "+pt.getAbsolutePath()+"\n";
            try(InputStream in=context.getAssets().open("flibusta-bridges.txt")){config+=new String(BookParser.read(in,16000),StandardCharsets.UTF_8);}
        }else config+="ReachableAddresses *:80,*:443\n";
        File rc=new File(dir,"torrc");try(FileOutputStream out=new FileOutputStream(rc)){out.write(config.getBytes(StandardCharsets.UTF_8));}
        percent=0;phase=snowflake?"snowflake":"tor";
        Process child=new ProcessBuilder(tor.getAbsolutePath(),"-f",rc.getAbsolutePath()).directory(dir).redirectErrorStream(true).redirectOutput(startupLog).start();
        synchronized(this){if(closed||Thread.currentThread().isInterrupted()){child.destroy();throw new IOException("Подключение отменено");}process=child;}
    }
    private void readProgress(){
        if(currentLog==null||!currentLog.isFile())return;
        try(RandomAccessFile log=new RandomAccessFile(currentLog,"r")){log.seek(Math.max(logPosition,log.length()-65536));String line;while((line=log.readLine())!=null){lastNotice=line;Matcher match=BOOTSTRAP.matcher(line);if(match.find())percent=Integer.parseInt(match.group(1));}logPosition=log.getFilePointer();}catch(IOException ignored){}
    }
    private boolean waitReady(long timeout)throws Exception{
        long until=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(timeout);
        while(System.nanoTime()<until){if(closed||Thread.currentThread().isInterrupted())throw new IOException("Подключение отменено");readProgress();if(process==null||!process.isAlive())throw new IOException("Модуль резервного соединения завершил работу");if(percent>=100)return true;Thread.sleep(150);}return false;
    }
    private synchronized void destroyProcess(){if(process!=null){process.destroy();process=null;}}
    private synchronized void stop(){if(pending!=null)pending.cancel(true);pending=null;destroyProcess();phase="idle";percent=0;}
    @Override public synchronized void close(){closed=true;stop();startup.shutdownNow();idle.shutdownNow();}
}
