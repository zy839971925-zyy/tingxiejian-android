package android.util;public class TypedValue{public static final int COMPLEX_UNIT_DIP=1;public static float applyDimension(int u,float v,DisplayMetrics d){return v*d.density;}}
