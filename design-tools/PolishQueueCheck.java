package com.example.tingxiejian;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class PolishQueueCheck {
    public static void main(String[] args)throws Exception {
        CountDownLatch entered=new CountDownLatch(1), release=new CountDownLatch(1);
        AtomicInteger delivered=new AtomicInteger(),calls=new AtomicInteger();
        SegmentPolishQueue q=new SegmentPolishQueue(2);
        if(!q.submit(()->{entered.countDown();while(release.getCount()>0){try{release.await();}catch(InterruptedException ignored){}}return "late";},v->delivered.incrementAndGet(),e->{}))throw new AssertionError("first work");
        if(!entered.await(2,TimeUnit.SECONDS))throw new AssertionError("worker starts");
        for(int i=0;i<2;i++)if(!q.submit(()->{calls.incrementAndGet();return "queued";},v->delivered.incrementAndGet(),e->{}))throw new AssertionError("bounded pending work accepted");
        if(q.submit(()->"overflow",v->{},e->{}))throw new AssertionError("overload must skip promptly");
        long start=System.nanoTime();
        if(q.finish(40))throw new AssertionError("blocked work reports unfinished");
        if((System.nanoTime()-start)/1_000_000>1000)throw new AssertionError("finish deadline");
        release.countDown();Thread.sleep(80);
        if(delivered.get()!=0||calls.get()!=0)throw new AssertionError("late/queued writes are frozen");
        SegmentPolishQueue ok=new SegmentPolishQueue(2);AtomicReference<String> result=new AtomicReference<>();
        ok.submit(()->"校正稿",result::set,e->{throw new AssertionError(e);});
        if(!ok.finish(2000)||!"校正稿".equals(result.get()))throw new AssertionError("successful correction delivered before finish");
        SegmentPolishQueue failed=new SegmentPolishQueue(1);AtomicInteger failures=new AtomicInteger();
        failed.submit(()->{throw new IllegalStateException("API error");},v->{},e->failures.incrementAndGet());failed.finish(2000);
        if(failures.get()!=1)throw new AssertionError("API error observable exactly once");
        System.out.println("PASS: bounded polish scheduling, overload, deadline, late writes, success and failure");
    }
}
