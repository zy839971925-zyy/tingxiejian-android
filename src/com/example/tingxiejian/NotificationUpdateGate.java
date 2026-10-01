package com.example.tingxiejian;

/** A presentation throttle only; holds the existing Job, never a second progress model. */
final class NotificationUpdateGate {
    private final long interval;
    private long postedAt;
    private Job posted;
    NotificationUpdateGate(long interval){this.interval=interval;}
    synchronized boolean allow(Job job,long now){
        if(job==null)return false;
        if(posted!=null){
            boolean changed=job.percent!=posted.percent||job.indeterminate!=posted.indeterminate
                    ||!job.stage.equals(posted.stage)||!job.detail.equals(posted.detail)||!job.eta.equals(posted.eta);
            if(!changed)return false;
            if(job.stage.equals(posted.stage)&&job.percent<100&&now-postedAt<interval)return false;
        }
        posted=job;postedAt=now;return true;
    }
    synchronized void reset(){posted=null;postedAt=0;}
}
