package vn.soulpets.rarity;

/** Bảy bậc độ hiếm. */
public enum Rarity {
    COMMON("Common", "§f", 1.00), UNCOMMON("Uncommon", "§a", 1.15), RARE("Rare", "§9", 1.35),
    EPIC("Epic", "§5", 1.65), LEGENDARY("Legendary", "§6", 2.05), MYTHIC("Mythic", "§d", 2.60), ANCIENT("Ancient", "§c", 3.40);

    private final String display, color; private final double statMult;
    Rarity(String d, String c, double m) { display = d; color = c; statMult = m; }
    public String display() { return display; }
    public String color() { return color; }
    public double statMult() { return statMult; }
    public String tag() { return color + display; }
    public boolean atLeast(Rarity o) { return ordinal() >= o.ordinal(); }
    /** Số mảnh nhận được khi trùng pet. */
    public int shardValue() { return ordinal() + 1; }
    public static Rarity parse(String s, Rarity def) {
        if (s == null) return def;
        try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return def; }
    }
}
