package vn.soulpets.combat;

import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.event.world.EntitiesLoadEvent;
import vn.soulpets.SoulPetsPlugin;
import vn.soulpets.pet.PetManager;

/** Sự kiện chiến đấu: bảo vệ chủ (giảm sát thương/khiên), pet bất tử, nhận kill, dọn thực thể pet còn sót, chống farm spawner. */
public final class CombatListener implements Listener {
    private final SoulPetsPlugin pl; private final Cooldowns<java.util.UUID> clickCd = new Cooldowns<>();
    public CombatListener(SoulPetsPlugin pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onOwnerDamaged(EntityDamageEvent e) {
        if (pl.pets().isPet(e.getEntity())) { e.setCancelled(true); return; }
        if (!(e.getEntity() instanceof Player p)) return;
        PetManager.Active a = pl.pets().get(p.getUniqueId());
        if (a == null) return;
        double dmg = e.getDamage();
        dmg = DamageCalc.absorb(a.shield, dmg);
        dmg *= 1.0 - DamageCalc.ownerReduction(a.stats.def(), a.tpl.role(), a.passives.reduction());
        e.setDamage(Math.max(0, dmg));
        if (dmg <= 0.0001) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPetAttacks(EntityDamageByEntityEvent e) {
        if (pl.pets().isPet(e.getDamager())) e.setCancelled(true);   // sát thương pet chỉ đi qua PetManager.hit
    }
    @EventHandler(ignoreCancelled = true)
    public void onPetTarget(EntityTargetEvent e) {
        if (pl.pets().isPet(e.getEntity())) e.setCancelled(true);
    }
    @EventHandler
    public void onPetPortal(EntityPortalEvent e) { if (pl.pets().isPet(e.getEntity())) e.setCancelled(true); }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(EntityDeathEvent e) {
        LivingEntity v = e.getEntity();
        if (pl.pets().isPet(v)) { e.getDrops().clear(); e.setDroppedExp(0); return; }
        Player killer = v.getKiller();
        if (killer == null || !(v instanceof Enemy)) return;
        var pdc = v.getPersistentDataContainer();
        if (pdc.has(pl.pets().dungeonKey(), PersistentDataType.STRING)) return;                 // dungeon: thưởng khi hoàn thành
        if (pl.settings().ignoreSpawner && pdc.has(pl.pets().noRewardKey(), PersistentDataType.STRING)) return;
        pl.service().onKill(killer, v);
    }
    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (pl.settings().ignoreSpawner && e.getSpawnReason() == CreatureSpawnEvent.SpawnReason.SPAWNER)
            e.getEntity().getPersistentDataContainer().set(pl.pets().noRewardKey(), PersistentDataType.STRING, "1");
    }
    /** Xóa pet "mồ côi" còn lưu trong chunk (sau crash/reload) để không bao giờ có thực thể trùng lặp. */
    @EventHandler
    public void onEntitiesLoad(EntitiesLoadEvent e) {
        for (Entity en : e.getEntities()) if (pl.pets().isPet(en) && !pl.pets().isCurrentPetEntity(en)) en.remove();
    }
    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractPet(PlayerInteractEntityEvent e) {
        if (!pl.pets().isPet(e.getRightClicked())) return;
        e.setCancelled(true);
        String owner = e.getRightClicked().getPersistentDataContainer().get(pl.pets().petKey(), PersistentDataType.STRING);
        if (!e.getPlayer().getUniqueId().toString().equals(owner)) return;
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        if (!clickCd.tryUse(e.getPlayer().getUniqueId(), System.currentTimeMillis(), 500)) return;
        pl.gui().openMain(e.getPlayer());
    }
}
