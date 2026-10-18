package vn.soulpets.combat;
import java.util.HashMap;
import java.util.Map;

/** Cooldown / chống spam theo khóa. Thời gian truyền vào (ms) để dễ test. Chỉ dùng trên main thread. */
public final class Cooldowns<K> {
    private final Map<K, Long> readyAt = new HashMap<>();
    public boolean tryUse(K key, long now, long cooldownMs) {
        Long r = readyAt.get(key);
        if (r != null && now < r) return false;
        readyAt.put(key, now + cooldownMs);
        return true;
    }
    public long remaining(K key, long now) { Long r = readyAt.get(key); return r == null ? 0 : Math.max(0, r - now); }
    public void clear(K key) { readyAt.remove(key); }
    public void purge(long now) { readyAt.values().removeIf(v -> v <= now); }
    public int size() { return readyAt.size(); }
}
