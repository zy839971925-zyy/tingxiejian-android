package android.content.pm;

public abstract class PackageManager {
    public static final int PERMISSION_GRANTED = 0;
    public abstract ApplicationInfo getApplicationInfo(String name, int flags) throws NameNotFoundException;
    public static final class ApplicationInfo { public int uid; }
    public static final class NameNotFoundException extends Exception { }
}
