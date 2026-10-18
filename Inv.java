package vn.soulpets.util;

import java.util.Map;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class Inv {
    private Inv() {}
    public static int count(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) if (it != null && it.getType() == m && !it.hasItemMeta()) n += it.getAmount();
        return n;
    }
    /** Đếm nguyên liệu (tên Material -> số lượng) trong túi, dùng cho kiểm tra tiến hóa/nâng cấp. */
    public static Map<String, Integer> counts(Player p, Iterable<String> names) {
        Map<String, Integer> m = new java.util.HashMap<>();
        for (String n : names) { Material mat = Material.matchMaterial(n); m.put(n, mat == null ? 0 : count(p, mat)); }
        return m;
    }
    public static void take(Player p, Material m, int amount) {
        int left = amount;
        ItemStack[] c = p.getInventory().getStorageContents();
        for (int i = 0; i < c.length && left > 0; i++) {
            ItemStack it = c[i];
            if (it == null || it.getType() != m || it.hasItemMeta()) continue;
            int t = Math.min(left, it.getAmount());
            if (t >= it.getAmount()) c[i] = null; else it.setAmount(it.getAmount() - t);
            left -= t;
        }
        p.getInventory().setStorageContents(c);
    }
    public static void give(Player p, ItemStack it) {
        for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
    }
}
