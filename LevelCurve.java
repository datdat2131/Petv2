package vn.soulpets.pet;

public final class LevelCurve {
    private LevelCurve() {}
    public static long expToNext(int level) { return Math.round(50 + 6 * Math.pow(Math.max(1, level), 1.9)); }
    public static int maxLevel(int stage, int base, int perStage) { return base + perStage * Math.max(0, stage); }
    /** Cộng EXP, trả về số cấp tăng. Ở cấp tối đa EXP được giữ ở 0. */
    public static int addExp(PetData p, long amount, int maxLevel) {
        if (amount <= 0 || p.level >= maxLevel) { if (p.level >= maxLevel) p.exp = 0; return 0; }
        p.exp += amount;
        int gained = 0;
        while (p.level < maxLevel && p.exp >= expToNext(p.level)) { p.exp -= expToNext(p.level); p.level++; p.skillPoints++; gained++; }
        if (p.level >= maxLevel) p.exp = 0;
        return gained;
    }
}
