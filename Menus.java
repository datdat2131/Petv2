package vn.soulpets.gui;

import java.util.*;
import java.util.function.Consumer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import vn.soulpets.SoulPetsPlugin;
import vn.soulpets.dungeon.DungeonDef;
import vn.soulpets.egg.EggType;
import vn.soulpets.equipment.*;
import vn.soulpets.evolution.EvolutionRules;
import vn.soulpets.pet.*;
import vn.soulpets.quest.AchievementDef;
import vn.soulpets.quest.QuestDef;
import vn.soulpets.quest.QuestService;
import vn.soulpets.skills.SkillDef;
import vn.soulpets.skills.SkillMath;
import vn.soulpets.util.Inv;
import vn.soulpets.util.Text;

/** Tất cả màn hình GUI của plugin. */
public final class Menus {
    private final SoulPetsPlugin pl;
    public Menus(SoulPetsPlugin pl) { this.pl = pl; }
    private PlayerProfile prof(Player p) { return pl.service().profile(p); }
    private static ItemStack pane() { return Text.item(Material.BLACK_STAINED_GLASS_PANE, " "); }
    private static ItemStack back() { return Text.item(Material.BARRIER, "&cQuay lại"); }
    private ItemStack petIcon(PetTemplate t) { Material m = Material.matchMaterial(t.entity() + "_SPAWN_EGG"); return new ItemStack(m == null ? Material.EGG : m); }

    private List<String> petLore(PetData d, boolean selected) {
        PetTemplate t = pl.service().tpl(d);
        List<String> l = new ArrayList<>();
        if (t == null) { l.add("&cPet không còn trong cấu hình"); return l; }
        PetService.Computed c = pl.service().compute(d);
        l.add(t.rarity().tag() + " &7| " + t.element().tag() + " &7| &f" + t.role().display());
        l.add("&7Cấp &f" + d.level + "/" + pl.service().maxLevel(d) + " &7Sao &e" + d.star + "&7 Tiến hóa &d" + d.stage);
        l.add(PetManager.shortStats(c.stats()));
        l.add("&7Sức mạnh: &6" + StatCalculator.power(c.stats()) + " &7| Điểm kỹ năng: &b" + d.skillPoints);
        if (selected) l.add("&a✔ Đang được chọn");
        return l;
    }
    private void confirm(Player p, String title, List<String> info, Runnable yes, Runnable no) {
        Menu m = new Menu("&8Xác nhận", 3); m.fill(pane());
        m.set(13, Text.item(Material.PAPER, title, info));
        m.set(11, Text.item(Material.LIME_CONCRETE, "&aĐồng ý"), e -> { p.closeInventory(); yes.run(); });
        m.set(15, Text.item(Material.RED_CONCRETE, "&cHủy"), e -> { p.closeInventory(); no.run(); });
        m.open(p);
    }

