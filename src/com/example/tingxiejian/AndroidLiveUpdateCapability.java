package com.example.tingxiejian;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;

/** Standard Android capability; never probes Xiaomi, Shizuku or an OEM private interface. */
final class AndroidLiveUpdateCapability {
    static final String PREF="live_updates";
    enum State { UNSUPPORTED, APP_DISABLED, NOTIFICATIONS_BLOCKED, CHANNEL_BLOCKED,
        SYSTEM_DISABLED, READY, API_UNAVAILABLE, PUBLISH_FAILED }
    static final class Info {
        final State state;final String detail;
        Info(State state,String detail){this.state=state;this.detail=detail;}
        String summary(){return state.name()+" · "+detail;}
    }
    static Info inspect(Context context,NotificationManager manager,String channelId){
        if(Build.VERSION.SDK_INT<36)return new Info(State.UNSUPPORTED,"Android 16 以下使用普通前台通知");
        if(!context.getSharedPreferences(SettingsActivity.PREFS,0).getBoolean(PREF,true))
            return new Info(State.APP_DISABLED,"已关闭标准实时进度；小米超级岛设置独立");
        if(manager==null)return new Info(State.API_UNAVAILABLE,"通知服务不可用");
        try{
            if(!manager.areNotificationsEnabled())return new Info(State.NOTIFICATIONS_BLOCKED,"系统通知已关闭；任务仍依赖合法 FGS 生命周期");
            NotificationChannel channel=manager.getNotificationChannel(channelId);
            if(channel!=null&&channel.getImportance()<=NotificationManager.IMPORTANCE_MIN)
                return new Info(State.CHANNEL_BLOCKED,"进度频道关闭或降为最低重要性，无法 promotion");
            return Api36.allowed(manager)?new Info(State.READY,"可请求 promotion；系统/OEM 决定是否显示，未证明呈现")
                    :new Info(State.SYSTEM_DISABLED,"系统未允许实时活动，保持普通前台通知");
        }catch(RuntimeException|LinkageError error){return new Info(State.API_UNAVAILABLE,"promotion 检测失败，普通通知保底");}
    }
    private static final class Api36 {
        static boolean allowed(NotificationManager manager){return manager.canPostPromotedNotifications();}
    }
}
