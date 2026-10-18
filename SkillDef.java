package vn.soulpets.skills;
import vn.soulpets.pet.Element;
public record SkillDef(String id, String name, boolean active, String effect, Element element, double power, double perLevel, double cooldown,
                       double range, int unlockLevel, int maxLevel, int cost, String particle, double extra, String mythicSkill, String description) {}
