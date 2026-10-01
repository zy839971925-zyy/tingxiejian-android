package com.example.tingxiejian;

import android.content.Context;
import org.json.JSONObject;
import java.io.*;

/** Foreground Activity-owned dictation. No microphone FGS or background permission. */
final class DictationController {
    interface Listener {void event(JSONObject event);}
    private static volatile DictationController active;
    static boolean isBusy(){return active!=null;}
    private final Context app;private volatile Listener listener;
    private final MicrophoneAudioSource source=new MicrophoneAudioSource();
    private volatile boolean stopped;private String sessionId="";
    private Thread worker;
    DictationController(Context context,Listener listener){app=context.getApplicationContext();this.listener=listener;}
    synchronized void start(){
        synchronized(DictationController.class){if(active!=null||LocalService.isBusy())throw new IllegalStateException("已有转写正在进行");active=this;}
        worker=new Thread(this::run,"dictation-asr");worker.start();
    }
    void pause(boolean value){source.pause(value);}
    void stop(){stopped=true;source.stop();}
    void detach(){listener=null;}
    private void emit(JSONObject event){Listener l=listener;if(l!=null)l.event(event);}
    private void run(){
        String savedId="";
        try{
            sessionId=SessionRepository.begin(app,"live","实时听写");
            try(SpeechPipeline pipeline=new SpeechPipeline(app,event->{
                String state=event.optString("state");
                if(!state.isEmpty())SessionRepository.transition(app,sessionId,SessionState.State.valueOf(state),event.optString("message"));
                emit(event);
            })){
                if(stopped)throw new InterruptedIOException("录音尚未开始就已离开前台");
                pipeline.phase("正在聆听 · 离开页面会结束并保存");source.pump(pipeline::accept);
                JSONObject result=pipeline.finish(false,0);result.put("name","实时听写");result.put("createdAt",System.currentTimeMillis());
                result.put("durationSeconds",result.optDouble("duration"));result.put("engine","local-live");
                result.put("session_id",sessionId);result.put("speakers",0);
                if(!source.warning().isEmpty())result.put("warning",result.optString("warning")+"；"+source.warning());
                savedId=History.save(app,result);result.put("saved_id",savedId);
                SessionRepository.transition(app,sessionId,SessionState.State.DONE,"已保存实时听写结果");emit(result);
            }
        }catch(Exception|LinkageError error){
            try{if(!sessionId.isEmpty())SessionRepository.transition(app,sessionId,
                    error instanceof InterruptedIOException?SessionState.State.INTERRUPTED:SessionState.State.FAILED,"实时听写结束："+error.getClass().getSimpleName());}
            catch(IOException ignored){}
            JSONObject event=new JSONObject();try{event.put("type","error");event.put("message","实时听写未完成："+error.getMessage());}catch(Exception ignored){}emit(event);
        }finally{source.stop();synchronized(DictationController.class){if(active==this)active=null;}}
    }
}
