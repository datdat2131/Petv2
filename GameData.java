package vn.soulpets.config;

import java.io.File;
import java.util.*;
import java.util.logging.Level;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import vn.soulpets.dungeon.DungeonDef;
import vn.soulpets.egg.EggType;
import vn.soulpets.equipment.EquipmentDef;
import vn.soulpets.pet.*;
import vn.soulpets.quest.AchievementDef;
import vn.soulpets.quest.QuestDef;
import vn.soulpets.rarity.Rarity;
import vn.soulpets.reward.Reward;
import vn.soulpets.skills.SkillDef;

/** Toàn bộ dữ liệu game đọc từ các file yml. Thêm pet/kỹ năng/trứng... chỉ cần sửa yml rồi reload. */
public final class GameData {
    public final Map<String, PetTemplate> pets = new LinkedHashMap<>();
    public final Map<String, SkillDef> skills = new LinkedHashMap<>();
    public final Map<String, EggType> eggs = new LinkedHashMap<>();
    public final Map<String, EquipmentDef> equipment = new LinkedHashMap<>();
    public final Map<String, QuestDef> quests = new LinkedHashMap<>();
    public final Map<String, AchievementDef> achievements = new LinkedHashMap<>();
    public final Map<String, DungeonDef> dungeons = new LinkedHashMap<>();

    public static YamlConfiguration yaml(JavaPlugin pl, String name) {
        File f = new File(pl.getDataFolder(), name);
        if (!f.exists()) pl.saveResource(name, false);
        return YamlConfiguration.loadConfiguration(f);
    }
    public static Reward reward(ConfigurationSection s) {
        if (s == null) return Reward.NONE;
        Map<String, Integer> eggs = new LinkedHashMap<>();
        ConfigurationSection e = s.getConfigurationSection("eggs");
        if (e != null) for (String k : e.getKeys(false)) eggs.put(k, e.getInt(k));
        return new Reward(s.getLong("souls"), s.getLong("pet-exp"), eggs, new ArrayList<>(s.getStringList("equipment")));
    }
    private static Map<String, Integer> intMap(ConfigurationSection s) {
        Map<String, Integer> m = new LinkedHashMap<>();
        if (s != null) for (String k : s.getKeys(false)) m.put(k.toUpperCase(), s.getInt(k));
        return m;
    }
    private static Stats stats(ConfigurationSection s) {
        return s == null ? Stats.ZERO : new Stats(s.getDouble("hp"), s.getDouble("atk"), s.getDouble("def"), s.getDouble("speed"));
    }

    public static GameData load(JavaPlugin pl) {
        GameData g = new GameData();
        try { g.loadSkills(yaml(pl, "skills.yml")); } catch (Exception ex) { pl.getLogger().log(Level.SEVERE, "Lỗi skills.yml", ex); }
        try { g.loadPets(yaml(pl, "pets.yml"), pl); } catch (Exception ex) { pl.getLogger().log(Level.SEVERE, "Lỗi pets.yml", ex); }
        try { g.loadEggs(yaml(pl, "eggs.yml")); } catch (Exception ex) { pl.getLogger().log(Level.SEVERE, "Lỗi eggs.yml", ex); }
        try { g.loadEquipment(yaml(pl, "equipment.yml")); } catch (Exception ex) { pl.getLogger().log(Level.SEVERE, "Lỗi equipment.yml", ex); }
        try { g.loadQuests(yaml(pl, "quests.yml")); } catch (Exception ex) { pl.getLogger().log(Level.SEVERE, "Lỗi quests.yml", ex); }
        try { g.loadAchievements(yaml(pl, "achievements.yml")); } catch (Exception ex) { pl.getLogger().log(Level.SEVERE, "Lỗi achievements.yml", ex); }
        try { g.loadDungeons(yaml(pl, "dungeons.yml")); } catch (Exception ex) { pl.getLogger().log(Level.SEVERE, "Lỗi dungeons.yml", ex); }
        pl.getLogger().info("Đã nạp " + g.pets.size() + " pet, " + g.skills.size() + " kỹ năng, " + g.eggs.size() + " trứng, " + g.equipment.size()
                + " trang bị, " + g.quests.size() + " nhiệm vụ, " + g.dungeons.size() + " dungeon.");
        return g;
    }

