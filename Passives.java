package vn.soulpets.skills;
import java.util.Map;
import vn.soulpets.pet.PetData;
public record Passives(double hpPct, double atkPct, double defPct, double speedPct, double crit, double lifesteal, double regen, double reduction) {
    public static final Passives NONE = new Passives(0, 0, 0, 0, 0, 0, 0, 0);
    public static Passives aggregate(PetData pet, Iterable<String> skillIds, Map<String, SkillDef> defs) {
        double hp = 0, atk = 0, def = 0, spd = 0, crit = 0, ls = 0, reg = 0, red = 0;
        for (String id : skillIds) {
            SkillDef d = defs.get(id);
            if (d == null || d.active()) continue;
            int lv = SkillMath.levelOf(pet, d);
            if (lv <= 0) continue;
            double v = SkillMath.value(d, lv);
            switch (d.effect()) {
                case "HP_PCT" -> hp += v; case "ATK_PCT" -> atk += v; case "DEF_PCT" -> def += v; case "SPEED_PCT" -> spd += v;
                case "CRIT" -> crit += v; case "LIFESTEAL" -> ls += v; case "REGEN" -> reg += v; case "REDUCTION" -> red += v;
                default -> { }
            }
        }
        return new Passives(hp, atk, def, spd, Math.min(0.75, crit), Math.min(0.5, ls), reg, Math.min(0.5, red));
    }
}
