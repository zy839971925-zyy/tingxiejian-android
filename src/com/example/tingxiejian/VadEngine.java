package com.example.tingxiejian;

/** Bounded deterministic RMS VAD. Neural VAD can replace this without endpoint-owned final cuts. */
final class VadEngine {
    interface Listener { void ended(Segment segment) throws Exception; }
    static final class Segment {
        final String id;final long startSample,endSample;
        Segment(String id,long a,long b){this.id=id;startSample=a;endSample=b;}
    }
    private static final int RATE=16000,ROLL=3200,HANGOVER=9600,MAX=RATE*25;
    private final Listener listener;
    private long position,start,lastVoice,previousEnd;private int index,voiceSamples;
    private boolean active;private float noise=.003f;
    VadEngine(Listener listener){this.listener=listener;}
    void accept(float[] frame,int length)throws Exception{
        double energy=0;for(int i=0;i<length;i++)energy+=frame[i]*frame[i];
        float rms=(float)Math.sqrt(energy/Math.max(1,length));
        boolean speech=rms>Math.max(.008f,noise*2.5f);
        if(speech){
            if(!active){active=true;start=Math.max(previousEnd,Math.max(0,position-ROLL));voiceSamples=0;}
            lastVoice=position+length;voiceSamples+=length;
        }else if(!active)noise=noise*.98f+Math.min(rms,.02f)*.02f;
        position+=length;
        if(active&&(position-lastVoice>=HANGOVER||position-start>=MAX)){
            long end=position-lastVoice>=HANGOVER?Math.min(position,lastVoice+ROLL):position;
            emit(end);
        }
    }
    void finish()throws Exception{if(active)emit(position);}
    /** An already-recognized low-energy utterance must not disappear solely on an RMS threshold. */
    void retainRecognizedText()throws Exception{
        long from=Math.max(previousEnd,Math.max(0,position-MAX));
        if(position<=from)return;
        active=false;voiceSamples=0;previousEnd=position;
        listener.ended(new Segment(String.format(java.util.Locale.ROOT,"s%06d",++index),from,position));
    }
    private void emit(long end)throws Exception{
        active=false;
        if(voiceSamples<1280)return;
        Segment s=new Segment(String.format(java.util.Locale.ROOT,"s%06d",++index),start,end);
        previousEnd=end;listener.ended(s);
    }
}
