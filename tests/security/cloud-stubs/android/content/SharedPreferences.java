package android.content;
import java.util.HashMap;
import java.util.Map;
/** JVM stand-in for Android's immediate preferences map. No production branch depends on it. */
public final class SharedPreferences {
    public final Map<String,String> values = new HashMap<>();
    public String getString(String key, String fallback) { return values.containsKey(key) ? values.get(key) : fallback; }
    public Editor edit() { return new Editor(); }
    public final class Editor {
        private final Map<String,String> pending = new HashMap<>();
        public Editor putString(String key, String value) { pending.put(key, value); return this; }
        public void apply() { values.putAll(pending); }
    }
}
