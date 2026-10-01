package com.example.tingxiejian;

import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Shared file/microphone recognition core. Final boundaries come from VAD, not streaming endpoint. */
final class RecognitionPipeline implements AutoCloseable {
    interface StreamingRecognizer extends AutoCloseable {
        String accept(float[] samples)throws Exception;
        String finishSegment()throws Exception;
        void close();
    }
    interface FinalRecognizer extends AutoCloseable {
        String recognize(float[] samples,List<String> hotwords)throws Exception;
        void close();
    }
    interface Observer {
        void partial(StableTextTracker.Text text,double seconds,float rms)throws Exception;
        void segment(Segment segment,boolean finalUpdate)throws Exception;
        void warning(String message);
    }
    static final class Segment {
        final String id;final double start,end;final String raw;
        volatile String finalText;volatile boolean finalized;
        Segment(VadEngine.Segment s,String raw){id=s.id;start=s.startSample/16000.0;end=s.endSample/16000.0;
            this.raw=raw;finalText=TextNormalizer.normalize(raw);}
    }
    private final File archive;private final FileOutputStream output;
    private final StreamingRecognizer streaming;private final FinalRecognizer finalizer;
    private final Observer observer;private final List<String> hotwords;
    private final List<Segment> segments=Collections.synchronizedList(new ArrayList<>());
    private final List<Segment> deferred=new ArrayList<>();
    private final StableTextTracker tracker=new StableTextTracker(3);
    private final AtomicBoolean cancelled=new AtomicBoolean();
    private final ThreadPoolExecutor finalQueue;
    private final VadEngine vad;
    private long samples,lastPartial;private int committed;private boolean finished;
    private long lastBoundary;
    private String recognizedSinceBoundary="";
    RecognitionPipeline(File archive,StreamingRecognizer streaming,FinalRecognizer finalizer,
                        Observer observer,List<String> hotwords)throws IOException{
        this.archive=archive;this.output=new FileOutputStream(archive);this.streaming=streaming;
        this.finalizer=finalizer;this.observer=observer;this.hotwords=new ArrayList<>(hotwords);
        finalQueue=new ThreadPoolExecutor(1,1,0,TimeUnit.MILLISECONDS,new ArrayBlockingQueue<>(4),r->{
            Thread t=new Thread(r,"speech-finalizer");t.setDaemon(true);return t;});
        vad=new VadEngine(this::commit);
    }
    void accept(float[] data,int length)throws Exception{
        if(finished||cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("cancelled");
        if(length<0||length>data.length)throw new IllegalArgumentException("length");
        if(samples+length>3600L*16000)throw new IOException("录音不能超过 1 小时");
        for(int offset=0;offset<length;offset+=320){
            int n=Math.min(320,length-offset);float[] frame=Arrays.copyOfRange(data,offset,offset+n);
            byte[] bytes=new byte[n*2];double e=0;
            for(int i=0;i<n;i++){float f=Float.isFinite(frame[i])?Math.max(-1,Math.min(1,frame[i])):0;
                frame[i]=f;short value=(short)(f*32767);bytes[i*2]=(byte)value;bytes[i*2+1]=(byte)(value>>>8);e+=f*f;}
            output.write(bytes);samples+=n;
            String partial=streaming.accept(frame);
            int before=committed;vad.accept(frame,n);
            if(committed!=before)partial="";
            if(partial!=null&&!partial.trim().isEmpty())recognizedSinceBoundary=partial;
            if(samples-lastBoundary>=25L*16000&&!recognizedSinceBoundary.trim().isEmpty()){
                retainQuietText();partial="";
            }
            if(samples-lastPartial>=1920){lastPartial=samples;
                observer.partial(tracker.update(partial),samples/16000.0,(float)Math.sqrt(e/n));}
        }
    }
    private void commit(VadEngine.Segment span)throws Exception{
        String raw=streaming.finishSegment();
        if(raw==null||raw.trim().isEmpty())raw=recognizedSinceBoundary;
        Segment segment=new Segment(span,raw);tracker.reset();segments.add(segment);committed++;
        lastBoundary=samples;recognizedSinceBoundary="";
        observer.segment(segment,false);
        try{finalQueue.execute(()->finalizeSegment(segment));}
        catch(RejectedExecutionException full){deferred.add(segment);observer.warning("高精度队列繁忙，已保留音频和即时稿，停止后继续复核");}
    }
    private void finalizeSegment(Segment segment){
        if(cancelled.get())return;
        try{
            String text=finalizer.recognize(read(archive,segment.start,segment.end),hotwords);
            if(text!=null&&!text.trim().isEmpty())segment.finalText=TextNormalizer.normalize(text);
        }catch(Exception|LinkageError error){observer.warning("高精度复核不可用，保留即时稿："+error.getClass().getSimpleName());}
        if(cancelled.get())return;
        segment.finalized=true;
        try{observer.segment(segment,true);}catch(Exception error){cancelled.set(true);}
    }
    void finish()throws Exception{
        if(finished)return;vad.finish();
        if(!recognizedSinceBoundary.trim().isEmpty())retainQuietText();
        finished=true;output.flush();
        // Backpressure is allowed after capture stopped; descriptors, not audio arrays, are queued.
        for(Segment segment:deferred){
            while(!finalQueue.getQueue().offer(()->finalizeSegment(segment),100,TimeUnit.MILLISECONDS)){
                if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("cancelled");}
        }
        finalQueue.shutdown();
        while(!finalQueue.awaitTermination(100,TimeUnit.MILLISECONDS)){
            if(cancelled.get()||Thread.currentThread().isInterrupted())throw new InterruptedIOException("cancelled");}
        if(cancelled.get())throw new InterruptedIOException("cancelled");
    }
    private void retainQuietText()throws Exception{
        observer.warning("能量检测未完整切出语音：已按有界区间保留即时文字并尝试复核");
        vad.retainRecognizedText();
    }
    List<Segment> snapshot(){synchronized(segments){return new ArrayList<>(segments);}}
    File audioFile(){return archive;}
    double duration(){return samples/16000.0;}
    static float[] read(File pcm,double from,double to)throws IOException{
        long first=Math.max(0,(long)(from*16000)),last=Math.min(pcm.length()/2,(long)(to*16000));
        long n=Math.max(0,last-first);if(n>3600L*16000)throw new IOException("PCM too large");
        float[] values=new float[(int)n];
        try(RandomAccessFile in=new RandomAccessFile(pcm,"r")){in.seek(first*2);byte[] block=new byte[8192];int off=0;
            while(off<values.length){int got=in.read(block,0,Math.min(block.length,(values.length-off)*2));
                if(got<0)throw new EOFException("PCM incomplete");
                for(int i=0;i+1<got;i+=2)values[off++]=(short)((block[i]&255)|(block[i+1]<<8))/32768f;}}
        return values;
    }
    static boolean canDiarize(double seconds,long availableHeap){
        return seconds>=0&&seconds<=2400&&seconds*16000*4*3+64L*1024*1024<availableHeap;
    }
    @Override public void close(){
        cancelled.set(true);finalQueue.shutdownNow();
        // Never release JNI resources while an interrupted worker is still inside native decode.
        boolean interrupted=Thread.interrupted();
        while(!finalQueue.isTerminated()){
            try{finalQueue.awaitTermination(100,TimeUnit.MILLISECONDS);}catch(InterruptedException e){interrupted=true;}
        }
        try{finalizer.close();}finally{
            try{streaming.close();}finally{try{output.close();}catch(IOException ignored){}archive.delete();
                if(interrupted)Thread.currentThread().interrupt();}
        }
    }
}
