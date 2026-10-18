package vn.soulpets.commands;

import java.util.*;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import vn.soulpets.SoulPetsPlugin;
import vn.soulpets.equipment.EquipmentDef;
import vn.soulpets.pet.*;
import vn.soulpets.storage.Codec;
import vn.soulpets.util.Debug;

public final class PetAdminCommand implements CommandExecutor, TabCompleter {
    private final SoulPetsPlugin pl;
    public PetAdminCommand(SoulPetsPlugin pl) { this.pl = pl; }
    private static final List<String> SUBS = List.of("give", "set", "reset", "info", "reload", "save", "debug", "setdungeon", "dungeon", "list");
    private void say(CommandSender s, String m) { s.sendMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', pl.settings().prefix) + m); }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 0) { say(s, "§e/petadmin <give|set|reset|info|reload|save|debug|setdungeon|dungeon|list>"); return true; }
        switch (a[0].toLowerCase()) {
            case "reload" -> { pl.reloadAll(); say(s, "§aĐã nạp lại cấu hình (" + pl.data().pets.size() + " pet)."); }
            case "save" -> { for (PlayerProfile p : pl.profiles().loaded()) pl.profiles().saveAsync(p); say(s, "§aĐang lưu toàn bộ hồ sơ."); }
            case "list" -> say(s, "§7Pet: §f" + String.join(", ", pl.data().pets.keySet()) + "\n§7Trứng: §f" + String.join(", ", pl.data().eggs.keySet()) + "\n§7Trang bị: §f" + String.join(", ", pl.data().equipment.keySet()));
            case "debug" -> {
                if (a.length >= 2 && a[1].equalsIgnoreCase("dump")) { for (String line : pl.pets().describeBrain().split("\n")) s.sendMessage(line); return true; }
                boolean on = a.length >= 2 ? a[1].equalsIgnoreCase("on") : !Debug.enabled(); Debug.set(on); say(s, "§7Debug: " + (on ? "§aBẬT" : "§cTẮT"));
            }
            case "setdungeon" -> {
                if (!(s instanceof Player p) || a.length < 2) { say(s, "§cDùng trong game: /petadmin setdungeon <id>"); return true; }
                say(s, pl.dungeons().setLocation(a[1], p.getLocation()) ? "§aĐã đặt vị trí dungeon (nhớ /petadmin reload)." : "§cKhông có dungeon đó.");
            }
            case "dungeon" -> {
                if (a.length >= 3 && a[1].equalsIgnoreCase("stop")) { OfflinePlayer t = Bukkit.getOfflinePlayer(a[2]); pl.dungeons().stopFor(t.getUniqueId()); say(s, "§aĐã dừng dungeon của " + a[2]); }
                else say(s, "§cDùng: /petadmin dungeon stop <người chơi>");
            }
            case "give", "set", "reset", "info" -> playerOp(s, a);
            default -> say(s, "§cLệnh không hợp lệ.");
        }
        return true;
    }
    private void playerOp(CommandSender s, String[] a) {
        String op = a[0].toLowerCase();
        int pi = op.equals("give") || op.equals("set") ? 2 : 1;
        if (a.length <= pi) { say(s, "§cThiếu tham số người chơi."); return; }
        OfflinePlayer t = Bukkit.getOfflinePlayer(a[pi]);
        if (!t.isOnline() && !t.hasPlayedBefore()) { say(s, "§cKhông tìm thấy người chơi '" + a[pi] + "'."); return; }
        UUID id = t.getUniqueId(); String name = t.getName() == null ? a[pi] : t.getName();
        String[] arg = a;
        switch (op) {
            case "info" -> pl.profiles().modify(id, name, p -> { }, ok -> { });
            default -> { }
        }
        if (op.equals("info")) {
            PlayerProfile p = pl.profiles().get(id);
            if (p == null) { say(s, "§7Người chơi offline: dùng /petadmin give/set để chỉnh, info chỉ xem được khi online."); return; }
            say(s, "§e" + name + " §7Linh Hồn §f" + p.souls + " §7Pet §f" + p.pets.size() + " §7Dex §f" + p.dex.size() + " §7Điểm §f" + p.score + " §7Pity §f" + p.pity.sinceEpic + "/" + p.pity.sinceLegendary);
            return;
        }
        if (op.equals("reset")) {
            if (a.length < 3 || !a[2].equalsIgnoreCase("confirm")) { say(s, "§cXóa TOÀN BỘ dữ liệu pet của " + name + ". Gõ: /petadmin reset " + name + " confirm"); return; }
            pl.pets().dismiss(id);
            pl.profiles().modify(id, name, p -> { p.pets.clear(); p.selected = null; p.dex.clear(); p.dexClaimed.clear(); p.achievements.clear(); p.eggs.clear(); p.shards.clear(); p.counters.clear(); p.quest.clear(); p.questClaimed.clear(); p.incubators.clear(); p.cooldowns.clear(); p.souls = pl.settings().startingSouls; p.score = 0; p.pity.sinceEpic = 0; p.pity.sinceLegendary = 0; },
                    ok -> say(s, ok ? "§aĐã reset " + name : "§cKhông reset được (không có hồ sơ)."));
            return;
        }
        if (a.length < 4) { say(s, "§cThiếu tham số. Ví dụ: /petadmin give pet <người> ember_fox 10"); return; }
        String what = a[1].toLowerCase(), val = a[3]; int amount = a.length >= 5 ? Codec.toInt(a[4], 1) : 1;
        Runnable notFound = () -> say(s, "§cId không tồn tại: " + val);
        if (op.equals("give")) switch (what) {
            case "pet" -> { PetTemplate tp = pl.data().pets.get(val); if (tp == null) { notFound.run(); return; }
                pl.profiles().modify(id, name, p -> pl.service().grantPet(p, tp, Math.max(1, amount)), ok -> say(s, ok ? "§aĐã tặng " + tp.coloredName() + " §acho " + name : "§cKhông có hồ sơ.")); }
            case "egg" -> { if (!pl.data().eggs.containsKey(val)) { notFound.run(); return; }
                pl.profiles().modify(id, name, p -> p.eggs.merge(val, amount, Integer::sum), ok -> say(s, ok ? "§aĐã tặng " + amount + "x " + val : "§cKhông có hồ sơ.")); }
            case "souls" -> { long n = Codec.toLong(val, 0);
                pl.profiles().modify(id, name, p -> p.souls += n, ok -> say(s, ok ? "§aĐã tặng " + n + " Linh Hồn" : "§cKhông có hồ sơ.")); }
            case "exp" -> { long n = Codec.toLong(val, 0); Player on = t.getPlayer();
                if (on == null) { say(s, "§cLệnh exp chỉ dùng khi người chơi online."); return; }
                PetData d = pl.service().selected(pl.service().profile(on)); if (d == null) { say(s, "§cNgười chơi chưa có pet."); return; }
                pl.service().addExp(on, d, n); say(s, "§aĐã cộng " + n + " EXP."); }
            case "equip" -> { EquipmentDef d = pl.data().equipment.get(val); Player on = t.getPlayer(); if (d == null) { notFound.run(); return; }
                if (on == null) { say(s, "§cLệnh equip chỉ dùng khi người chơi online."); return; }
                vn.soulpets.util.Inv.give(on, pl.equipment().createItem(d, 0)); say(s, "§aĐã tặng trang bị " + d.name()); }
            case "shards" -> { pl.profiles().modify(id, name, p -> p.shards.merge(val, amount, Integer::sum), ok -> say(s, ok ? "§aĐã tặng " + amount + " mảnh " + val : "§cKhông có hồ sơ.")); }
            default -> say(s, "§cgive <pet|egg|souls|exp|equip|shards> <người> <id|số> [số lượng]");
        } else if (op.equals("set") && what.equals("level")) {
            int lv = Math.max(1, Codec.toInt(val, 1)); Player on = t.getPlayer();
            if (on == null) { say(s, "§cChỉ dùng khi người chơi online."); return; }
            PetData d = pl.service().selected(pl.service().profile(on)); if (d == null) { say(s, "§cChưa có pet."); return; }
            d.level = Math.min(lv, pl.service().maxLevel(d)); d.exp = 0; pl.service().afterPetChanged(on, d); say(s, "§aĐã đặt cấp " + d.level);
        } else say(s, "§cset level <người> <cấp>");
    }
    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) out.addAll(SUBS);
        else if (a[0].equalsIgnoreCase("give") && a.length == 2) out.addAll(List.of("pet", "egg", "souls", "exp", "equip", "shards"));
        else if (a[0].equalsIgnoreCase("set") && a.length == 2) out.add("level");
        else if ((a[0].equalsIgnoreCase("give") && a.length == 3) || (a[0].equalsIgnoreCase("set") && a.length == 3) || ((a[0].equalsIgnoreCase("info") || a[0].equalsIgnoreCase("reset")) && a.length == 2)) for (Player p : Bukkit.getOnlinePlayers()) out.add(p.getName());
        else if (a[0].equalsIgnoreCase("give") && a.length == 4) {
            switch (a[1].toLowerCase()) { case "pet", "shards" -> out.addAll(pl.data().pets.keySet()); case "egg" -> out.addAll(pl.data().eggs.keySet()); case "equip" -> out.addAll(pl.data().equipment.keySet()); default -> { } }
        } else if ((a[0].equalsIgnoreCase("setdungeon") && a.length == 2)) out.addAll(pl.data().dungeons.keySet());
        out.removeIf(x -> !x.toLowerCase().startsWith(a[a.length - 1].toLowerCase()));
        return out;
    }
}
