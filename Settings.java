package vn.soulpets.config;

import java.util.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import vn.soulpets.egg.GachaEngine;
import vn.soulpets.rarity.Rarity;
import vn.soulpets.reward.Reward;

/** Giá trị cấu hình config.yml đã đọc sẵn (nạp lại bằng /petadmin reload). */
public final class Settings {
    public String prefix; public boolean debug;
    public long startingSouls, soulsPerKill; public int releaseRefund;
    public boolean autoSummon, autoRespawn, releaseConfirm; public int maxRespawnFail;
    public double followDistance, teleportDistance; public int levelMaxBase, levelMaxPerStage; public long expPerKill; public double bossExpMult;
    public double hpBuff, hpBuffCap, atkBuff, atkBuffCap, spdBuff, spdBuffCap;
    public int thinkTicks, maxScan, maxAoe, maxParticles, maxEffectsPerTick, tauntMax; public double scanRadius, attackRange, sticky;
    public long attackIntervalMs, skillGcdMs; public boolean autoCast, taunt, ignoreSpawner;
    public int incubatorSlots; public double instantMult, hatchTimeMult; public String defaultEgg; public GachaEngine.PityConfig pity;
    public boolean convertShards; public long dupExp; public Rarity broadcastMin;
    public record Drop(String egg, double chance, Set<String> mobs) {}
    public final List<Drop> drops = new ArrayList<>();
    public final TreeMap<Integer, Reward> dexRewards = new TreeMap<>();
    public long guiClickMs, cmdMs; public int autosaveMinutes;
    public boolean hookMythicMobs, hookMythicLib, hookPapi;

    public void load(FileConfiguration c) {
        prefix = c.getString("prefix", "&6[Pet] &r"); debug = c.getBoolean("debug");
        startingSouls = c.getLong("souls.starting", 100); soulsPerKill = c.getLong("souls.per-kill", 5); releaseRefund = c.getInt("souls.release-refund-per-rarity", 50);
        autoSummon = c.getBoolean("pets.auto-summon-on-join"); autoRespawn = c.getBoolean("pets.auto-respawn", true); maxRespawnFail = c.getInt("pets.max-respawn-failures", 5);
        followDistance = c.getDouble("pets.follow-distance", 3); teleportDistance = c.getDouble("pets.teleport-distance", 16);
        levelMaxBase = c.getInt("pets.level-max-base", 50); levelMaxPerStage = c.getInt("pets.level-max-per-stage", 25);
        expPerKill = c.getLong("pets.exp-per-kill", 10); bossExpMult = c.getDouble("pets.boss-exp-multiplier", 5); releaseConfirm = c.getBoolean("pets.release-requires-confirm", true);
        hpBuff = c.getDouble("pets.owner-buff.max-health-per-pet-hp", 0.1); hpBuffCap = c.getDouble("pets.owner-buff.max-health-cap", 20);
        atkBuff = c.getDouble("pets.owner-buff.attack-per-pet-atk", 0.05); atkBuffCap = c.getDouble("pets.owner-buff.attack-cap", 6);
        spdBuff = c.getDouble("pets.owner-buff.speed-per-pet-speed", 0); spdBuffCap = c.getDouble("pets.owner-buff.speed-cap", 0.05);
        thinkTicks = Math.max(2, c.getInt("combat.think-interval-ticks", 10)); scanRadius = c.getDouble("combat.scan-radius", 12);
        maxScan = c.getInt("combat.max-scan-entities", 32); attackRange = c.getDouble("combat.attack-range", 2.8);
        attackIntervalMs = c.getLong("combat.attack-interval-ms", 1000); sticky = c.getDouble("combat.sticky-target-multiplier", 1.5);
        autoCast = c.getBoolean("combat.auto-cast", true); maxAoe = c.getInt("combat.max-aoe-targets", 8); maxParticles = c.getInt("combat.max-particles-per-skill", 24);
        maxEffectsPerTick = c.getInt("combat.max-effects-per-tick", 12); skillGcdMs = c.getLong("combat.skill-global-cooldown-ms", 1500);
        taunt = c.getBoolean("combat.taunt", true); tauntMax = c.getInt("combat.taunt-max-mobs", 3); ignoreSpawner = c.getBoolean("combat.ignore-spawner-mobs", true);
        incubatorSlots = c.getInt("egg.incubator-slots", 3); instantMult = c.getDouble("egg.instant-hatch-cost-multiplier", 2.0);
        hatchTimeMult = c.getDouble("egg.hatch-time-multiplier", 1.0); defaultEgg = c.getString("egg.default-egg", "basic_egg");
        pity = new GachaEngine.PityConfig(c.getInt("egg.pity.epic-hard", 10), c.getInt("egg.pity.legendary-soft", 60), c.getInt("egg.pity.legendary-hard", 90), c.getDouble("egg.pity.soft-boost", 0.06));
        convertShards = c.getBoolean("egg.duplicate.convert-to-shards", true); dupExp = c.getLong("egg.duplicate.bonus-exp", 150);
        broadcastMin = Rarity.parse(c.getString("egg.broadcast-min-rarity", "LEGENDARY"), Rarity.LEGENDARY);
        drops.clear();
        for (Map<?, ?> m : c.getMapList("egg.drops")) {
            Set<String> mobs = new HashSet<>();
            Object o = m.get("mobs");
            if (o instanceof List<?> l) for (Object x : l) mobs.add(String.valueOf(x).toUpperCase());
            drops.add(new Drop(String.valueOf(m.get("egg")), m.get("chance") instanceof Number n ? n.doubleValue() : 0, mobs));
        }
        dexRewards.clear();
        ConfigurationSection dx = c.getConfigurationSection("dex-rewards");
        if (dx != null) for (String k : dx.getKeys(false)) try { dexRewards.put(Integer.parseInt(k), GameData.reward(dx.getConfigurationSection(k))); } catch (NumberFormatException ignored) { }
        guiClickMs = c.getLong("gui.click-cooldown-ms", 150); cmdMs = c.getLong("gui.command-cooldown-ms", 300);
        autosaveMinutes = Math.max(1, c.getInt("autosave-minutes", 5));
        hookMythicMobs = c.getBoolean("hooks.mythicmobs", true); hookMythicLib = c.getBoolean("hooks.mythiclib", true); hookPapi = c.getBoolean("hooks.placeholderapi", true);
    }
}
