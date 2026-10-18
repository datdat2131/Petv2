package vn.soulpets;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.soulpets.egg.*;
import vn.soulpets.equipment.*;
import vn.soulpets.evolution.EvolutionRules;
import vn.soulpets.pet.*;
import vn.soulpets.quest.*;
import vn.soulpets.rarity.Rarity;
import vn.soulpets.reward.Reward;

class ProgressionTest {
    static PetData pet(int level, int star) { PetData p = PetData.create("t", level); p.star = star; return p; }
    @Test void evolutionRequirements() {
        EvolutionBranch b = new EvolutionBranch("x_king", 20, 300, 1, Map.of("BLAZE_ROD", 8));
        assertFalse(EvolutionRules.check(pet(10, 1), b, 1000, Map.of("BLAZE_ROD", 8)).ok());
        assertFalse(EvolutionRules.check(pet(20, 0), b, 1000, Map.of("BLAZE_ROD", 8)).ok());
        assertFalse(EvolutionRules.check(pet(20, 1), b, 100, Map.of("BLAZE_ROD", 8)).ok());
        assertFalse(EvolutionRules.check(pet(20, 1), b, 1000, Map.of("BLAZE_ROD", 7)).ok());
        assertTrue(EvolutionRules.check(pet(20, 1), b, 1000, Map.of("BLAZE_ROD", 8)).ok());
    }
    @Test void starUpRules() {
        assertEquals(5, EvolutionRules.shardCost(0)); assertEquals(25, EvolutionRules.shardCost(4));
        assertFalse(EvolutionRules.checkStar(pet(1, 0), 4).ok()); assertTrue(EvolutionRules.checkStar(pet(1, 0), 5).ok());
        assertFalse(EvolutionRules.checkStar(pet(1, EvolutionRules.MAX_STAR), 999).ok());
    }
    @Test void eggRollerRespectsElementAndFallsBack() {
        PetTemplate fireCommon = new PetTemplate("a", "A", Element.FIRE, Rarity.COMMON, Role.DPS, "FOX", true, "", Stats.ZERO, List.of(), List.of(), "");
        PetTemplate iceCommon = new PetTemplate("b", "B", Element.ICE, Rarity.COMMON, Role.DPS, "FOX", true, "", Stats.ZERO, List.of(), List.of(), "");
        PetTemplate hidden = new PetTemplate("c", "C", Element.FIRE, Rarity.EPIC, Role.DPS, "FOX", false, "", Stats.ZERO, List.of(), List.of(), "");
        EggType fire = new EggType("f", "F", "X", 1, 1, Element.FIRE, Map.of());
        for (int i = 0; i < 20; i++) assertEquals("a", EggRoller.pick(fire, Rarity.COMMON, List.of(fireCommon, iceCommon), new Random(i)).id());
        assertEquals("a", EggRoller.pick(fire, Rarity.ANCIENT, List.of(fireCommon, iceCommon, hidden), new Random(1)).id());
        assertNull(EggRoller.pick(fire, Rarity.COMMON, List.of(), new Random(1)));
    }
    @Test void equipmentUpgradeMath() {
        EquipmentDef d = new EquipmentDef("w", "W", "WEAPON", Rarity.COMMON, "STICK", new Stats(0, 10, 0, 0), 0, 10, 40, Map.of("IRON_INGOT", 2));
        assertEquals(10, EquipmentMath.bonus(d, 0).atk(), 1e-9); assertEquals(15, EquipmentMath.bonus(d, 5).atk(), 1e-9);
        assertEquals(40, EquipmentMath.soulCost(d, 0)); assertEquals(120, EquipmentMath.soulCost(d, 2)); assertEquals(6, EquipmentMath.materialCount(d, "IRON_INGOT", 2));
    }
    static List<QuestDef> quests() {
        return List.of(new QuestDef("dk", "K", "KILL_MOB", 3, true, new Reward(50, 0, Map.of(), List.of())),
                       new QuestDef("wk", "WK", "KILL_MOB", 5, false, Reward.NONE));
    }
    @Test void questProgressCompletesOnceAndClaimsOnce() {
        PlayerProfile p = new PlayerProfile(UUID.randomUUID()); LocalDate d = LocalDate.of(2026, 10, 14);
        assertTrue(QuestService.progress(p, quests(), "KILL_MOB", 2, d).isEmpty());
        List<QuestDef> done = QuestService.progress(p, quests(), "KILL_MOB", 5, d);
        assertEquals(2, done.size()); assertEquals(3, p.quest.get("dk")); assertEquals(5, p.quest.get("wk"));
        assertTrue(QuestService.progress(p, quests(), "KILL_MOB", 5, d).isEmpty());
        QuestDef q = quests().get(0);
        assertNotNull(QuestService.claim(p, q)); assertNull(QuestService.claim(p, q));
    }
    @Test void dailyResetsAtNewDayWeeklyAtNewWeek() {
        PlayerProfile p = new PlayerProfile(UUID.randomUUID());
        LocalDate wed = LocalDate.of(2026, 10, 14), thu = wed.plusDays(1), nextMon = LocalDate.of(2026, 10, 19);
        QuestService.progress(p, quests(), "KILL_MOB", 5, wed); QuestService.claim(p, quests().get(0));
        QuestService.refresh(p, quests(), thu);
        assertNull(p.quest.get("dk")); assertFalse(p.questClaimed.contains("dk")); assertEquals(5, p.quest.get("wk"));
        QuestService.refresh(p, quests(), nextMon);
        assertNull(p.quest.get("wk"));
    }
    @Test void achievementsUnlockOnceAndIncludeDex() {
        List<AchievementDef> defs = List.of(new AchievementDef("k", "K", "kills", 100, Reward.NONE, ""), new AchievementDef("d", "D", "dex", 2, Reward.NONE, ""));
        PlayerProfile p = new PlayerProfile(UUID.randomUUID()); p.count("kills", 99);
        assertTrue(QuestService.checkAchievements(p, defs).isEmpty());
        p.count("kills", 1); p.dex.add("a"); p.dex.add("b");
        assertEquals(2, QuestService.checkAchievements(p, defs).size());
        assertTrue(QuestService.checkAchievements(p, defs).isEmpty());
    }
    @Test void rewardDescribe() {
        assertTrue(new Reward(10, 0, Map.of("basic_egg", 1), List.of()).describe().contains("basic_egg"));
        assertTrue(Reward.NONE.describe().contains("không có"));
    }
}
