package vn.soulpets.pet;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import vn.soulpets.SoulPetsPlugin;

/** Tải hồ sơ khi đăng nhập (từ chối nếu lỗi để không ghi đè dữ liệu), dọn buff, lưu khi thoát. */
public final class LifecycleListener implements Listener {
    private final SoulPetsPlugin pl;
    public LifecycleListener(SoulPetsPlugin pl) { this.pl = pl; }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        try { pl.profiles().loadBlocking(e.getUniqueId(), e.getName()); }
        catch (Exception ex) {
            pl.getLogger().severe("Không tải được dữ liệu pet của " + e.getName() + ": " + ex.getMessage());
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cKhông tải được dữ liệu pet. Vui lòng thử lại sau ít phút.");
        }
    }
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        var p = e.getPlayer();
        PlayerProfile pr = pl.profiles().get(p.getUniqueId());
        if (pr == null) { try { pr = pl.profiles().loadBlocking(p.getUniqueId(), p.getName()); } catch (Exception ex) { p.kickPlayer("§cLỗi dữ liệu pet."); return; } }
        pl.pets().removeBuffs(p);                       // gỡ buff còn sót từ phiên trước/crash
        pr.name = p.getName();
        pl.service().recalcScore(pr);
        final PlayerProfile prof = pr;
        Bukkit.getScheduler().runTaskLater(pl, () -> {
            if (!p.isOnline()) return;
            long ready = prof.incubators.stream().filter(i -> i.readyAt() <= System.currentTimeMillis()).count();
            if (ready > 0) pl.service().msg(p, "§dBạn có §f" + ready + " §dtrứng đã nở! Mở §f/pet egg§d để nhận.");
            pl.service().quest(p, "LOGIN", 0);
            if (pl.settings().autoSummon && pl.service().selected(prof) != null) pl.service().summon(p);
        }, 20L);
    }
    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        var p = e.getPlayer();
        pl.dungeons().stopFor(p.getUniqueId());
        pl.pets().dismiss(p.getUniqueId());
        pl.profiles().unload(p.getUniqueId());
    }
}
