package com.example.tingxiejian;

import android.app.ActivityManager;
import android.content.Context;
import com.k2fsa.sherpa.onnx.*;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;

/** Android/native adapters around the behavior-tested shared recognition core. */
final class SpeechPipeline implements AutoCloseable {
    private final Context app;
    private final Transcriber.Events out;
    private final RecognitionPipeline core;
    private final List<String> hotwords;
    private final Set<String> warnings=Collections.synchronizedSet(new LinkedHashSet<>());
    private final Map<String,String> polished=new ConcurrentHashMap<>();
    private final SegmentPolishQueue polishQueue;
    private final NativeFinalizer nativeFinalizer=new NativeFinalizer();
    private final boolean autoPolish;
    private volatile boolean closed;
    private volatile String modelUsed="streaming-fallback";
    private long lastUi;
    private double total;
    private final ArrayDeque<Float> envelope=new ArrayDeque<>();

    SpeechPipeline(Context context,Transcriber.Events out)throws Exception {
        app=context.getApplicationContext();this.out=out;hotwords=HotwordRepository.load(app);
        autoPolish=Cloud.configured(app);
        if(!autoPolish)warning("未配置 API Key：已跳过 AI 文字校正，保留本地忠实稿");
        polishQueue=new SegmentPolishQueue(4);
        NativeStreaming nativeStreaming=new NativeStreaming();
        try {
            core=new RecognitionPipeline(File.createTempFile("speech-",".pcm",app.getCacheDir()),nativeStreaming,
                    nativeFinalizer,new RecognitionPipeline.Observer(){
                public void partial(StableTextTracker.Text text,double sec,float rms)throws Exception{
                    long now=System.currentTimeMillis();if(now-lastUi<120)return;lastUi=now;
                    envelope.addLast(Math.min(1,rms*5));while(envelope.size()>160)envelope.removeFirst();
                    JSONObject e=event("progress","");e.put("processed",sec);e.put("total",total);
                    e.put("partial",text.stable+text.unstable);e.put("stable",text.stable);e.put("unstable",text.unstable);
                    e.put("rms",rms);e.put("completed",core.snapshot().size());
                    JSONArray wave=new JSONArray();for(float value:envelope)wave.put(value);e.put("wave",wave);emit(e);
                }
                public void segment(RecognitionPipeline.Segment segment,boolean finalUpdate)throws Exception{
                    JSONObject e=event("segment",finalUpdate?"语句复核完成":"语句已记录");
                    e.put("segment",json(segment));emit(e);
                    if(finalUpdate&&autoPolish){
                        if(!polishQueue.submit(()->TranscriptPolisher.polishSegment(app,json(segment),hotwords),
                                text->deliverPolish(segment,text),error->warning("AI 文字校正未完成或未通过保护校验，忠实稿保留")))
                            warning("文字校正队列已满：部分语句跳过校正，忠实稿已保留");
                    }
                }
                public void warning(String message){SpeechPipeline.this.warning(message);}
            },hotwords);
        }catch(Exception|Error error){nativeStreaming.close();polishQueue.close();throw error;}
    }
    void accept(float[] samples,int length,double seconds,double total)throws Exception{
        this.total=total;core.accept(samples,length);
    }
    void phase(String text)throws IOException{emit(event("phase",text));}
    private void statePhase(String state,String text)throws Exception {JSONObject e=event("phase",text);e.put("state",state);emit(e);}
    private void emit(JSONObject event)throws IOException {
        if(closed||Thread.currentThread().isInterrupted())throw new InterruptedIOException("cancelled");out.emit(event);
    }
    private void warning(String message){if(message!=null)warnings.add(message);}
    private static JSONObject event(String type,String message){JSONObject e=new JSONObject();
        try{e.put("type",type);e.put("message",message);}catch(JSONException ignored){}return e;}
    private void deliverPolish(RecognitionPipeline.Segment segment,String text){
        if(closed)return;
        if(text==null||text.trim().isEmpty()){warning("文字校正未返回有效内容或 API Key 已移除，保留忠实稿");return;}
        try{
            if(text!=null&&!text.trim().isEmpty())polished.put(segment.id,text);
            JSONObject e=event("polished-segment","文字轻度校正完成");e.put("segment_id",segment.id);
            e.put("polished_text",text);emit(e);
        }catch(Exception error){warning("AI 文字校正未完成或未通过保护校验，忠实稿保留");}
    }
    JSONObject finish(boolean wantSpeakers,int speakerCount)throws Exception{
        statePhase("FINALIZING","正在完成高精度复核");core.finish();nativeFinalizer.close();
        if(autoPolish){
            statePhase("POLISHING","正在完成文字轻度校正");
        }
        if(!polishQueue.finish(autoPolish?30_000:0))warning("文字校正等待超过 30 秒：剩余语句保留忠实稿，稍后重试");
        List<RecognitionPipeline.Segment> segments=core.snapshot();Map<String,Integer> speakers=new HashMap<>();
        if(segments.isEmpty())warning("未检测到有效语音或模型未返回文字，请检查音量与录音内容");
        if(wantSpeakers&&!segments.isEmpty())diarize(segments,speakers,speakerCount);
        JSONArray rows=new JSONArray();StringBuilder raw=new StringBuilder(),faithful=new StringBuilder(),edited=new StringBuilder();
        for(RecognitionPipeline.Segment s:segments){
            JSONObject row=json(s);row.put("speaker",speakers.containsKey(s.id)?speakers.get(s.id):JSONObject.NULL);
            if(polished.containsKey(s.id))row.put("polished_text",polished.get(s.id));rows.put(row);
            append(raw,s.raw);append(faithful,s.finalText);append(edited,polished.getOrDefault(s.id,s.finalText));
        }
        JSONObject result=event("result","");result.put("segments",rows);result.put("duration",core.duration());
        result.put("text",faithful.toString());result.put("raw_text",raw.toString());result.put("final_text",faithful.toString());
        if(!polished.isEmpty())result.put("polished_text",edited.toString());
        result.put("polish_status",!autoPolish?"SKIPPED_NO_KEY":polished.isEmpty()?"FALLBACK":polished.size()<segments.size()?"PARTIAL":"DONE");
        result.put("model_metadata",new JSONObject().put("streaming","zipformer-zh-2025-06-30")
                .put("finalizer",modelUsed).put("vad","adaptive-rms-v1").put("normalization","width-spacing-v1"));
        result.put("hotwords",new JSONArray(hotwords));synchronized(warnings){result.put("warning",String.join("；",warnings));}return result;
    }
    private static void append(StringBuilder b,String s){if(s!=null&&!s.isEmpty()){if(b.length()>0)b.append('\n');b.append(s);}}
    private static JSONObject json(RecognitionPipeline.Segment s)throws JSONException{
        return new JSONObject().put("segment_id",s.id).put("start",s.start).put("end",s.end)
                .put("raw_text",s.raw).put("original_text",s.raw).put("final_text",s.finalText).put("text",s.finalText);
    }
    private File model(String name)throws IOException{return ModelManager.ensure(app,name);}

