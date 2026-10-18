package vn.soulpets.equipment;

import java.util.*;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import vn.soulpets.SoulPetsPlugin;
import vn.soulpets.pet.*;
import vn.soulpets.util.Inv;
import vn.soulpets.util.Text;

/** Trang bị cho pet: 3 ô WEAPON / ARMOR / CHARM, vật phẩm có PDC, nâng cấp bằng Linh Hồn + nguyên liệu. */
public final class EquipmentService {
    public static final List<String> SLOTS = List.of("WEAPON", "ARMOR", "CHARM");
    private final SoulPetsPlugin pl; private final NamespacedKey idKey, lvKey;
    public EquipmentService(SoulPetsPlugin pl) { this.pl = pl; idKey = new NamespacedKey(pl, "equip_id"); lvKey = new NamespacedKey(pl, "equip_lv"); }

    public static String slotName(String s) { return switch (s) { case "WEAPON" -> "Vũ khí"; case "ARMOR" -> "Giáp"; default -> "Bùa"; }; }

    public ItemStack createItem(EquipmentDef d, int upgrade) {
        List<String> lore = new ArrayList<>();
        lore.add(d.rarity().tag() + " §7- " + slotName(d.slot()));
        Stats s = EquipmentMath.bonus(d, upgrade);
        if (s.hp() > 0) lore.add("§aHP +" + fmt(s.hp())); if (s.atk() > 0) lore.add("§cATK +" + fmt(s.atk()));
        if (s.def() > 0) lore.add("§9DEF +" + fmt(s.def())); if (s.speed() > 0) lore.add("§bSPEED +" + fmt(s.speed()));
        lore.add("§7Nâng cấp: §f+" + upgrade); lore.add("§8Dùng /pet equip để trang bị cho pet.");
        ItemStack it = Text.item(Text.material(d.material(), Material.STICK), d.rarity().color() + d.name(), lore);
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, d.id());
        m.getPersistentDataContainer().set(lvKey, PersistentDataType.INTEGER, upgrade);
        it.setItemMeta(m);
        return it;
    }
    private static String fmt(double v) { return String.format(java.util.Locale.ROOT, "%.1f", v); }
    public String idOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
    }
    public int levelOf(ItemStack it) {
        Integer v = it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(lvKey, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }
    /** Tổng chỉ số cộng thêm từ trang bị đang mặc. */
    public Stats flat(PetData p) {
        Stats sum = Stats.ZERO;
        for (Map.Entry<String, String> e : p.equip.entrySet()) {
            EquipmentDef d = pl.data().equipment.get(e.getValue());
            if (d != null) sum = sum.plus(EquipmentMath.bonus(d, p.equipLevel.getOrDefault(e.getKey(), 0)));
        }
        return sum;
    }
    /** Trang bị vật phẩm đang nằm ở ô túi chỉ định. */
    public boolean equipFromInventory(Player pl0, PetData pet, int invSlot) {
        ItemStack it = pl0.getInventory().getItem(invSlot);
        String id = idOf(it);
        EquipmentDef d = id == null ? null : pl.data().equipment.get(id);
        if (d == null) { pl.service().msg(pl0, "§cVật phẩm này không phải trang bị pet."); return false; }
        String old = pet.equip.get(d.slot());
        int oldLv = pet.equipLevel.getOrDefault(d.slot(), 0);
        int lv = levelOf(it);
        if (it.getAmount() > 1) it.setAmount(it.getAmount() - 1); else pl0.getInventory().setItem(invSlot, null);
        pet.equip.put(d.slot(), d.id()); pet.equipLevel.put(d.slot(), lv);
        if (old != null) { EquipmentDef od = pl.data().equipment.get(old); if (od != null) Inv.give(pl0, createItem(od, oldLv)); }
        pl.service().afterPetChanged(pl0, pet);
        pl.service().msg(pl0, "§aĐã trang bị §f" + d.name() + " §acho pet.");
        return true;
    }
    public boolean unequip(Player p, PetData pet, String slot) {
        String id = pet.equip.remove(slot);
        int lv = pet.equipLevel.getOrDefault(slot, 0); pet.equipLevel.remove(slot);
        if (id == null) return false;
        EquipmentDef d = pl.data().equipment.get(id);
        if (d != null) Inv.give(p, createItem(d, lv));
        pl.service().afterPetChanged(p, pet);
        return true;
    }
    public boolean upgrade(Player p, PetData pet, String slot) {
        String id = pet.equip.get(slot);
        EquipmentDef d = id == null ? null : pl.data().equipment.get(id);
        if (d == null) { pl.service().msg(p, "§cÔ này chưa có trang bị."); return false; }
        int lv = pet.equipLevel.getOrDefault(slot, 0);
        if (lv >= d.maxUpgrade()) { pl.service().msg(p, "§cTrang bị đã đạt cấp nâng tối đa."); return false; }
        PlayerProfile prof = pl.service().profile(p);
        long souls = EquipmentMath.soulCost(d, lv);
        if (prof.souls < souls) { pl.service().msg(p, "§cCần " + souls + " Linh Hồn."); return false; }
        for (String mat : d.upgradeMaterials().keySet()) {
            Material m = Material.matchMaterial(mat); int need = EquipmentMath.materialCount(d, mat, lv);
            if (m == null || Inv.count(p, m) < need) { pl.service().msg(p, "§cThiếu nguyên liệu: " + need + "x " + mat); return false; }
        }
        for (String mat : d.upgradeMaterials().keySet()) Inv.take(p, Material.matchMaterial(mat), EquipmentMath.materialCount(d, mat, lv));
        prof.souls -= souls; pet.equipLevel.put(slot, lv + 1);
        pl.service().afterPetChanged(p, pet);
        pl.service().msg(p, "§aNâng cấp §f" + d.name() + " §alên §e+" + (lv + 1) + "§a!");
        return true;
    }
    public boolean buy(Player p, EquipmentDef d) {
        PlayerProfile prof = pl.service().profile(p);
        if (d.shopCost() <= 0) return false;
        if (prof.souls < d.shopCost()) { pl.service().msg(p, "§cCần " + d.shopCost() + " Linh Hồn."); return false; }
        prof.souls -= d.shopCost(); Inv.give(p, createItem(d, 0)); pl.profiles().markDirty(p.getUniqueId());
        pl.service().msg(p, "§aĐã mua §f" + d.name() + "§a.");
        return true;
    }
}
