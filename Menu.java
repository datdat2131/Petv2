package vn.soulpets.gui;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import vn.soulpets.util.Text;

/** Menu inventory: mỗi ô có hành động riêng. Người chơi không thể lấy item ra. */
public class Menu implements InventoryHolder {
    private final Inventory inv; private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();
    public Menu(String title, int rows) { inv = Bukkit.createInventory(this, rows * 9, Text.c(title)); }
    @Override public Inventory getInventory() { return inv; }
    public void set(int slot, ItemStack it, Consumer<InventoryClickEvent> action) { inv.setItem(slot, it); if (action != null) actions.put(slot, action); else actions.remove(slot); }
    public void set(int slot, ItemStack it) { set(slot, it, null); }
    public void fill(ItemStack it) { for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, it); }
    public void open(Player p) { p.openInventory(inv); }
    void handle(InventoryClickEvent e) { Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot()); if (a != null) a.accept(e); }
}
