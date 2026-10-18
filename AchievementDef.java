package vn.soulpets.quest;
import vn.soulpets.reward.Reward;
public record AchievementDef(String id, String name, String counter, long threshold, Reward reward, String description) {}
