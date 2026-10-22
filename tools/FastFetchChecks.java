package by.list.reader;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public class FastFetchChecks {
    static int count;static void check(boolean ok,String name){if(!ok)throw new AssertionError(name);count++;System.out.println("PASS "+name);}
    public static void main(String[] args)throws Exception{ExecutorService pool=Executors.newFixedThreadPool(4);try{
        AtomicInteger alternatives=new AtomicInteger();FirstSuccessfulFetch.Result<String> fast=FirstSuccessfulFetch.run(pool,()->"secure",()->{alternatives.incrementAndGet();return "fallback";},150,()->{},()->{},()->{});check(!fast.alternative&&alternatives.get()==0,"Fast HTTPS avoids duplicate HTTP request");
        long start=System.nanoTime();FirstSuccessfulFetch.Result<String> raced=FirstSuccessfulFetch.run(pool,()->{Thread.sleep(1600);return "slow";},()->"fast",80,()->{},()->{},()->{});long elapsed=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);check(raced.alternative&&raced.value.equals("fast"),"Delayed alternative can win");check(elapsed<650,"First response does not wait for slow connection timeout");
        start=System.nanoTime();FirstSuccessfulFetch.Result<String> failed=FirstSuccessfulFetch.run(pool,()->{throw new IOException("HTTPS unavailable");},()->"HTTP",1200,()->{},()->{},()->{});check(failed.alternative&&TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<650,"Immediate failure starts alternative immediately");
        boolean error=false;try{FirstSuccessfulFetch.run(pool,()->{throw new IOException("first");},()->{throw new IOException("second");},20,()->{},()->{},()->{});}catch(IOException expected){error=true;}check(error,"Two failed connections return an error");
        AtomicInteger cancelled=new AtomicInteger();final long deadline=System.nanoTime()+TimeUnit.MILLISECONDS.toNanos(90);start=System.nanoTime();error=false;try{FirstSuccessfulFetch.run(pool,()->{Thread.sleep(2000);return "x";},()->{Thread.sleep(2000);return "y";},20,()->{if(System.nanoTime()>deadline)throw new IOException("Cancelled");},cancelled::incrementAndGet,cancelled::incrementAndGet);}catch(IOException expected){error=true;}check(error&&TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)<650,"Cancellation is checked while waiting");check(cancelled.get()==2,"Both connection cleanups run on cancellation");
        System.out.println(count+" fast fetch checks passed");
    }finally{pool.shutdownNow();}}
}
