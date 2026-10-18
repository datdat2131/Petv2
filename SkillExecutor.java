package vn.soulpets.skills;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import vn.soulpets.SoulPetsPlugin;
import vn.soulpets.pet.Element;
import vn.soulpets.pet.PetManager;

/** Thực thi kỹ năng chủ động của pet (tự động hoặc /pet skill). */
public final class SkillExecutor {
    private final SoulPetsPlugin pl;
    public SkillExecutor(SoulPetsPlugin pl) { this.pl = pl; }

    private void fx(Location loc, String particle, int count) {
        int n = Math.min(count, pl.settings().maxParticles);
        Particle p;
        try { p = Particle.valueOf(particle.toUpperCase()); } catch (Exception e) { p = Particle.CRIT; }
        try { loc.getWorld().spawnParticle(p, loc.clone().add(0, 1, 0), n, 0.4, 0.4, 0.4, 0.02); } catch (Exception ignored) { /* particle cần dữ liệu riêng: bỏ qua */ }
    }
    private static String ele(Element e) { return e == null ? "" : e.tag(); }

    /** @return true nếu kỹ năng đã được dùng (và bắt đầu hồi chiêu). */
    public boolean cast(Player owner, PetManager.Active a, SkillDef d) {
        int lv = SkillMath.levelOf(a.data, d);
        if (lv <= 0 || !d.active()) return false;
        long now = System.currentTimeMillis();
        if (a.skillCd.remaining(d.id(), now) > 0) return false;
        double mult = SkillMath.value(d, lv);
        Element el = d.element() != null ? d.element() : a.tpl.element();
        PetManager pm = pl.pets();
        Location center = a.entity.getLocation();
        double ownerMax = owner.getAttribute(Attribute.MAX_HEALTH).getValue();
        boolean used = false;
        switch (d.effect()) {
            case "DAMAGE_AOE" -> {
                Location c = a.target != null && a.target.isValid() ? a.target.getLocation() : owner.getLocation();
                List<Mob> list = pm.enemiesAround(c, d.range(), pl.settings().maxAoe);
                if (list.isEmpty()) return false;
                for (Mob m : list) { pm.hit(a, owner, m, mult, el); status(m, el, d); }
                fx(c, d.particle(), 30); used = true;
            }
            case "DAMAGE_SINGLE" -> {
                LivingEntity t = a.target;
                if (t == null || !t.isValid() || t.getLocation().distance(center) > d.range() + 2) return false;
                double dmg = pm.hit(a, owner, t, mult, el);
                if (d.extra() > 0) owner.setHealth(Math.min(ownerMax, owner.getHealth() + dmg * d.extra()));
                fx(t.getLocation(), d.particle(), 20); used = true;
            }
            case "CHAIN" -> {
                LivingEntity cur = a.target;
                if (cur == null || !cur.isValid()) { List<Mob> l = pm.enemiesAround(owner.getLocation(), d.range(), 1); if (l.isEmpty()) return false; cur = l.get(0); }
                Set<UUID> hit = new HashSet<>(); double m = mult; int hops = Math.max(1, (int) d.extra());
                for (int i = 0; i < hops && cur != null; i++) {
                    hit.add(cur.getUniqueId()); pm.hit(a, owner, cur, m, el); fx(cur.getLocation(), d.particle(), 12); m *= 0.8;
                    LivingEntity next = null; double best = 25;
                    for (Mob e : pm.enemiesAround(cur.getLocation(), 5, 10)) {
                        if (hit.contains(e.getUniqueId())) continue;
                        double dist = e.getLocation().distanceSquared(cur.getLocation());
                        if (dist < best) { best = dist; next = e; }
                    }
                    cur = next;
                }
                used = true;
            }
            case "HEAL" -> {
                if (owner.getHealth() >= ownerMax) return false;
                owner.setHealth(Math.min(ownerMax, owner.getHealth() + ownerMax * mult));
                if (el == Element.NATURE) owner.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 0, false, true));
                fx(owner.getLocation(), d.particle(), 12); used = true;
            }
            case "SHIELD" -> {
                a.shield[0] = ownerMax * mult; a.shieldUntil = now + (long) (Math.max(1, d.extra()) * 1000);
                fx(owner.getLocation(), d.particle(), 16); used = true;
            }
            case "BUFF" -> {
                int ticks = (int) (Math.max(1, d.extra()) * 20);
                owner.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, ticks, Math.min(2, lv / 2), false, true));
                owner.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, ticks, Math.min(1, lv / 3), false, true));
                fx(owner.getLocation(), d.particle(), 16); used = true;
            }
            default -> { return false; }
        }
        if (used) {
            a.skillCd.tryUse(d.id(), now, (long) (d.cooldown() * 1000));
            if (!d.mythicSkill().isEmpty()) pl.mythic().castSkill(a.entity, d.mythicSkill());
            pl.service().onSkillUsed(owner);
            if (pl.settings().debug) pl.getLogger().info("[DEBUG] " + owner.getName() + " dùng " + d.id() + " Lv" + lv + " " + ele(el));
        }
        return used;
    }
    private void status(Mob m, Element el, SkillDef d) {
        int ticks = (int) (Math.max(1, d.extra()) * 20);
        if (el == Element.FIRE) m.setFireTicks(ticks);
        else if (el == Element.ICE) m.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, ticks, 1, false, true));
    }
}
