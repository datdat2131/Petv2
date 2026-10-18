package vn.soulpets.gui;

import java.util.List;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import vn.soulpets.util.Text;

/** Menu phân trang: 45 ô nội dung + hàng điều hướng (trước / quay lại / sau). */
public final class PagedMenu extends Menu {
    public record Entry(ItemStack item, Consumer<InventoryClickEvent> click) {}
    private final List<Entry> entries; private final Consumer<Player> back; private final String title; private int page;

    public PagedMenu(String title, List<Entry> entries, Consumer<Player> back) { super(title, 6); this.title = title; this.entries = entries; this.back = back; }
    public void show(Player p, int pg) {
        int pages = Math.max(1, (entries.size() + 44) / 45);
        page = Math.max(0, Math.min(pg, pages - 1));
        for (int i = 0; i < 54; i++) set(i, null);
        for (int i = 0; i < 45; i++) {
            int idx = page * 45 + i;
            if (idx < entries.size()) set(i, entries.get(idx).item(), entries.get(idx).click());
        }
        ItemStack pane = Text.item(Material.GRAY_STAINED_GLASS_PANE, " ");
        for (int i = 45; i < 54; i++) set(i, pane);
        if (page > 0) set(45, Text.item(Material.ARROW, "&e« Trang trước"), e -> show(p, page - 1));
        set(49, Text.item(Material.BARRIER, "&cQuay lại", "&7Trang " + (page + 1) + "/" + pages), e -> { if (back != null) back.accept(p); else p.closeInventory(); });
        if (page < pages - 1) set(53, Text.item(Material.ARROW, "&eTrang sau »"), e -> show(p, page + 1));
        open(p);
    }
}