    // ---------- menu chính ----------
    public void openMain(Player p) {
        PlayerProfile pr = prof(p); if (pr == null) return;
        Menu m = new Menu("&6&lAstrax Pet RPG", 4); m.fill(pane());
        PetData sel = pl.service().selected(pr);
        m.set(4, Text.item(Material.SUNFLOWER, "&eLinh Hồn: &f" + pr.souls, "&7Pity: Epic " + pr.pity.sinceEpic + " | Legendary " + pr.pity.sinceLegendary, "&7Điểm xếp hạng: &6" + pr.score));
        m.set(10, Text.item(Material.CHEST, "&bBộ sưu tập", "&7" + pr.pets.size() + " pet"), e -> openCollection(p, 0));
        if (sel != null && pl.service().tpl(sel) != null) {
            m.set(11, Text.item(petIcon(pl.service().tpl(sel)).getType(), "&fPet hiện tại: " + pl.service().displayName(sel), petLore(sel, true)), e -> openDetail(p, sel));
        } else m.set(11, Text.item(Material.GRAY_DYE, "&7Chưa chọn pet", "&7Hãy ấp trứng ở mục Trứng."));
        m.set(12, Text.item(Material.TURTLE_EGG, "&dTrứng & lò ấp", "&7Mua, ấp và mở trứng"), e -> openEggs(p));
        m.set(13, Text.item(Material.BOOK, "&aPetDex", "&7" + pr.dex.size() + "/" + pl.data().pets.size() + " đã khám phá"), e -> openDex(p));
        m.set(14, Text.item(Material.WRITABLE_BOOK, "&eNhiệm vụ ngày/tuần"), e -> openQuests(p));
        m.set(15, Text.item(Material.NETHER_STAR, "&6Thành tựu"), e -> openAchievements(p));
        m.set(16, Text.item(Material.GOLDEN_HELMET, "&cBảng xếp hạng"), e -> openTop(p));
        m.set(19, Text.item(Material.IRON_SWORD, "&cDungeon"), e -> openDungeons(p));
        m.set(20, Text.item(Material.ANVIL, "&bCửa hàng trang bị"), e -> openShop(p));
        m.set(22, Text.item(Material.LEAD, pl.pets().get(p.getUniqueId()) == null ? "&aTriệu hồi pet" : "&7Thu hồi pet"), e -> {
            if (pl.pets().get(p.getUniqueId()) == null) pl.service().summon(p); else pl.service().dismiss(p); openMain(p); });
        m.set(31, Text.item(Material.BARRIER, "&cĐóng"), e -> p.closeInventory());
        m.open(p);
    }

    // ---------- bộ sưu tập ----------
    public void openCollection(Player p, int page) {
        PlayerProfile pr = prof(p);
        List<PetData> list = new ArrayList<>(pr.pets.values());
        list.sort(Comparator.comparingLong((PetData d) -> pl.service().power(d)).reversed());
        List<PagedMenu.Entry> es = new ArrayList<>();
        for (PetData d : list) {
            PetTemplate t = pl.service().tpl(d); if (t == null) continue;
            es.add(new PagedMenu.Entry(Text.item(petIcon(t).getType(), pl.service().displayName(d), petLore(d, d.id.equals(pr.selected))), e -> openDetail(p, d)));
        }
        new PagedMenu("&8Bộ sưu tập (" + list.size() + ")", es, this::openMain).show(p, page);
    }

    // ---------- chi tiết pet ----------
    public void openDetail(Player p, PetData d) {
        PlayerProfile pr = prof(p); PetTemplate t = pl.service().tpl(d);
        if (t == null) return;
        Menu m = new Menu("&8" + org.bukkit.ChatColor.stripColor(Text.c(pl.service().displayName(d))), 6); m.fill(pane());
        List<String> lore = new ArrayList<>(petLore(d, d.id.equals(pr.selected)));
        lore.add("&7" + t.description());
        lore.add("&7EXP: &f" + d.exp + "/" + LevelCurve.expToNext(d.level) + " " + Text.bar(d.exp / (double) LevelCurve.expToNext(d.level), 15));
        lore.add("&7Mảnh: &b" + pr.shards.getOrDefault(d.root, 0));
        m.set(13, Text.item(petIcon(t).getType(), pl.service().displayName(d), lore));
        m.set(20, Text.item(Material.EMERALD, "&aChọn & triệu hồi pet này"), e -> { pl.service().select(p, d); pl.service().summon(p); openDetail(p, d); });
        m.set(21, Text.item(Material.ENCHANTED_BOOK, "&bKỹ năng", "&7Điểm kỹ năng: &f" + d.skillPoints), e -> openSkills(p, d));
        m.set(22, Text.item(Material.DRAGON_BREATH, "&dTiến hóa", t.evolutions().isEmpty() ? "&7Không thể tiến hóa" : "&7" + t.evolutions().size() + " nhánh"), e -> openEvolve(p, d));
        int cost = EvolutionRules.shardCost(d.star);
        m.set(23, Text.item(Material.GOLD_NUGGET, "&eNâng sao", "&7Sao hiện tại: &e" + d.star + "/" + EvolutionRules.MAX_STAR, "&7Cần: &b" + (d.star >= EvolutionRules.MAX_STAR ? "-" : cost) + " mảnh &7(có " + pr.shards.getOrDefault(d.root, 0) + ")"),
                e -> confirm(p, "&eNâng sao?", List.of("&7Tốn " + cost + " mảnh " + t.name()), () -> { pl.service().starUp(p, d); openDetail(p, d); }, () -> openDetail(p, d)));
        m.set(24, Text.item(Material.IRON_CHESTPLATE, "&6Trang bị", "&7Vũ khí / Giáp / Bùa"), e -> openEquipment(p, d));
        m.set(31, Text.item(Material.NAME_TAG, "&fĐổi tên", "&7Dùng &f/pet rename <tên>"));
        m.set(33, Text.item(Material.TNT, "&cThả pet", "&7Nhận lại Linh Hồn theo độ hiếm"), e -> {
            Runnable go = () -> { pl.service().release(p, d); openCollection(p, 0); };
            if (pl.settings().releaseConfirm) confirm(p, "&cThả pet?", List.of("&7Không thể hoàn tác!", "&7" + pl.service().displayName(d)), go, () -> openDetail(p, d)); else go.run(); });
        m.set(49, back(), e -> openCollection(p, 0));
        m.open(p);
    }

