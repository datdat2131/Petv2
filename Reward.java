package vn.soulpets.reward;
import java.util.*;

public record Reward(long souls, long petExp, Map<String, Integer> eggs, List<String> equipment) {
    public static final Reward NONE = new Reward(0, 0, Map.of(), List.of());
    public String describe() {
        List<String> p = new ArrayList<>();
        if (souls > 0) p.add("§e" + souls + " Linh Hồn");
        if (petExp > 0) p.add("§a" + petExp + " EXP pet");
        eggs.forEach((k, v) -> p.add("§d" + v + "x " + k));
        for (String e : equipment) p.add("§b" + e);
        return p.isEmpty() ? "§7(không có)" : String.join("§7, ", p);
    }
}
