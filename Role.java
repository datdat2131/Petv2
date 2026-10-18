package vn.soulpets.pet;
public enum Role {
    TANK("Tank"), HEALER("Healer"), DPS("DPS");
    private final String display;
    Role(String d) { display = d; }
    public String display() { return display; }
    public static Role parse(String s, Role def) {
        if (s == null) return def;
        try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return def; }
    }
}