    // ---------- kỹ năng ----------
    public void openSkills(Player p, PetData d) {
        PetTemplate t = pl.service().tpl(d);
        Menu m = new Menu("&8Kỹ năng - " + t.name(), 3); m.fill(pane());
        int slot = 9;
        for (SkillDef s : pl.data().skillsOf(t)) {
            int lv = SkillMath.levelOf(d, s);
            List<String> l = new ArrayList<>();
            l.add((s.active() ? "&cChủ động" : "&aBị động") + (s.element() != null ? " &7| " + s.element().tag() : ""));
            l.add("&7" + s.description());
            l.add(lv <= 0 ? "&cMở khóa ở cấp " + s.unlockLevel() : "&7Cấp: &f" + lv + "/" + s.maxLevel() + " &7Giá trị: &f" + String.format(Locale.ROOT, "%.2f", SkillMath.value(s, lv)));
            if (s.active()) l.add("&7Hồi chiêu: &f" + s.cooldown() + "s");
            if (lv > 0 && lv < s.maxLevel()) l.add("&eClick: nâng cấp (&f" + s.cost() + " điểm&e)");
            m.set(slot++, Text.item(lv <= 0 ? Material.GRAY_DYE : s.active() ? Material.BLAZE_POWDER : Material.GLOWSTONE_DUST, "&f" + s.name(), l), e -> { pl.service().upgradeSkill(p, d, s.id()); openSkills(p, d); });
        }
        m.set(4, Text.item(Material.EXPERIENCE_BOTTLE, "&bĐiểm kỹ năng: &f" + d.skillPoints));
        m.set(22, back(), e -> openDetail(p, d));
        m.open(p);
    }

