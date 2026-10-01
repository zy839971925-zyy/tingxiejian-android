package com.example.tingxiejian;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.*;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;
import org.json.*;
import java.util.*;

/** Front-of-app listening surface. Permission is requested only by a user's start action. */
public final class DictationActivity extends Activity {
    private static final int REQ_MIC=105;
    private final Handler ui=new Handler(Looper.getMainLooper());
    private final Map<String,String> sentences=new LinkedHashMap<>();
    private DictationController controller;
    private TextView status,hint,text,timer,action,pause,result,orb;
    private WaveView wave;private String stable="",unstable="",savedId="";
    private boolean paused,finishing,destroyed,foreground;
    @Override protected void attachBaseContext(Context base){super.attachBaseContext(UiTheme.wrap(base));}
    @Override public void onCreate(Bundle state){
        super.onCreate(state);setContentView(R.layout.activity_dictation);
        PortalTransition.install(this,findViewById(R.id.portal_surface),findViewById(R.id.portal_content),state);
        UiTheme.padForSystemBars(this,findViewById(R.id.portal_content));
        status=findViewById(R.id.dictation_status);hint=findViewById(R.id.dictation_hint);
        text=findViewById(R.id.dictation_text);timer=findViewById(R.id.dictation_timer);
        action=findViewById(R.id.dictation_start_stop);pause=findViewById(R.id.dictation_pause);
        result=findViewById(R.id.dictation_result);orb=findViewById(R.id.dictation_orb);wave=findViewById(R.id.dictation_wave);
        hint.setText(Cloud.configured(this)?"本机识别；文字会自动发送到你配置的接口轻度校正。离开页面会结束录音。"
                :"未配置 API Key：跳过 AI 文字校正。仅在本页面前台录音，离开会结束并保存。");
        findViewById(R.id.back).setOnClickListener(v->onBackPressed());
        action.setOnClickListener(v->{if(controller==null)requestStart();else finishCapture();});
        pause.setOnClickListener(v->{if(controller!=null&&!finishing){paused=!paused;controller.pause(paused);
            if(paused){Motion.listeningLevel(orb,0);wave.setEnvelope(new float[0],0);}
            pause.setText(paused?"继续":"暂停");status.setText(paused?"已暂停录音":"正在聆听");Motion.haptic(pause,8);}});
        result.setOnClickListener(v->{if(!savedId.isEmpty())TranscriptActivity.open(this,savedId,result);});
        Motion.press(action);Motion.press(pause);
        UiControls.apply(findViewById(R.id.portal_content));
        if(state!=null){savedId=state.getString("saved_id","");if(!savedId.isEmpty())result.setVisibility(View.VISIBLE);}
    }
    private void requestStart(){
        if(!foreground||isFinishing()||isDestroyed()){status.setText("已授权时可回到本页面，点击开始听写");return;}
        if(DictationController.isBusy()||LocalService.isBusy()){status.setText("已有转写正在进行，请先完成或停止");return;}
        if(!Consent.accepted(this)){Consent.show(this,this::requestStart);return;}
        if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQ_MIC);return;}
        try{
            sentences.clear();stable="";unstable="";savedId="";paused=false;finishing=false;render();
            pause.setText("暂停");timer.setText("00:00");wave.setEnvelope(new float[0],0);Motion.listeningLevel(orb,0);
            result.setVisibility(View.GONE);status.setText("准备本地识别模型…");action.setText("结束并保存");pause.setEnabled(true);
            controller=new DictationController(this,e->ui.post(()->{if(!destroyed)event(e);}));controller.start();Motion.haptic(action,12);
        }catch(Exception error){controller=null;status.setText("无法开始："+error.getMessage());action.setText("开始听写");pause.setEnabled(false);}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==REQ_MIC){if(results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED)requestStart();
            else{status.setText("麦克风权限未授权；仍可导入录音转写");hint.setText("再次开始可重试，或到系统设置允许麦克风权限。");}}
    }
    private void finishCapture(){
        if(controller==null||finishing)return;finishing=true;controller.stop();pause.setEnabled(false);
        action.setEnabled(false);action.setText("正在完成…");status.setText("录音已结束，复核与校正继续进行");Motion.haptic(action,8);
    }
    private void event(JSONObject e){
        String type=e.optString("type");
        if(type.equals("progress")){
            stable=e.optString("stable");unstable=e.optString("unstable");timer.setText(Job.clock(e.optDouble("processed")));
            JSONArray values=e.optJSONArray("wave");if(values!=null){float[] p=new float[values.length()];
                for(int i=0;i<p.length;i++)p[i]=(float)values.optDouble(i);wave.setEnvelope(p,1);}
            Motion.listeningLevel(orb,paused||finishing?0:(float)e.optDouble("rms"));render();
        }else if(type.equals("segment")){
            JSONObject s=e.optJSONObject("segment");if(s!=null){String id=s.optString("segment_id");boolean first=!sentences.containsKey(id);
                sentences.put(id,s.optString("final_text"));if(first){stable="";unstable="";}render(id);
                if(!finishing&&!paused)status.setText("正在聆听 · 语句后台复核中");}
        }else if(type.equals("polished-segment")){
            String id=e.optString("segment_id"),value=e.optString("polished_text");
            if(sentences.containsKey(id)&&!value.trim().isEmpty()){sentences.put(id,value);render(id);}
        }else if(type.equals("phase")){status.setText(e.optString("message"));}
        else if(type.equals("result")){
            savedId=e.optString("saved_id");controller=null;finishing=false;action.setEnabled(true);action.setText("再听写一段");
            pause.setEnabled(false);status.setText("已保存 · 忠实稿始终保留");result.setVisibility(View.VISIBLE);
            Motion.listeningLevel(orb,0);
            text.setText(e.optString("polished_text",e.optString("final_text")));hint.setText(e.optString("warning"));
            status.announceForAccessibility("实时听写已保存");Motion.haptic(action,18);
        }else if(type.equals("error")){
            controller=null;finishing=false;action.setEnabled(true);action.setText("重试听写");pause.setEnabled(false);
            Motion.listeningLevel(orb,0);
            status.setText(e.optString("message"));Motion.haptic(action,10);
        }
    }
    private void render(){
        render(null);
    }
    private void render(String changedId){
        SpannableStringBuilder b=new SpannableStringBuilder();
        int changedFrom=-1,changedTo=-1;
        for(Map.Entry<String,String> sentence:sentences.entrySet()){
            if(b.length()>0)b.append('\n');int rowStart=b.length();b.append(sentence.getValue());
            if(sentence.getKey().equals(changedId)){changedFrom=rowStart;changedTo=b.length();}
        }
        if(b.length()>0&&(!stable.isEmpty()||!unstable.isEmpty()))b.append('\n');b.append(stable);int from=b.length();b.append(unstable);
        if(from<b.length())b.setSpan(new ForegroundColorSpan(getColor(R.color.muted)),from,b.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        text.setText(b);
        if(changedFrom>=0)MotionSpec.tintCommit(text,changedFrom,changedTo);
    }
    @Override protected void onStart(){super.onStart();foreground=true;}
    @Override protected void onStop(){foreground=false;finishCapture();super.onStop();}
    @Override protected void onDestroy(){destroyed=true;ui.removeCallbacksAndMessages(null);if(controller!=null){controller.stop();controller.detach();}super.onDestroy();}
    @Override protected void onSaveInstanceState(Bundle state){state.putString("saved_id",savedId);super.onSaveInstanceState(state);}
    @Override public void onBackPressed(){finishCapture();PortalTransition.close(this);}
}
