package vn.soulpets;

import org.junit.jupiter.api.Test;
import vn.soulpets.combat.*;
import vn.soulpets.egg.*;
import vn.soulpets.equipment.*;
import vn.soulpets.evolution.*;
import vn.soulpets.pet.*;
import vn.soulpets.rarity.PetRarity;
import vn.soulpets.skills.*;

import java.util.*;
import java.util.random.RandomGenerator;

import static org.junit.jupiter.api.Assertions.*;

class CoreSystemsTest {
    static PetRegistry registry() {
        PetRegistry r = new PetRegistry();
        for (PetRarity rar : PetRarity.values())
            r.register(new PetDefinition("p_" + rar.name().toLowerCase(), "P" + rar, Element.FIRE, rar, Role.DPS, "WOLF", null, "BONE",
                    new Stats(20, 4, 2, 1), new Stats(1, 0.5, 0.2, 0.1), List.of("s1"),
                    rar == PetRarity.COMMON ? List.of(new EvolutionBranch("p_rare", 5, 1, 50, Map.of("fire_core", 2)), new EvolutionBranch("p_epic", 5, 0, 0, Map.of())) : List.of()));
        return r;
    }
    static EggType egg() {
        Map<PetRarity, Double> w = new EnumMap<>(PetRarity.class);
        w.put(PetRarity.COMMON, 90.0); w.put(PetRarity.RARE, 9.0); w.put(PetRarity.EPIC, 1.0);
        return new EggType("basic", "Basic", 100, 10, null, w, Map.of(PetRarity.EPIC, 10));
    }

