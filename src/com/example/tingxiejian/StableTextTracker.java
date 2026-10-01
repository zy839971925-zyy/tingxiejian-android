package com.example.tingxiejian;

import java.util.ArrayDeque;

/** Commits only a prefix agreed by several observations; authoritative final text is separate. */
final class StableTextTracker {
    static final class Text {
        final String stable, unstable;
        Text(String stable, String unstable) { this.stable=stable; this.unstable=unstable; }
    }
    private final int observations;
    private final ArrayDeque<String> recent=new ArrayDeque<>();
    private String stable="", latest="";
    StableTextTracker(int observations) {
        if(observations<2)throw new IllegalArgumentException("observations");
        this.observations=observations;
    }
    synchronized Text update(String partial) {
        latest=partial==null?"":partial;
        recent.addLast(latest);while(recent.size()>observations)recent.removeFirst();
        if(recent.size()==observations){
            String prefix=recent.getFirst();
            for(String s:recent){int n=0;while(n<Math.min(prefix.length(),s.length())&&prefix.charAt(n)==s.charAt(n))n++;
                if(n>0&&n<prefix.length()&&Character.isHighSurrogate(prefix.charAt(n-1)))n--;
                prefix=prefix.substring(0,n);}
            if(prefix.startsWith(stable))stable=prefix;
        }
        // A rare decoder revision before the committed boundary does not duplicate the old prefix.
        return new Text(stable,latest.startsWith(stable)?latest.substring(stable.length()):"");
    }
    synchronized void reset(){recent.clear();stable="";latest="";}
}
