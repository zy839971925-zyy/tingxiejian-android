package rikka.shizuku;

public final class Shizuku {
    public static boolean running = true;
    public static int permission = 0;
    public static int uid = 2000;
    public static boolean pingBinder() { return running; }
    public static int checkSelfPermission() { return permission; }
    public static int getUid() { return uid; }
    public static void requestPermission(int code) { }
}
