package vn.soulpets.pet;

import java.util.*;
import vn.soulpets.egg.GachaEngine;
import vn.soulpets.storage.Codec;

/** Dữ liệu một người chơi. Chỉ thay đổi trên main thread; lưu bằng chuỗi snapshot. */
public final class PlayerProfile {
    public record Incubation(String egg, long readyAt) {}
    public final UUID uuid;
    public String name = "";
    public long souls, score;
    public UUID selected;
    public final Map<UUID, PetData> pets = new LinkedHashMap<>();
    public final Set<String> dex = new LinkedHashSet<>(), dexClaimed = new LinkedHashSet<>(), achievements = new LinkedHashSet<>(), questClaimed = new LinkedHashSet<>();
    public final Map<String, Integer> eggs = new LinkedHashMap<>(), shards = new LinkedHashMap<>();
    public final Map<String, Long> counters = new LinkedHashMap<>(), quest = new LinkedHashMap<>(), cooldowns = new LinkedHashMap<>();
    public final GachaEngine.PityState pity = new GachaEngine.PityState();
    public final List<Incubation> incubators = new ArrayList<>();
    public String dailyKey = "", weeklyKey = "";

    public PlayerProfile(UUID uuid) { this.uuid = uuid; }
    public void count(String key, long n) { counters.merge(key, n, Long::sum); }
    public long counter(String key) { return counters.getOrDefault(key, 0L); }

    public String encode() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("v", "2"); m.put("name", name); m.put("souls", "" + souls); m.put("score", "" + score);
        m.put("selected", selected == null ? "" : selected.toString());
        m.put("pe", "" + pity.sinceEpic); m.put("pl", "" + pity.sinceLegendary);
        m.put("dex", Codec.encodeList(dex)); m.put("dexc", Codec.encodeList(dexClaimed));
        m.put("ach", Codec.encodeList(achievements)); m.put("qc", Codec.encodeList(questClaimed));
        m.put("eggs", Codec.encodeMap(eggs)); m.put("shards", Codec.encodeMap(shards));
        m.put("counters", Codec.encodeMap(counters)); m.put("quest", Codec.encodeMap(quest)); m.put("cd", Codec.encodeMap(cooldowns));
        m.put("dk", dailyKey); m.put("wk", weeklyKey);
        List<String> inc = new ArrayList<>();
        for (Incubation i : incubators) inc.add(i.egg() + ":" + i.readyAt());
        m.put("inc", Codec.encodeList(inc));
        List<String> ps = new ArrayList<>();
        for (PetData p : pets.values()) ps.add(p.encode());
        m.put("pets", Codec.encodeList(ps));
        return Codec.encodeMap(m);
    }
    public static PlayerProfile decode(UUID uuid, String data) {
        PlayerProfile p = new PlayerProfile(uuid);
        Map<String, String> m = Codec.decodeMap(data);
        p.name = m.getOrDefault("name", ""); p.souls = Codec.toLong(m.get("souls"), 0); p.score = Codec.toLong(m.get("score"), 0);
        String sel = m.getOrDefault("selected", "");
        p.selected = sel.isEmpty() ? null : UUID.fromString(sel);
        p.pity.sinceEpic = Codec.toInt(m.get("pe"), 0); p.pity.sinceLegendary = Codec.toInt(m.get("pl"), 0);
        p.dex.addAll(Codec.decodeList(m.get("dex"))); p.dexClaimed.addAll(Codec.decodeList(m.get("dexc")));
        p.achievements.addAll(Codec.decodeList(m.get("ach"))); p.questClaimed.addAll(Codec.decodeList(m.get("qc")));
        Codec.decodeMap(m.get("eggs")).forEach((k, v) -> p.eggs.put(k, Codec.toInt(v, 0)));
        Codec.decodeMap(m.get("shards")).forEach((k, v) -> p.shards.put(k, Codec.toInt(v, 0)));
        Codec.decodeMap(m.get("counters")).forEach((k, v) -> p.counters.put(k, Codec.toLong(v, 0)));
        Codec.decodeMap(m.get("quest")).forEach((k, v) -> p.quest.put(k, Codec.toLong(v, 0)));
        Codec.decodeMap(m.get("cd")).forEach((k, v) -> p.cooldowns.put(k, Codec.toLong(v, 0)));
        p.dailyKey = m.getOrDefault("dk", ""); p.weeklyKey = m.getOrDefault("wk", "");
        for (String s : Codec.decodeList(m.get("inc"))) { int i = s.lastIndexOf(':'); if (i > 0) p.incubators.add(new Incubation(s.substring(0, i), Codec.toLong(s.substring(i + 1), 0))); }
        for (String s : Codec.decodeList(m.get("pets"))) { PetData pd = PetData.decode(s); p.pets.put(pd.id, pd); }
        return p;
    }
}
