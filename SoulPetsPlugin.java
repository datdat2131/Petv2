package vn.soulpets;

import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.plugin.java.JavaPlugin;
import vn.soulpets.combat.PetCombatManager;
import vn.soulpets.combat.RateLimiter;
import vn.soulpets.commands.PetAdminCommand;
import vn.soulpets.commands.PetCommand;
import vn.soulpets.config.ConfigLoader;
import vn.soulpets.egg.*;
import vn.soulpets.equipment.*;
import vn.soulpets.evolution.EvolutionService;
import vn.soulpets.gui.GuiManager;
import vn.soulpets.hook.PapiHook;
import vn.soulpets.mythiclib.MythicLibBridge;
import vn.soulpets.mythicmobs.MythicBridge;
import vn.soulpets.pet.*;
import vn.soulpets.rarity.PetRarity;
import vn.soulpets.skills.*;
import vn.soulpets.storage.*;

import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/** Main class (giữ nguyên tên để tương thích bản cũ). Chỉ điều phối các module. */
public final class SoulPetsPlugin extends JavaPlugin implements Listener {
    private final PetRegistry petRegistry = new PetRegistry(); private final SkillRegistry skillRegistry = new SkillRegistry();
    private final EggRegistry eggRegistry = new EggRegistry(); private final EquipmentRegistry equipRegistry = new EquipmentRegistry();
    private final Map<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final Random random = new Random();
    private SqlStorage storage; private PetManager petManager; private PetCombatManager combat; private GuiManager gui;
    private EggService eggService; private EvolutionService evolutionService; private StarService starService; private EquipmentService equipmentService;
    private StatCalculator statCalculator; private MythicBridge mythic; private MythicLibBridge mythicLib; private RateLimiter commandLimiter, clickLimiter;
    private final Map<EntityType, Element> mobElements = new EnumMap<>(EntityType.class);
    private int autosaveTask = -1;

