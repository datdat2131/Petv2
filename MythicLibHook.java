package vn.soulpets.mythiclib;

import java.lang.reflect.Method;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Tích hợp MythicLib (reflection, thử nghiệm): đọc chỉ số tùy chỉnh ASTRAX_PET_DAMAGE (đơn vị %) của chủ
 * và nhân vào sát thương pet. Tạo chỉ số này bằng item/class/skill MythicLib của bạn. Lỗi => trả 0, không ảnh hưởng game.
 */
public final class MythicLibHook {
    public static final String STAT = "ASTRAX_PET_DAMAGE";
    private Method get, statMap, getStat; private boolean ready, warned; private final Logger log;

    public MythicLibHook(Logger log, boolean enabled, boolean pluginPresent) {
        this.log = log;
        if (!enabled || !pluginPresent) return;
        try {
            Class<?> d = Class.forName("io.lumine.mythic.lib.api.player.MMOPlayerData");
            get = d.getMethod("get", UUID.class); statMap = d.getMethod("getStatMap");
            getStat = statMap.getReturnType().getMethod("getStat", String.class);
            ready = true; log.info("Đã kết nối MythicLib (chỉ số " + STAT + ").");
        } catch (Throwable t) { log.warning("Không kết nối được MythicLib: " + t.getClass().getSimpleName()); }
    }
    public boolean available() { return ready; }
    /** @return hệ số nhân sát thương (1.0 = không đổi) */
    public double damageMultiplier(UUID owner) {
        if (!ready) return 1.0;
        try {
            Object data = get.invoke(null, owner);
            if (data == null) return 1.0;
            Object v = getStat.invoke(statMap.invoke(data), STAT);
            return 1.0 + Math.max(-90, ((Number) v).doubleValue()) / 100.0;
        } catch (Throwable t) { if (!warned) { warned = true; log.warning("MythicLib đọc chỉ số lỗi: " + t.getMessage()); } return 1.0; }
    }
}
