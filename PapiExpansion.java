package vn.soulpets.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import vn.soulpets.SoulPetsPlugin;
import vn.soulpets.pet.PetData;
import vn.soulpets.pet.PetTemplate;
import vn.soulpets.pet.PlayerProfile;

/** %astrax_souls%, %astrax_pet_name%, %astrax_pet_level%, %astrax_pet_rarity%, %astrax_pet_element%, %astrax_pet_role%, %astrax_pet_power%, %astrax_pet_star%, %astrax_dex%, %astrax_dex_total%, %astrax_pets%, %astrax_score% */
public final class PapiExpansion extends PlaceholderExpansion {
    private final SoulPetsPlugin pl;
    public PapiExpansion(SoulPetsPlugin pl) { this.pl = pl; }
    @Override public String getIdentifier() { return "astrax"; }
    @Override public String getAuthor() { return "Astrax"; }
    @Override public String getVersion() { return pl.getPluginMeta().getVersion(); }
    @Override public boolean persist() { return true; }
    @Override public String onRequest(OfflinePlayer player, String id) {
        if (player == null) return "";
        PlayerProfile pr = pl.profiles().get(player.getUniqueId());
        if (pr == null) return "";
        PetData d = pl.service().selected(pr); PetTemplate t = d == null ? null : pl.service().tpl(d);
        switch (id.toLowerCase()) {
            case "souls": return String.valueOf(pr.souls);
            case "dex": return String.valueOf(pr.dex.size());
            case "dex_total": return String.valueOf(pl.data().pets.size());
            case "pets": return String.valueOf(pr.pets.size());
            case "score": return String.valueOf(pr.score);
            default: break;
        }
        if (d == null || t == null) return "-";
        return switch (id.toLowerCase()) {
            case "pet_name" -> d.nickname.isEmpty() ? t.name() : d.nickname;
            case "pet_level" -> String.valueOf(d.level);
            case "pet_rarity" -> t.rarity().display();
            case "pet_element" -> t.element().display();
            case "pet_role" -> t.role().display();
            case "pet_power" -> String.valueOf(pl.service().power(d));
            case "pet_star" -> String.valueOf(d.star);
            default -> null;
        };
    }
}
