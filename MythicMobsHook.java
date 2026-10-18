package vn.soulpets.mythicmobs;

import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.Location;
import org.bukkit.entity.Entity;

/** Tích hợp MythicMobs mềm bằng reflection (không cần MythicMobs lúc biên dịch). */
public final class MythicMobsHook {
    private final Logger log; private Object helper; private Method spawn, cast; private boolean ready;

    public MythicMobsHook(Logger log, boolean enabled, boolean pluginPresent) {
        this.log = log;
        if (!enabled || !pluginPresent) return;
        try {
            Class<?> mb = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            Object inst = mb.getMethod("inst").invoke(null);
            helper = inst.getClass().getMethod("getAPIHelper").invoke(inst);
            Class<?> hc = Class.forName("io.lumine.mythic.bukkit.BukkitAPIHelper");
            spawn = hc.getMethod("spawnMythicMob", String.class, Location.class);
            cast = hc.getMethod("castSkill", Entity.class, String.class);
            ready = true;
            log.info("Đã kết nối MythicMobs.");
        } catch (Throwable t) { log.warning("Không kết nối được MythicMobs (" + t.getClass().getSimpleName() + "): tính năng Mythic sẽ bị tắt."); }
    }
    public boolean available() { return ready; }
    public Entity spawn(String mob, Location loc) {
        if (!ready || mob == null || mob.isEmpty()) return null;
        try { Object o = spawn.invoke(helper, mob, loc); return o instanceof Entity e ? e : null; }
        catch (Throwable t) { log.warning("MythicMobs spawn '" + mob + "' lỗi: " + t.getMessage()); return null; }
    }
    public boolean castSkill(Entity caster, String skill) {
        if (!ready || skill == null || skill.isEmpty()) return false;
        try { return Boolean.TRUE.equals(cast.invoke(helper, caster, skill)); }
        catch (Throwable t) { log.warning("MythicMobs castSkill '" + skill + "' lỗi: " + t.getMessage()); return false; }
    }
}
