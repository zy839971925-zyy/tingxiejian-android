package android.app;
public class NotificationManager {
    public static final int IMPORTANCE_MIN=1;
    public boolean enabled=true,promotion=true,broken;
    public int promotionProbes;
    public NotificationChannel channel=new NotificationChannel();
    public boolean areNotificationsEnabled(){return enabled;}
    public NotificationChannel getNotificationChannel(String id){return channel;}
    public boolean canPostPromotedNotifications(){
        if(android.os.Build.VERSION.SDK_INT<36)throw new AssertionError("unguarded API36 manager method");
        promotionProbes++;if(broken)throw new IllegalStateException("OEM implementation unavailable");return promotion;
    }
}
