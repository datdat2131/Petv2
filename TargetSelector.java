package vn.soulpets.combat;
import java.util.List;
import java.util.UUID;
import vn.soulpets.pet.Role;

/** Chọn mục tiêu thông minh: ưu tiên kẻ đang tấn công chủ, boss, máu thấp (DPS), gần. */
public final class TargetSelector {
    private TargetSelector() {}
    public record Candidate(UUID id, double distSq, double health, boolean targetsOwner, boolean boss) {}
    public static double score(Candidate c, Role role) {
        double s = (c.targetsOwner() ? 100 : 0) + (c.boss() ? 25 : 0) - Math.sqrt(c.distSq()) * 3;
        if (role == Role.DPS) s += 30.0 / (1 + c.health());
        if (role == Role.TANK && c.targetsOwner()) s += 20;
        return s;
    }
    public static Candidate select(List<Candidate> list, Role role) {
        Candidate best = null; double bs = Double.NEGATIVE_INFINITY;
        for (Candidate c : list) { double s = score(c, role); if (s > bs) { bs = s; best = c; } }
        return best;
    }
}
