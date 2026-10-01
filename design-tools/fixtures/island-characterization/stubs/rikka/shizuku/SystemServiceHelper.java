package rikka.shizuku;
import android.net.IConnectivityManager;
import android.os.IBinder;
public final class SystemServiceHelper {
    public static IConnectivityManager remote;
    public static boolean binderAvailable = true;
    public static IBinder getSystemService(String name) {
        return binderAvailable ? new IBinder() { } : null;
    }
}
