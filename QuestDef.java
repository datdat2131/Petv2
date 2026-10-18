package vn.soulpets.quest;
import vn.soulpets.reward.Reward;
public record QuestDef(String id, String name, String type, int amount, boolean daily, Reward reward) {}