    // ---------- tiến hóa ----------
    public void openEvolve(Player p, PetData d) {
        PetTemplate t = pl.service().tpl(d);
        Menu m = new Menu("&8Tiến hóa - " + t.name(), 3); m.fill(pane());
        if (t.evolutions().isEmpty()) m.set(13, Text.item(Material.BARRIER, "&cPet này không thể tiến hóa thêm"));
        int slot = t.evolutions().size() == 2 ? 11 : 10;
        for (int i = 0; i < t.evolutions().size(); i++) {
            final int idx = i; EvolutionBranch b = t.evolutions().get(i); PetTemplate to = pl.data().pets.get(b.to());
            if (to == null) continue;
            List<String> l = new ArrayList<>();
            l.add(to.rarity().tag() + " &7| " + to.element().tag() + " &7| &f" + to.role().display());
            l.add("&7" + to.description());
            l.add("&7Cấp tối thiểu: &f" + b.minLevel() + " &7Sao: &f" + b.minStar() + " &7Linh Hồn: &e" + b.souls());
            b.materials().forEach((k, v) -> l.add("&7- &f" + v + "x " + k));
            EvolutionRules.Result r = EvolutionRules.check(d, b, prof(p).souls, Inv.counts(p, b.materials().keySet()));
            l.add(r.ok() ? "&aĐủ điều kiện - click để tiến hóa" : "&c" + r.reason());
            m.set(slot, Text.item(petIcon(to).getType(), to.coloredName(), l), e -> confirm(p, "&dTiến hóa thành " + to.coloredName() + "&d?", List.of("&7Không thể hoàn tác."), () -> { pl.service().evolve(p, d, idx); openDetail(p, d); }, () -> openEvolve(p, d)));
            slot += 4;
        }
        m.set(22, back(), e -> openDetail(p, d));
        m.open(p);
    }

    // ---------- trang bị ----------
    public void openEquipment(Player p, PetData d) {
        Menu m = new Menu("&8Trang bị pet", 6); m.fill(pane());
        int[] slots = {11, 13, 15}; int i = 0;
        for (String slot : EquipmentService.SLOTS) {
            String id = d.equip.get(slot); EquipmentDef def = id == null ? null : pl.data().equipment.get(id);
            int pos = slots[i++];
            if (def == null) m.set(pos, Text.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7Ô " + EquipmentService.slotName(slot), "&7Trống"));
            else {
                int lv = d.equipLevel.getOrDefault(slot, 0);
                List<String> l = new ArrayList<>(List.of(def.rarity().tag(), "&7Nâng cấp: &f+" + lv + "/" + def.maxUpgrade(), PetManager.shortStats(EquipmentMath.bonus(def, lv)),
                        "&eClick trái: &7gỡ trang bị", "&eClick phải: &7nâng cấp (" + EquipmentMath.soulCost(def, lv) + " Linh Hồn + " + def.upgradeMaterials() + ")"));
                m.set(pos, Text.item(Text.material(def.material(), Material.STICK), def.rarity().color() + def.name(), l), e -> {
                    if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) pl.equipment().upgrade(p, d, slot); else pl.equipment().unequip(p, d, slot);
                    openEquipment(p, d); });
            }
        }
        m.set(4, Text.item(Material.PAPER, "&6Chọn vật phẩm trang bị trong túi đồ bên dưới", "&7Mua trang bị ở Cửa hàng hoặc nhận từ dungeon."));
        ItemStack[] inv = p.getInventory().getStorageContents(); int pos = 27;
        for (int s = 0; s < inv.length && pos < 45; s++) {
            String eid = pl.equipment().idOf(inv[s]); if (eid == null) continue;
            final int invSlot = s;
            m.set(pos++, inv[s].clone(), e -> { pl.equipment().equipFromInventory(p, d, invSlot); openEquipment(p, d); });
        }
        m.set(49, back(), e -> openDetail(p, d));
        m.open(p);
    }
    public void openShop(Player p) {
        List<PagedMenu.Entry> es = new ArrayList<>();
        for (EquipmentDef def : pl.data().equipment.values()) {
            if (def.shopCost() <= 0) continue;
            es.add(new PagedMenu.Entry(Text.item(Text.material(def.material(), Material.STICK), def.rarity().color() + def.name(), List.of(def.rarity().tag() + " &7- " + EquipmentService.slotName(def.slot()), PetManager.shortStats(def.stats()), "&eGiá: &f" + def.shopCost() + " Linh Hồn", "&7Click để mua")),
                    e -> { pl.equipment().buy(p, def); openShop(p); }));
        }
        new PagedMenu("&8Cửa hàng trang bị (Linh Hồn: " + prof(p).souls + ")", es, this::openMain).show(p, 0);
    }

