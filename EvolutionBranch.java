package vn.soulpets.pet;
import java.util.Map;
public record EvolutionBranch(String to, int minLevel, long souls, int minStar, Map<String, Integer> materials) {}
