package android.net;

import android.os.IBinder;
import rikka.shizuku.SystemServiceHelper;

public abstract class IConnectivityManager {
    public abstract int getUidFirewallRule(int chain, int uid);
    public abstract boolean getFirewallChainEnabled(int chain);
    public abstract void setUidFirewallRule(int chain, int uid, int rule);
    public abstract void setFirewallChainEnabled(int chain, boolean enabled);
    public static final class Stub {
        public static IConnectivityManager asInterface(IBinder binder) {
            return SystemServiceHelper.remote;
        }
    }
}
