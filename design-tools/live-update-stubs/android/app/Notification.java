package android.app;
import android.content.Context;
import android.os.Bundle;
public class Notification {
    public static final String CATEGORY_PROGRESS="progress";
    public Bundle extras=new Bundle();public ProgressStyle style;
    public Object contentIntent,stopAction;public int flags,progress;
    public boolean ongoing,colorized;public long when;
    public String title,detail,eta,shortText,category;
    public static int builds;
    public static boolean failBuild;
    public boolean hasPromotableCharacteristics(){
        if(android.os.Build.VERSION.SDK_INT<36)throw new AssertionError("unguarded API36 characteristics");
        return extras.getBoolean("android.requestPromotedOngoing")&&ongoing&&!colorized&&title!=null;
    }
    public static class ProgressStyle {
        public int progress;public boolean indeterminate;
        public ProgressStyle(){if(android.os.Build.VERSION.SDK_INT<36)throw new AssertionError("unguarded API36 class");}
        public ProgressStyle setProgress(int p){progress=p;return this;}
        public ProgressStyle setProgressIndeterminate(boolean b){indeterminate=b;return this;}
    }
    public static class Builder {
        private final Notification n=new Notification();
        public static Builder recoverBuilder(Context c,Notification old){
            Builder b=new Builder();b.n.extras.putAll(old.extras);b.n.flags=old.flags;
            b.n.title=old.title;b.n.detail=old.detail;b.n.eta=old.eta;b.n.contentIntent=old.contentIntent;
            b.n.stopAction=old.stopAction;b.n.when=old.when;b.n.ongoing=old.ongoing;return b;
        }
        public Builder setStyle(ProgressStyle s){n.style=s;return this;}
        public Builder setShortCriticalText(String t){n.shortText=t;return this;}
        public Builder setOngoing(boolean b){n.ongoing=b;return this;}
        public Builder setColorized(boolean b){n.colorized=b;return this;}
        public Builder setCategory(String c){n.category=c;return this;}
        public Builder addExtras(Bundle e){n.extras.putAll(e);return this;}
        public Notification build(){builds++;if(failBuild)throw new IllegalStateException("build failed");return n;}
    }
}
