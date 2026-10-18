package vn.soulpets;

import java.io.File;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import vn.soulpets.combat.CombatListener;
import vn.soulpets.commands.PetAdminCommand;
import vn.soulpets.commands.PetCommand;
import vn.soulpets.config.GameData;
import vn.soulpets.config.Settings;
import vn.soulpets.dungeon.DungeonManager;
import vn.soulpets.egg.EggService;
import vn.soulpets.equipment.EquipmentService;
import vn.soulpets.gui.GuiListener;
import vn.soulpets.gui.Menus;
import vn.soulpets.mythiclib.MythicLibHook;
import vn.soulpets.mythicmobs.MythicMobsHook;
import vn.soulpets.pet.LifecycleListener;
import vn.soulpets.pet.PetManager;
import vn.soulpets.pet.PetService;
import vn.soulpets.pet.PlayerProfile;
import vn.soulpets.placeholder.PapiExpansion;
import vn.soulpets.skills.SkillExecutor;
import vn.soulpets.storage.ProfileManager;
import vn.soulpets.storage.SqlStorage;
import vn.soulpets.util.Debug;

/** Astrax Pet RPG 2.0 - lớp chính, nối các module lại với nhau. */
public final class SoulPetsPlugin extends JavaPlugin {
    private Settings settings; private GameData data; private ProfileManager profiles; private PetManager pets; private PetService service;
    private SkillExecutor skills; private EggService eggs; private EquipmentService equipment; private DungeonManager dungeons; private Menus gui;
    private MythicMobsHook mythic; private MythicLibHook mythicLib; private org.bukkit.scheduler.BukkitTask autosave;

    public Settings settings() { return settings; }
    public GameData data() { return data; }
    public ProfileManager profiles() { return profiles; }
    public PetManager pets() { return pets; }
    public PetService service() { return service; }
    public SkillExecutor skills() { return skills; }
    public EggService eggs() { return eggs; }
    public EquipmentService equipment() { return equipment; }
    public DungeonManager dungeons() { return dungeons; }
    public Menus gui() { return gui; }
    public MythicMobsHook mythic() { return mythic; }
    public MythicLibHook mythicLib() { return mythicLib; }

    @Override public void onEnable() {
        saveDefaultConfig();
        settings = new Settings(); settings.load(getConfig());
        Debug.init(getLogger(), settings.debug);
        data = GameData.load(this);
        try {
            SqlStorage storage = createStorage();
            storage.init();
            profiles = new ProfileManager(this, storage, settings.startingSouls);
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Không khởi tạo được cơ sở dữ liệu, tắt plugin để tránh mất dữ liệu.", e);
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }
        mythic = new MythicMobsHook(getLogger(), settings.hookMythicMobs, Bukkit.getPluginManager().getPlugin("MythicMobs") != null);
        mythicLib = new MythicLibHook(getLogger(), settings.hookMythicLib, Bukkit.getPluginManager().getPlugin("MythicLib") != null);
        pets = new PetManager(this); service = new PetService(this); skills = new SkillExecutor(this); eggs = new EggService(this);
        equipment = new EquipmentService(this); dungeons = new DungeonManager(this); gui = new Menus(this);

        var pm = Bukkit.getPluginManager();
        pm.registerEvents(new LifecycleListener(this), this); pm.registerEvents(new CombatListener(this), this); pm.registerEvents(new GuiListener(this), this);
        bind("pet", new PetCommand(this)); bind("petadmin", new PetAdminCommand(this));

        for (Player p : Bukkit.getOnlinePlayers()) {          // /reload hoặc plugin load muộn
            try { PlayerProfile pr = profiles.loadBlocking(p.getUniqueId(), p.getName()); pets.removeBuffs(p); service.recalcScore(pr); } catch (Exception e) { p.kickPlayer("§cLỗi dữ liệu pet."); }
        }
        for (var w : Bukkit.getWorlds()) for (var e : w.getEntities()) if (pets.isPet(e)) e.remove();   // dọn pet mồ côi
        pets.start(); dungeons.start();
        scheduleAutosave();
        if (settings.hookPapi && pm.getPlugin("PlaceholderAPI") != null) { new PapiExpansion(this).register(); getLogger().info("Đã đăng ký PlaceholderAPI (%astrax_...%)."); }
        getLogger().info("Astrax Pet RPG " + getPluginMeta().getVersion() + " đã sẵn sàng.");
    }
    private void bind(String name, Object handler) {
        PluginCommand c = getCommand(name);
        if (c == null) { getLogger().severe("Lệnh /" + name + " chưa khai báo trong plugin.yml!"); return; }
        c.setExecutor((org.bukkit.command.CommandExecutor) handler); c.setTabCompleter((org.bukkit.command.TabCompleter) handler);
    }
    private SqlStorage createStorage() {
        var c = getConfig();
        if ("mysql".equalsIgnoreCase(c.getString("database.type", "sqlite"))) {
            String url = "jdbc:mysql://" + c.getString("database.mysql.host", "localhost") + ":" + c.getInt("database.mysql.port", 3306) + "/" + c.getString("database.mysql.database", "astrax") + "?" + c.getString("database.mysql.params", "useSSL=false");
            getLogger().info("Dùng MySQL: " + c.getString("database.mysql.host"));
            return new SqlStorage(url, c.getString("database.mysql.user", "root"), c.getString("database.mysql.password", ""), true, getLogger());
        }
        if (!getDataFolder().exists()) getDataFolder().mkdirs();
        try { Class.forName("org.sqlite.JDBC"); } catch (ClassNotFoundException e) { getLogger().warning("Chưa thấy driver SQLite (libraries trong plugin.yml sẽ tải khi khởi động server)."); }
        File f = new File(getDataFolder(), c.getString("database.sqlite-file", "astrax.db"));
        return new SqlStorage("jdbc:sqlite:" + f.getAbsolutePath(), null, null, false, getLogger());
    }
    private void scheduleAutosave() {
        if (autosave != null) autosave.cancel();
        long ticks = settings.autosaveMinutes * 60L * 20L;
        autosave = Bukkit.getScheduler().runTaskTimer(this, () -> profiles.saveDirtyAsync(), ticks, ticks);
    }
    /** /petadmin reload */
    public void reloadAll() {
        reloadConfig(); settings.load(getConfig()); Debug.set(settings.debug); data = GameData.load(this);
        pets.start(); scheduleAutosave();
        for (var a : pets.all()) pets.recalc(a);
    }
    @Override public void onDisable() {
        if (autosave != null) autosave.cancel();
        if (dungeons != null) { dungeons.stop(); dungeons.stopAll(); }
        if (pets != null) { pets.stop(); pets.removeAll(); }
        if (profiles != null) profiles.shutdown();
    }
}