    private final class NativeStreaming implements RecognitionPipeline.StreamingRecognizer {
        private OnlineRecognizer recognizer;private OnlineStream stream;
        NativeStreaming()throws Exception{
            OnlineTransducerModelConfig transducer=new OnlineTransducerModelConfig();
            transducer.setEncoder(model("stream-encoder.onnx").getAbsolutePath());transducer.setDecoder(model("stream-decoder.onnx").getAbsolutePath());
            transducer.setJoiner(model("stream-joiner.onnx").getAbsolutePath());
            OnlineModelConfig m=new OnlineModelConfig();m.setTransducer(transducer);m.setTokens(model("stream-tokens.txt").getAbsolutePath());m.setNumThreads(2);
            OnlineRecognizerConfig c=new OnlineRecognizerConfig();c.setModelConfig(m);c.setEnableEndpoint(false);
            if(!hotwords.isEmpty()){c.setDecodingMethod("modified_beam_search");c.setHotwordsScore(1.5f);}
            recognizer=new OnlineRecognizer(null,c);
            try{stream=recognizer.createStream(HotwordRepository.nativeTerms(hotwords));}
            catch(Exception|Error error){recognizer.release();recognizer=null;throw error;}
        }
        public String accept(float[] samples){stream.acceptWaveform(samples,16000);
            while(recognizer.isReady(stream))recognizer.decode(stream);return recognizer.getResult(stream).getText();}
        public String finishSegment(){
            stream.inputFinished();while(recognizer.isReady(stream))recognizer.decode(stream);
            String text=recognizer.getResult(stream).getText();stream.release();stream=null;
            stream=recognizer.createStream(HotwordRepository.nativeTerms(hotwords));return text;
        }
        public void close(){if(stream!=null){stream.release();stream=null;}if(recognizer!=null){recognizer.release();recognizer=null;}}
    }
    private final class NativeFinalizer implements RecognitionPipeline.FinalRecognizer {
        private OfflineRecognizer recognizer;private OfflinePunctuation punctuation;private boolean initialized,punctTried;
        private void initialize()throws Exception{
            if(initialized)return;initialized=true;
            String selected=app.getSharedPreferences(SettingsActivity.PREFS,0).getString("local_finalizer","qwen3");
            if("qwen3".equals(selected)){
                try{
                    ActivityManager am=(ActivityManager)app.getSystemService(Context.ACTIVITY_SERVICE);
                    ActivityManager.MemoryInfo info=new ActivityManager.MemoryInfo();if(am!=null)am.getMemoryInfo(info);
                    if(am==null||info.lowMemory||info.availMem<3L*1024*1024*1024)throw new IOException("可用系统内存不足 3 GiB，安全跳过 Qwen3-ASR 0.6B");
                    File dir=ModelManager.ensureQwen(app);OfflineQwen3AsrModelConfig q=new OfflineQwen3AsrModelConfig();
                    q.setConvFrontend(new File(dir,"conv_frontend.onnx").getAbsolutePath());q.setEncoder(new File(dir,"encoder.int8.onnx").getAbsolutePath());
                    q.setDecoder(new File(dir,"decoder.int8.onnx").getAbsolutePath());q.setTokenizer(new File(dir,"tokenizer").getAbsolutePath());
                    q.setMaxTotalLen(512);q.setMaxNewTokens(256);q.setTemperature(.000001f);q.setSeed(42);q.setHotwords(String.join(",",hotwords));
                    OfflineModelConfig m=new OfflineModelConfig();m.setNumThreads(2);m.setQwen3Asr(q);m.setTokens("");
                    FeatureConfig features=new FeatureConfig();features.setFeatureDim(128);features.setSampleRate(16000);
                    OfflineRecognizerConfig c=new OfflineRecognizerConfig();c.setFeatConfig(features);c.setModelConfig(m);
                    phase("加载 Qwen3-ASR 0.6B 高精度模型");recognizer=new OfflineRecognizer(null,c);
                    modelUsed="qwen3-asr-0.6b-int8";return;
                }catch(Exception|LinkageError unavailable){warning("Qwen3-ASR 不可用："+unavailable.getMessage()+"；尝试标准复核");}
            }
            OfflineModelConfig m=new OfflineModelConfig();m.setNumThreads(2);
            OfflineParaformerModelConfig para=new OfflineParaformerModelConfig();para.setModel(model("offline-paraformer.onnx").getAbsolutePath());
            m.setParaformer(para);m.setTokens(model("offline-tokens.txt").getAbsolutePath());
            OfflineRecognizerConfig c=new OfflineRecognizerConfig();c.setModelConfig(m);recognizer=new OfflineRecognizer(null,c);
            modelUsed="paraformer-zh-2023-09-14";
        }
        public String recognize(float[] samples,List<String> words)throws Exception{
            initialize();if(recognizer==null)throw new IOException("Finalizer 未就绪");String text;OfflineStream s=recognizer.createStream();
            try{s.acceptWaveform(samples,16000);recognizer.decode(s);text=recognizer.getResult(s).getText();}finally{s.release();}
            if(!punctTried){punctTried=true;try{OfflinePunctuationModelConfig p=new OfflinePunctuationModelConfig();
                p.setCtTransformer(model("punct.onnx").getAbsolutePath());punctuation=new OfflinePunctuation(null,new OfflinePunctuationConfig(p));}
                catch(Exception|LinkageError e){warning("标点模型未就绪，保留识别文本");}}
            if(punctuation!=null)try{text=punctuation.addPunctuation(text);}catch(Exception|LinkageError e){warning("标点处理失败，保留识别文本");}
            return text;
        }
        public void close(){if(punctuation!=null){punctuation.release();punctuation=null;}if(recognizer!=null){recognizer.release();recognizer=null;}}
    }
    private void diarize(List<RecognitionPipeline.Segment> pieces,Map<String,Integer> speakers,int count)throws Exception{
        Runtime rt=Runtime.getRuntime();long available=rt.maxMemory()-rt.totalMemory()+rt.freeMemory();
        if(!RecognitionPipeline.canDiarize(core.duration(),available)){warning("内存预算不足或录音过长，已安全跳过分人，文字保留");return;}
        phase("正在辨认录音中的不同发言人");
        try{
            OfflineSpeakerSegmentationPyannoteModelConfig py=new OfflineSpeakerSegmentationPyannoteModelConfig();py.setModel(model("diar-segmentation.onnx").getAbsolutePath());
            OfflineSpeakerSegmentationModelConfig segmentation=new OfflineSpeakerSegmentationModelConfig();segmentation.setPyannote(py);
            SpeakerEmbeddingExtractorConfig embed=new SpeakerEmbeddingExtractorConfig(model("diar-embedding.onnx").getAbsolutePath(),1,false,"cpu");
            FastClusteringConfig cluster=new FastClusteringConfig();if(count>0)cluster.setNumClusters(count);else cluster.setThreshold(.9f);
            OfflineSpeakerDiarizationConfig c=new OfflineSpeakerDiarizationConfig();c.setSegmentation(segmentation);c.setEmbedding(embed);c.setClustering(cluster);
            OfflineSpeakerDiarization diar=new OfflineSpeakerDiarization(null,c);
            try{OfflineSpeakerDiarizationSegment[] spans=diar.process(RecognitionPipeline.read(core.audioFile(),0,core.duration()));
                for(RecognitionPipeline.Segment piece:pieces){double best=0;for(OfflineSpeakerDiarizationSegment span:spans){
                    double overlap=Math.max(0,Math.min(piece.end,span.getEnd())-Math.max(piece.start,span.getStart()));
                    if(overlap>best){best=overlap;speakers.put(piece.id,span.getSpeaker());}}}
            }finally{diar.release();}
        }catch(Exception|LinkageError error){warning("分人不可用，已保留文字："+error.getClass().getSimpleName());}
    }
    public void close(){closed=true;polishQueue.close();core.close();}
}
