package vn.soulpets;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.soulpets.egg.GachaEngine;
import vn.soulpets.rarity.Rarity;

class GachaEngineTest {
    static Map<Rarity, Double> basic() {
        Map<Rarity, Double> w = new EnumMap<>(Rarity.class);
        w.put(Rarity.COMMON, 60.0); w.put(Rarity.UNCOMMON, 25.0); w.put(Rarity.RARE, 10.0); w.put(Rarity.EPIC, 3.5);
        w.put(Rarity.LEGENDARY, 1.2); w.put(Rarity.MYTHIC, 0.28); w.put(Rarity.ANCIENT, 0.02);
        return w;
    }
    @Test void epicGuaranteedWithinHardPity() {
        GachaEngine g = new GachaEngine(GachaEngine.PityConfig.DEFAULT);
        for (long seed = 0; seed < 200; seed++) {
            Random rng = new Random(seed); GachaEngine.PityState st = new GachaEngine.PityState(); int sinceEpic = 0;
            for (int i = 0; i < 300; i++) {
                Rarity r = g.roll(basic(), st, rng);
                if (r.atLeast(Rarity.EPIC)) sinceEpic = 0; else sinceEpic++;
                assertTrue(sinceEpic < 10, "Epic pity vi phạm, seed=" + seed);
            }
        }
    }
    @Test void legendaryGuaranteedWithinHardPity() {
        GachaEngine g = new GachaEngine(GachaEngine.PityConfig.DEFAULT);
        for (long seed = 0; seed < 100; seed++) {
            Random rng = new Random(seed); GachaEngine.PityState st = new GachaEngine.PityState(); int since = 0;
            for (int i = 0; i < 1000; i++) {
                Rarity r = g.roll(basic(), st, rng);
                if (r.atLeast(Rarity.LEGENDARY)) since = 0; else since++;
                assertTrue(since < 90, "Legendary pity vi phạm");
            }
        }
    }
    @Test void pityCountersReset() {
        GachaEngine g = new GachaEngine(new GachaEngine.PityConfig(10, 60, 90, 0.06));
        GachaEngine.PityState st = new GachaEngine.PityState(); st.sinceEpic = 9; st.sinceLegendary = 89;
        Rarity r = g.roll(basic(), st, new Random(1));
        assertTrue(r.atLeast(Rarity.LEGENDARY));
        assertEquals(0, st.sinceEpic); assertEquals(0, st.sinceLegendary);
    }
    @Test void eggWithoutEpicIgnoresPity() {
        Map<Rarity, Double> w = new EnumMap<>(Rarity.class); w.put(Rarity.COMMON, 1.0);
        GachaEngine g = new GachaEngine(GachaEngine.PityConfig.DEFAULT); GachaEngine.PityState st = new GachaEngine.PityState();
        for (int i = 0; i < 50; i++) assertEquals(Rarity.COMMON, g.roll(w, st, new Random(i)));
        assertEquals(0, st.sinceEpic);
    }
    @Test void distributionRoughlyMatchesWeights() {
        GachaEngine g = new GachaEngine(new GachaEngine.PityConfig(0, 0, 0, 0));
        Random rng = new Random(42); int common = 0, n = 100000; GachaEngine.PityState st = new GachaEngine.PityState();
        for (int i = 0; i < n; i++) if (g.roll(basic(), st, rng) == Rarity.COMMON) common++;
        double p = common / (double) n;
        assertTrue(p > 0.58 && p < 0.62, "tỉ lệ Common = " + p);
    }
}
