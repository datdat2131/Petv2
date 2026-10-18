package vn.soulpets;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.soulpets.combat.*;
import vn.soulpets.pet.Role;
import vn.soulpets.skills.*;
import vn.soulpets.pet.*;

class CombatTest {
    @Test void cooldownBlocksUntilReady() {
        Cooldowns<String> c = new Cooldowns<>();
        assertTrue(c.tryUse("a", 1000, 500)); assertFalse(c.tryUse("a", 1200, 500)); assertEquals(300, c.remaining("a", 1200));
        assertTrue(c.tryUse("a", 1500, 500)); assertTrue(c.tryUse("b", 1000, 500));
        c.purge(10_000); assertEquals(0, c.size());
    }
    @Test void spamLimiterAllowsOnePerInterval() {
        Cooldowns<UUID> lim = new Cooldowns<>(); UUID u = UUID.randomUUID(); int allowed = 0;
        for (long t = 0; t < 1000; t += 10) if (lim.tryUse(u, t, 250)) allowed++;
        assertEquals(4, allowed);
    }
    @Test void targetSelectorPrefersThreatsToOwner() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID(), c = UUID.randomUUID();
        var near = new TargetSelector.Candidate(a, 4, 20, false, false);
        var threat = new TargetSelector.Candidate(b, 36, 20, true, false);
        var boss = new TargetSelector.Candidate(c, 25, 300, false, true);
        assertEquals(b, TargetSelector.select(List.of(near, threat, boss), Role.DPS).id());
        assertEquals(c, TargetSelector.select(List.of(near, boss), Role.DPS).id()); // boss được ưu tiên khi không ai đe dọa chủ
        assertNull(TargetSelector.select(List.of(), Role.DPS));
    }
    @Test void dpsPrefersLowHealth() {
        UUID a = UUID.randomUUID(), b = UUID.randomUUID();
        var weak = new TargetSelector.Candidate(a, 16, 1, false, false);
        var strong = new TargetSelector.Candidate(b, 16, 200, false, false);
        assertEquals(a, TargetSelector.select(List.of(strong, weak), Role.DPS).id());
    }
    @Test void damageAndCrit() {
        assertEquals(26, DamageCalc.hit(10, 2.0, 1.3, 0, new Random(1)), 1e-9);
        assertEquals(39, DamageCalc.hit(10, 2.0, 1.3, 1.0, new Random(1)), 1e-9);
    }
    @Test void reductionCappedAndTankBetter() {
        assertTrue(DamageCalc.ownerReduction(40, Role.TANK, 0) > DamageCalc.ownerReduction(40, Role.DPS, 0));
        assertEquals(0.6, DamageCalc.ownerReduction(100000, Role.TANK, 0.5), 1e-9);
    }
    @Test void shieldAbsorbs() {
        double[] s = {10}; assertEquals(5, DamageCalc.absorb(s, 15), 1e-9); assertEquals(0, s[0], 1e-9);
        double[] t = {10}; assertEquals(0, DamageCalc.absorb(t, 4), 1e-9); assertEquals(6, t[0], 1e-9);
    }
    @Test void passiveAggregationRespectsUnlockAndLevel() {
        Map<String, SkillDef> defs = new HashMap<>();
        defs.put("atk", new SkillDef("atk", "A", false, "ATK_PCT", null, 0.05, 0.025, 0, 0, 5, 5, 1, "", 0, "", ""));
        defs.put("crit", new SkillDef("crit", "C", false, "CRIT", null, 0.5, 0.2, 0, 0, 1, 5, 1, "", 0, "", ""));
        defs.put("act", new SkillDef("act", "X", true, "HEAL", null, 1, 0, 5, 5, 1, 5, 1, "", 0, "", ""));
        PetData p = PetData.create("t", 3);
        Passives lowLevel = Passives.aggregate(p, List.of("atk", "crit", "act"), defs);
        assertEquals(0, lowLevel.atkPct(), 1e-9); assertEquals(0.5, lowLevel.crit(), 1e-9);
        p.level = 10; p.skills.put("atk", 3);
        Passives hi = Passives.aggregate(p, List.of("atk", "crit"), defs);
        assertEquals(0.10, hi.atkPct(), 1e-9);
        p.skills.put("crit", 5);
        assertEquals(0.75, Passives.aggregate(p, List.of("crit"), defs).crit(), 1e-9);
    }
    @Test void skillLevelCappedAtMax() {
        SkillDef d = new SkillDef("s", "S", true, "HEAL", null, 1, 0.5, 5, 5, 1, 3, 1, "", 0, "", "");
        PetData p = PetData.create("t", 5); p.skills.put("s", 99);
        assertEquals(3, SkillMath.levelOf(p, d)); assertEquals(2.0, SkillMath.value(d, 3), 1e-9);
    }
}
