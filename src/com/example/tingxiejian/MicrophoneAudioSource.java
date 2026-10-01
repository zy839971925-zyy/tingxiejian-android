package com.example.tingxiejian;

import android.media.*;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

/** AudioRecord thread only reads/copies PCM. Inference and storage run on the consumer. */
final class MicrophoneAudioSource implements AudioSource {
    private static final int RATE=16000;
    private final BoundedAudioQueue frames=new BoundedAudioQueue(128);
    private final AtomicBoolean stopped=new AtomicBoolean();
    private final Object lifecycleLock=new Object();
    private volatile boolean paused;
    private volatile AudioRecord recorder;
    private volatile String warning="";
    private volatile Exception failure;
    private Thread capture;
    void pause(boolean value){paused=value;}
    String warning(){return warning;}
    void stop(){
        synchronized(lifecycleLock){stopped.set(true);AudioRecord r=recorder;
            if(r!=null)try{r.stop();}catch(IllegalStateException ignored){} }
    }
    public double pump(PcmDecoder.Sink sink)throws Exception {
        capture=new Thread(this::capture,"microphone-capture");capture.start();long samples=0;
        try{
            float[] data;while((data=frames.take())!=null){samples+=data.length;sink.accept(data,data.length,samples/16000.0,0);}
            if(failure!=null){if(samples==0)throw failure;warning="录音被中断，已保存已采集音频的文字："+failure.getClass().getSimpleName();}
            return samples/16000.0;
        }finally{stop();if(capture!=null)capture.join(3000);}
    }
    private void capture(){
        AudioRecord audio=null;
        try{
            int min=AudioRecord.getMinBufferSize(RATE,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IOException("设备不支持 16 kHz 单声道录音");
            if(stopped.get())return;
            audio=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,RATE,AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,Math.max(min,RATE*4));
            if(audio.getState()!=AudioRecord.STATE_INITIALIZED)throw new IOException("麦克风初始化失败或正被占用");
            synchronized(lifecycleLock){if(stopped.get())return;recorder=audio;audio.startRecording();}
            boolean recording=true;short[] block=new short[640];
            while(!stopped.get()){
                if(paused){if(recording){synchronized(lifecycleLock){audio.stop();recording=false;}}Thread.sleep(30);continue;}
                if(!recording){synchronized(lifecycleLock){if(stopped.get())break;audio.startRecording();recording=true;}}
                int n=audio.read(block,0,block.length,AudioRecord.READ_BLOCKING);
                if(n<=0&&stopped.get())break;
                if(n<0)throw new IOException("麦克风不可用（"+n+"）");if(n==0)continue;
                float[] values=new float[n];for(int i=0;i<n;i++)values[i]=block[i]/32768f;
                if(!frames.offer(values,200)){warning="设备跟不上实时识别，已安全结束录音；已读音频完整保留，请改用导入录音";break;}
            }
        }catch(Exception error){if(!stopped.get())failure=error;}
        finally{synchronized(lifecycleLock){recorder=null;if(audio!=null){try{audio.stop();}catch(IllegalStateException ignored){}audio.release();}}frames.finish();}
    }
}