    // ---------- trứng ----------
    public void openEggs(Player p) {
        PlayerProfile pr = prof(p);
        Menu m = new Menu("&8Trứng & lò ấp", 6); m.fill(pane());
        m.set(4, Text.item(Material.SUNFLOWER, "&eLinh Hồn: &f" + pr.souls, "&7Pity Epic: " + pr.pity.sinceEpic + "/" + pl.settings().pity.epicHard(), "&7Pity Legendary: " + pr.pity.sinceLegendary + "/" + pl.settings().pity.legendHard()));
        int slot = 9;
        for (EggType egg : pl.data().eggs.values()) {
            if (slot > 26) break;
            int own = pr.eggs.getOrDefault(egg.id(), 0);
            m.set(slot++, Text.item(Text.material(egg.icon(), Material.EGG), "&d" + egg.name(), List.of("&7Sở hữu: &f" + own, "&7Giá: &e" + egg.cost() + " &7| Ấp: &f" + Text.time(egg.hatchSeconds()), egg.element() == null ? "&7Mọi hệ" : "&7Hệ: " + egg.element().tag(),
                    "&eTrái: &7mua  &eShift+Trái: &7mở ngay (" + (long) Math.ceil(egg.cost() * pl.settings().instantMult) + ")", "&ePhải: &7đặt vào lò ấp")), e -> {
                if (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT) pl.eggs().startIncubation(p, egg);
                else if (e.getClick() == ClickType.SHIFT_LEFT) pl.eggs().instant(p, egg);
                else pl.eggs().buy(p, egg);
                openEggs(p); });
        }
        for (int i = 0; i < pl.settings().incubatorSlots && i < 9; i++) {
            int pos = 36 + i;
            if (i < pr.incubators.size()) {
                PlayerProfile.Incubation inc = pr.incubators.get(i); EggType egg = pl.data().eggs.get(inc.egg());
                long left = (inc.readyAt() - System.currentTimeMillis()) / 1000; final int idx = i;
                if (left <= 0) m.set(pos, Text.item(Material.NETHER_STAR, "&a✔ Đã nở: " + (egg == null ? inc.egg() : egg.name()), "&eClick để nhận pet!"), e -> { pl.eggs().collect(p, idx); openEggs(p); });
                else m.set(pos, Text.item(Material.CLOCK, "&e" + (egg == null ? inc.egg() : egg.name()), "&7Còn lại: &f" + Text.time(left)), e -> openEggs(p));
            } else m.set(pos, Text.item(Material.LIGHT_GRAY_STAINED_GLASS_PANE, "&7Ô ấp trống"));
        }
        m.set(49, back(), e -> openMain(p));
        m.open(p);
    }

