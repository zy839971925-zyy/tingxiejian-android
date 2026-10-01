package com.example.tingxiejian;
import android.content.Context;import android.app.Activity;import android.view.*;import android.os.Build;import android.animation.ValueAnimator;
public final class UiMotionCheck {
 private static int checks;private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
 public static void main(String[] args){
  Context c=new Context();View v=new View(c);Motion.init(c);Motion.press(v);
  Motion.setReduced(c,true);v.touch.touch(v,new MotionEvent(MotionEvent.ACTION_DOWN));check(v.sx==1&&v.sy==1,"reduced motion has no touch shrink");
  Motion.setReduced(c,false);v.enabled=false;v.touch.touch(v,new MotionEvent(0));check(v.sx==1,"disabled controls never shrink");
  v.enabled=true;ValueAnimator.enabled=false;v.touch.touch(v,new MotionEvent(0));check(v.sx==1,"system animation off wins");
  ValueAnimator.enabled=true;v.touch.touch(v,new MotionEvent(0));check(v.sx<1,"enabled touch acknowledged");
  v.animate().setStartDelay(240);v.touch.touch(v,new MotionEvent(1));check(v.animate().delay==0&&v.sx==1,"release clears inherited stagger delay");
  v.alpha=.3f;v.ty=7;v.touch.touch(v,new MotionEvent(0));check(v.alpha==1&&v.ty==0,"tap interruption normalizes the unfinished row entrance");
  Activity a=new Activity();View content=new View(a);content.setPadding(20,16,20,24);Build.VERSION.SDK_INT=35;UiTheme.padForSystemBars(a,content);
  content.insets.onInsets(content,new WindowInsets(12,30,8,48));check(content.left==32&&content.top==46&&content.right==28&&content.bottom==72,"all safe areas add to original padding");
  content.insets.onInsets(content,new WindowInsets(12,30,8,48));check(content.top==46&&content.bottom==72,"repeated dispatch never accumulates");
  content.insets.onInsets(content,new WindowInsets(0,30,0,300));check(content.left==20&&content.bottom==324,"IME resize preserves design padding");
  content.insets.onInsets(content,new WindowInsets(0,30,0,48));check(content.bottom==72,"IME close restores baseline");
  Build.VERSION.SDK_INT=29;View old=new View(a);old.setPadding(20,16,20,24);UiTheme.padForSystemBars(a,old);check(old.insets==null&&old.top==16,"legacy window is not double-inset");
  System.out.println("PASS: motion and safe-area behavior ("+checks+" assertions; host stand-ins)");
 }
}
