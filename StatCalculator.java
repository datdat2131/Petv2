package vn.soulpets.pet;

public final class StatCalculator {
    private StatCalculator() {}
    public static final double MAX_SPEED = 2.0;
    /** Chỉ số gốc theo cấp / sao / giai đoạn tiến hóa. */
    public static Stats base(PetTemplate t, int level, int star, int stage) {
        double m = t.rarity().statMult() * (1 + 0.07 * (Math.max(1, level) - 1)) * (1 + 0.10 * star) * (1 + 0.20 * stage);
        double spd = t.base().speed() * (1 + 0.003 * (Math.max(1, level) - 1)) * (1 + 0.02 * star);
        return new Stats(t.base().hp() * m, t.base().atk() * m, t.base().def() * m, spd);
    }
    /** Cộng trang bị (flat) rồi nhân phần trăm từ bị động. */
    public static Stats finalStats(Stats base, Stats equipFlat, double hpPct, double atkPct, double defPct, double speedPct) {
        Stats s = base.plus(equipFlat);
        return new Stats(s.hp() * (1 + hpPct), s.atk() * (1 + atkPct), s.def() * (1 + defPct), Math.min(MAX_SPEED, s.speed() * (1 + speedPct)));
    }
    public static long power(Stats s) { return Math.round(s.hp() / 10 + s.atk() * 2 + s.def() * 1.5 + s.speed() * 50); }
}