    private void loadSkills(YamlConfiguration y) {
        ConfigurationSection root = y.getConfigurationSection("skills");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            skills.put(id, new SkillDef(id, s.getString("name", id), "ACTIVE".equalsIgnoreCase(s.getString("type", "PASSIVE")), s.getString("effect", "").toUpperCase(),
                    Element.parse(s.getString("element"), null), s.getDouble("power"), s.getDouble("per-level"), s.getDouble("cooldown", 10), s.getDouble("range", 6),
                    s.getInt("unlock-level", 1), s.getInt("max-level", 5), s.getInt("cost", 1), s.getString("particle", "CRIT"), s.getDouble("extra"),
                    s.getString("mythic-skill", ""), s.getString("description", "")));
        }
    }
    private void loadPets(YamlConfiguration y, JavaPlugin pl) {
        ConfigurationSection root = y.getConfigurationSection("pets");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            List<EvolutionBranch> evo = new ArrayList<>();
            for (Map<?, ?> m : s.getMapList("evolutions")) {
                Map<String, Integer> mats = new LinkedHashMap<>();
                if (m.get("materials") instanceof Map<?, ?> mm) mm.forEach((k, v) -> mats.put(String.valueOf(k).toUpperCase(), v instanceof Number n ? n.intValue() : 0));
                evo.add(new EvolutionBranch(String.valueOf(m.get("to")), m.get("min-level") instanceof Number n ? n.intValue() : 20, m.get("souls") instanceof Number n2 ? n2.longValue() : 0,
                        m.get("star") instanceof Number n3 ? n3.intValue() : 0, mats));
            }
            List<String> sk = new ArrayList<>();
            for (String k : s.getStringList("skills")) if (skills.containsKey(k)) sk.add(k); else pl.getLogger().warning("Pet " + id + ": skill không tồn tại " + k);
            pets.put(id, new PetTemplate(id, s.getString("name", id), Element.parse(s.getString("element"), Element.NATURE), Rarity.parse(s.getString("rarity"), Rarity.COMMON),
                    Role.parse(s.getString("role"), Role.DPS), s.getString("entity", "WOLF").toUpperCase(), s.getBoolean("obtainable", true), s.getString("mythic-mob", ""),
                    stats(s.getConfigurationSection("stats")), sk, evo, s.getString("description", "")));
        }
        for (PetTemplate t : pets.values()) for (EvolutionBranch b : t.evolutions()) if (!pets.containsKey(b.to())) pl.getLogger().warning("Pet " + t.id() + ": tiến hóa tới pet không tồn tại " + b.to());
    }
    private void loadEggs(YamlConfiguration y) {
        ConfigurationSection root = y.getConfigurationSection("eggs");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            Map<Rarity, Double> w = new EnumMap<>(Rarity.class);
            ConfigurationSection ws = s.getConfigurationSection("weights");
            if (ws != null) for (String k : ws.getKeys(false)) { Rarity r = Rarity.parse(k, null); if (r != null) w.put(r, ws.getDouble(k)); }
            eggs.put(id, new EggType(id, s.getString("name", id), s.getString("icon", "EGG"), s.getLong("cost", 100), s.getLong("hatch-seconds", 60), Element.parse(s.getString("element"), null), w));
        }
    }
    private void loadEquipment(YamlConfiguration y) {
        ConfigurationSection root = y.getConfigurationSection("equipment");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            equipment.put(id, new EquipmentDef(id, s.getString("name", id), s.getString("slot", "WEAPON").toUpperCase(), Rarity.parse(s.getString("rarity"), Rarity.COMMON),
                    s.getString("material", "STICK"), stats(s.getConfigurationSection("stats")), s.getLong("shop-cost"), s.getInt("max-upgrade", 10), s.getLong("upgrade-souls", 50), intMap(s.getConfigurationSection("upgrade-materials"))));
        }
    }
    private void loadQuests(YamlConfiguration y) {
        ConfigurationSection root = y.getConfigurationSection("quests");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            quests.put(id, new QuestDef(id, s.getString("name", id), s.getString("type", "KILL_MOB").toUpperCase(), s.getInt("amount", 1), !"WEEKLY".equalsIgnoreCase(s.getString("period")), reward(s.getConfigurationSection("reward"))));
        }
    }
    private void loadAchievements(YamlConfiguration y) {
        ConfigurationSection root = y.getConfigurationSection("achievements");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            achievements.put(id, new AchievementDef(id, s.getString("name", id), s.getString("counter", "kills"), s.getLong("threshold", 1), reward(s.getConfigurationSection("reward")), s.getString("description", "")));
        }
    }
    private void loadDungeons(YamlConfiguration y) {
        ConfigurationSection root = y.getConfigurationSection("dungeons");
        if (root == null) return;
        for (String id : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(id);
            if (s == null) continue;
            List<DungeonDef.Wave> waves = new ArrayList<>();
            for (Map<?, ?> m : s.getMapList("waves"))
                waves.add(new DungeonDef.Wave(m.get("type") == null ? "" : String.valueOf(m.get("type")).toUpperCase(), m.get("mythic") == null ? "" : String.valueOf(m.get("mythic")),
                        m.get("count") instanceof Number n ? n.intValue() : 5, m.get("hp-mult") instanceof Number n2 ? n2.doubleValue() : 1.0));
            ConfigurationSection b = s.getConfigurationSection("boss");
            DungeonDef.Boss boss = b == null ? null : new DungeonDef.Boss(b.getString("type", "ZOMBIE").toUpperCase(), b.getString("mythic", ""), b.getString("name", "&cBoss"), b.getDouble("hp", 100));
            dungeons.put(id, new DungeonDef(id, s.getString("name", id), s.getInt("min-pet-level", 1), s.getLong("cooldown-seconds", 600), s.getLong("timeout-seconds", 300),
                    s.getBoolean("teleport", true), waves, boss, reward(s.getConfigurationSection("rewards")), s.getString("location", "")));
        }
    }
    public List<PetTemplate> obtainablePets() { List<PetTemplate> l = new ArrayList<>(); for (PetTemplate t : pets.values()) if (t.obtainable()) l.add(t); return l; }
    public Collection<SkillDef> skillsOf(PetTemplate t) { List<SkillDef> l = new ArrayList<>(); for (String id : t.skills()) { SkillDef d = skills.get(id); if (d != null) l.add(d); } return l; }
}
