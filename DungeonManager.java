package vn.soulpets.dungeon;

import java.io.File;
import java.util.*;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import vn.soulpets.SoulPetsPlugin;
import vn.soulpets.pet.PlayerProfile;
import vn.soulpets.util.Text;

/** Dungeon dạng đợt quái + boss quanh một điểm. Một task duy nhất kiểm tra mọi dungeon đang chạy (không rò task / thực thể). */
public final class DungeonManager {
    private static final class Run {
        final UUID owner; final DungeonDef def; final Location origin, back; int wave = -1; final Set<UUID> mobs = new HashSet<>(); final Map<UUID, Integer> missing = new HashMap<>();
        final long start = System.currentTimeMillis(); BossBar bar; int total = 1; boolean bossWave;
        Run(UUID o, DungeonDef d, Location origin, Location back) { owner = o; def = d; this.origin = origin; this.back = back; }
    }
    private final SoulPetsPlugin pl; private final Map<UUID, Run> runs = new HashMap<>(); private BukkitTask task;
    public DungeonManager(SoulPetsPlugin pl) { this.pl = pl; }

    public void start() { stop(); task = Bukkit.getScheduler().runTaskTimer(pl, this::tick, 20L, 20L); }
    public void stop() { if (task != null) { task.cancel(); task = null; } }
    public boolean inDungeon(UUID id) { return runs.containsKey(id); }

