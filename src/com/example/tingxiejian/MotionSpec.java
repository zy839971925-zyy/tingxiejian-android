package com.example.tingxiejian;

import android.view.View;
import android.view.animation.LinearInterpolator;
import android.animation.ValueAnimator;
import android.graphics.Color;
import android.text.Spannable;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;
/** Text/fades use deterministic timing; existing portal/press springs stay untouched. */
final class MotionSpec {
    static final long TEXT_CROSSFADE_MS=140,COMMIT_MS=180,ENTER_MS=280;
    static void crossfade(View view){
        view.animate().cancel();if(!Motion.animatorsEnabled()){view.setAlpha(1);return;}
        view.setAlpha(.75f);view.animate().setStartDelay(0).alpha(1).setDuration(TEXT_CROSSFADE_MS).setInterpolator(new LinearInterpolator()).start();
    }
    static void tintCommit(TextView view,int from,int to){
        if(!Motion.animatorsEnabled()||from>=to)return;
        CharSequence initial=view.getText();if(!(initial instanceof Spannable))return;
        String expected=initial.toString();int ink=view.getCurrentTextColor();
        ForegroundColorSpan[] previous={null};
        ValueAnimator animation=ValueAnimator.ofFloat(.7f,1f);
        animation.setDuration(TEXT_CROSSFADE_MS);animation.setInterpolator(new LinearInterpolator());
        animation.addUpdateListener(a->{
            CharSequence current=view.getText();
            if(!view.isAttachedToWindow()||!(current instanceof Spannable)||!expected.contentEquals(current)){
                if(current instanceof Spannable&&previous[0]!=null)((Spannable)current).removeSpan(previous[0]);
                a.cancel();return;
            }
            Spannable spans=(Spannable)current;if(previous[0]!=null)spans.removeSpan(previous[0]);
            float fraction=(float)a.getAnimatedValue();
            if(fraction<1){previous[0]=new ForegroundColorSpan(Color.argb((int)(Color.alpha(ink)*fraction),
                    Color.red(ink),Color.green(ink),Color.blue(ink)));
                spans.setSpan(previous[0],from,to,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
            view.invalidate();
        });animation.start();
    }
}
