package vn.soulpets;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import vn.soulpets.pet.*;
import vn.soulpets.storage.Codec;

class StorageCodecTest {
    @Test void escapeRoundTripWithSpecialChars() {
        String s = "Hồ Hỏa & Băng=1,2;%25 \n§c!";
        assertEquals(s, Codec.unesc(Codec.esc(s)));
    }
    @Test void mapAndListRoundTrip() {
        Map<String, String> m = new LinkedHashMap<>(); m.put("a=b", "c&d"); m.put("k", "");
        assertEquals(m, Codec.decodeMap(Codec.encodeMap(m)));
        List<String> l = List.of("x,y", "z", "Việt");
        assertEquals(l, Codec.decodeList(Codec.encodeList(l)));
        assertTrue(Codec.decodeList("").isEmpty());
    }
    @Test void petRoundTrip() {
        PetData p = PetData.create("ember_fox", 7); p.nickname = "Bé Lửa & Co"; p.exp = 123; p.star = 2; p.stage = 1; p.skillPoints = 3;
        p.skills.put("ember_burst", 3); p.equip.put("WEAPON", "wooden_fang"); p.equipLevel.put("WEAPON", 4); p.templateId = "ember_fox_king";
        PetData q = PetData.decode(p.encode());
        assertEquals(p.id, q.id); assertEquals("ember_fox", q.root); assertEquals("ember_fox_king", q.templateId); assertEquals("Bé Lửa & Co", q.nickname);
        assertEquals(7, q.level); assertEquals(123, q.exp); assertEquals(2, q.star); assertEquals(1, q.stage); assertEquals(3, q.skillPoints);
        assertEquals(3, q.skills.get("ember_burst")); assertEquals("wooden_fang", q.equip.get("WEAPON")); assertEquals(4, q.equipLevel.get("WEAPON"));
    }
    @Test void profileRoundTripKeepsEverything() {
        UUID id = UUID.randomUUID(); PlayerProfile p = new PlayerProfile(id); p.name = "Tèo"; p.souls = 999; p.score = 55;
        PetData a = PetData.create("ember_fox", 3), b = PetData.create("dusk_bat", 9); p.pets.put(a.id, a); p.pets.put(b.id, b); p.selected = b.id;
        p.dex.add("ember_fox"); p.dex.add("dusk_bat"); p.dexClaimed.add("5"); p.achievements.add("hunter_1"); p.questClaimed.add("daily_kill");
        p.eggs.put("basic_egg", 3); p.shards.put("ember_fox", 12); p.counters.put("kills", 77L); p.quest.put("daily_kill", 5L); p.cooldowns.put("dungeon:x", 123456789L);
        p.pity.sinceEpic = 4; p.pity.sinceLegendary = 44; p.dailyKey = "2026-10-17"; p.weeklyKey = "2026-W42";
        p.incubators.add(new PlayerProfile.Incubation("rare_egg", 1700000000000L));
        PlayerProfile q = PlayerProfile.decode(id, p.encode());
        assertEquals("Tèo", q.name); assertEquals(999, q.souls); assertEquals(2, q.pets.size()); assertEquals(b.id, q.selected);
        assertEquals(p.dex, q.dex); assertEquals(3, q.eggs.get("basic_egg")); assertEquals(12, q.shards.get("ember_fox")); assertEquals(77L, q.counter("kills"));
        assertEquals(4, q.pity.sinceEpic); assertEquals(44, q.pity.sinceLegendary); assertEquals("2026-W42", q.weeklyKey);
        assertEquals(1, q.incubators.size()); assertEquals("rare_egg", q.incubators.get(0).egg()); assertEquals(1700000000000L, q.incubators.get(0).readyAt());
        assertEquals(123456789L, q.cooldowns.get("dungeon:x")); assertTrue(q.achievements.contains("hunter_1")); assertTrue(q.questClaimed.contains("daily_kill"));
    }
    @Test void emptyProfileDecodes() {
        PlayerProfile q = PlayerProfile.decode(UUID.randomUUID(), new PlayerProfile(UUID.randomUUID()).encode());
        assertEquals(0, q.pets.size()); assertNull(q.selected);
    }
}
