package vn.soulpets.egg;

import java.util.Map;
import java.util.Random;
import vn.soulpets.rarity.Rarity;

/** Quay độ hiếm có pity: đảm bảo >= Epic sau epicHard lần, >= Legendary sau legendHard lần, tăng tỉ lệ mềm từ legendSoft. */
public final class GachaEngine {
    public static final class PityState { public int sinceEpic, sinceLegendary; }
    public record PityConfig(int epicHard, int legendSoft, int legendHard, double softBoost) {
        public static final PityConfig DEFAULT = new PityConfig(10, 60, 90, 0.06);
    }
    private final PityConfig cfg;
    public GachaEngine(PityConfig cfg) { this.cfg = cfg; }

    public Rarity roll(Map<Rarity, Double> weights, PityState st, Random rng) {
        boolean pityApplies = false;
        for (Rarity r : Rarity.values()) if (r.atLeast(Rarity.EPIC) && weights.getOrDefault(r, 0.0) > 0) pityApplies = true;
        int nextEpic = st.sinceEpic + 1, nextLeg = st.sinceLegendary + 1;
        Rarity r;
        if (pityApplies && cfg.legendHard() > 0 && nextLeg >= cfg.legendHard() && hasWeight(weights, Rarity.LEGENDARY)) r = pick(weights, Rarity.LEGENDARY, 1.0, rng);
        else if (pityApplies && cfg.epicHard() > 0 && nextEpic >= cfg.epicHard()) r = pick(weights, Rarity.EPIC, 1.0, rng);
        else {
            double boost = pityApplies && nextLeg > cfg.legendSoft() ? 1 + cfg.softBoost() * (nextLeg - cfg.legendSoft()) : 1.0;
            r = pick(weights, null, boost, rng);
        }
        if (pityApplies) {
            if (r.atLeast(Rarity.LEGENDARY)) { st.sinceEpic = 0; st.sinceLegendary = 0; }
            else if (r.atLeast(Rarity.EPIC)) { st.sinceEpic = 0; st.sinceLegendary++; }
            else { st.sinceEpic++; st.sinceLegendary++; }
        }
        return r;
    }
    private static boolean hasWeight(Map<Rarity, Double> w, Rarity min) {
        for (Rarity r : Rarity.values()) if (r.atLeast(min) && w.getOrDefault(r, 0.0) > 0) return true;
        return false;
    }
    private static Rarity pick(Map<Rarity, Double> w, Rarity min, double boost, Random rng) {
        double total = 0;
        for (Rarity r : Rarity.values()) total += eff(w, r, min, boost);
        if (total <= 0) return min != null ? min : Rarity.COMMON;
        double x = rng.nextDouble() * total;
        for (Rarity r : Rarity.values()) { x -= eff(w, r, min, boost); if (x < 0 && eff(w, r, min, boost) > 0) return r; }
        return min != null ? min : Rarity.COMMON;
    }
    private static double eff(Map<Rarity, Double> w, Rarity r, Rarity min, double boost) {
        if (min != null && r.ordinal() < min.ordinal()) return 0;
        double v = w.getOrDefault(r, 0.0);
        return r.atLeast(Rarity.LEGENDARY) ? v * boost : v;
    }
}
