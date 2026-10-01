package com.example.tingxiejian;
import java.util.concurrent.*;
/** Single-producer/single-consumer FIFO; overrun retains the last read frame and ends capture honestly. */
final class BoundedAudioQueue {
    private final ArrayBlockingQueue<float[]> queue;
    private volatile boolean finished;
    private volatile float[] tail;
    BoundedAudioQueue(int capacity){queue=new ArrayBlockingQueue<>(capacity);}
    boolean offer(float[] data,long timeoutMs)throws InterruptedException{
        if(finished)throw new IllegalStateException("finished");
        if(queue.offer(data,timeoutMs,TimeUnit.MILLISECONDS))return true;
        tail=data;return false;
    }
    void finish(){finished=true;}
    float[] take()throws InterruptedException{
        for(;;){float[] data=queue.poll(100,TimeUnit.MILLISECONDS);if(data!=null)return data;
            if(finished){data=queue.poll();if(data!=null)return data;data=tail;tail=null;return data;}}
    }
    int size(){return queue.size();}
}
