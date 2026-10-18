package vn.soulpets.skills;
import vn.soulpets.pet.PetData;
public final class SkillMath {
    private SkillMath() {}
    public static double value(SkillDef d, int level) { return d.power() + d.perLevel() * (Math.max(1, level) - 1); }
    /** 0 nếu chưa mở khóa; skill mở khóa tự có cấp 1. */
    public static int levelOf(PetData p, SkillDef d) {
        if (p.level < d.unlockLevel()) return 0;
        return Math.min(d.maxLevel(), Math.max(1, p.skills.getOrDefault(d.id(), 1)));
    }
}
