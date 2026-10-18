package vn.soulpets.dungeon;
import java.util.List;
import vn.soulpets.reward.Reward;
public record DungeonDef(String id, String name, int minPetLevel, long cooldownSeconds, long timeoutSeconds, boolean teleport, List<Wave> waves, Boss boss, Reward rewards, String location) {
    public record Wave(String type, String mythic, int count, double hpMult) {}
    public record Boss(String type, String mythic, String name, double hp) {}
}
