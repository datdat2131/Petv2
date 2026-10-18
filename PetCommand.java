package vn.soulpets.commands;

import java.util.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import vn.soulpets.SoulPetsPlugin;
import vn.soulpets.combat.Cooldowns;
import vn.soulpets.dungeon.DungeonDef;
import vn.soulpets.egg.EggType;
import vn.soulpets.equipment.EquipmentService;
import vn.soulpets.pet.*;
import vn.soulpets.skills.SkillDef;
import vn.soulpets.skills.SkillMath;
import vn.soulpets.util.Text;

public final class PetCommand implements CommandExecutor, TabCompleter {
    private final SoulPetsPlugin pl; private final Cooldowns<UUID> spam = new Cooldowns<>(), skillGcd = new Cooldowns<>();
    private static final List<String> SUBS = List.of("menu", "summon", "dismiss", "stats", "skill", "evolve", "equip", "egg", "gacha", "dex", "quests", "top", "dungeon", "rename", "release", "select", "souls", "help");
    public PetCommand(SoulPetsPlugin pl) { this.pl = pl; }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Lệnh này chỉ dành cho người chơi."); return true; }
        PetService sv = pl.service(); PlayerProfile pr = sv.profile(p);
        if (pr == null) { sv.msg(p, "§cDữ liệu chưa tải xong, thử lại sau giây lát."); return true; }
        if (!spam.tryUse(p.getUniqueId(), System.currentTimeMillis(), pl.settings().cmdMs)) return true;   // chống spam lệnh
        String sub = a.length == 0 ? "menu" : a[0].toLowerCase();
        PetData sel = sv.selected(pr);
        switch (sub) {
            case "menu" -> pl.gui().openMain(p);
            case "summon" -> sv.summon(p);
            case "dismiss" -> sv.dismiss(p);
            case "souls" -> sv.msg(p, "§eLinh Hồn: §f" + pr.souls);
            case "select" -> {
                if (a.length < 2) { sv.msg(p, "§cDùng: /pet select <số thứ tự trong bộ sưu tập>"); return true; }
                List<PetData> list = new ArrayList<>(pr.pets.values()); int i = vn.soulpets.storage.Codec.toInt(a[1], 0) - 1;
                if (i < 0 || i >= list.size()) { sv.msg(p, "§cSố không hợp lệ (1-" + list.size() + ")."); return true; }
                sv.select(p, list.get(i));
            }
            case "stats" -> {
                if (sel == null) { sv.msg(p, "§cChưa chọn pet."); return true; }
                PetTemplate t = sv.tpl(sel); PetService.Computed cm = sv.compute(sel);
                p.sendMessage("§6━━ " + sv.displayName(sel) + " §7Lv." + sel.level + "/" + sv.maxLevel(sel) + " ★" + sel.star + " ━━");
                p.sendMessage(t.rarity().tag() + " §7| " + t.element().tag() + " §7| §f" + t.role().display() + " §7| Sức mạnh §6" + sv.power(sel));
                p.sendMessage(PetManager.shortStats(cm.stats()));
                p.sendMessage("§7EXP §f" + sel.exp + "/" + LevelCurve.expToNext(sel.level) + " §7| Điểm kỹ năng §b" + sel.skillPoints + " §7| Mảnh §b" + pr.shards.getOrDefault(sel.root, 0));
                p.sendMessage(String.format(Locale.ROOT, "§7Chí mạng §f%.0f%% §7Hút máu §f%.0f%% §7Giảm sát thương §f%.0f%%", cm.passives().crit() * 100, cm.passives().lifesteal() * 100, cm.passives().reduction() * 100));
            }
            case "skill" -> skill(p, pr, sel, a);
            case "evolve" -> {
                if (sel == null) { sv.msg(p, "§cChưa chọn pet."); return true; }
                if (a.length >= 2) sv.evolve(p, sel, vn.soulpets.storage.Codec.toInt(a[1], 1) - 1); else pl.gui().openEvolve(p, sel);
            }
            case "equip" -> {
                if (sel == null) { sv.msg(p, "§cChưa chọn pet."); return true; }
                if (a.length >= 3 && a[1].equalsIgnoreCase("upgrade")) pl.equipment().upgrade(p, sel, a[2].toUpperCase());
                else if (a.length >= 3 && a[1].equalsIgnoreCase("unequip")) { if (!pl.equipment().unequip(p, sel, a[2].toUpperCase())) sv.msg(p, "§cÔ trống."); }
                else if (a.length >= 2 && a[1].equalsIgnoreCase("hand")) {
                    if (!pl.equipment().equipFromInventory(p, sel, p.getInventory().getHeldItemSlot())) return true;
                } else pl.gui().openEquipment(p, sel);
            }
            case "egg", "eggs" -> pl.gui().openEggs(p);
            case "gacha" -> {
                EggType egg = pl.data().eggs.get(a.length >= 2 ? a[1] : pl.settings().defaultEgg);
                if (egg == null) { sv.msg(p, "§cKhông có loại trứng này. Có: §f" + String.join(", ", pl.data().eggs.keySet())); return true; }
                pl.eggs().instant(p, egg);
            }
            case "dex" -> pl.gui().openDex(p);
            case "quests", "quest" -> pl.gui().openQuests(p);
            case "top" -> pl.gui().openTop(p);
            case "dungeon" -> {
                if (a.length >= 3 && a[1].equalsIgnoreCase("start")) pl.dungeons().begin(p, a[2]);
                else if (a.length >= 2 && a[1].equalsIgnoreCase("leave")) { pl.dungeons().stopFor(p.getUniqueId()); sv.msg(p, "§7Đã rời dungeon."); }
                else pl.gui().openDungeons(p);
            }
            case "rename" -> {
                if (sel == null || a.length < 2) { sv.msg(p, "§cDùng: /pet rename <tên>"); return true; }
                sv.rename(p, sel, String.join(" ", Arrays.copyOfRange(a, 1, a.length)));
            }
            case "release" -> {
                if (sel == null) { sv.msg(p, "§cChưa chọn pet."); return true; }
                if (a.length >= 2 && a[1].equalsIgnoreCase("confirm")) sv.release(p, sel);
                else sv.msg(p, "§cThả pet " + sv.displayName(sel) + "§c? Gõ §f/pet release confirm §choặc dùng menu.");
            }
            default -> help(p);
        }
        return true;
    }
    private void skill(Player p, PlayerProfile pr, PetData sel, String[] a) {
        PetService sv = pl.service();
        if (sel == null) { sv.msg(p, "§cChưa chọn pet."); return; }
        PetTemplate t = sv.tpl(sel);
        if (a.length >= 2 && a[1].equalsIgnoreCase("list")) {
            p.sendMessage("§6Kỹ năng của " + sv.displayName(sel) + " §7(điểm: §b" + sel.skillPoints + "§7)");
            for (SkillDef d : pl.data().skillsOf(t)) { int lv = SkillMath.levelOf(sel, d); p.sendMessage((d.active() ? "§c[Chủ động] " : "§a[Bị động] ") + "§f" + d.id() + " §7- " + d.name() + " §7Lv." + lv + "/" + d.maxLevel() + (lv <= 0 ? " §8(cấp " + d.unlockLevel() + ")" : "")); }
            return;
        }
        if (a.length >= 3 && a[1].equalsIgnoreCase("upgrade")) { sv.upgradeSkill(p, sel, a[2]); return; }
        if (a.length == 1) { pl.gui().openSkills(p, sel); return; }
        PetManager.Active act = pl.pets().get(p.getUniqueId());
        if (act == null) { sv.msg(p, "§cHãy triệu hồi pet trước."); return; }
        if (!skillGcd.tryUse(p.getUniqueId(), System.currentTimeMillis(), pl.settings().skillGcdMs)) return;
        String want = a[1].equalsIgnoreCase("cast") ? (a.length >= 3 ? a[2] : null) : a[1];
        for (SkillDef d : pl.data().skillsOf(act.tpl)) {
            if (!d.active() || (want != null && !d.id().equalsIgnoreCase(want))) continue;
            if (pl.skills().cast(p, act, d)) { sv.msg(p, "§aPet dùng §f" + d.name() + "§a!"); return; }
            if (want != null) { sv.msg(p, "§cKhông thể dùng §f" + d.name() + " §c(hồi chiêu/không có mục tiêu/chưa mở khóa)."); return; }
        }
        sv.msg(p, "§cKhông có kỹ năng nào dùng được lúc này.");
    }
    private void help(Player p) {
        p.sendMessage("§6━━ Astrax Pet RPG ━━");
        for (String s : new String[]{"/pet §7- mở menu", "/pet summon | dismiss §7- triệu hồi / thu hồi", "/pet stats §7- chỉ số pet", "/pet skill [list|cast <id>|upgrade <id>]", "/pet evolve [nhánh]", "/pet equip [hand|upgrade <ô>|unequip <ô>]",
                "/pet egg §7- trứng & lò ấp", "/pet gacha [trứng] §7- mở ngay", "/pet dex | quests | top", "/pet dungeon [start <id>|leave]", "/pet rename <tên> | release | select <số>"}) p.sendMessage("§e" + s);
    }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) { for (String x : SUBS) if (x.startsWith(a[0].toLowerCase())) out.add(x); return out; }
        String sub = a[0].toLowerCase();
        if (a.length == 2) {
            switch (sub) {
                case "gacha" -> out.addAll(pl.data().eggs.keySet());
                case "skill" -> { out.addAll(List.of("list", "cast", "upgrade")); out.addAll(pl.data().skills.keySet()); }
                case "equip" -> out.addAll(List.of("hand", "upgrade", "unequip"));
                case "dungeon" -> out.addAll(List.of("start", "leave"));
                case "release" -> out.add("confirm");
                default -> { }
            }
        } else if (a.length == 3) {
            switch (sub) {
                case "dungeon" -> out.addAll(pl.data().dungeons.keySet());
                case "equip" -> out.addAll(EquipmentService.SLOTS);
                case "skill" -> out.addAll(pl.data().skills.keySet());
                default -> { }
            }
        }
        out.removeIf(x -> !x.toLowerCase().startsWith(a[a.length - 1].toLowerCase()));
        return out;
    }
}
