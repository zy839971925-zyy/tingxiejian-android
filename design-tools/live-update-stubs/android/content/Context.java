package android.content;
public class Context {
    public boolean liveEnabled=true;
    public Preferences getSharedPreferences(String name,int mode){return new Preferences();}
    public class Preferences {public boolean getBoolean(String name,boolean def){return liveEnabled;}}
}
