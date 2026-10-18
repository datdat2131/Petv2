package vn.soulpets.pet;

public enum Element {
    FIRE("Lửa", "§c"), ICE("Băng", "§b"), LIGHTNING("Sét", "§e"), DARK("Bóng tối", "§8"), LIGHT("Ánh sáng", "§f"), NATURE("Thiên nhiên", "§a");
    private final String display, color;
    Element(String d, String c) { display = d; color = c; }
    public String display() { return display; }
    public String color() { return color; }
    public String tag() { return color + display; }
    private boolean beats(Element o) {
        return switch (this) { case FIRE -> o == ICE; case ICE -> o == LIGHTNING; case LIGHTNING -> o == NATURE; case NATURE -> o == FIRE;
            case DARK -> o == LIGHT; case LIGHT -> o == DARK; };
    }
    /** Hệ số sát thương khi hệ này đánh hệ phòng thủ d (null = trung tính). */
    public double multiplierAgainst(Element d) {
        if (d == null) return 1.0;
        if (beats(d)) return 1.3;
        if (d.beats(this) && !(this == DARK || this == LIGHT)) return 0.8;
        return 1.0;
    }
    public static Element parse(String s, Element def) {
        if (s == null || s.isBlank()) return def;
        try { return valueOf(s.trim().toUpperCase()); } catch (IllegalArgumentException e) { return def; }
    }
}
