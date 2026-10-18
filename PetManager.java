package vn.soulpets.pet;

import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import vn.soulpets.SoulPetsPlugin;
import vn.soulpets.combat.Cooldowns;
import vn.soulpets.combat.DamageCalc;
import vn.soulpets.combat.TargetSelector;
import vn.soulpets.config.Settings;
import vn.soulpets.skills.Passives;
import vn.soulpets.skills.SkillDef;
import vn.soulpets.skills.SkillMath;
import vn.soulpets.util.Debug;
import vn.soulpets.util.Text;

/** Quản lý pet đang triệu hồi (thực thể + AI). Chỉ chạy trên main thread; MỘT task duy nhất cho mọi pet. */
public final class PetManager {
    public static final class Active {
        public final UUID owner; public PetData data; public PetTemplate tpl; public Mob entity;
        public Stats stats = Stats.ZERO; public Passives passives = Passives.NONE; public LivingEntity target;
        public long nextAttack, nextRegen, nextAutoCast, lastSpawn, shieldUntil, lastTaunt; public double[] shield = {0}; public int fails, tickCount;
        public final Cooldowns<String> skillCd = new Cooldowns<>();
        Active(UUID owner) { this.owner = owner; }
    }
    private final SoulPetsPlugin pl; private final Map<UUID, Active> active = new HashMap<>();
    private final NamespacedKey petKey, dungeonKey, bossKey, noRewardKey, hpMod, atkMod, spdMod;
    private BukkitTask task; private int effects; private final Random rng = new Random();

