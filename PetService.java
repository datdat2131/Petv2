package vn.soulpets.pet;

import java.time.LocalDate;
import java.util.*;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Boss;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import vn.soulpets.SoulPetsPlugin;
import vn.soulpets.config.Settings;
import vn.soulpets.equipment.EquipmentDef;
import vn.soulpets.evolution.EvolutionRules;
import vn.soulpets.quest.AchievementDef;
import vn.soulpets.quest.QuestDef;
import vn.soulpets.quest.QuestService;
import vn.soulpets.reward.Reward;
import vn.soulpets.skills.Passives;
import vn.soulpets.skills.SkillDef;
import vn.soulpets.skills.SkillMath;
import vn.soulpets.util.Inv;
import vn.soulpets.util.Text;

/** Các thao tác nghiệp vụ trên pet / hồ sơ (có phản hồi tiếng Việt cho người chơi). */
public final class PetService {
    public record Computed(Stats stats, Passives passives) {}
    private final SoulPetsPlugin pl;
    public PetService(SoulPetsPlugin pl) { this.pl = pl; }

    public void msg(Player p, String s) { p.sendMessage(Text.c(pl.settings().prefix) + s); }
    public void actionBar(Player p, String s) { p.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(s)); }
    public PlayerProfile profile(Player p) { return pl.profiles().get(p.getUniqueId()); }
    public PetTemplate tpl(PetData d) { return pl.data().pets.get(d.templateId); }
    public int maxLevel(PetData d) { return LevelCurve.maxLevel(d.stage, pl.settings().levelMaxBase, pl.settings().levelMaxPerStage); }

    public Computed compute(PetData d) {
        PetTemplate t = tpl(d);
        if (t == null) return new Computed(Stats.ZERO, Passives.NONE);
        Passives pas = Passives.aggregate(d, t.skills(), pl.data().skills);
        Stats fin = StatCalculator.finalStats(StatCalculator.base(t, d.level, d.star, d.stage), pl.equipment().flat(d), pas.hpPct(), pas.atkPct(), pas.defPct(), pas.speedPct());
        return new Computed(fin, pas);
    }
    public long power(PetData d) { return StatCalculator.power(compute(d).stats()); }
    public void recalcScore(PlayerProfile pr) {
        List<Long> ps = new ArrayList<>();
        for (PetData d : pr.pets.values()) ps.add(power(d));
        ps.sort(Comparator.reverseOrder());
        long s = 0; for (int i = 0; i < Math.min(3, ps.size()); i++) s += ps.get(i);
        pr.score = s;
    }
    public void afterPetChanged(Player p, PetData d) {
        PlayerProfile pr = profile(p);
        if (pr != null) { recalcScore(pr); pl.profiles().markDirty(p.getUniqueId()); }
        PetManager.Active a = pl.pets().get(p.getUniqueId());
        if (a != null && a.data == d) pl.pets().recalc(a);
    }

    // ---------- sở hữu ----------
    public PetData grantPet(PlayerProfile pr, PetTemplate t, int level) {
        PetData d = PetData.create(t.id(), level);
        pr.pets.put(d.id, d); pr.dex.add(t.id()); pr.count("pets", 1);
        if (pr.selected == null) pr.selected = d.id;
        recalcScore(pr);
        return d;
    }
    public void select(Player p, PetData d) {
        PlayerProfile pr = profile(p); pr.selected = d.id; pl.profiles().markDirty(p.getUniqueId());
        if (pl.pets().get(p.getUniqueId()) != null) summon(p); else msg(p, "§aĐã chọn pet §f" + displayName(d) + "§a.");
    }
    public String displayName(PetData d) { PetTemplate t = tpl(d); return t == null ? d.templateId : (d.nickname.isEmpty() ? t.coloredName() : t.rarity().color() + d.nickname); }
    public PetData selected(PlayerProfile pr) { return pr.selected == null ? null : pr.pets.get(pr.selected); }
    public boolean summon(Player p) {
        PlayerProfile pr = profile(p); PetData d = selected(pr);
        if (d == null) { msg(p, "§cBạn chưa chọn pet. Dùng §f/pet menu§c."); return false; }
        if (pl.pets().summon(p, d)) { msg(p, "§aĐã triệu hồi " + displayName(d) + "§a."); return true; }
        return false;
    }
    public void dismiss(Player p) {
        if (pl.pets().get(p.getUniqueId()) == null) { msg(p, "§7Bạn chưa triệu hồi pet nào."); return; }
        pl.pets().dismiss(p.getUniqueId()); msg(p, "§7Đã thu hồi pet.");
    }
    public void rename(Player p, PetData d, String name) {
        String plain = ChatColor.stripColor(Text.c(name));
        if (plain.length() > 16) { msg(p, "§cTên tối đa 16 ký tự."); return; }
        d.nickname = name; afterPetChanged(p, d); msg(p, "§aĐã đổi tên pet.");
    }
    public void release(Player p, PetData d) {
        PlayerProfile pr = profile(p);
        for (String slot : new ArrayList<>(d.equip.keySet())) pl.equipment().unequip(p, d, slot);
        PetManager.Active a = pl.pets().get(p.getUniqueId());
        if (a != null && a.data == d) pl.pets().dismiss(p.getUniqueId());
        pr.pets.remove(d.id);
        if (d.id.equals(pr.selected)) pr.selected = pr.pets.isEmpty() ? null : pr.pets.keySet().iterator().next();
        PetTemplate t = tpl(d);
        long refund = t == null ? 0 : (long) (t.rarity().ordinal() + 1) * pl.settings().releaseRefund;
        pr.souls += refund; recalcScore(pr); pl.profiles().markDirty(p.getUniqueId());
        msg(p, "§7Đã thả pet, nhận lại §e" + refund + " Linh Hồn§7.");
    }

    // ---------- EXP / cấp ----------
    public void addExp(Player p, PetData d, long amount) {
        int gained = LevelCurve.addExp(d, amount, maxLevel(d));
        PetManager.Active a = pl.pets().get(p.getUniqueId());
        boolean isActive = a != null && a.data == d;
        if (gained > 0) {
            afterPetChanged(p, d);
            msg(p, displayName(d) + " §alên cấp §e" + d.level + "§a! (+" + gained + " điểm kỹ năng)");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
            PlayerProfile pr = profile(p); pr.count("levelups", gained);
            quest(p, "LEVEL_UP", gained);
        } else pl.profiles().markDirty(p.getUniqueId());
        if (isActive && d.level < maxLevel(d)) actionBar(p, displayName(d) + " §7Lv." + d.level + " " + Text.bar(d.exp / (double) LevelCurve.expToNext(d.level), 20) + " §7" + d.exp + "/" + LevelCurve.expToNext(d.level));
    }
    public void onKill(Player killer, LivingEntity victim) {
        PlayerProfile pr = profile(killer);
        if (pr == null) return;
        Settings s = pl.settings();
        boolean boss = victim instanceof Boss || victim.getPersistentDataContainer().has(pl.pets().bossKey(), PersistentDataType.STRING);
        pr.souls += s.soulsPerKill; pr.count("kills", 1);
        PetManager.Active a = pl.pets().get(killer.getUniqueId());
        if (a != null) addExp(killer, a.data, (long) (s.expPerKill * (boss ? s.bossExpMult : 1)));
        String type = victim.getType().name();
        for (Settings.Drop d : s.drops) {
            if (!pl.data().eggs.containsKey(d.egg())) continue;
            if (!d.mobs().contains("*") && !d.mobs().contains(type)) continue;
            if (pl.pets().rng().nextDouble() < d.chance() * (boss ? 5 : 1)) {
                pr.eggs.merge(d.egg(), 1, Integer::sum);
                msg(killer, "§dBạn nhặt được §f" + pl.data().eggs.get(d.egg()).name() + "§d!");
                killer.playSound(killer.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.5f);
            }
        }
        quest(killer, "KILL_MOB", 1);
        pl.profiles().markDirty(killer.getUniqueId());
    }
    public void onSkillUsed(Player p) { PlayerProfile pr = profile(p); if (pr == null) return; pr.count("skills", 1); quest(p, "USE_SKILL", 1); pl.profiles().markDirty(p.getUniqueId()); }

    // ---------- thưởng / nhiệm vụ / thành tựu / dex ----------
    public void giveReward(Player p, Reward r, String source) {
        PlayerProfile pr = profile(p);
        pr.souls += r.souls();
        r.eggs().forEach((k, v) -> pr.eggs.merge(k, v, Integer::sum));
        for (String id : r.equipment()) { EquipmentDef d = pl.data().equipment.get(id); if (d != null) Inv.give(p, pl.equipment().createItem(d, 0)); }
        if (r.petExp() > 0) {
            PetManager.Active a = pl.pets().get(p.getUniqueId());
            PetData target = a != null ? a.data : selected(pr);
            if (target != null) addExp(p, target, r.petExp());
        }
        msg(p, "§6" + source + " §7→ " + r.describe());
        pl.profiles().markDirty(p.getUniqueId());
    }
    public void quest(Player p, String type, long amount) {
        PlayerProfile pr = profile(p);
        if (pr == null) return;
        List<QuestDef> done = QuestService.progress(pr, pl.data().quests.values(), type, amount, LocalDate.now());
        for (QuestDef q : done) { msg(p, "§aNhiệm vụ hoàn thành: §f" + q.name() + " §7(mở §f/pet quests§7 để nhận thưởng)"); p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.5f, 1.6f); }
        checkAchievements(p);
    }
    public void checkAchievements(Player p) {
        PlayerProfile pr = profile(p);
        for (AchievementDef a : QuestService.checkAchievements(pr, pl.data().achievements.values())) {
            msg(p, "§e★ Thành tựu: §f" + a.name() + " §7- " + a.description());
            giveReward(p, a.reward(), "Thành tựu");
        }
        for (Map.Entry<Integer, Reward> e : pl.settings().dexRewards.entrySet()) {
            String key = String.valueOf(e.getKey());
            if (pr.dex.size() >= e.getKey() && pr.dexClaimed.add(key)) { msg(p, "§bPetDex đạt §f" + e.getKey() + " §bpet!"); giveReward(p, e.getValue(), "Thưởng PetDex"); }
        }
    }
    public boolean claimQuest(Player p, QuestDef q) {
        PlayerProfile pr = profile(p);
        QuestService.refresh(pr, pl.data().quests.values(), LocalDate.now());
        Reward r = QuestService.claim(pr, q);
        if (r == null) return false;
        giveReward(p, r, "Nhiệm vụ " + q.name());
        return true;
    }

    // ---------- tiến hóa / sao / kỹ năng ----------
    public boolean evolve(Player p, PetData d, int branchIdx) {
        PlayerProfile pr = profile(p); PetTemplate t = tpl(d);
        if (t == null || t.evolutions().isEmpty()) { msg(p, "§cPet này không thể tiến hóa."); return false; }
        if (branchIdx < 0 || branchIdx >= t.evolutions().size()) { msg(p, "§cNhánh tiến hóa không hợp lệ."); return false; }
        EvolutionBranch b = t.evolutions().get(branchIdx);
        EvolutionRules.Result r = EvolutionRules.check(d, b, pr.souls, Inv.counts(p, b.materials().keySet()));
        if (!r.ok()) { msg(p, "§c" + r.reason()); return false; }
        for (Map.Entry<String, Integer> e : b.materials().entrySet()) Inv.take(p, Material.matchMaterial(e.getKey()), e.getValue());
        pr.souls -= b.souls();
        PetTemplate to = pl.data().pets.get(b.to());
        d.templateId = b.to(); d.stage++; d.skillPoints += 2; pr.dex.add(b.to()); pr.count("evolutions", 1);
        afterPetChanged(p, d);
        msg(p, "§d✦ Tiến hóa thành công: " + (to == null ? b.to() : to.coloredName()) + "§d! (+2 điểm kỹ năng)");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.6f);
        quest(p, "EVOLVE", 1);
        return true;
    }
    public boolean starUp(Player p, PetData d) {
        PlayerProfile pr = profile(p);
        int shards = pr.shards.getOrDefault(d.root, 0);
        EvolutionRules.Result r = EvolutionRules.checkStar(d, shards);
        if (!r.ok()) { msg(p, "§c" + r.reason()); return false; }
        pr.shards.put(d.root, shards - EvolutionRules.shardCost(d.star));
        d.star++; pr.count("stars", 1);
        afterPetChanged(p, d);
        msg(p, "§e★ Nâng sao thành công: " + displayName(d) + " §e" + "★".repeat(d.star));
        checkAchievements(p);
        return true;
    }
    public boolean upgradeSkill(Player p, PetData d, String skillId) {
        PetTemplate t = tpl(d); SkillDef def = pl.data().skills.get(skillId);
        if (t == null || def == null || !t.skills().contains(skillId)) return false;
        int lv = SkillMath.levelOf(d, def);
        if (lv <= 0) { msg(p, "§cKỹ năng chưa mở khóa (cần cấp " + def.unlockLevel() + ")."); return false; }
        if (lv >= def.maxLevel()) { msg(p, "§cKỹ năng đã đạt cấp tối đa."); return false; }
        if (d.skillPoints < def.cost()) { msg(p, "§cKhông đủ điểm kỹ năng."); return false; }
        d.skillPoints -= def.cost(); d.skills.put(skillId, lv + 1);
        afterPetChanged(p, d);
        msg(p, "§aNâng §f" + def.name() + " §alên cấp §e" + (lv + 1) + "§a.");
        return true;
    }
}
