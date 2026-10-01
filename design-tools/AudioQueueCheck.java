package com.example.tingxiejian;
public final class AudioQueueCheck {
    public static void main(String[] args)throws Exception{
        BoundedAudioQueue q=new BoundedAudioQueue(2);
        if(!q.offer(new float[]{1},0)||!q.offer(new float[]{2},0)||q.offer(new float[]{3},0))throw new AssertionError("bound");
        if(q.size()!=2)throw new AssertionError("queue exceeded bound");q.finish();
        for(int i=1;i<=3;i++)if(q.take()[0]!=i)throw new AssertionError("lost/ reordered recorded PCM");
        if(q.take()!=null)throw new AssertionError("end");
        try{q.offer(new float[]{4},0);throw new AssertionError("write after stop");}catch(IllegalStateException expected){}
        System.out.println("PASS: bounded audio FIFO/overrun tail/stop behavior");
    }
}
