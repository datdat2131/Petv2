package vn.soulpets.combat;
import java.util.Random;
import vn.soulpets.pet.Role;

public final class DamageCalc {
    private DamageCalc() {}
    public static final double CRIT_MULT = 1.5;
    public static double hit(double atk, double skillMult, double elemMult, double critChance, Random rng) {
        double d = atk * skillMult * elemMult;
        if (critChance > 0 && rng.nextDouble() < critChance) d *= CRIT_MULT;
        return Math.max(0, d);
    }
    /** Tỉ lệ giảm sát thương chủ nhận được nhờ pet (tối đa 60%). */
    public static double ownerReduction(double petDef, Role role, double passiveReduction) {
        double base = petDef / (petDef + 50.0) * (role == Role.TANK ? 1.0 : 0.4);
        return Math.min(0.6, base + passiveReduction);
    }
    /** Khiên hấp thụ trước; trả về sát thương còn lại, cập nhật khiên qua mảng 1 phần tử. */
    public static double absorb(double[] shield, double dmg) {
        double a = Math.min(shield[0], dmg);
        shield[0] -= a;
        return dmg - a;
    }
}
