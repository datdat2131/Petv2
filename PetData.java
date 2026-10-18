package vn.soulpets.pet;

import java.util.*;
import vn.soulpets.storage.Codec;

/** Một pet người chơi sở hữu (có thể thay đổi). */
public final class PetData {
    public UUID id = UUID.randomUUID();
    public String root, templateId, nickname = "";
    public int level = 1, star = 0, stage = 0, skillPoints = 0;
    public long exp = 0, createdAt = System.currentTimeMillis();
    public final Map<String, Integer> skills = new LinkedHashMap<>();
    public final Map<String, String> equip = new LinkedHashMap<>();
    public final Map<String, Integer> equipLevel = new LinkedHashMap<>();

    public static PetData create(String templateId, int level) {
        PetData p = new PetData(); p.root = templateId; p.templateId = templateId; p.level = Math.max(1, level); return p;
    }
    public String encode() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("id", id.toString()); m.put("root", root); m.put("tpl", templateId); m.put("nick", nickname);
        m.put("lv", "" + level); m.put("exp", "" + exp); m.put("star", "" + star); m.put("stage", "" + stage);
        m.put("sp", "" + skillPoints); m.put("created", "" + createdAt);
        m.put("skills", Codec.encodeMap(skills)); m.put("equip", Codec.encodeMap(equip)); m.put("eqlv", Codec.encodeMap(equipLevel));
        return Codec.encodeMap(m);
    }
    public static PetData decode(String s) {
        Map<String, String> m = Codec.decodeMap(s);
        PetData p = new PetData();
        p.id = UUID.fromString(m.get("id")); p.templateId = m.get("tpl"); p.root = m.getOrDefault("root", p.templateId);
        p.nickname = m.getOrDefault("nick", "");
        p.level = Codec.toInt(m.get("lv"), 1); p.exp = Codec.toLong(m.get("exp"), 0); p.star = Codec.toInt(m.get("star"), 0);
        p.stage = Codec.toInt(m.get("stage"), 0); p.skillPoints = Codec.toInt(m.get("sp"), 0); p.createdAt = Codec.toLong(m.get("created"), 0);
        Codec.decodeMap(m.get("skills")).forEach((k, v) -> p.skills.put(k, Codec.toInt(v, 1)));
        p.equip.putAll(Codec.decodeMap(m.get("equip")));
        Codec.decodeMap(m.get("eqlv")).forEach((k, v) -> p.equipLevel.put(k, Codec.toInt(v, 0)));
        return p;
    }
}
