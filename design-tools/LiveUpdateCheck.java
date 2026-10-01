package com.example.tingxiejian;
import android.app.*;
import android.content.Context;
import android.os.Build;
import java.util.*;

/** Executes actual capability, publisher and Job throttle. Android stand-ins are not device tests. */
public final class LiveUpdateCheck {
    static int checks;
    static void check(boolean b,String why){checks++;if(!b)throw new AssertionError(why);}
    static Job progress(double p){return Job.of("progress","",p,100,2,0,5000);}
    static Notification base(){Notification n=new Notification();n.title="正在转写";n.detail="已听1分钟";n.eta="剩余1分钟";
        n.contentIntent=new Object();n.stopAction=new Object();n.flags=64;n.when=123;return n;}
    static void state(Context c,NotificationManager m,AndroidLiveUpdateCapability.State wanted){
        check(AndroidLiveUpdateCapability.inspect(c,m,"quiet").state==wanted,"capability "+wanted);
    }
    public static void main(String[] args){
        Context c=new Context();NotificationManager m=new NotificationManager();
        AndroidLiveUpdatePublisher publisher=new AndroidLiveUpdatePublisher();List<Notification> posted=new ArrayList<>();
        Build.VERSION.SDK_INT=35;state(c,m,AndroidLiveUpdateCapability.State.UNSUPPORTED);
        Notification normal=base();publisher.publish(c,m,"quiet",progress(10),normal,posted::add,0);
        check(posted.get(0)==normal&&m.promotionProbes==0&&Notification.builds==0,"pre36 never touches API36, posts original FGS");
        publisher.publish(c,m,"quiet",progress(11),normal,posted::add,1500);
        check(posted.size()==1,"legacy ordinary notification retains independent 5 second throttle");
        publisher.publish(c,m,"quiet",progress(11),normal,posted::add,5000);
        check(posted.size()==2,"legacy ordinary progress advances after throttle");
        Build.VERSION.SDK_INT=36;c.liveEnabled=false;state(c,m,AndroidLiveUpdateCapability.State.APP_DISABLED);
        c.liveEnabled=true;m.enabled=false;state(c,m,AndroidLiveUpdateCapability.State.NOTIFICATIONS_BLOCKED);
        m.enabled=true;m.channel.importance=0;state(c,m,AndroidLiveUpdateCapability.State.CHANNEL_BLOCKED);
        m.channel.importance=1;state(c,m,AndroidLiveUpdateCapability.State.CHANNEL_BLOCKED);
        m.channel.importance=2;m.promotion=false;state(c,m,AndroidLiveUpdateCapability.State.SYSTEM_DISABLED);
        publisher.reset();posted.clear();publisher.publish(c,m,"quiet",progress(20),normal,posted::add,0);
        Notification n=posted.get(0);
        check(n.style!=null&&n.extras.getBoolean("android.requestPromotedOngoing"),"system-denied promotion uses same standard-style FGS, natural downgrade");
        check(AndroidLiveUpdatePublisher.lastStatus().contains("SYSTEM_DISABLED"),"denied permission diagnostic, no rendered claim");
        m.promotion=true;state(c,m,AndroidLiveUpdateCapability.State.READY);
        publisher.reset();posted.clear();publisher.publish(c,m,"quiet",progress(30),normal,posted::add,0);n=posted.get(0);
        check(n.style.progress==27&&!n.style.indeterminate,"ProgressStyle uses existing Job percent");
        check(n.flags==normal.flags&&n.contentIntent==normal.contentIntent&&n.stopAction==normal.stopAction&&n.when==normal.when,
                "identity/lifecycle/open/stop/timestamp preserved");
        check(n.ongoing&&!n.colorized&&n.title.equals(normal.title)&&n.detail.equals(normal.detail)&&n.eta.equals(normal.eta),"ordinary notification fields retained");
        int before=posted.size();publisher.publish(c,m,"quiet",progress(31),normal,posted::add,100);
        check(posted.size()==before,"live independent 1.5 second throttle");
        publisher.publish(c,m,"quiet",progress(31),normal,posted::add,1500);check(posted.size()==before+1,"latest changed Job delivered");
        Job phase=Job.of("phase","正在完成高精度复核",0,0,0,0,5000);
        publisher.publish(c,m,"quiet",phase,normal,posted::add,1510);
        check(posted.get(posted.size()-1).style.indeterminate==phase.indeterminate,"phase unknown progress isn't fabricated");
        Job done=Job.of("result","",100,100,2,0,5000);
        publisher.publish(c,m,"quiet",done,normal,posted::add,1520);check(posted.get(posted.size()-1).style.progress==100,"terminal result bypasses throttle");
        publisher.reset();Notification.failBuild=true;posted.clear();publisher.publish(c,m,"quiet",progress(40),normal,posted::add,0);
        check(posted.get(0)==normal,"build failure falls back to same ordinary FGS");int builds=Notification.builds;
        Notification.failBuild=false;publisher.publish(c,m,"quiet",progress(42),normal,posted::add,6000);
        check(Notification.builds==builds&&posted.get(1)==normal,"failed run never retries promotion");
        check(AndroidLiveUpdatePublisher.lastStatus().contains("PUBLISH_FAILED"),"sticky failed diagnostic");
        publisher.reset();posted.clear();publisher.publish(c,m,"quiet",progress(50),normal,v->{if(v!=normal)throw new SecurityException("promotion denied");posted.add(v);},0);
        check(posted.size()==1&&posted.get(0)==normal,"post failure attempts plain notification once");
        publisher.reset();posted.clear();publisher.publish(c,m,"quiet",progress(60),normal,posted::add,0);
        check(posted.get(0).style!=null,"new task resets failure/throttle");
        m.broken=true;state(c,m,AndroidLiveUpdateCapability.State.API_UNAVAILABLE);
        publisher.reset();posted.clear();publisher.publish(c,m,"quiet",progress(70),normal,posted::add,0);
        check(posted.get(0)==normal,"broken platform API does not crash, retains plain FGS");
        NotificationUpdateGate gate=new NotificationUpdateGate(1500);
        Job a=Job.pending("读入A"),b=Job.pending("读入B");
        check(gate.allow(a,0)&&!gate.allow(b,100)&&gate.allow(b,1500),"detail-only update governed independently");
        check(!gate.allow(b,3000),"identical snapshot suppressed");gate.reset();check(gate.allow(b,3001),"new run not throttled");
        System.out.println("PASS: standard live updates ("+checks+" behavioral assertions); OEM display untested");
    }
}
