package vn.soulpets.storage;

import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import vn.soulpets.pet.PlayerProfile;
import vn.soulpets.util.Debug;

/**
 * Quản lý hồ sơ: cache trong bộ nhớ (chỉ sửa trên main thread), lưu bằng snapshot chuỗi qua một luồng IO duy nhất.
 * Lỗi tải => từ chối đăng nhập thay vì tạo hồ sơ trống (tránh ghi đè mất dữ liệu).
 */
public final class ProfileManager {
    private final JavaPlugin plugin; private final SqlStorage storage;
    private final Map<UUID, PlayerProfile> cache = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> dirty = new ConcurrentHashMap<>();
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> { Thread t = new Thread(r, "AstraxPet-IO"); t.setDaemon(false); return t; });
    private final long startingSouls;

    public ProfileManager(JavaPlugin plugin, SqlStorage storage, long startingSouls) { this.plugin = plugin; this.storage = storage; this.startingSouls = startingSouls; }

    public PlayerProfile get(UUID id) { return cache.get(id); }
    public Collection<PlayerProfile> loaded() { return cache.values(); }
    public void markDirty(UUID id) { dirty.put(id, Boolean.TRUE); }

    /** Gọi từ luồng bất kỳ (kể cả async prelogin). Ném RuntimeException nếu không tải được. */
    public PlayerProfile loadBlocking(UUID id, String name) {
        try {
            PlayerProfile cached = cache.get(id);
            if (cached != null) return cached;
            // Đọc qua CHÍNH luồng IO để chắc chắn các lệnh ghi đang chờ (vd. lưu khi vừa thoát) đã xong -> không đọc dữ liệu cũ
            String raw = io.submit(() -> storage.load(id)).get(15, TimeUnit.SECONDS);
            PlayerProfile p;
            if (raw == null) { p = new PlayerProfile(id); p.souls = startingSouls; p.name = name; }
            else p = PlayerProfile.decode(id, raw);
            if (name != null && !name.isEmpty()) p.name = name;
            cache.put(id, p);
            if (raw == null) markDirty(id);
            Debug.log("Đã tải hồ sơ " + name + " (" + p.pets.size() + " pet)");
            return p;
        } catch (Exception e) { throw new IllegalStateException("Không tải được hồ sơ " + id + ": " + e.getMessage(), e); }
    }

    private void write(UUID id, String name, long score, String data) {
        io.submit(() -> {
            for (int attempt = 1; attempt <= 3; attempt++) {
                try { storage.save(id, name, score, data); return; }
                catch (Exception e) { plugin.getLogger().warning("Lưu " + name + " lỗi (lần " + attempt + "): " + e.getMessage()); try { Thread.sleep(300L * attempt); } catch (InterruptedException ie) { return; } }
            }
            plugin.getLogger().severe("KHÔNG LƯU ĐƯỢC hồ sơ " + name + " (" + id + "). Dữ liệu thô để khôi phục thủ công: " + data);
        });
    }
    public void saveAsync(PlayerProfile p) { dirty.remove(p.uuid); write(p.uuid, p.name, p.score, p.encode()); }
    public void saveDirtyAsync() { for (UUID id : new ArrayList<>(dirty.keySet())) { PlayerProfile p = cache.get(id); if (p != null) saveAsync(p); else dirty.remove(id); } }

    /** Lưu rồi gỡ khỏi cache khi người chơi thoát. */
    public void unload(UUID id) { PlayerProfile p = cache.remove(id); dirty.remove(id); if (p != null) write(p.uuid, p.name, p.score, p.encode()); }

    /** Sửa hồ sơ kể cả người chơi offline. Người online: chạy ngay trên main thread. Offline: tải/sửa/lưu trên luồng IO, callback về main thread. */
    public void modify(UUID id, String name, Consumer<PlayerProfile> change, Consumer<Boolean> done) {
        PlayerProfile online = cache.get(id);
        if (online != null) { change.accept(online); markDirty(id); if (done != null) done.accept(true); return; }
        io.submit(() -> {
            boolean ok = false;
            try {
                String raw = storage.load(id);
                if (raw != null) {
                    PlayerProfile p = PlayerProfile.decode(id, raw);
                    change.accept(p);
                    storage.save(id, p.name, p.score, p.encode());
                    ok = true;
                }
            } catch (Exception e) { plugin.getLogger().warning("Sửa hồ sơ offline lỗi: " + e.getMessage()); }
            boolean result = ok;
            if (done != null && plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> done.accept(result));
        });
    }
    public void top(int limit, Consumer<List<SqlStorage.TopRow>> cb) {
        io.submit(() -> {
            List<SqlStorage.TopRow> rows;
            try { rows = storage.top(limit); } catch (Exception e) { plugin.getLogger().warning("Top lỗi: " + e.getMessage()); rows = List.of(); }
            List<SqlStorage.TopRow> r = rows;
            if (plugin.isEnabled()) Bukkit.getScheduler().runTask(plugin, () -> cb.accept(r));
        });
    }
    /** Gọi trong onDisable: lưu mọi hồ sơ, chờ luồng IO xong, đóng DB. */
    public void shutdown() {
        for (PlayerProfile p : cache.values()) write(p.uuid, p.name, p.score, p.encode());
        io.shutdown();
        try { if (!io.awaitTermination(30, TimeUnit.SECONDS)) plugin.getLogger().severe("Luồng IO chưa kịp lưu xong sau 30s!"); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        storage.close();
    }
}
