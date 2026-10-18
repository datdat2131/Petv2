package vn.soulpets;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import org.junit.jupiter.api.Test;

/** Kiểm tra tĩnh plugin.yml / pom / pets.yml để lỗi đóng gói bị phát hiện sớm. */
class ResourceIntegrityTest {
    static String read(String p) throws Exception { return Files.readString(Path.of(p)); }
    @Test void pluginYmlDeclaresExistingMainClassAndMatchesPom() throws Exception {
        String y = read("src/main/resources/plugin.yml");
        Matcher m = Pattern.compile("(?m)^main:\\s*(\\S+)").matcher(y); assertTrue(m.find());
        String main = m.group(1);
        assertTrue(Files.exists(Path.of("src/main/java/" + main.replace('.', '/') + ".java")), "Không thấy class main " + main);
        Matcher v = Pattern.compile("(?m)^version:\\s*'?([^'\\s]+)'?").matcher(y); assertTrue(v.find());
        assertTrue(read("pom.xml").contains("<version>" + v.group(1) + "</version>"), "version plugin.yml khác pom.xml");
        assertTrue(y.contains("api-version: '1.21'"));
        for (String cmd : List.of("pet:", "petadmin:")) assertTrue(y.contains("  " + cmd));
        for (String perm : List.of("soulpets.use:", "soulpets.admin:")) assertTrue(y.contains(perm));
    }
    @Test void atLeast30PetsAndAllElementsAndRarities() throws Exception {
        String y = read("src/main/resources/pets.yml");
        Matcher m = Pattern.compile("(?m)^  [a-z_]+:$").matcher(y); int n = 0; while (m.find()) n++;
        assertTrue(n >= 30, "Chỉ có " + n + " pet");
        for (String e : List.of("FIRE", "ICE", "LIGHTNING", "DARK", "LIGHT", "NATURE")) assertTrue(y.contains("element: " + e), e);
        for (String r : List.of("COMMON", "UNCOMMON", "RARE", "EPIC", "LEGENDARY", "MYTHIC", "ANCIENT")) assertTrue(y.contains("rarity: " + r), r);
    }
    @Test void petSkillsAndEvolutionTargetsExist() throws Exception {
        String pets = read("src/main/resources/pets.yml"), skills = read("src/main/resources/skills.yml");
        Set<String> petIds = new HashSet<>(), skillIds = new HashSet<>();
        Matcher a = Pattern.compile("(?m)^  ([a-z_]+):$").matcher(pets); while (a.find()) petIds.add(a.group(1));
        Matcher b = Pattern.compile("(?m)^  ([a-z_]+):\\s+\\{").matcher(skills); while (b.find()) skillIds.add(b.group(1));
        Matcher s = Pattern.compile("skills: \\[([^\\]]+)\\]").matcher(pets);
        while (s.find()) for (String id : s.group(1).split(",\\s*")) assertTrue(skillIds.contains(id.trim()), "skill thiếu: " + id);
        Matcher t = Pattern.compile("- \\{to: ([a-z_]+),").matcher(pets);
        while (t.find()) assertTrue(petIds.contains(t.group(1)), "tiến hóa tới pet không tồn tại: " + t.group(1));
    }
    @Test void allConfigFilesPresent() {
        for (String f : List.of("config.yml", "pets.yml", "skills.yml", "eggs.yml", "equipment.yml", "quests.yml", "achievements.yml", "dungeons.yml", "plugin.yml"))
            assertTrue(Files.exists(Path.of("src/main/resources/" + f)), f);
    }
}
