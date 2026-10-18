package vn.soulpets;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.soulpets.pet.*;
import vn.soulpets.rarity.Rarity;

class PetSystemTest {
    static PetTemplate tpl(Rarity r) { return new PetTemplate("t", "T", Element.FIRE, r, Role.DPS, "FOX", true, "", new Stats(30, 6, 2, 1.0), List.of(), List.of(), ""); }
    @Test void levelUpMultipleLevelsAndCap() {
        PetData p = PetData.create("t", 1);
        int g = LevelCurve.addExp(p, 100000, 10);
        assertEquals(10, p.level); assertEquals(9, g); assertEquals(9, p.skillPoints); assertEquals(0, p.exp);
        assertEquals(0, LevelCurve.addExp(p, 500, 10));
    }
    @Test void expCarriesOver() {
        PetData p = PetData.create("t", 1);
        long need = LevelCurve.expToNext(1);
        assertEquals(1, LevelCurve.addExp(p, need + 5, 50));
        assertEquals(2, p.level); assertEquals(5, p.exp);
    }
    @Test void statsGrowWithLevelStarStageAndRarity() {
        PetTemplate c = tpl(Rarity.COMMON);
        assertTrue(StatCalculator.base(c, 10, 0, 0).atk() > StatCalculator.base(c, 1, 0, 0).atk());
        assertTrue(StatCalculator.base(c, 1, 3, 0).hp() > StatCalculator.base(c, 1, 0, 0).hp());
        assertTrue(StatCalculator.base(c, 1, 0, 1).def() > StatCalculator.base(c, 1, 0, 0).def());
        assertTrue(StatCalculator.base(tpl(Rarity.ANCIENT), 1, 0, 0).hp() > StatCalculator.base(c, 1, 0, 0).hp());
    }
    @Test void speedIsCapped() {
        Stats s = StatCalculator.finalStats(new Stats(10, 1, 1, 1.9), Stats.ZERO, 0, 0, 0, 5.0);
        assertEquals(StatCalculator.MAX_SPEED, s.speed(), 1e-9);
    }
    @Test void passivesApplyPercentages() {
        Stats s = StatCalculator.finalStats(new Stats(100, 10, 10, 1.0), new Stats(10, 5, 0, 0), 0.1, 0.2, 0, 0);
        assertEquals(121, s.hp(), 1e-9); assertEquals(18, s.atk(), 1e-9);
    }
    @Test void elementMatrix() {
        assertEquals(1.3, Element.FIRE.multiplierAgainst(Element.ICE), 1e-9);
        assertEquals(0.8, Element.ICE.multiplierAgainst(Element.FIRE), 1e-9);
        assertEquals(1.3, Element.LIGHT.multiplierAgainst(Element.DARK), 1e-9);
        assertEquals(1.3, Element.DARK.multiplierAgainst(Element.LIGHT), 1e-9);
        assertEquals(1.0, Element.FIRE.multiplierAgainst(null), 1e-9);
        assertEquals(1.0, Element.FIRE.multiplierAgainst(Element.FIRE), 1e-9);
    }
    @Test void mobElements() {
        assertEquals(Element.FIRE, MobElements.of("BLAZE")); assertEquals(Element.DARK, MobElements.of("ZOMBIE")); assertNull(MobElements.of("COW"));
    }
    @Test void rarityOrdering() {
        assertTrue(Rarity.ANCIENT.atLeast(Rarity.MYTHIC)); assertFalse(Rarity.COMMON.atLeast(Rarity.RARE));
        assertEquals(7, Rarity.values().length); assertEquals(Rarity.EPIC, Rarity.parse("epic", null)); assertEquals(Rarity.COMMON, Rarity.parse("zzz", Rarity.COMMON));
    }
}
