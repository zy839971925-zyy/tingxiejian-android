package android.content;

public interface SharedPreferences {
    boolean getBoolean(String key, boolean fallback);
    int getInt(String key, int fallback);
    Editor edit();
    interface Editor {
        Editor putBoolean(String key, boolean value);
        Editor putInt(String key, int value);
        Editor clear();
        boolean commit();
    }
}
