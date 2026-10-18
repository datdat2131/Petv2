package vn.soulpets.evolution;
import java.util.Map;
import vn.soulpets.pet.EvolutionBranch;
import vn.soulpets.pet.PetData;

public final class EvolutionRules {
    private EvolutionRules() {}
    public static final int MAX_STAR = 5;
    public record Result(boolean ok, String reason) {}
    public static Result check(PetData p, EvolutionBranch b, long souls, Map<String, Integer> inventory) {
        if (p.level < b.minLevel()) return new Result(false, "Cần pet cấp " + b.minLevel());
        if (p.star < b.minStar()) return new Result(false, "Cần " + b.minStar() + " sao");
        if (souls < b.souls()) return new Result(false, "Cần " + b.souls() + " Linh Hồn");
        for (Map.Entry<String, Integer> e : b.materials().entrySet())
            if (inventory.getOrDefault(e.getKey(), 0) < e.getValue()) return new Result(false, "Thiếu " + e.getValue() + "x " + e.getKey());
        return new Result(true, "");
    }
    public static int shardCost(int currentStar) { return (currentStar + 1) * 5; }
    public static Result checkStar(PetData p, int shards) {
        if (p.star >= MAX_STAR) return new Result(false, "Đã đạt sao tối đa");
        if (shards < shardCost(p.star)) return new Result(false, "Cần " + shardCost(p.star) + " mảnh (có " + shards + ")");
        return new Result(true, "");
    }
}
