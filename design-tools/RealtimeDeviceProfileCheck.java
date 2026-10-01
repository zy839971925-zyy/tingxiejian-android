package com.example.tingxiejian;
public final class RealtimeDeviceProfileCheck {
    private static int checks;
    static void check(boolean ok, String why) { checks++; if (!ok) throw new AssertionError(why); }
    public static void main(String[] args) {
        for (String name : new String[]{"Xiaomi", "Redmi", "POCO"}) {
            RealtimeDeviceProfile p = RealtimeDeviceProfile.of(name, "Xiaomi", 36);
            check(p.xiaomi && !p.colorOs && p.title().contains("超级岛"), "Xiaomi display family");
            check(p.description().contains("检测") && p.description().contains("普通前台通知始终保留"), "brand not a capability claim");
        }
        RealtimeDeviceProfile old = RealtimeDeviceProfile.of("OPPO", "OPPO", 35);
        check(old.colorOs && !old.xiaomi && !old.standardLiveUpdate, "OPPO pre-16 uses standard fallback");
        check(old.description().contains("普通通知") && old.description().contains("不接入"), "no private old ColorOS integration");
        RealtimeDeviceProfile current = RealtimeDeviceProfile.of("oppo", "OPPO", 36);
        check(current.standardLiveUpdate && current.title().contains("流体云"), "OPPO16 naming");
        check(current.description().contains("不能保证显示") && current.description().contains("标准"), "cannot claim rendered fluid cloud");
        for (String name : new String[]{"Samsung", "Google", "OnePlus", "realme"}) {
            RealtimeDeviceProfile p = RealtimeDeviceProfile.of(name, name, 35);
            check(!p.xiaomi && !p.colorOs && p.title().contains("原生通知"), "do not infer a vendor ROM from adjacent brands");
            check(!p.standardLiveUpdate && p.description().contains("普通通知"), "pre-36 fallback");
        }
        check(RealtimeDeviceProfile.of(null, null, 36).standardLiveUpdate, "missing brand remains standard Android");
        System.out.println("Device display profiles: " + checks + " assertions passed (no OEM rendering claim)");
    }
}
