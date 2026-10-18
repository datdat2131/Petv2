package vn.soulpets.pet;

/** Gán hệ nguyên tố cho quái vanilla (theo tên EntityType) để tính khắc hệ. */
public final class MobElements {
    private MobElements() {}
    public static Element of(String type) {
        if (type == null) return null;
        return switch (type) {
            case "BLAZE", "MAGMA_CUBE", "GHAST", "STRIDER", "HOGLIN", "ZOGLIN", "PIGLIN", "PIGLIN_BRUTE", "ZOMBIFIED_PIGLIN" -> Element.FIRE;
            case "STRAY", "SNOW_GOLEM", "POLAR_BEAR", "DROWNED" -> Element.ICE;
            case "CREEPER", "GUARDIAN", "ELDER_GUARDIAN", "BREEZE" -> Element.LIGHTNING;
            case "ENDERMAN", "ENDERMITE", "WITCH", "PHANTOM", "VEX", "WITHER_SKELETON", "WITHER", "WARDEN", "SHULKER", "EVOKER",
                 "ZOMBIE", "SKELETON", "HUSK", "ZOMBIE_VILLAGER" -> Element.DARK;
            case "SPIDER", "CAVE_SPIDER", "SLIME", "SILVERFISH", "BOGGED" -> Element.NATURE;
            default -> null;
        };
    }
}
