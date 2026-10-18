package vn.soulpets.util;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class Text {
    private Text() {}
    public static String c(String s) { return s == null ? "" : ChatColor.translateAlternateColorCodes('&', s); }
    public static ItemStack item(Material m, String name, String... lore) { return item(m, name, List.of(lore)); }
    public static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(c(name));
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(c(s));
            meta.setLore(l);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
            it.setItemMeta(meta);
        }
        return it;
    }
    public static Material material(String name, Material def) {
        Material m = name == null ? null : Material.matchMaterial(name);
        return m == null ? def : m;
    }
    public static String bar(double frac, int len) {
        int f = (int) Math.round(Math.max(0, Math.min(1, frac)) * len);
        return "§a" + "|".repeat(f) + "§7" + "|".repeat(len - f);
    }
    public static String time(long seconds) {
        if (seconds <= 0) return "0s";
        long h = seconds / 3600, m = seconds % 3600 / 60, s = seconds % 60;
        return (h > 0 ? h + "h " : "") + (m > 0 ? m + "m " : "") + s + "s";
    }
}
