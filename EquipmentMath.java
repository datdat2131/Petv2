package vn.soulpets.equipment;
import vn.soulpets.pet.Stats;
public final class EquipmentMath {
    private EquipmentMath() {}
    public static Stats bonus(EquipmentDef d, int upgrade) { return d.stats().scale(1 + 0.10 * Math.max(0, upgrade)); }
    public static long soulCost(EquipmentDef d, int currentUpgrade) { return d.upgradeSouls() * (currentUpgrade + 1L); }
    public static int materialCount(EquipmentDef d, String mat, int currentUpgrade) { return d.upgradeMaterials().getOrDefault(mat, 0) * (currentUpgrade + 1); }
}
