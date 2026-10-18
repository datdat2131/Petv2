package vn.soulpets;

import org.junit.jupiter.api.Test;
import vn.soulpets.equipment.EquipSlot;
import vn.soulpets.pet.*;
import vn.soulpets.storage.*;

import java.nio.file.*;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/** Cần sqlite-jdbc (scope test trong pom). Kiểm tra lưu/đọc đầy đủ + chống mất dữ liệu. */
class SqlStorageTest {
    @Test void roundTripAndOfflineModify() throws Exception {
        Path dir = Files.createTempDirectory("astrax"); 
        SqlStorage s = new SqlStorage(StorageConfig.sqlite(dir.resolve("t.db").toString()), Logger.getLogger("t")); s.open();
        UUID owner = UUID.randomUUID(); assertNull(s.load(owner));
        PlayerProfile p = new PlayerProfile(owner); p.setSouls(77);
        PetInstance pet = PetInstance.create("wolf"); pet.addXp(500); pet.skills().put("s1", 2); pet.equipment().put(EquipSlot.WEAPON, "sword"); pet.upgrades().put(EquipSlot.WEAPON, 3); pet.setName("Bé Sói");
        p.add(pet); p.setActive(pet.id()); p.addMaterial("shard:wolf", 4); p.addEgg("basic", 2); p.setPity("basic:EPIC", 7);
        p.incubations().add(new PlayerProfile.Incubation("basic", 123456789L));
        s.saveAsync(p).get();
        PlayerProfile l = s.load(owner);
        assertEquals(77, l.souls()); assertEquals(pet.id(), l.activeId()); assertEquals(pet.level(), l.active().level());
        assertEquals(2, l.active().skillLevel("s1")); assertEquals("sword", l.active().equipment().get(EquipSlot.WEAPON)); assertEquals(3, (int) l.active().upgrades().get(EquipSlot.WEAPON));
        assertEquals("Bé Sói", l.active().name()); assertEquals(4, l.material("shard:wolf")); assertEquals(2, l.egg("basic")); assertEquals(7, l.pity("basic:EPIC"));
        assertEquals(123456789L, l.incubations().get(0).readyAt());
        assertTrue(s.modifyOffline(owner, x -> x.addSouls(23)).get()); assertEquals(100, s.load(owner).souls());
        assertFalse(s.modifyOffline(UUID.randomUUID(), x -> { }).get());
        s.close();
    }
}
