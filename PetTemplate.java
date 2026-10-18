package vn.soulpets.pet;
import java.util.List;
import vn.soulpets.rarity.Rarity;
/** Cấu hình một loại pet (đọc từ pets.yml). */
public record PetTemplate(String id, String name, Element element, Rarity rarity, Role role, String entity, boolean obtainable,
                          String mythicMob, Stats base, List<String> skills, List<EvolutionBranch> evolutions, String description) {
    public String coloredName() { return rarity.color() + name; }
}
