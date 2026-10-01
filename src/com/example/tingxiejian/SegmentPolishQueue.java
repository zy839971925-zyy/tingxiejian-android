package com.example.tingxiejian;

import java.util.concurrent.*;
import java.util.function.Consumer;

/** Optional network work never applies backpressure to audio. Finish seals all result delivery. */
final class SegmentPolishQueue implements AutoCloseable {
    private final ThreadPoolExecutor executor;
    private final Object deliveryLock=new Object();
    private boolean sealed;
    SegmentPolishQueue(int capacity){
        executor=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(capacity),r->{
            Thread t=new Thread(r,"speech-text-polish");t.setDaemon(true);return t;});
    }
    boolean submit(Callable<String> work,Consumer<String> result,Consumer<Exception> failure){
        synchronized(deliveryLock){
            if(sealed)return false;
            try{executor.execute(()->{
                try{String value=work.call();synchronized(deliveryLock){if(!sealed)result.accept(value);}}
                catch(Exception e){synchronized(deliveryLock){if(!sealed)failure.accept(e);}}
            });return true;}catch(RejectedExecutionException full){return false;}
        }
    }
    boolean finish(long timeoutMillis)throws InterruptedException{
        executor.shutdown();
        boolean done;
        try{done=executor.awaitTermination(timeoutMillis,TimeUnit.MILLISECONDS);}
        catch(InterruptedException e){close();throw e;}
        synchronized(deliveryLock){sealed=true;}
        if(!done)executor.shutdownNow();return done;
    }
    public void close(){synchronized(deliveryLock){sealed=true;}executor.shutdownNow();}
}
