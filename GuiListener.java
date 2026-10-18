package vn.soulpets.gui;

import java.util.UUID;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;
import org.bukkit.entity.Player;
import vn.soulpets.SoulPetsPlugin;
import vn.soulpets.combat.Cooldowns;

public final class GuiListener implements Listener {
    private final SoulPetsPlugin pl; private final Cooldowns<UUID> cd = new Cooldowns<>();
    public GuiListener(SoulPetsPlugin pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu menu) {
            e.setCancelled(true);                                   // chặn lấy/đặt item, shift-click, number-key...
            if (!(e.getWhoClicked() instanceof Player p)) return;
            if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;
            if (!cd.tryUse(p.getUniqueId(), System.currentTimeMillis(), pl.settings().guiClickMs)) return;   // chống spam click
            try { menu.handle(e); } catch (Exception ex) { pl.getLogger().warning("Lỗi GUI: " + ex); }
        }
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent e) { if (e.getView().getTopInventory().getHolder() instanceof Menu) e.setCancelled(true); }
}