    public void begin(Player p, String id) {
        DungeonDef d = pl.data().dungeons.get(id);
        if (d == null) { pl.service().msg(p, "§cKhông có dungeon '" + id + "'."); return; }
        if (!p.hasPermission("soulpets.dungeon")) { pl.service().msg(p, "§cBạn không có quyền vào dungeon."); return; }
        if (runs.containsKey(p.getUniqueId())) { pl.service().msg(p, "§cBạn đang trong một dungeon."); return; }
        PlayerProfile pr = pl.service().profile(p);
        var a = pl.pets().get(p.getUniqueId());
        if (a == null) { pl.service().msg(p, "§cHãy triệu hồi pet trước (§f/pet summon§c)."); return; }
        if (a.data.level < d.minPetLevel()) { pl.service().msg(p, "§cPet cần đạt cấp " + d.minPetLevel() + "."); return; }
        long ready = pr.cooldowns.getOrDefault("dungeon:" + id, 0L), now = System.currentTimeMillis();
        if (now < ready) { pl.service().msg(p, "§cDungeon đang hồi: còn " + Text.time((ready - now) / 1000)); return; }
        Location origin = parseLoc(d.location());
        if (origin == null) origin = p.getLocation().clone();
        Location back = p.getLocation().clone();
        if (d.teleport() && parseLoc(d.location()) != null) p.teleport(origin);
        Run r = new Run(p.getUniqueId(), d, origin, back);
        runs.put(p.getUniqueId(), r);
        r.bar = Bukkit.createBossBar(Text.c("&c" + d.name()), BarColor.RED, BarStyle.SEGMENTED_10); r.bar.addPlayer(p);
        pl.service().msg(p, "§aVào dungeon §f" + d.name() + "§a! Hạ hết quái trong " + Text.time(d.timeoutSeconds()) + ".");
        nextWave(r, p);
    }
    private Location parseLoc(String s) {
        if (s == null || s.isBlank()) return null;
        try { String[] x = s.split(","); World w = Bukkit.getWorld(x[0].trim()); if (w == null) return null; return new Location(w, Double.parseDouble(x[1]), Double.parseDouble(x[2]), Double.parseDouble(x[3])); }
        catch (Exception e) { return null; }
    }
    public boolean setLocation(String id, Location l) {
        if (!pl.data().dungeons.containsKey(id)) return false;
        File f = new File(pl.getDataFolder(), "dungeons.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        y.set("dungeons." + id + ".location", l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ());
        try { y.save(f); } catch (Exception e) { pl.getLogger().warning("Không lưu được dungeons.yml: " + e.getMessage()); return false; }
        return true;
    }

    private void nextWave(Run r, Player p) {
        r.wave++;
        r.mobs.clear(); r.missing.clear();
        DungeonDef d = r.def;
        if (r.wave < d.waves().size()) {
            DungeonDef.Wave w = d.waves().get(r.wave);
            int n = Math.min(40, Math.max(1, w.count()));
            for (int i = 0; i < n; i++) spawn(r, p, w.type(), w.mythic(), w.hpMult(), -1, null, false);
            r.total = n; r.bossWave = false;
            pl.service().msg(p, "§eĐợt " + (r.wave + 1) + "/" + (d.waves().size() + (d.boss() != null ? 1 : 0)) + " §7(" + n + " quái)");
        } else if (d.boss() != null && r.wave == d.waves().size()) {
            DungeonDef.Boss b = d.boss();
            spawn(r, p, b.type(), b.mythic(), 1.0, b.hp(), b.name(), true);
            r.total = 1; r.bossWave = true;
            p.sendTitle("§4BOSS", Text.c(b.name()), 5, 40, 10);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1f);
        } else win(r, p);
    }
    private Location safe(Location base, Random rng) {
        Location l = base.clone().add(rng.nextDouble() * 12 - 6, 0, rng.nextDouble() * 12 - 6);
        for (int i = 0; i < 6 && l.getBlock().getType().isSolid(); i++) l.add(0, 1, 0);
        return l;
    }
    private void spawn(Run r, Player p, String type, String mythic, double hpMult, double fixedHp, String name, boolean boss) {
        Location loc = safe(r.origin, pl.pets().rng());
        Entity e = null;
        if (mythic != null && !mythic.isEmpty() && pl.mythic().available()) e = pl.mythic().spawn(mythic, loc);
        if (e == null && !type.isEmpty()) {
            try { e = loc.getWorld().spawnEntity(loc, EntityType.valueOf(type)); } catch (IllegalArgumentException ex) { pl.getLogger().warning("Dungeon " + r.def.id() + ": EntityType sai " + type); }
        }
        if (!(e instanceof LivingEntity le)) { if (e != null) e.remove(); return; }
        var hp = le.getAttribute(Attribute.MAX_HEALTH);
        if (hp != null) {
            double v = fixedHp > 0 ? fixedHp : hp.getBaseValue() * hpMult;
            hp.setBaseValue(Math.min(2000, Math.max(1, v))); le.setHealth(hp.getBaseValue());
        }
        le.setPersistent(false); le.setRemoveWhenFarAway(false);
        le.getPersistentDataContainer().set(pl.pets().dungeonKey(), PersistentDataType.STRING, r.owner.toString());
        if (boss) { le.getPersistentDataContainer().set(pl.pets().bossKey(), PersistentDataType.STRING, "1"); if (name != null) { le.setCustomName(Text.c(name)); le.setCustomNameVisible(true); } le.setGlowing(true); }
        if (le instanceof Mob m) m.setTarget(p);
        r.mobs.add(le.getUniqueId());
    }
    private void tick() {
        for (Run r : new ArrayList<>(runs.values())) {
            Player p = Bukkit.getPlayer(r.owner);
            if (p == null || !p.isOnline()) { cleanup(r, null); continue; }
            if (p.isDead()) { fail(r, p, "Bạn đã gục ngã."); continue; }
            if (System.currentTimeMillis() - r.start > r.def.timeoutSeconds() * 1000L) { fail(r, p, "Hết thời gian!"); continue; }
            r.mobs.removeIf(id -> {
                Entity e = Bukkit.getEntity(id);
                boolean gone = e == null || !e.isValid() || (e instanceof LivingEntity le && le.isDead());
                if (!gone) { r.missing.remove(id); return false; }
                int c = r.missing.merge(id, 1, Integer::sum);
                return e != null && e.isDead() || c >= 3;   // chunk unload tạm thời: chờ 3 nhịp mới coi là mất
            });
            if (r.bar != null) r.bar.setProgress(Math.max(0, Math.min(1, r.mobs.size() / (double) Math.max(1, r.total))));
            if (r.mobs.isEmpty()) nextWave(r, p);
            else for (UUID id : r.mobs) { Entity e = Bukkit.getEntity(id); if (e instanceof Mob m && (m.getTarget() == null || !m.getTarget().isValid())) m.setTarget(p); }
        }
    }
    private void win(Run r, Player p) {
        PlayerProfile pr = pl.service().profile(p);
        pr.cooldowns.put("dungeon:" + r.def.id(), System.currentTimeMillis() + r.def.cooldownSeconds() * 1000L);
        pr.count("dungeons", 1);
        cleanup(r, p);
        p.sendTitle("§aHOÀN THÀNH", Text.c("&f" + r.def.name()), 5, 50, 10);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        pl.service().giveReward(p, r.def.rewards(), "Dungeon " + r.def.name());
        pl.service().quest(p, "WIN_DUNGEON", 1);
        if (r.def.teleport() && r.back.getWorld() != null && !r.back.equals(r.origin)) p.teleport(r.back);
    }
    private void fail(Run r, Player p, String why) {
        cleanup(r, p);
        pl.service().msg(p, "§cDungeon thất bại: " + why);
    }
    private void cleanup(Run r, Player p) {
        runs.remove(r.owner);
        for (UUID id : r.mobs) { Entity e = Bukkit.getEntity(id); if (e != null) e.remove(); }
        r.mobs.clear();
        if (r.bar != null) { r.bar.removeAll(); r.bar = null; }
    }
    public void stopFor(UUID id) { Run r = runs.get(id); if (r != null) cleanup(r, null); }
    public void stopAll() { for (Run r : new ArrayList<>(runs.values())) cleanup(r, null); }
}
