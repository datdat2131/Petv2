package vn.soulpets.egg;
import java.util.Map;
import vn.soulpets.pet.Element;
import vn.soulpets.rarity.Rarity;
public record EggType(String id, String name, String icon, long cost, long hatchSeconds, Element element, Map<Rarity, Double> weights) {}
