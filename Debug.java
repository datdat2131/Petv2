package vn.soulpets.util;

import java.util.logging.Logger;

/** Log debug bật/tắt lúc chạy (/petadmin debug on|off). */
public final class Debug {
    private static volatile boolean on;
    private static Logger log = Logger.getLogger("AstraxPetRPG");
    private Debug() {}
    public static void init(Logger l, boolean enabled) { log = l; on = enabled; }
    public static boolean enabled() { return on; }
    public static void set(boolean v) { on = v; }
    public static void log(String msg) { if (on) log.info("[DEBUG] " + msg); }
}