    // ---------- PetDex / nhiệm vụ / thành tựu / xếp hạng / dungeon ----------
    public void openDex(Player p) {
        PlayerProfile pr = prof(p); List<PagedMenu.Entry> es = new ArrayList<>();
        for (PetTemplate t : pl.data().pets.values()) {
            if (pr.dex.contains(t.id())) es.add(new PagedMenu.Entry(Text.item(petIcon(t).getType(), t.coloredName(), List.of(t.rarity().tag() + " &7| " + t.element().tag() + " &7| &f" + t.role().display(), PetManager.shortStats(t.base()), "&7" + t.description(), t.obtainable() ? "&7Nguồn: trứng" : "&7Nguồn: tiến hóa"), null)));
            else es.add(new PagedMenu.Entry(Text.item(Material.GRAY_DYE, "&8???", "&7Chưa khám phá", t.rarity().tag()), null));
        }
        new PagedMenu("&8PetDex (" + pr.dex.size() + "/" + pl.data().pets.size() + ")", es, this::openMain).show(p, 0);
    }
    public void openQuests(Player p) {
        PlayerProfile pr = prof(p); QuestService.refresh(pr, pl.data().quests.values(), java.time.LocalDate.now());
        List<PagedMenu.Entry> es = new ArrayList<>();
        for (QuestDef q : pl.data().quests.values()) {
            long cur = pr.quest.getOrDefault(q.id(), 0L); boolean claimed = pr.questClaimed.contains(q.id()), can = QuestService.claimable(pr, q);
            Material mat = claimed ? Material.CHEST_MINECART : can ? Material.GOLD_BLOCK : Material.PAPER;
            es.add(new PagedMenu.Entry(Text.item(mat, (q.daily() ? "&e[Ngày] " : "&b[Tuần] ") + "&f" + q.name(), List.of("&7Tiến độ: &f" + cur + "/" + q.amount() + " " + Text.bar(cur / (double) q.amount(), 12), "&7Thưởng: " + q.reward().describe(), claimed ? "&8Đã nhận" : can ? "&aClick để nhận thưởng!" : "&7Chưa hoàn thành")),
                    e -> { pl.service().claimQuest(p, q); openQuests(p); }));
        }
        new PagedMenu("&8Nhiệm vụ", es, this::openMain).show(p, 0);
    }
    public void openAchievements(Player p) {
        PlayerProfile pr = prof(p); List<PagedMenu.Entry> es = new ArrayList<>();
        pr.counters.put("dex", (long) pr.dex.size());
        for (AchievementDef a : pl.data().achievements.values()) {
            boolean done = pr.achievements.contains(a.id());
            es.add(new PagedMenu.Entry(Text.item(done ? Material.NETHER_STAR : Material.COAL, (done ? "&a✔ " : "&7") + a.name(), List.of("&7" + a.description(), "&7Tiến độ: &f" + Math.min(a.threshold(), pr.counter(a.counter())) + "/" + a.threshold(), "&7Thưởng: " + a.reward().describe())), null));
        }
        new PagedMenu("&8Thành tựu (" + pr.achievements.size() + "/" + pl.data().achievements.size() + ")", es, this::openMain).show(p, 0);
    }
    public void openTop(Player p) {
        pl.service().recalcScore(prof(p)); pl.profiles().saveAsync(prof(p));
        pl.service().msg(p, "§7Đang tải bảng xếp hạng...");
        pl.profiles().top(45, rows -> {
            if (!p.isOnline()) return;
            List<PagedMenu.Entry> es = new ArrayList<>(); int rank = 1;
            for (var r : rows) es.add(new PagedMenu.Entry(Text.item(rank <= 3 ? Material.GOLD_INGOT : Material.IRON_INGOT, "&6#" + rank++ + " &f" + r.name(), "&7Điểm sức mạnh: &e" + r.score()), null));
            new PagedMenu("&8Bảng xếp hạng", es, this::openMain).show(p, 0);
        });
    }
    public void openDungeons(Player p) {
        PlayerProfile pr = prof(p); List<PagedMenu.Entry> es = new ArrayList<>();
        for (DungeonDef d : pl.data().dungeons.values()) {
            long left = (pr.cooldowns.getOrDefault("dungeon:" + d.id(), 0L) - System.currentTimeMillis()) / 1000;
            es.add(new PagedMenu.Entry(Text.item(Material.IRON_SWORD, "&c" + d.name(), List.of("&7Pet tối thiểu: &fLv." + d.minPetLevel(), "&7Số đợt: &f" + d.waves().size() + (d.boss() != null ? " + Boss" : ""), "&7Thưởng: " + d.rewards().describe(), left > 0 ? "&cHồi: " + Text.time(left) : "&aClick để vào")),
                    e -> { p.closeInventory(); pl.dungeons().begin(p, d.id()); }));
        }
        new PagedMenu("&8Dungeon", es, this::openMain).show(p, 0);
    }
}
