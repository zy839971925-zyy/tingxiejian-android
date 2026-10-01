package com.example.tingxiejian;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.Bundle;

/** Adds a standard presentation to the SAME foreground notification; owns no service lifecycle. */
final class AndroidLiveUpdatePublisher {
    interface Poster {void post(Notification notification);}
    private final NotificationUpdateGate updates=new NotificationUpdateGate(1500);
    private final NotificationUpdateGate ordinaryUpdates=new NotificationUpdateGate(5000);
    private volatile boolean failed;
    private static volatile String lastStatus="尚未提交标准实时进度";
    static String lastStatus(){return lastStatus;}
    void reset(){updates.reset();ordinaryUpdates.reset();failed=false;lastStatus="当前无标准实时进度任务";}
    boolean publish(Context context,NotificationManager manager,String channelId,Job job,
                    Notification ordinary,Poster poster,long now){
        boolean standardRequested=Build.VERSION.SDK_INT>=36&&!failed
                &&context.getSharedPreferences(SettingsActivity.PREFS,0).getBoolean(AndroidLiveUpdateCapability.PREF,true);
        if(!(standardRequested?updates:ordinaryUpdates).allow(job,now))return false;
        Notification notification=ordinary;
        AndroidLiveUpdateCapability.Info capability=failed
                ?new AndroidLiveUpdateCapability.Info(AndroidLiveUpdateCapability.State.PUBLISH_FAILED,"本任务已停止 promotion 尝试")
                :AndroidLiveUpdateCapability.inspect(context,manager,channelId);
        if(capability.state==AndroidLiveUpdateCapability.State.API_UNAVAILABLE){failed=true;ordinaryUpdates.allow(job,now);}
        lastStatus=failed?"PUBLISH_FAILED · 本任务已停止 promotion 尝试；普通 FGS 底座保留":capability.summary();
        if(!failed&&(capability.state==AndroidLiveUpdateCapability.State.READY
                ||capability.state==AndroidLiveUpdateCapability.State.SYSTEM_DISABLED)&&Build.VERSION.SDK_INT>=36){
            try{
                notification=Api36.prepare(context,ordinary,job);
                lastStatus=capability.summary()+"；结构可提升="+notification.hasPromotableCharacteristics()
                        +"；同一通知提交不代表系统/OEM 已显示";
            }catch(RuntimeException|LinkageError error){
                failed=true;notification=ordinary;ordinaryUpdates.allow(job,now);
                lastStatus="PUBLISH_FAILED · 构造失败，本任务不再请求 promotion";
            }
        }
        try{poster.post(notification);return true;}
        catch(RuntimeException|LinkageError error){
            failed=true;ordinaryUpdates.allow(job,now);
            lastStatus="PUBLISH_FAILED · 提交失败，保留已有 FGS 底座；本任务不重试 promotion";
            if(notification!=ordinary)try{poster.post(ordinary);return true;}catch(RuntimeException|LinkageError ignored){}
            return false;
        }
    }
    private static final class Api36 {
        static Notification prepare(Context context,Notification ordinary,Job job){
            // API 36.0 has ProgressStyle but no public Builder.setRequestPromotedOngoing (36.1).
            // Standard extra used by NotificationCompat and the pinned reference repositories.
            Bundle extras=new Bundle();extras.putBoolean("android.requestPromotedOngoing",true);
            return Notification.Builder.recoverBuilder(context,ordinary)
                    .setStyle(new Notification.ProgressStyle().setProgress(Math.max(0,Math.min(100,job.percent)))
                            .setProgressIndeterminate(job.indeterminate))
                    .setShortCriticalText(job.indeterminate?"处理中":job.percent+"%")
                    .setOngoing(true).setColorized(false).setCategory(Notification.CATEGORY_PROGRESS)
                    .addExtras(extras).build();
        }
    }
}