    @Override public void onEnable() {
        saveDefaultConfig();
        for (String f : List.of("pets.yml", "skills.yml", "eggs.yml", "equipment.yml")) if (!new java.io.File(getDataFolder(), f).exists()) saveResource(f, false);
        mythic = new MythicBridge(getConfig().getBoolean("mythicmobs.enabled", true) && getServer().getPluginManager().isPluginEnabled("MythicMobs"), getLogger());
        mythicLib = new MythicLibBridge(getConfig().getBoolean("mythiclib.enabled", true) && getServer().getPluginManager().isPluginEnabled("MythicLib"), getLogger());
        storage = new SqlStorage(storageConfig(), getLogger());
        try { storage.open(); } catch (SQLException ex) { getLogger().severe("Không mở được database: " + ex.getMessage() + " -> tắt plugin để tránh mất dữ liệu."); getServer().getPluginManager().disablePlugin(this); return; }
        petManager = new PetManager(this); combat = new PetCombatManager(this); gui = new GuiManager(this);
        loadAll();
        getServer().getPluginManager().registerEvents(this, this);
        var pc = new PetCommand(this); var ac = new PetAdminCommand(this);
        Objects.requireNonNull(getCommand("pet")).setExecutor(pc); getCommand("pet").setTabCompleter(pc);
        Objects.requireNonNull(getCommand("petadmin")).setExecutor(ac); getCommand("petadmin").setTabCompleter(ac);
        getServer().getPluginManager().registerEvents(gui, this);
        for (org.bukkit.World w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) if (petManager.isPet(e)) e.remove(); // dọn pet mồ côi sau crash
        for (Player p : Bukkit.getOnlinePlayers()) { petManager.removeBuffs(p); loadOnlineBlocking(p.getUniqueId()); }
        combat.start();
        long ticks = Math.max(1, getConfig().getInt("storage.autosave-minutes", 5)) * 1200L;
        autosaveTask = Bukkit.getScheduler().runTaskTimer(this, this::autosave, ticks, ticks).getTaskId();
        Bukkit.getScheduler().runTaskTimer(this, () -> { commandLimiter.purge(System.currentTimeMillis()); clickLimiter.purge(System.currentTimeMillis()); }, 6000L, 6000L);
        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) { new PapiHook(this).register(); getLogger().info("Đã đăng ký PlaceholderAPI (%astraxpet_*%)."); }
        getLogger().info("Astrax Pet RPG " + getPluginMeta().getVersion() + " bật: " + petRegistry.size() + " pet, " + skillRegistry.all().size() + " kỹ năng, " + eggRegistry.all().size() + " trứng.");
    }

    /** Nạp lại toàn bộ cấu hình (dùng cho /petadmin reload). */
    public void loadAll() {
        reloadConfig();
        ConfigLoader l = new ConfigLoader(this);
        l.loadSkills(skillRegistry); l.loadPets(petRegistry); l.loadEggs(eggRegistry); l.loadEquipment(equipRegistry);
        var c = getConfig();
        equipmentService = new EquipmentService(equipRegistry, c.getInt("settings.upgrade-souls-per-level", 50));
        statCalculator = new StatCalculator(petRegistry, skillRegistry, equipmentService);
        eggService = new EggService(eggRegistry, petRegistry, random, c.getInt("settings.max-incubators", 3), c.getInt("settings.shards-per-duplicate", 3));
        evolutionService = new EvolutionService(petRegistry); starService = new StarService(c.getInt("settings.shards-per-star", 5));
        commandLimiter = new RateLimiter(c.getLong("combat.command-cooldown-ms", 400)); clickLimiter = new RateLimiter(c.getLong("combat.gui-click-cooldown-ms", 200));
        mobElements.clear();
        var sec = c.getConfigurationSection("combat.mob-elements");
        if (sec != null) for (String k : sec.getKeys(false)) try { mobElements.put(EntityType.valueOf(k.toUpperCase(Locale.ROOT)), Element.parse(sec.getString(k))); } catch (IllegalArgumentException ignored) { getLogger().warning("mob-elements: bỏ qua " + k); }
        if (combat != null) combat.start();
        for (UUID o : petManager.owners()) { Player p = Bukkit.getPlayer(o); if (p != null) { petManager.refreshName(p); petManager.applyBuffs(p); } }
    }

    private StorageConfig storageConfig() {
        var c = getConfig();
        String type = c.getString("storage.type", "SQLITE");
        return new StorageConfig(type, new java.io.File(getDataFolder(), c.getString("storage.sqlite-file", "pets.db")).getAbsolutePath(),
                c.getString("storage.mysql.host", "localhost"), c.getInt("storage.mysql.port", 3306), c.getString("storage.mysql.database", "astraxpet"),
                c.getString("storage.mysql.user", "root"), c.getString("storage.mysql.password", ""), c.getBoolean("storage.mysql.use-ssl", false));
    }

    private void autosave() { for (PlayerProfile p : profiles.values()) if (p.dirty()) storage.saveAsync(p); }

    @Override public void onDisable() {
        if (autosaveTask != -1) Bukkit.getScheduler().cancelTask(autosaveTask);
        if (combat != null) combat.stop();
        if (petManager != null) petManager.despawnAll();
        Bukkit.getScheduler().cancelTasks(this);
        if (storage != null) { for (PlayerProfile p : profiles.values()) storage.saveAsync(p); profiles.clear(); storage.close(); } // close() chờ ghi xong
    }

    // ---------------- accessors cho các module ----------------
    public PetRegistry pets() { return petRegistry; } public SkillRegistry skills() { return skillRegistry; }
    public EggRegistry eggs() { return eggRegistry; } public EquipmentRegistry equipment() { return equipRegistry; }
    public SqlStorage storage() { return storage; } public PetManager pet() { return petManager; } public PetCombatManager combat() { return combat; }
    public GuiManager gui() { return gui; } public EggService eggService() { return eggService; } public EvolutionService evolution() { return evolutionService; }
    public StarService star() { return starService; } public EquipmentService equipService() { return equipmentService; } public StatCalculator stats() { return statCalculator; }
    public MythicBridge mythic() { return mythic; } public MythicLibBridge mythicLib() { return mythicLib; }
    public RateLimiter commandLimiter() { return commandLimiter; } public RateLimiter clickLimiter() { return clickLimiter; }
    public PlayerProfile profile(UUID id) { return profiles.get(id); }
    public Element elementOfMob(EntityType t) { return mobElements.get(t); }

    private PlayerProfile loadOnlineBlocking(UUID id) {
        try {
            PlayerProfile p = storage.loadAsync(id).get(10, TimeUnit.SECONDS);
            if (p == null) p = newProfile(id);
            profiles.put(id, p); return p;
        } catch (Exception ex) { getLogger().severe("Không tải được profile " + id + ": " + ex); return null; }
    }
    private PlayerProfile newProfile(UUID id) {
        PlayerProfile p = new PlayerProfile(id); var c = getConfig();
        p.setSouls(c.getInt("settings.starting-souls", 300));
        PetDefinition starter = petRegistry.get(c.getString("settings.starter-pet", ""));
        if (starter != null) { PetInstance pi = PetInstance.create(starter.id()); p.add(pi); p.setActive(pi.id()); }
        String egg = c.getString("settings.starter-egg", ""); if (eggRegistry.get(egg) != null) p.addEgg(egg, 1);
        return p;
    }

    // ---------------- hiệu ứng nhận pet hiếm ----------------
    public void revealEffect(Player player, PetDefinition def, boolean duplicate, int shards) {
        PetRarity r = def.rarity();
        PetRarity min = Optional.ofNullable(PetRarity.parse(getConfig().getString("effects.rare-reveal-min", "EPIC"))).orElse(PetRarity.EPIC);
        PetRarity bc = Optional.ofNullable(PetRarity.parse(getConfig().getString("effects.broadcast-min", "LEGENDARY"))).orElse(PetRarity.LEGENDARY);
        String line = r.color() + "[" + r.display() + "] " + def.displayName();
        if (duplicate) player.sendMessage("§7Trùng pet " + line + "§7 → +" + shards + " mảnh để nâng sao.");
        else player.sendMessage("§aBạn nhận được pet: " + line + "§a!");
        if (!r.atLeast(min)) { player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f); return; }
        player.showTitle(net.kyori.adventure.title.Title.title(vn.soulpets.gui.Text.c(r.color() + r.display().toUpperCase()), vn.soulpets.gui.Text.c(def.displayName())));
        player.playSound(player.getLocation(), r.atLeast(PetRarity.LEGENDARY) ? Sound.UI_TOAST_CHALLENGE_COMPLETE : Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f);
        Firework fw = player.getWorld().spawn(player.getLocation().add(0, 1, 0), Firework.class, f -> {
            var m = f.getFireworkMeta();
            m.addEffect(org.bukkit.FireworkEffect.builder().withColor(r.atLeast(PetRarity.MYTHIC) ? org.bukkit.Color.RED : org.bukkit.Color.YELLOW).with(org.bukkit.FireworkEffect.Type.STAR).flicker(true).build());
            m.setPower(0); f.setFireworkMeta(m); });
        Bukkit.getScheduler().runTaskLater(this, fw::detonate, 2L);
        if (!duplicate && r.atLeast(bc)) Bukkit.broadcast(vn.soulpets.gui.Text.c("§6" + player.getName() + " §evừa ấp ra pet " + line + "§e!"));
    }

    // ---------------- listeners ----------------
    @EventHandler(priority = EventPriority.LOWEST) public void onPreLogin(org.bukkit.event.player.AsyncPlayerPreLoginEvent e) {
        try {
            PlayerProfile p = storage.loadAsync(e.getUniqueId()).get(10, TimeUnit.SECONDS); // xếp hàng sau các lần ghi đang chờ -> không đọc dữ liệu cũ
            if (p == null) { p = newProfile(e.getUniqueId()); storage.saveAsync(p); }
            profiles.put(e.getUniqueId(), p);
        } catch (Exception ex) {
            getLogger().severe("Tải dữ liệu pet thất bại cho " + e.getName() + ": " + ex);
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, net.kyori.adventure.text.Component.text("Dữ liệu pet đang lỗi, vui lòng thử lại sau ít phút."));
        }
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        Player pl = e.getPlayer(); petManager.removeBuffs(pl);
        PlayerProfile pr = profiles.get(pl.getUniqueId()); if (pr == null) { loadOnlineBlocking(pl.getUniqueId()); pr = profiles.get(pl.getUniqueId()); }
        if (pr != null && !pr.incubations().isEmpty()) {
            long now = System.currentTimeMillis(); long ready = pr.incubations().stream().filter(i -> i.readyAt() <= now).count();
            if (ready > 0) pl.sendMessage("§aBạn có §e" + ready + "§a trứng đã ấp xong! Dùng §e/pet eggs§a để nhận.");
        }
        if (getConfig().getBoolean("settings.auto-summon-on-join", false))
            Bukkit.getScheduler().runTaskLater(this, () -> { if (pl.isOnline()) petManager.summon(pl); }, 20L);
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        petManager.despawn(id); commandLimiter.forget(id); clickLimiter.forget(id);
        PlayerProfile p = profiles.remove(id); if (p != null) storage.saveAsync(p); // snapshot -> ghi bất đồng bộ, tuần tự
    }
    @EventHandler public void onEntitiesLoad(EntitiesLoadEvent e) { for (Entity en : e.getEntities()) if (petManager.isPet(en) && !petManager.tracked(en.getUniqueId())) en.remove(); }
    @EventHandler public void onEntitiesUnload(EntitiesUnloadEvent e) {
        for (Entity en : e.getEntities()) if (petManager.isPet(en)) { UUID o = petManager.ownerOf(en); en.remove(); if (o != null && petManager.tracked(en.getUniqueId())) petManager.forget(o); }
    }
    @EventHandler public void onInteract(PlayerInteractEntityEvent e) {
        if (!petManager.isPet(e.getRightClicked())) return;
        e.setCancelled(true);
        if (e.getHand() == EquipmentSlot.HAND && e.getPlayer().getUniqueId().equals(petManager.ownerOf(e.getRightClicked()))) gui.openMain(e.getPlayer());
    }
    @EventHandler public void onTarget(EntityTargetLivingEntityEvent e) {
        if (e.getTarget() == null) return;
        if (petManager.isPet(e.getEntity())) e.setCancelled(true); // pet không bao giờ tự nhắm ai (AI của plugin quyết định)
    }
    @EventHandler(priority = EventPriority.HIGH) public void onDamage(EntityDamageByEntityEvent e) {
        if (petManager.isPet(e.getDamager())) { e.setCancelled(true); return; }
        if (petManager.isPet(e.getEntity())) { e.setCancelled(true); return; }
        if (e.getEntity() instanceof Player owner && petManager.entityOf(owner.getUniqueId()) != null) combat.onOwnerHurt(owner, e.getDamager());
    }
    @EventHandler public void onBlockChange(EntityChangeBlockEvent e) { if (petManager.isPet(e.getEntity())) e.setCancelled(true); }
    @EventHandler public void onExplode(EntityExplodeEvent e) { if (petManager.isPet(e.getEntity())) e.setCancelled(true); }
    @EventHandler public void onDeath(EntityDeathEvent e) {
        LivingEntity dead = e.getEntity();
        if (petManager.isPet(dead)) { UUID o = petManager.ownerOf(dead); if (o != null) petManager.forget(o); e.getDrops().clear(); e.setDroppedExp(0); return; }
        if (!(dead instanceof Enemy)) return;
        Player killer = dead.getKiller(); if (killer == null) return;
        PlayerProfile pr = profiles.get(killer.getUniqueId()); if (pr == null) return;
        var c = getConfig();
        pr.addSouls(c.getInt("settings.souls-per-kill", 5));
        var dr = c.getConfigurationSection("drops");
        if (dr != null) for (String k : dr.getKeys(false)) if (random.nextDouble() < dr.getDouble(k + ".chance")) { int a = Math.max(1, dr.getInt(k + ".amount", 1)); pr.addMaterial(k, a); killer.sendActionBar(vn.soulpets.gui.Text.c("§7+" + a + " §f" + k)); }
        PetInstance pet = pr.active();
        if (pet != null && petManager.entityOf(killer.getUniqueId()) != null) {
            int lv = pet.addXp(c.getInt("settings.xp-per-kill", 12));
            if (lv > 0) { killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f); killer.sendMessage("§6Pet lên cấp §e" + pet.level() + "§6! (+" + lv + " điểm kỹ năng)"); petManager.refreshName(killer); petManager.applyBuffs(killer); }
        }
    }
}
