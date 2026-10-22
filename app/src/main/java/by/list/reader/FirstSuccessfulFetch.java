package by.list.reader;

import java.util.*;
import java.util.concurrent.*;

/** Starts the existing alternative after a short delay; returns the first valid response. */
final class FirstSuccessfulFetch {
    interface Guard{void check()throws Exception;}
    static final class Result<T>{final T value;final boolean alternative;Result(T v,boolean a){value=v;alternative=a;}}
    private static final class Outcome<T>{T value;Exception error;boolean alternative;}
    static <T> Result<T> run(ExecutorService pool,Callable<T> primary,Callable<T> alternative,long delayMs,Guard guard,Runnable cancelPrimary,Runnable cancelAlternative)throws Exception{
        CompletionService<Outcome<T>> completed=new ExecutorCompletionService<>(pool);List<Future<Outcome<T>>> jobs=new ArrayList<>();boolean alternativeStarted=false;int pending=1;Exception failure=null;long start=System.nanoTime();
        jobs.add(completed.submit(()->attempt(primary,false)));
        try{while(true){guard.check();if(!alternativeStarted&&(pending==0||TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start)>=delayMs)){jobs.add(completed.submit(()->attempt(alternative,true)));alternativeStarted=true;pending++;}
            Future<Outcome<T>> future=completed.poll(35,TimeUnit.MILLISECONDS);if(future==null)continue;Outcome<T> result=future.get();pending--;guard.check();if(result.error==null)return new Result<>(result.value,result.alternative);failure=result.error;if(alternativeStarted&&pending==0)throw failure;
        }}finally{for(Future<?> job:jobs)if(!job.isDone())job.cancel(true);cancelPrimary.run();if(alternativeStarted)cancelAlternative.run();}
    }
    private static <T> Outcome<T> attempt(Callable<T> task,boolean alternative){Outcome<T> result=new Outcome<>();result.alternative=alternative;try{result.value=task.call();}catch(Exception e){result.error=e;}return result;}
}
