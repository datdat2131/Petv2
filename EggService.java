package vn.soulpets.egg;

import java.util.*;
import org.bukkit.*;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.inventory.meta.FireworkMeta;
import vn.soulpets.SoulPetsPlugin;
import vn.soulpets.pet.*;
import vn.soulpets.rarity.Rarity;

/** Mua trứng, ấp trứng (có thời gian, lưu qua restart), mở trứng với pity và hiệu ứng nhận pet hiếm. */
public final class EggService {
    private final SoulPetsPlugin pl;
    public EggService(SoulPetsPlugin pl) { this.pl = pl; }

    public boolean buy(Player p, EggType egg) {
        PlayerProfile pr = pl.service().profile(p);
        if (pr.souls < egg.cost()) { pl.service().msg(p, "§cCần " + egg.cost() + " Linh Hồn."); return false; }
        pr.souls -= egg.cost(); pr.eggs.merge(egg.id(), 1, Integer::sum); pl.profiles().markDirty(p.getUniqueId());
        pl.service().msg(p, "§aĐã mua §f" + egg.name() + "§a.");
        return true;
    }
    public boolean startIncubation(Player p, EggType egg) {
        PlayerProfile pr = pl.service().profile(p);
        if (pr.eggs.getOrDefault(egg.id(), 0) <= 0) { pl.service().msg(p, "§cBạn không có " + egg.name() + "."); return false; }
        if (pr.incubators.size() >= pl.settings().incubatorSlots) { pl.service().msg(p, "§cLò ấp đã đầy (" + pl.settings().incubatorSlots + " ô)."); return false; }
        pr.eggs.merge(egg.id(), -1, Integer::sum);
        if (pr.eggs.get(egg.id()) <= 0) pr.eggs.remove(egg.id());
        long ms = (long) (egg.hatchSeconds() * 1000 * pl.settings().hatchTimeMult);
        pr.incubators.add(new PlayerProfile.Incubation(egg.id(), System.currentTimeMillis() + ms));
        pl.profiles().markDirty(p.getUniqueId());
        pl.service().msg(p, "§aĐã đặt §f" + egg.name() + " §avào lò ấp.");
        return true;
    }
    /** Nhận trứng đã ấp xong ở ô idx. */
    public boolean collect(Player p, int idx) {
        PlayerProfile pr = pl.service().profile(p);
        if (idx < 0 || idx >= pr.incubators.size()) return false;
        PlayerProfile.Incubation inc = pr.incubators.get(idx);
        if (System.currentTimeMillis() < inc.readyAt()) { pl.service().msg(p, "§cTrứng chưa nở."); return false; }
        EggType egg = pl.data().eggs.get(inc.egg());
        pr.incubators.remove(idx);
        if (egg == null) { pl.service().msg(p, "§cLoại trứng không còn tồn tại, đã gỡ khỏi lò."); return false; }
        open(p, pr, egg);
        return true;
    }
    /** Mở ngay (/pet gacha): tốn Linh Hồn x hệ số. */
    public boolean instant(Player p, EggType egg) {
        PlayerProfile pr = pl.service().profile(p);
        long cost = (long) Math.ceil(egg.cost() * pl.settings().instantMult);
        if (pr.souls < cost) { pl.service().msg(p, "§cCần " + cost + " Linh Hồn để mở ngay " + egg.name() + "."); return false; }
        pr.souls -= cost;
        open(p, pr, egg);
        return true;
    }
    private void open(Player p, PlayerProfile pr, EggType egg) {
        GachaEngine engine = new GachaEngine(pl.settings().pity);
        Rarity r = engine.roll(egg.weights(), pr.pity, pl.pets().rng());
        PetTemplate t = EggRoller.pick(egg, r, pl.data().pets.values(), pl.pets().rng());
        if (t == null) { pr.souls += egg.cost(); pl.service().msg(p, "§cKhông có pet nào để nở (kiểm tra pets.yml). Đã hoàn Linh Hồn."); return; }
        PetData existing = null;
        for (PetData d : pr.pets.values()) if (d.root.equals(t.id())) { existing = d; break; }
        boolean dup = existing != null && pl.settings().convertShards;
        if (dup) {
            pr.shards.merge(t.id(), t.rarity().shardValue(), Integer::sum);
            pl.service().msg(p, "§7Trùng " + t.coloredName() + "§7: nhận §b" + t.rarity().shardValue() + " mảnh §7+ §a" + pl.settings().dupExp + " EXP§7.");
            pl.service().addExp(p, existing, pl.settings().dupExp);
        } else pl.service().grantPet(pr, t, 1);
        pr.dex.add(t.id()); pr.count("hatches", 1);
        if (t.rarity().atLeast(Rarity.LEGENDARY)) pr.count("legendary_pulls", 1);
        reveal(p, t, dup);
        pl.service().quest(p, "HATCH_EGG", 1);
        pl.service().recalcScore(pr);
        pl.profiles().markDirty(p.getUniqueId());
        pl.service().checkAchievements(p);
    }
    private void reveal(Player p, PetTemplate t, boolean dup) {
        Rarity r = t.rarity(); Location loc = p.getLocation();
        p.sendTitle(r.color() + t.name(), r.tag() + " §7| " + t.element().tag() + (dup ? " §8(trùng)" : ""), 10, 60, 20);
        Sound s = r.atLeast(Rarity.MYTHIC) ? Sound.ENTITY_ENDER_DRAGON_GROWL : r.atLeast(Rarity.EPIC) ? Sound.ENTITY_PLAYER_LEVELUP : Sound.ENTITY_EXPERIENCE_ORB_PICKUP;
        p.playSound(loc, s, 1f, 1f);
        if (r.atLeast(Rarity.EPIC)) loc.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1, 0), r.atLeast(Rarity.MYTHIC) ? 80 : 40, 0.6, 0.8, 0.6, 0.2);
        if (r.atLeast(Rarity.LEGENDARY)) {
            Firework fw = loc.getWorld().spawn(loc, Firework.class);
            FireworkMeta m = fw.getFireworkMeta();
            m.addEffect(FireworkEffect.builder().withColor(r == Rarity.LEGENDARY ? Color.ORANGE : r == Rarity.MYTHIC ? Color.FUCHSIA : Color.RED).with(FireworkEffect.Type.BALL_LARGE).withFlicker().build());
            m.setPower(0); fw.setFireworkMeta(m); fw.detonate();
        }
        if (r.atLeast(pl.settings().broadcastMin)) Bukkit.broadcastMessage(org.bukkit.ChatColor.translateAlternateColorCodes('&', pl.settings().prefix) + "§e" + p.getName() + " §7vừa nhận được " + r.tag() + " §7" + t.coloredName() + " §7(" + t.element().tag() + "§7)!");
    }
}