    public PetManager(SoulPetsPlugin pl) {
        this.pl = pl;
        petKey = new NamespacedKey(pl, "pet_owner"); dungeonKey = new NamespacedKey(pl, "dungeon_mob"); bossKey = new NamespacedKey(pl, "dungeon_boss");
        noRewardKey = new NamespacedKey(pl, "no_reward"); hpMod = new NamespacedKey(pl, "pet_buff_hp"); atkMod = new NamespacedKey(pl, "pet_buff_atk"); spdMod = new NamespacedKey(pl, "pet_buff_spd");
    }
    public NamespacedKey dungeonKey() { return dungeonKey; }
    public NamespacedKey bossKey() { return bossKey; }
    public NamespacedKey noRewardKey() { return noRewardKey; }
    public NamespacedKey petKey() { return petKey; }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(pl, this::brain, 20L, pl.settings().thinkTicks);
    }
    public void stop() { if (task != null) { task.cancel(); task = null; } }

    public Active get(UUID owner) { return active.get(owner); }
    public Collection<Active> all() { return active.values(); }
    public boolean isPet(Entity e) { return e != null && e.getPersistentDataContainer().has(petKey, PersistentDataType.STRING); }
    public boolean isCurrentPetEntity(Entity e) { for (Active a : active.values()) if (a.entity != null && a.entity.getUniqueId().equals(e.getUniqueId())) return true; return false; }
    public boolean tryEffect() { if (effects >= pl.settings().maxEffectsPerTick) return false; effects++; return true; }
    public Random rng() { return rng; }

    // ---------- vòng đời ----------
    public boolean summon(Player owner, PetData data) {
        dismiss(owner.getUniqueId());
        PetTemplate t = pl.data().pets.get(data.templateId);
        if (t == null) { pl.service().msg(owner, "§cPet này không còn tồn tại trong cấu hình (" + data.templateId + ")."); return false; }
        Active a = new Active(owner.getUniqueId()); a.data = data; a.tpl = t;
        active.put(owner.getUniqueId(), a);
        recalc(a);
        if (!spawnEntity(a, owner)) { active.remove(owner.getUniqueId()); removeBuffs(owner); return false; }
        applyOwnerBuffs(owner, a);
        return true;
    }
    public void dismiss(UUID ownerId) {
        Active a = active.remove(ownerId);
        if (a == null) return;
        despawn(a);
        Player p = Bukkit.getPlayer(ownerId);
        if (p != null) removeBuffs(p);
    }
    public void removeAll() {
        for (Active a : new ArrayList<>(active.values())) { despawn(a); Player p = Bukkit.getPlayer(a.owner); if (p != null) removeBuffs(p); }
        active.clear();
        // quét sạch thực thể pet còn sót (an toàn khi reload/crash)
        for (World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) if (isPet(e)) e.remove();
    }
    private void despawn(Active a) {
        if (a.entity != null) { a.entity.remove(); a.entity = null; }
        a.target = null;
    }
    /** Tính lại chỉ số + đặt lại tên/thuộc tính (sau lên cấp, tiến hóa, đổi trang bị...). */
    public void recalc(Active a) {
        PetService.Computed c = pl.service().compute(a.data);
        a.stats = c.stats(); a.passives = c.passives();
        PetTemplate t = pl.data().pets.get(a.data.templateId);
        if (t != null) a.tpl = t;
        if (a.entity != null && a.entity.isValid()) a.entity.setCustomName(displayName(a));
        Player p = Bukkit.getPlayer(a.owner);
        if (p != null && active.get(a.owner) == a) applyOwnerBuffs(p, a);
    }
    public String displayName(Active a) {
        String nick = a.data.nickname == null || a.data.nickname.isEmpty() ? a.tpl.name() : a.data.nickname;
        return a.tpl.rarity().color() + nick + " §7[Lv." + a.data.level + "]" + (a.data.star > 0 ? " §e" + "★".repeat(a.data.star) : "");
    }
    private boolean spawnEntity(Active a, Player owner) {
        despawn(a);
        a.lastSpawn = System.currentTimeMillis();
        Location loc = owner.getLocation().add(rng.nextDouble() * 2 - 1, 0.2, rng.nextDouble() * 2 - 1);
        Entity e = null;
        if (!a.tpl.mythicMob().isEmpty() && pl.mythic().available()) e = pl.mythic().spawn(a.tpl.mythicMob(), loc);
        if (e == null) {
            try { e = loc.getWorld().spawnEntity(loc, EntityType.valueOf(a.tpl.entity()), CreatureSpawnEvent.SpawnReason.CUSTOM); }
            catch (IllegalArgumentException ex) { pl.getLogger().warning("EntityType không hợp lệ cho pet " + a.tpl.id() + ": " + a.tpl.entity()); return false; }
        }
        if (!(e instanceof Mob mob)) { e.remove(); pl.getLogger().warning("Pet " + a.tpl.id() + " cần entity dạng Mob (đang là " + e.getType() + ")."); return false; }
        mob.setPersistent(false); mob.setRemoveWhenFarAway(false); mob.setInvulnerable(true); mob.setSilent(true); mob.setCollidable(false);
        mob.setCanPickupItems(false); mob.setTarget(null);
        if (mob instanceof Ageable ag) { ag.setAdult(); ag.setAgeLock(true); }
        if (mob instanceof Breedable br) br.setBreed(false);
        mob.setCustomName(displayName(a)); mob.setCustomNameVisible(true);
        mob.getPersistentDataContainer().set(petKey, PersistentDataType.STRING, a.owner.toString());
        a.entity = mob; a.fails = 0;
        Debug.log("Spawn pet " + a.tpl.id() + " cho " + owner.getName());
        return true;
    }

    // ---------- buff cho chủ ----------
    private void setMod(Player p, Attribute attr, NamespacedKey key, double amount, AttributeModifier.Operation op) {
        AttributeInstance inst = p.getAttribute(attr);
        if (inst == null) return;
        for (AttributeModifier m : new ArrayList<>(inst.getModifiers())) if (key.equals(m.getKey())) inst.removeModifier(m);
        if (amount > 0) inst.addModifier(new AttributeModifier(key, amount, op));
    }
    public void applyOwnerBuffs(Player p, Active a) {
        Settings s = pl.settings();
        setMod(p, Attribute.MAX_HEALTH, hpMod, Math.min(s.hpBuffCap, a.stats.hp() * s.hpBuff), AttributeModifier.Operation.ADD_NUMBER);
        setMod(p, Attribute.ATTACK_DAMAGE, atkMod, Math.min(s.atkBuffCap, a.stats.atk() * s.atkBuff), AttributeModifier.Operation.ADD_NUMBER);
        setMod(p, Attribute.MOVEMENT_SPEED, spdMod, Math.min(s.spdBuffCap, a.stats.speed() * s.spdBuff), AttributeModifier.Operation.ADD_SCALAR);
    }
    /** Modifier của API được lưu cùng người chơi, nên PHẢI gỡ khi thoát/tắt plugin/đăng nhập lại. */
    public void removeBuffs(Player p) {
        setMod(p, Attribute.MAX_HEALTH, hpMod, 0, AttributeModifier.Operation.ADD_NUMBER);
        setMod(p, Attribute.ATTACK_DAMAGE, atkMod, 0, AttributeModifier.Operation.ADD_NUMBER);
        setMod(p, Attribute.MOVEMENT_SPEED, spdMod, 0, AttributeModifier.Operation.ADD_NUMBER);
        AttributeInstance hp = p.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null && p.getHealth() > hp.getValue()) p.setHealth(hp.getValue());
    }

    // ---------- AI ----------
    private void brain() {
        effects = 0;
        for (Active a : new ArrayList<>(active.values())) {
            try { tick(a); } catch (Exception ex) { pl.getLogger().warning("Lỗi AI pet của " + a.owner + ": " + ex); Debug.log(Arrays.toString(ex.getStackTrace())); }
        }
    }
    private boolean hostile(Entity e) {
        if (!(e instanceof Mob m) || !m.isValid() || m.isDead() || isPet(m) || m instanceof Tameable) return false;
        return m instanceof Enemy || m.getPersistentDataContainer().has(dungeonKey, PersistentDataType.STRING);
    }
    public List<Mob> enemiesAround(Location loc, double radius, int limit) {
        List<Mob> out = new ArrayList<>();
        for (Entity e : loc.getWorld().getNearbyEntities(loc, radius, radius, radius, this::hostile)) { out.add((Mob) e); if (out.size() >= limit) break; }
        return out;
    }
    private void tick(Active a) {
        Settings s = pl.settings();
        Player owner = Bukkit.getPlayer(a.owner);
        if (owner == null || !owner.isOnline()) { dismiss(a.owner); return; }
        if (owner.isDead()) return;
        long now = System.currentTimeMillis(); a.tickCount++;
        if (a.entity == null || !a.entity.isValid()) {
            if (!s.autoRespawn) { dismiss(a.owner); return; }
            if (now - a.lastSpawn < 3000) return;
            if (++a.fails > s.maxRespawnFail) { dismiss(a.owner); pl.service().msg(owner, "§cPet liên tục biến mất, đã thu hồi. Hãy triệu hồi lại."); return; }
            spawnEntity(a, owner);
            return;
        }
        Location ol = owner.getLocation(), pe = a.entity.getLocation();
        if (pe.getWorld() != ol.getWorld() || pe.distanceSquared(ol) > s.teleportDistance * s.teleportDistance) { a.entity.teleport(ol.clone().add(1, 0.2, 1)); a.target = null; return; }
        if (a.shield[0] > 0 && now > a.shieldUntil) a.shield[0] = 0;
        if (a.passives.regen() > 0 && now >= a.nextRegen) {
            a.nextRegen = now + 2000;
            double max = owner.getAttribute(Attribute.MAX_HEALTH).getValue();
            if (owner.getHealth() < max) owner.setHealth(Math.min(max, owner.getHealth() + a.passives.regen()));
        }
        // mục tiêu
        if (a.target != null && (!a.target.isValid() || a.target.isDead() || a.target.getWorld() != ol.getWorld()
                || a.target.getLocation().distanceSquared(ol) > Math.pow(s.scanRadius * s.sticky, 2))) a.target = null;
        if (a.target == null || a.tickCount % 3 == 0) pickTarget(a, owner);
        double speed = Math.max(0.6, a.stats.speed());
        if (a.target != null) {
            double d = a.entity.getLocation().distance(a.target.getLocation());
            if (s.autoCast && now >= a.nextAutoCast) { a.nextAutoCast = now + 1000; autoCast(a, owner, d, now); }
            if (d > s.attackRange) a.entity.getPathfinder().moveTo(a.target.getLocation(), speed);
            else if (now >= a.nextAttack) attack(a, owner, now);
        } else {
            if (s.autoCast && now >= a.nextAutoCast) { a.nextAutoCast = now + 1000; autoCast(a, owner, Double.MAX_VALUE, now); }
            if (pe.distanceSquared(ol) > s.followDistance * s.followDistance) a.entity.getPathfinder().moveTo(ol, speed);
        }
    }
    private void pickTarget(Active a, Player owner) {
        Settings s = pl.settings();
        double r = s.scanRadius; int n = 0; int taunted = 0;
        boolean doTaunt = s.taunt && a.tpl.role() == Role.TANK && System.currentTimeMillis() - a.lastTaunt > 3000;
        List<TargetSelector.Candidate> list = new ArrayList<>(); Map<UUID, LivingEntity> map = new HashMap<>();
        for (Entity e : owner.getNearbyEntities(r, r / 2, r)) {
            if (++n > s.maxScan) break;
            if (!hostile(e)) continue;
            Mob m = (Mob) e;
            boolean atOwner = owner.equals(m.getTarget());
            if (doTaunt && atOwner && taunted < s.tauntMax) { m.setTarget(a.entity); taunted++; }
            boolean boss = m instanceof Boss || m.getPersistentDataContainer().has(bossKey, PersistentDataType.STRING);
            list.add(new TargetSelector.Candidate(m.getUniqueId(), m.getLocation().distanceSquared(a.entity.getLocation()), m.getHealth(), atOwner, boss));
            map.put(m.getUniqueId(), m);
        }
        if (doTaunt) a.lastTaunt = System.currentTimeMillis();
        TargetSelector.Candidate best = TargetSelector.select(list, a.tpl.role());
        a.target = best == null ? null : map.get(best.id());
    }
    /** Sát thương pet gây ra (đã tính chí mạng, khắc hệ, MythicLib, hút máu). Sát thương ghi nhận cho chủ để kill được tính. */
    public double hit(Active a, Player owner, LivingEntity t, double mult, Element el) {
        double elem = el.multiplierAgainst(MobElements.of(t.getType().name()));
        double dmg = DamageCalc.hit(a.stats.atk(), mult, elem, a.passives.crit(), rng) * pl.mythicLib().damageMultiplier(owner.getUniqueId());
        if (dmg <= 0) return 0;
        t.damage(dmg, owner);
        if (a.passives.lifesteal() > 0) {
            double max = owner.getAttribute(Attribute.MAX_HEALTH).getValue();
            owner.setHealth(Math.min(max, owner.getHealth() + dmg * a.passives.lifesteal()));
        }
        return dmg;
    }
    private void attack(Active a, Player owner, long now) {
        a.nextAttack = now + (long) (pl.settings().attackIntervalMs / Math.max(0.6, a.stats.speed()));
        a.entity.swingMainHand();
        hit(a, owner, a.target, 1.0, a.tpl.element());
    }
    private void autoCast(Active a, Player owner, double distToTarget, long now) {
        double max = owner.getAttribute(Attribute.MAX_HEALTH).getValue();
        for (SkillDef d : pl.data().skillsOf(a.tpl)) {
            if (!d.active() || SkillMath.levelOf(a.data, d) <= 0) continue;
            if (a.skillCd.remaining(d.id(), now) > 0) continue;
            boolean ok = switch (d.effect()) {
                case "HEAL" -> owner.getHealth() < max * 0.65;
                case "SHIELD" -> a.target != null && a.shield[0] <= 0 && owner.getHealth() < max * 0.9;
                case "BUFF" -> a.target != null && distToTarget < 10;
                default -> a.target != null && distToTarget <= Math.max(d.range(), 3);
            };
            if (ok && tryEffect() && pl.skills().cast(owner, a, d)) return;
        }
    }
    public String describeBrain() {
        StringBuilder b = new StringBuilder("Pet đang hoạt động: " + active.size());
        for (Active a : active.values()) b.append("\n - ").append(a.owner).append(" ").append(a.tpl.id()).append(" valid=").append(a.entity != null && a.entity.isValid()).append(" target=").append(a.target != null);
        return b.toString();
    }
    public static String shortStats(Stats s) { return String.format(Locale.ROOT, "§aHP %.0f §cATK %.1f §9DEF %.1f §bSPD %.2f", s.hp(), s.atk(), s.def(), s.speed()); }
    @SuppressWarnings("unused") private static String unused() { return Text.c(""); }
}
