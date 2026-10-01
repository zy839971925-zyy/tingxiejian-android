package android.os;
import java.util.HashMap;
public class Bundle extends HashMap<String,Object> {
    public void putBoolean(String k,boolean v){put(k,v);}
    public boolean getBoolean(String k){return Boolean.TRUE.equals(get(k));}
}
