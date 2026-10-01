package android.content;
public final class Context {
    public static final int MODE_PRIVATE=0;
    public final SharedPreferences preferences = new SharedPreferences();
    public SharedPreferences getSharedPreferences(String name, int mode) { return preferences; }
}
