package com.example.tingxiejian;

import java.io.File;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

public final class PipelineLogicCheck {
    private static int checks;
    private static void check(boolean condition, String why) {
        checks++; if (!condition) throw new AssertionError(why);
    }
    public static void main(String[] args) throws Exception {
        StableTextTracker t = new StableTextTracker(3);
        check(t.update("你好世界").stable.isEmpty(), "one partial is unstable");
        t.update("你好世间");
        StableTextTracker.Text text = t.update("你好世界");
        check(text.stable.equals("你好世"), "common prefix commits after three partials");
        check(t.update("你好世界呀").stable.startsWith("你好世"), "stable prefix never rolls back");
        t.reset(); check(t.update("新句").stable.isEmpty(), "new segment resets stability");
        check(TextNormalizer.normalize("ＡＢＣ　１２３  %\n你好").equals("ABC 123 % 你好"), "width/whitespace normalization");
        check(TextNormalizer.normalize("一千二百元").equals("一千二百元"), "no guessed numeral substitution");
        List<VadEngine.Segment> spans = new ArrayList<>();
        VadEngine v = new VadEngine(spans::add);
        float[] silence = new float[320], speech = new float[320]; Arrays.fill(speech, .1f);
        for (int i=0;i<20;i++) v.accept(silence,320);
        for (int i=0;i<50;i++) v.accept(speech,320);
        for (int i=0;i<35;i++) v.accept(silence,320);
        check(spans.size()==1, "silence endpoint produces one independent VAD segment");
        check(spans.get(0).startSample < 20*320 && spans.get(0).endSample > 70*320, "VAD pre/post roll");
        for (int i=0;i<20;i++) v.accept(speech,320); v.finish();
        check(spans.size()==2 && spans.get(1).startSample>=spans.get(0).endSample, "segments do not overlap");
        check(!spans.get(0).id.equals(spans.get(1).id), "stable unique ids");
        File audio=File.createTempFile("pipeline-test", ".pcm");
        List<RecognitionPipeline.Segment> finished=Collections.synchronizedList(new ArrayList<>());
        RecognitionPipeline.StreamingRecognizer streaming=new RecognitionPipeline.StreamingRecognizer(){
            boolean hasAudio;
            public String accept(float[] a){for(float value:a)if(Math.abs(value)>.001f)hasAudio=true;
                return hasAudio?"原始文字":"";}
            public String finishSegment(){String text=hasAudio?"原始文字":"";hasAudio=false;return text;}
            public void close(){}
        };
        RecognitionPipeline.FinalRecognizer finalizer=new RecognitionPipeline.FinalRecognizer(){
            public String recognize(float[] a,List<String> h)throws Exception{throw new Exception("model unavailable");}
            public void close(){}
        };
        RecognitionPipeline.Observer observer=new RecognitionPipeline.Observer(){
            public void partial(StableTextTracker.Text a,double sec,float rms){}
            public void segment(RecognitionPipeline.Segment a,boolean update){if(update)finished.add(a);}
            public void warning(String message){}
        };
        try(RecognitionPipeline p=new RecognitionPipeline(audio,streaming,finalizer,observer,Collections.emptyList())){
            for(int i=0;i<50;i++)p.accept(speech,320);
            for(int i=0;i<35;i++)p.accept(silence,320);
            p.finish();
            check(p.snapshot().size()==1,"pipeline preserves segment");
            check(p.snapshot().get(0).finalText.equals("原始文字"),"failed finalizer preserves streaming");
            check(!finished.isEmpty(),"fallback completion is observable");
        }
        check(!audio.exists(),"PCM archive closed and removed");
        check(!RecognitionPipeline.canDiarize(2400,128L*1024*1024),"diar memory preflight");
        check(RecognitionPipeline.canDiarize(10,256L*1024*1024),"small waveform permitted");
        CountDownLatch entered=new CountDownLatch(1),release=new CountDownLatch(1);
        AtomicInteger calls=new AtomicInteger(),submitted=new AtomicInteger();
        RecognitionPipeline.FinalRecognizer blocked=new RecognitionPipeline.FinalRecognizer(){
            public String recognize(float[] a,List<String> h)throws Exception{
                entered.countDown();release.await();calls.incrementAndGet();return "高精度结果";
            }
            public void close(){}
        };
        RecognitionPipeline.Observer events=new RecognitionPipeline.Observer(){
            public void partial(StableTextTracker.Text a,double sec,float rms){}
            public void segment(RecognitionPipeline.Segment a,boolean update){if(!update)submitted.incrementAndGet();}
            public void warning(String message){}
        };
        File queuedAudio=File.createTempFile("pipeline-async", ".pcm");
        try(RecognitionPipeline p=new RecognitionPipeline(queuedAudio,streaming,blocked,events,Collections.emptyList())){
            for(int i=0;i<9;i++){
                for(int j=0;j<10;j++)p.accept(speech,320);
                for(int j=0;j<35;j++)p.accept(silence,320);
            }
            check(entered.await(1,TimeUnit.SECONDS),"async finalizer starts during capture");
            check(submitted.get()==9,"capture continues across full finalizer queue");
            check(calls.get()==0,"native worker may be busy without blocking next sentence");
            release.countDown();p.finish();
            check(calls.get()==9,"deferred descriptors drained without losing audio");
            for(RecognitionPipeline.Segment s:p.snapshot()){
                check(s.raw.equals("原始文字")&&s.finalText.equals("高精度结果")&&s.finalized,"raw immutable/finalizer authoritative");
            }
        }finally{release.countDown();}
        CountDownLatch nativeEntered=new CountDownLatch(1),nativeRelease=new CountDownLatch(1);
        AtomicBoolean nativeClosed=new AtomicBoolean();
        RecognitionPipeline.FinalRecognizer ignoresInterrupt=new RecognitionPipeline.FinalRecognizer(){
            public String recognize(float[] a,List<String> h){nativeEntered.countDown();while(nativeRelease.getCount()>0){
                try{nativeRelease.await();}catch(InterruptedException ignored){}}return "完成";}
            public void close(){nativeClosed.set(true);}
        };
        File cancelAudio=File.createTempFile("pipeline-cancel", ".pcm");
        RecognitionPipeline cancelling=new RecognitionPipeline(cancelAudio,streaming,ignoresInterrupt,events,Collections.emptyList());
        for(int j=0;j<10;j++)cancelling.accept(speech,320);for(int j=0;j<35;j++)cancelling.accept(silence,320);
        check(nativeEntered.await(1,TimeUnit.SECONDS),"native worker running before cancel");
        AtomicBoolean interruptPreserved=new AtomicBoolean();Thread closer=new Thread(()->{
            Thread.currentThread().interrupt();cancelling.close();interruptPreserved.set(Thread.currentThread().isInterrupted());});closer.start();
        closer.join(80);check(!nativeClosed.get()&&closer.isAlive(),"cancel never releases active native resource");
        nativeRelease.countDown();closer.join(2000);
        check(!closer.isAlive()&&nativeClosed.get()&&interruptPreserved.get()&&!cancelAudio.exists(),"cancel cleanup and interrupt restored");
        List<VadEngine.Segment> longSpans=new ArrayList<>();VadEngine longVad=new VadEngine(longSpans::add);
        for(int i=0;i<3000;i++)longVad.accept(speech,320);longVad.finish();
        check(longSpans.size()==3,"continuous input bounded into independent segments");
        for(int i=0;i<longSpans.size();i++){
            check(longSpans.get(i).endSample-longSpans.get(i).startSample<=25*16000,"max segment bound");
            if(i>0)check(longSpans.get(i).startSample>=longSpans.get(i-1).endSample,"continuous segments no overlap");
        }
        File quietAudio=File.createTempFile("pipeline-quiet", ".pcm");float[] whisper=new float[320];Arrays.fill(whisper,.002f);
        try(RecognitionPipeline p=new RecognitionPipeline(quietAudio,streaming,finalizer,observer,Collections.emptyList())){
            for(int i=0;i<2750;i++)p.accept(whisper,320);p.finish();
            check(p.snapshot().size()==3,"low-energy recognized text survives bounded cuts and EOF");
            for(RecognitionPipeline.Segment s:p.snapshot()){
                check(s.end-s.start<=25.001&&s.finalText.equals("原始文字"),"fallback preserves text without an unbounded audio allocation");
            }
        }
        RecognitionPipeline.StreamingRecognizer revisesToEmpty=new RecognitionPipeline.StreamingRecognizer(){
            int frames;
            public String accept(float[] a){return ++frames<50?"已显示的低音量文字":"";}
            public String finishSegment(){return "";}
            public void close(){}
        };
        try(RecognitionPipeline p=new RecognitionPipeline(File.createTempFile("pipeline-prefix", ".pcm"),
                revisesToEmpty,finalizer,observer,Collections.emptyList())){
            for(int i=0;i<60;i++)p.accept(whisper,320);p.finish();
            check(p.snapshot().size()==1&&p.snapshot().get(0).finalText.equals("已显示的低音量文字"),
                    "empty final streaming revision cannot silently drop previously displayed text");
        }
        System.out.println("PASS: pipeline behavior ("+checks+" assertions)");
    }
}
