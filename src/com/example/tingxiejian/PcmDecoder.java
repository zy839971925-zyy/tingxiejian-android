package com.example.tingxiejian;

import android.media.AudioFormat;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** Incremental file decoder + continuous 16 kHz mono linear resampler. */
final class PcmDecoder {
    interface Sink { void accept(float[] samples,int length,double seconds,double total) throws Exception; }
    static double decode(File input,Sink sink)throws Exception {
        MediaExtractor extractor=new MediaExtractor();MediaCodec codec=null;
        try {
            extractor.setDataSource(input.getAbsolutePath());
            int track=-1;
            for(int i=0;i<extractor.getTrackCount();i++){
                MediaFormat f=extractor.getTrackFormat(i);
                if(f.getString(MediaFormat.KEY_MIME).startsWith("audio/")){track=i;break;}
            }
            if(track<0)throw new IOException("文件中没有可识别的音频轨道");
            extractor.selectTrack(track);
            MediaFormat format=extractor.getTrackFormat(track);
            String mime=format.getString(MediaFormat.KEY_MIME);
            int rate=format.containsKey(MediaFormat.KEY_SAMPLE_RATE)?format.getInteger(MediaFormat.KEY_SAMPLE_RATE):16000;
            int channels=format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)?format.getInteger(MediaFormat.KEY_CHANNEL_COUNT):1;
            if(rate<8000||rate>192000||channels<1||channels>8)throw new IOException("音频采样率或声道数量不支持");
            double total=format.containsKey(MediaFormat.KEY_DURATION)?format.getLong(MediaFormat.KEY_DURATION)/1000000.0:0;
            Resampler resample=new Resampler(rate,sink,total);
            if(mime.equals("audio/raw")) {
                ByteBuffer buffer=ByteBuffer.allocateDirect(1<<20);
                while(true){buffer.clear();int n=extractor.readSampleData(buffer,0);if(n<0)break;
                    buffer.position(0);buffer.limit(n);
                    resample.feed(buffer,channels,format.containsKey(MediaFormat.KEY_PCM_ENCODING)?format.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT);
                    extractor.advance();
                }
            } else {
                codec=MediaCodec.createDecoderByType(mime);
                codec.configure(format,null,null,0);codec.start();
                boolean inDone=false,outDone=false;MediaCodec.BufferInfo info=new MediaCodec.BufferInfo();
                while(!outDone){
                    if(!inDone){int index=codec.dequeueInputBuffer(10000);
                        if(index>=0){ByteBuffer buffer=codec.getInputBuffer(index);buffer.clear();int n=extractor.readSampleData(buffer,0);
                            if(n<0){codec.queueInputBuffer(index,0,0,0,MediaCodec.BUFFER_FLAG_END_OF_STREAM);inDone=true;}
                            else{codec.queueInputBuffer(index,0,n,extractor.getSampleTime(),0);extractor.advance();}
                        }}
                    int index=codec.dequeueOutputBuffer(info,10000);
                    if(index==MediaCodec.INFO_OUTPUT_FORMAT_CHANGED){
                        MediaFormat f=codec.getOutputFormat();rate=f.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                        channels=f.getInteger(MediaFormat.KEY_CHANNEL_COUNT);resample.setRate(rate);
                        format=f;
                    }else if(index>=0){
                        if(info.size>0){ByteBuffer buffer=codec.getOutputBuffer(index);
                            buffer.position(info.offset);buffer.limit(info.offset+info.size);
                            resample.feed(buffer,channels,format.containsKey(MediaFormat.KEY_PCM_ENCODING)?format.getInteger(MediaFormat.KEY_PCM_ENCODING):AudioFormat.ENCODING_PCM_16BIT);
                        }
                        outDone=(info.flags&MediaCodec.BUFFER_FLAG_END_OF_STREAM)!=0;
                        codec.releaseOutputBuffer(index,false);
                    }
                }
            }
            resample.finish();return resample.seconds();
        }finally{
            if(codec!=null){try{codec.stop();}catch(Exception ignored){}codec.release();}
            extractor.release();
        }
    }
    private static final class Resampler {
        private final Sink sink;private final double total;
        private final float[] buffer=new float[4000];private int used=0;
        private int rate;private long inputFrames=0,outputFrames=0;private double nextOutput=0;private float prev=0;
        Resampler(int rate,Sink sink,double total){this.rate=rate;this.sink=sink;this.total=total;}
        void setRate(int newRate)throws IOException{
            // A decoder reporting nonsense here would make the resampler spin forever.
            if(newRate<4000||newRate>192000)throw new IOException("解码器报告的采样率无效："+newRate);
            if(newRate!=rate){rate=newRate;nextOutput=inputFrames;}
        }
        double seconds(){return outputFrames/16000.0;}
        void feed(ByteBuffer bytes,int channels,int encoding)throws Exception{
            bytes.order(ByteOrder.LITTLE_ENDIAN);
            int sampleBytes=encoding==AudioFormat.ENCODING_PCM_FLOAT?4:encoding==AudioFormat.ENCODING_PCM_16BIT?2:0;
            if(sampleBytes==0)throw new IOException("此录音的 PCM 编码不受支持（"+encoding+"）");
            while(bytes.remaining()>=sampleBytes*channels){
                float mono=0;
                for(int c=0;c<channels;c++) mono+=encoding==AudioFormat.ENCODING_PCM_FLOAT?bytes.getFloat():bytes.getShort()/32768f;
                mono/=channels;
                long i=inputFrames++;
                while(nextOutput<=i){
                    float t=i==0?1f:(float)Math.max(0,Math.min(1,nextOutput-(i-1)));
                    float value=i==0?mono:prev+(mono-prev)*t;
                    buffer[used++]=value;outputFrames++;
                    if(outputFrames>3600L*16000)throw new IOException("录音不能超过 1 小时");                    if(used==buffer.length){sink.accept(buffer,used,seconds(),total);used=0;}
                    nextOutput+=rate/16000.0;
                }
                prev=mono;
            }
        }
        void finish()throws Exception{if(used>0){sink.accept(buffer,used,seconds(),total);used=0;}}
    }
}
