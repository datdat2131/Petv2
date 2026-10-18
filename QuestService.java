package vn.soulpets.quest;

import java.time.LocalDate;
import java.time.temporal.IsoFields;
import java.util.*;
import vn.soulpets.pet.PlayerProfile;
import vn.soulpets.reward.Reward;

/** Logic nhiệm vụ ngày/tuần và thành tựu (thuần, dễ test). */
public final class QuestService {
    private QuestService() {}
    public static String dailyKey(LocalDate d) { return d.toString(); }
    public static String weeklyKey(LocalDate d) { return d.get(IsoFields.WEEK_BASED_YEAR) + "-W" + d.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR); }

    /** Đặt lại tiến độ khi sang ngày/tuần mới. */
    public static void refresh(PlayerProfile p, Collection<QuestDef> defs, LocalDate today) {
        String dk = dailyKey(today), wk = weeklyKey(today);
        boolean newDay = !dk.equals(p.dailyKey), newWeek = !wk.equals(p.weeklyKey);
        if (newDay || newWeek) {
            for (QuestDef q : defs) {
                if ((q.daily() && newDay) || (!q.daily() && newWeek)) { p.quest.remove(q.id()); p.questClaimed.remove(q.id()); }
            }
            p.dailyKey = dk; p.weeklyKey = wk;
        }
    }
    /** Cộng tiến độ cho mọi nhiệm vụ cùng loại. Trả về danh sách nhiệm vụ vừa hoàn thành. */
    public static List<QuestDef> progress(PlayerProfile p, Collection<QuestDef> defs, String type, long amount, LocalDate today) {
        refresh(p, defs, today);
        List<QuestDef> done = new ArrayList<>();
        for (QuestDef q : defs) {
            if (!q.type().equalsIgnoreCase(type)) continue;
            long cur = p.quest.getOrDefault(q.id(), 0L);
            if (cur >= q.amount()) continue;
            long now = Math.min(q.amount(), cur + amount);
            p.quest.put(q.id(), now);
            if (now >= q.amount()) done.add(q);
        }
        return done;
    }
    public static boolean claimable(PlayerProfile p, QuestDef q) { return p.quest.getOrDefault(q.id(), 0L) >= q.amount() && !p.questClaimed.contains(q.id()); }
    public static Reward claim(PlayerProfile p, QuestDef q) {
        if (!claimable(p, q)) return null;
        p.questClaimed.add(q.id());
        return q.reward();
    }
    /** Kiểm tra thành tựu mới đạt. */
    public static List<AchievementDef> checkAchievements(PlayerProfile p, Collection<AchievementDef> defs) {
        p.counters.put("dex", (long) p.dex.size());
        List<AchievementDef> unlocked = new ArrayList<>();
        for (AchievementDef a : defs) {
            if (p.achievements.contains(a.id())) continue;
            if (p.counter(a.counter()) >= a.threshold()) { p.achievements.add(a.id()); unlocked.add(a); }
        }
        return unlocked;
    }
}
