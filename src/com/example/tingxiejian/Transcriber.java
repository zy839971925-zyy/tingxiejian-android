package com.example.tingxiejian;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import org.json.JSONArray;
import org.json.JSONObject;
import com.k2fsa.sherpa.onnx.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** Models and temporary audio remain private to this app. No network inference. */
final class Transcriber {
    interface Events { void emit(JSONObject obj)throws IOException; }
    private static final int RATE=16000;
    private final Context context;
    private final File modelDir;
    private final List<Piece> pieces=new ArrayList<>();
    private String warning="";
    private double currentStart,lastProgress;
    /** Whole-file speaker clustering needs the full waveform in memory, so it is bounded. */
    private static final double MAX_DIARIZE_SECONDS=2400;
    // Peak envelope of the decoded audio, bucketed for the progress waveform.
    private float[] wave=new float[0];
    private double bucketSeconds=.25,bucketEnd=.25,bucketPeak;
    private boolean waveSized,waveDirty;
    Transcriber(Context c){context=c;modelDir=new File(c.getFilesDir(),"models-v1");}
    private static final class Piece {
        double start,end;String text;int speaker=-1;
        Piece(double a,double b,String t){start=a;end=b;text=t;}
    }
    private static JSONObject event(String type,String message){JSONObject j=new JSONObject();try{j.put("type",type);j.put("message",message);}catch(Exception ignored){}return j;}
    private void phase(Events out,String message)throws IOException{out.emit(event("phase",message));}
    private File model(String filename,Events out)throws Exception{
        if(!modelDir.isDirectory()&&!modelDir.mkdirs())throw new IOException("无法创建私有模型目录");
        File file=new File(modelDir,filename),temp=new File(modelDir,filename+".partial");
        long expected;
        try(AssetFileDescriptor afd=context.getAssets().openFd("model/"+filename)){expected=afd.getLength();}
        if(file.isFile()&&file.length()==expected)return file;
        phase(out,"首次准备离线模型："+filename+"（"+(expected/1048576)+" MB）");
        try(InputStream src=context.getAssets().open("model/"+filename);
            FileOutputStream dest=new FileOutputStream(temp)){
            byte[] buf=new byte[65536];int n;long copied=0,next=8L*1048576;
            while((n=src.read(buf))!=-1){dest.write(buf,0,n);copied+=n;
                if(copied>=next){phase(out,"准备 " + filename + " · " + (copied/1048576) + "/" + (expected/1048576) + " MB");next+=8L*1048576;}}
        }
        if(temp.length()!=expected)throw new IOException("模型文件写入不完整："+filename);
        if(!temp.renameTo(file))throw new IOException("模型文件不能移动到应用私有目录");
        return file;
    }
    private float[] readPcm(File pcm,double from,double until)throws Exception{
        long total=pcm.length()/2;
        long start=Math.max(0,(long)(from*RATE)),end=Math.min(total,(long)(until*RATE));
        if(end<=start)return new float[0];
        long count=end-start;if(count>3600L*RATE)throw new IOException("录音超出 1 小时上限");
        float[] out=new float[(int)count];
        byte[] block=new byte[1<<16];
        try(RandomAccessFile in=new RandomAccessFile(pcm,"r")){
            in.seek(start*2);
            int filled=0;
            while(filled<out.length){
                int got=in.read(block,0,(int)Math.min(block.length,(out.length-filled)*2L));
                if(got<=0)break;
                for(int i=0;i+1<got;i+=2){int v=(block[i]&255)|(block[i+1]<<8);out[filled++]=(short)v/32768f;}
            }
            if(filled<out.length){float[] trimmed=new float[filled];System.arraycopy(out,0,trimmed,0,filled);return trimmed;}
        }
        return out;
    }
    private static String clean(String s){return s==null?"":s.trim();}
    private void note(String message){warning+=(warning.isEmpty()?"":"；")+message;}
    private static String describe(Throwable error){
        String message=error.getMessage();
        return message==null||message.isEmpty()?error.getClass().getSimpleName():message;
    }
    /** Collects peak amplitude per time bucket so the page can draw the real recording envelope. */
    private void accumulate(float[] samples,int length,double seconds,double total){
        if(!waveSized&&total>0){bucketSeconds=Math.max(.08,total/160.0);bucketEnd=bucketSeconds;waveSized=true;}
        double chunkStart=seconds-length/16000.0;
        for(int i=0;i<length;i++){
            float value=Math.abs(samples[i]);
            if(value>bucketPeak)bucketPeak=value;
            if(chunkStart+i/16000.0>=bucketEnd)pushBucket();
        }
    }
    private void pushBucket(){
        if(wave.length>=200)halve();
        float[] grown=new float[wave.length+1];
        System.arraycopy(wave,0,grown,0,wave.length);
        grown[wave.length]=(float)Math.min(1,bucketPeak);
        wave=grown;bucketPeak=0;waveDirty=true;bucketEnd+=bucketSeconds;
    }
    /** Keeps the envelope bounded for very long recordings by merging neighbour buckets. */
    private void halve(){
        float[] half=new float[(wave.length+1)/2];
        for(int i=0;i<half.length;i++)half[i]=Math.max(wave[2*i],2*i+1<wave.length?wave[2*i+1]:0f);
        wave=half;bucketSeconds*=2;
    }
    private JSONArray waveJson()throws Exception{
        JSONArray array=new JSONArray();
        for(float value:wave)array.put(Math.round(value*100)/100.0);
        if(bucketPeak>0)array.put(Math.round(Math.min(1f,(float)bucketPeak)*100)/100.0);
        return array;
    }
    private void commit(OnlineRecognizer recognizer,OnlineStream stream,double now){
        String text=clean(recognizer.getResult(stream).getText());
        if(!text.isEmpty())pieces.add(new Piece(currentStart,now,text));
        currentStart=now;recognizer.reset(stream);
    }
    void transcribe(File input,boolean wantSpeakers,int count,Events out)throws Exception{
        File pcm=File.createTempFile("audio-",".pcm",context.getCacheDir());
        double duration=0;
        try {
            phase(out,"准备流式识别模型");
            OnlineModelConfig onlineModel=new OnlineModelConfig();
            OnlineTransducerModelConfig transducer=new OnlineTransducerModelConfig();
            transducer.setEncoder(model("stream-encoder.onnx",out).getAbsolutePath());
            transducer.setDecoder(model("stream-decoder.onnx",out).getAbsolutePath());
            transducer.setJoiner(model("stream-joiner.onnx",out).getAbsolutePath());
            onlineModel.setTransducer(transducer);
            onlineModel.setTokens(model("stream-tokens.txt",out).getAbsolutePath());
            onlineModel.setNumThreads(3);
            OnlineRecognizerConfig config=new OnlineRecognizerConfig();
            config.setModelConfig(onlineModel);config.setEnableEndpoint(true);
            OnlineRecognizer recognizer=new OnlineRecognizer(null,config);
            OnlineStream stream=recognizer.createStream("");
            try(FileOutputStream audio=new FileOutputStream(pcm)){
                phase(out,"正在逐段解码并识别录音");
                duration=PcmDecoder.decode(input,(samples,length,seconds,total)->{
                    byte[] bytes=new byte[length*2];
                    for(int i=0;i<length;i++){short v=(short)(Math.max(-1,Math.min(1,samples[i]))*32767);
                        bytes[i*2]=(byte)v;bytes[i*2+1]=(byte)(v>>>8);}
                    audio.write(bytes);
                    float[] chunk=new float[length];System.arraycopy(samples,0,chunk,0,length);
                    stream.acceptWaveform(chunk,RATE);
                    while(recognizer.isReady(stream))recognizer.decode(stream);
                    accumulate(samples,length,seconds,total);
                    if(recognizer.isEndpoint(stream)||seconds-currentStart>=20){commit(recognizer,stream,seconds);}
                    if(seconds-lastProgress>=0.35 || (total>0 && seconds>=total)){
                        JSONObject j=event("progress","");
                        try{j.put("processed",seconds);j.put("total",total);j.put("partial",clean(recognizer.getResult(stream).getText()));
                            j.put("completed",pieces.size());j.put("recent",pieces.isEmpty()?"":pieces.get(pieces.size()-1).text);
                            if(waveDirty){j.put("wave",waveJson());waveDirty=false;}}catch(Exception ignored){}
                        out.emit(j);lastProgress=seconds;
                    }
                });
                stream.inputFinished();while(recognizer.isReady(stream))recognizer.decode(stream);
                String finalText=clean(recognizer.getResult(stream).getText());
                if(!finalText.isEmpty())pieces.add(new Piece(currentStart,duration,finalText));
                if(bucketPeak>0){pushBucket();JSONObject j=event("progress","");
                    try{j.put("processed",duration);j.put("total",duration);j.put("partial","");j.put("completed",pieces.size());
                        j.put("wave",waveJson());}catch(Exception ignored){}
                    out.emit(j);waveDirty=false;}
            }finally{stream.release();recognizer.release();}
            phase(out,"流式识别完成，正在校正文字");
            try{
                OfflineModelConfig offlineModel=new OfflineModelConfig();
                OfflineParaformerModelConfig para=new OfflineParaformerModelConfig();
                para.setModel(model("offline-paraformer.onnx",out).getAbsolutePath());offlineModel.setParaformer(para);
                offlineModel.setTokens(model("offline-tokens.txt",out).getAbsolutePath());offlineModel.setNumThreads(3);
                OfflineRecognizerConfig offlineConfig=new OfflineRecognizerConfig();offlineConfig.setModelConfig(offlineModel);
                OfflineRecognizer offline=new OfflineRecognizer(null,offlineConfig);
                try {
                    int index=0;
                    for(Piece piece:pieces){
                        float[] samples=readPcm(pcm,Math.max(0,piece.start-.15),Math.min(duration,piece.end+.15));
                        OfflineStream s=offline.createStream();
                        try{s.acceptWaveform(samples,RATE);offline.decode(s);
                            String better=clean(offline.getResult(s).getText());if(!better.isEmpty())piece.text=better;
                        }finally{s.release();}
                        JSONObject e=event("phase","校正文字 · "+(++index)+"/"+pieces.size());
                        try{e.put("processed",index);e.put("total",pieces.size());}catch(Exception ignored){}out.emit(e);
                    }
                }finally{offline.release();}
            }catch(Throwable ex){
                if(ex instanceof LocalService.CancelledException)throw (LocalService.CancelledException)ex;
                note("高精度校正未完成，保留流式结果："+describe(ex));
            }
            phase(out,"正在恢复标点");
            try{
                OfflinePunctuationModelConfig pModel=new OfflinePunctuationModelConfig();
                pModel.setCtTransformer(model("punct.onnx",out).getAbsolutePath());
                OfflinePunctuation punct=new OfflinePunctuation(null,new OfflinePunctuationConfig(pModel));
                phase(out,"正在恢复标点"); // model() may have emitted a preparation phase
                try{for(Piece piece:pieces)piece.text=clean(punct.addPunctuation(piece.text));}
                finally{punct.release();}
            }catch(Throwable ex){
                if(ex instanceof LocalService.CancelledException)throw (LocalService.CancelledException)ex;
                note("标点恢复未完成");
            }
            if(wantSpeakers && !pieces.isEmpty() && duration>MAX_DIARIZE_SECONDS){
                note("录音超过 "+(int)(MAX_DIARIZE_SECONDS/60)+" 分钟，未做发言人区分");
            } else if(wantSpeakers && !pieces.isEmpty()){
                phase(out,"正在辨认录音中的不同发言人");
                try{
                    OfflineSpeakerSegmentationPyannoteModelConfig py=new OfflineSpeakerSegmentationPyannoteModelConfig();
                    py.setModel(model("diar-segmentation.onnx",out).getAbsolutePath());
                    OfflineSpeakerSegmentationModelConfig seg=new OfflineSpeakerSegmentationModelConfig();seg.setPyannote(py);
                    SpeakerEmbeddingExtractorConfig embed=new SpeakerEmbeddingExtractorConfig(
                        model("diar-embedding.onnx",out).getAbsolutePath(),1,false,"cpu");
                    FastClusteringConfig cluster=new FastClusteringConfig();
                    if(count>0)cluster.setNumClusters(count);else cluster.setThreshold(.9f);
                    OfflineSpeakerDiarizationConfig dc=new OfflineSpeakerDiarizationConfig();
                    dc.setSegmentation(seg);dc.setEmbedding(embed);dc.setClustering(cluster);
                    OfflineSpeakerDiarization diar=new OfflineSpeakerDiarization(null,dc);
                    try {
                        phase(out,"正在辨认录音中的不同发言人"); // restore indeterminate after model extraction
                        OfflineSpeakerDiarizationSegment[] spans=diar.process(readPcm(pcm,0,duration));
                        for(Piece piece:pieces){double best=0;for(OfflineSpeakerDiarizationSegment s:spans){
                            double overlap=Math.max(0,Math.min(piece.end,s.getEnd())-Math.max(piece.start,s.getStart()));
                            if(overlap>best){best=overlap;piece.speaker=s.getSpeaker();}
                        }}
                    }finally{diar.release();}
                }catch(Throwable ex){
                    if(ex instanceof LocalService.CancelledException)throw (LocalService.CancelledException)ex;
                    note("发言人区分未完成："+describe(ex));
                }
            }
            JSONArray arr=new JSONArray();
            for(Piece piece:pieces){JSONObject o=new JSONObject();o.put("start",Math.round(piece.start*100)/100.0);
                o.put("end",Math.round(piece.end*100)/100.0);o.put("speaker",piece.speaker<0?JSONObject.NULL:piece.speaker);
                o.put("text",piece.text);arr.put(o);}
            JSONObject result=event("result","");result.put("duration",Math.round(duration*100)/100.0);
            result.put("segments",arr);result.put("warning",warning.isEmpty()?JSONObject.NULL:warning);out.emit(result);
        }finally{pcm.delete();}
    }
}
