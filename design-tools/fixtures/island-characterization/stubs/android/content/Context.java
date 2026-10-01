package android.content;

import android.content.pm.PackageManager;

public abstract class Context {
    public static final int MODE_PRIVATE = 0;
    public static final String CONNECTIVITY_SERVICE = "connectivity";
    public Context getApplicationContext() { return this; }
    public abstract SharedPreferences getSharedPreferences(String name, int mode);
    public abstract PackageManager getPackageManager();
}
