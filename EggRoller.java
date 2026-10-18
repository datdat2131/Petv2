package vn.soulpets.egg;

import java.util.*;
import vn.soulpets.pet.PetTemplate;
import vn.soulpets.rarity.Rarity;

public final class EggRoller {
    private EggRoller() {}
    /** Chọn pet theo độ hiếm; nếu pool trống thì hạ dần độ hiếm. Trả null nếu không có pet nào. */
    public static PetTemplate pick(EggType egg, Rarity r, Collection<PetTemplate> all, Random rng) {
        for (int o = r.ordinal(); o >= 0; o--) {
            Rarity cur = Rarity.values()[o];
            List<PetTemplate> pool = new ArrayList<>();
            for (PetTemplate t : all) if (t.obtainable() && t.rarity() == cur && (egg.element() == null || t.element() == egg.element())) pool.add(t);
            if (!pool.isEmpty()) { pool.sort(Comparator.comparing(PetTemplate::id)); return pool.get(rng.nextInt(pool.size())); }
        }
        List<PetTemplate> any = new ArrayList<>();
        for (PetTemplate t : all) if (t.obtainable()) any.add(t);
        any.sort(Comparator.comparing(PetTemplate::id));
        return any.isEmpty() ? null : any.get(rng.nextInt(any.size()));
    }
}