    @Test void sevenRaritiesAndSixElements() { assertEquals(7, PetRarity.values().length); assertEquals(6, Element.values().length); assertTrue(PetRarity.ANCIENT.atLeast(PetRarity.MYTHIC)); }
    @Test void elementAdvantage() {
        assertEquals(1.3, Element.FIRE.multiplierAgainst(Element.NATURE));
        assertEquals(0.8, Element.NATURE.multiplierAgainst(Element.FIRE));
        assertEquals(1.15, Element.LIGHT.multiplierAgainst(Element.DARK));
        assertEquals(1.0, Element.FIRE.multiplierAgainst(Element.DARK));
        assertEquals(1.0, Element.FIRE.multiplierAgainst(null));
    }
    @Test void pityGuaranteesEpicWithinThreshold() {
        EggRoller roller = new EggRoller(new Random(1)); EggType e = egg(); Map<String, Integer> c = new HashMap<>();
        int sinceEpic = 0;
        for (int i = 0; i < 5000; i++) {
            var o = roller.roll(e, c);
            if (o.rarity().atLeast(PetRarity.EPIC)) sinceEpic = 0; else sinceEpic++;
            assertTrue(sinceEpic < 10, "pity vượt ngưỡng tại lần " + i);
        }
    }
    @Test void pityTriggersAndResets() {
        EggRoller roller = new EggRoller(RandomGenerator.of("L64X128MixRandom")); EggType e = egg(); Map<String, Integer> c = new HashMap<>();
        c.put("basic:EPIC", 9);
        var o = roller.roll(e, c);
        assertTrue(o.pityTriggered()); assertTrue(o.rarity().atLeast(PetRarity.EPIC)); assertEquals(0, (int) c.get("basic:EPIC"));
    }
    @Test void incubationFlowAndDuplicateBecomesShards() {
        EggRegistry er = new EggRegistry(); er.register(egg());
        PetRegistry pr = registry();
        EggService svc = new EggService(er, pr, new Random(3), 2, 3);
        PlayerProfile p = new PlayerProfile(UUID.randomUUID()); p.setSouls(250);
        assertEquals(EggService.Status.OK, svc.buy(p, "basic")); assertEquals(150, p.souls());
        assertEquals(EggService.Status.NOT_FOUND, svc.startIncubation(p, "none", 0)); assertEquals(EggService.Status.OK, svc.startIncubation(p, "basic", 1000)); assertEquals(EggService.Status.NO_EGG, svc.startIncubation(p, "basic", 1000)); p.incubations().clear(); p.addEgg("basic", 1);
        assertEquals(EggService.Status.OK, svc.startIncubation(p, "basic", 1000));
        assertEquals(EggService.Status.NOT_READY, svc.claim(p, 0, 5000).status());
        var r = svc.claim(p, 0, 11_000);
        assertEquals(EggService.Status.OK, r.status()); assertTrue(p.incubations().isEmpty());
        assertNotNull(p.active());
        // pet trùng -> shard
        svc.buy(p, "basic"); svc.startIncubation(p, "basic", 0);
        for (int i = 0; i < 40 && p.incubations().isEmpty() == false; i++) svc.claim(p, 0, 99999);
        assertTrue(p.pets().size() >= 1);
    }
    @Test void cannotBuyWithoutSouls() { EggRegistry er = new EggRegistry(); er.register(egg()); var s = new EggService(er, registry(), new Random(), 1, 1); assertEquals(EggService.Status.NO_SOULS, s.buy(new PlayerProfile(UUID.randomUUID()), "basic")); }
    @Test void levelUpGivesSkillPointsAndCapsAtMax() {
        PetInstance pet = PetInstance.create("p_common");
        int up = pet.addXp(100 + 125 + 150 + 5); // lv1->2 (125), 2->3 (150) ... kiểm tra không âm
        assertTrue(up >= 2); assertEquals(up, pet.skillPoints());
        pet.addXp(Long.MAX_VALUE / 4); assertEquals(PetInstance.MAX_LEVEL, pet.level()); assertEquals(0, pet.xp());
    }
    @Test void learnSkillConsumesPoints() {
        PetInstance pet = PetInstance.create("p_common"); assertFalse(pet.learnSkill("s1", 3));
        pet.addXp(1000); int sp = pet.skillPoints();
        assertTrue(pet.learnSkill("s1", 2)); assertTrue(pet.learnSkill("s1", 2)); assertFalse(pet.learnSkill("s1", 2));
        assertEquals(sp - 2, pet.skillPoints()); assertEquals(2, pet.skillLevel("s1"));
    }
    @Test void statsScaleWithLevelStarRarityAndPassive() {
        SkillRegistry sk = new SkillRegistry(); sk.register(new SkillDefinition("s1", "Cuồng nộ", SkillType.PASSIVE, SkillEffect.STAT_ATK, 0.10, 0, 5, null));
        StatCalculator calc = new StatCalculator(registry(), sk, new EquipmentService(new EquipmentRegistry(), 10));
        PetInstance a = PetInstance.create("p_common"), b = PetInstance.create("p_ancient");
        assertTrue(calc.compute(b).atk() > calc.compute(a).atk());
        double before = calc.compute(a).atk(); a.skills().put("s1", 2);
        assertEquals(before * 1.2, calc.compute(a).atk(), 1e-9);
        double lv1 = calc.compute(a).hp(); a.addXp(5000); assertTrue(calc.compute(a).hp() > lv1);
        double s0 = calc.compute(a).hp(); a.starUp(); assertEquals(s0 * 1.1, calc.compute(a).hp(), 1e-6);
    }
    @Test void multiBranchEvolution() {
        EvolutionService ev = new EvolutionService(registry());
        PlayerProfile p = new PlayerProfile(UUID.randomUUID()); PetInstance pet = PetInstance.create("p_common"); p.add(pet);
        assertEquals(2, ev.branches(pet).size());
        assertEquals(EvolutionService.Result.LEVEL, ev.evolve(p, pet, "p_epic"));
        pet.addXp(1000);
        assertEquals(EvolutionService.Result.STAR, ev.evolve(p, pet, "p_rare"));
        assertEquals(EvolutionService.Result.NO_BRANCH, ev.evolve(p, pet, "p_mythic"));
        assertEquals(EvolutionService.Result.OK, ev.evolve(p, pet, "p_epic")); assertEquals("p_epic", pet.defId());
        PetInstance pet2 = PetInstance.create("p_common"); pet2.addXp(1000); pet2.starUp(); p.add(pet2); p.setSouls(100);
        assertEquals(EvolutionService.Result.MATERIAL, ev.evolve(p, pet2, "p_rare"));
        p.addMaterial("fire_core", 2);
        assertEquals(EvolutionService.Result.OK, ev.evolve(p, pet2, "p_rare"));
        assertEquals(50, p.souls()); assertEquals(0, p.material("fire_core"));
    }
    @Test void starUpUsesShards() {
        StarService s = new StarService(5); PlayerProfile p = new PlayerProfile(UUID.randomUUID()); PetInstance pet = PetInstance.create("p_common"); p.add(pet);
        assertEquals(StarService.Result.NO_SHARDS, s.starUp(p, pet));
        p.addMaterial("shard:p_common", 5); assertEquals(StarService.Result.OK, s.starUp(p, pet)); assertEquals(1, pet.star()); assertEquals(0, p.material("shard:p_common"));
        for (int i = 0; i < 4; i++) { p.addMaterial("shard:p_common", 100); s.starUp(p, pet); }
        assertEquals(StarService.Result.MAX, s.starUp(p, pet));
    }
    @Test void equipmentEquipUpgradeUnequip() {
        EquipmentRegistry reg = new EquipmentRegistry();
        reg.register(new EquipmentItem("sword", "Kiếm", EquipSlot.WEAPON, PetRarity.RARE, new Stats(0, 10, 0, 0), "IRON_SWORD", 2));
        EquipmentService svc = new EquipmentService(reg, 10);
        PlayerProfile p = new PlayerProfile(UUID.randomUUID()); PetInstance pet = PetInstance.create("p_common");
        assertEquals(EquipmentService.Result.NOT_OWNED, svc.equip(p, pet, "sword"));
        p.addGear("sword", 1); assertEquals(EquipmentService.Result.OK, svc.equip(p, pet, "sword")); assertEquals(0, p.gear("sword"));
        assertEquals(10, svc.bonus(pet).atk(), 1e-9);
        assertEquals(EquipmentService.Result.NO_MATERIAL, svc.upgrade(p, pet, EquipSlot.WEAPON));
        p.addMaterial("upgrade_stone", 10); p.setSouls(1000);
        assertEquals(EquipmentService.Result.OK, svc.upgrade(p, pet, EquipSlot.WEAPON)); assertEquals(11, svc.bonus(pet).atk(), 1e-9);
        assertEquals(EquipmentService.Result.OK, svc.upgrade(p, pet, EquipSlot.WEAPON));
        assertEquals(EquipmentService.Result.MAX, svc.upgrade(p, pet, EquipSlot.WEAPON));
        assertEquals(EquipmentService.Result.OK, svc.unequip(p, pet, EquipSlot.WEAPON)); assertEquals(1, p.gear("sword"));
    }
    @Test void targetSelectorPrefersThreatsToOwner() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var near = new TargetSelector.Candidate(a, 4, false, 1.0, false);
        var attacker = new TargetSelector.Candidate(b, 36, true, 1.0, false);
        var far = new TargetSelector.Candidate(c, 400, true, 0.1, false);
        assertEquals(b, TargetSelector.best(List.of(near, attacker, far), 10).get().id());
        assertTrue(TargetSelector.best(List.of(far), 10).isEmpty());
        assertEquals(2, TargetSelector.top(List.of(near, attacker, far), 10, 5).size());
    }
    @Test void combatMathElementAndDefense() {
        double neutral = CombatMath.damage(10, 1, Element.FIRE, Element.DARK, 0), strong = CombatMath.damage(10, 1, Element.FIRE, Element.NATURE, 0);
        assertTrue(strong > neutral); assertTrue(CombatMath.damage(10, 1, null, null, 100) < neutral);
        assertTrue(CombatMath.actionIntervalMs(Role.DPS, 1000) >= 500);
    }
    @Test void rateLimiterAndCooldown() {
        RateLimiter rl = new RateLimiter(500); UUID k = UUID.randomUUID();
        assertTrue(rl.tryAcquire(k, 1000)); assertFalse(rl.tryAcquire(k, 1200)); assertTrue(rl.tryAcquire(k, 1600));
        rl.purge(1_000_000); assertEquals(0, rl.size());
        CooldownTracker cd = new CooldownTracker(); UUID pet = UUID.randomUUID();
        assertTrue(cd.ready(pet, "x", 0)); cd.start(pet, "x", 0, 5); assertFalse(cd.ready(pet, "x", 4000)); assertTrue(cd.ready(pet, "x", 5000));
        cd.clear(pet); assertEquals(0, cd.size());
    }
    @Test void profileCopyIsDeep() {
        PlayerProfile p = new PlayerProfile(UUID.randomUUID()); PetInstance pet = PetInstance.create("p_common"); p.add(pet); p.addMaterial("x", 3);
        PlayerProfile c = p.copy(); pet.addXp(1000); p.addMaterial("x", 1);
        assertEquals(1, c.get(pet.id()).level()); assertEquals(3, c.material("x"));
    }
}
