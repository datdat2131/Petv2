package vn.soulpets.equipment;
import java.util.Map;
import vn.soulpets.pet.Stats;
import vn.soulpets.rarity.Rarity;
public record EquipmentDef(String id, String name, String slot, Rarity rarity, String material, Stats stats, long shopCost, int maxUpgrade,
                           long upgradeSouls, Map<String, Integer> upgradeMaterials) {}
