package dev.astralclash;

import com.github.retrooper.packetevents.PacketEvents;
import dev.astralclash.champion.ChampionManager;
import dev.astralclash.commands.AdminCommand;
import dev.astralclash.commands.ArenaCommand;
import dev.astralclash.config.ConfigManager;
import dev.astralclash.database.DatabaseManager;
import dev.astralclash.economy.EconomyManager;
import dev.astralclash.game.GameManager;
import dev.astralclash.listeners.GameListener;
import dev.astralclash.listeners.PlayerListener;
import dev.astralclash.player.PlayerManager;
import dev.astralclash.shop.ShopManager;
import dev.astralclash.ui.UIManager;
import io.github.retrooper.packetevents.factory.spigot.SpigotPacketEventsBuilder;
import org.bukkit.plugin.java.JavaPlugin;

public final class AstralClash extends JavaPlugin {

    private static AstralClash instance;

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private PlayerManager playerManager;
    private ChampionManager championManager;
    private EconomyManager economyManager;
    private ShopManager shopManager;
    private GameManager gameManager;
    private UIManager uiManager;

    // ── Lifecycle ────────────────────────────────────────────────────────────

    @Override
    public void onLoad() {
        PacketEvents.setAPI(SpigotPacketEventsBuilder.build(this));
        PacketEvents.getAPI().load();
    }

    @Override
    public void onEnable() {
        instance = this;

        printBanner();

        // Config
        saveDefaultConfig();
        this.configManager = new ConfigManager(this);

        // Database
        this.databaseManager = new DatabaseManager(this);
        this.databaseManager.initialize();

        // Game systems
        this.championManager = new ChampionManager(this);
        this.playerManager   = new PlayerManager(this);
        this.economyManager  = new EconomyManager(this);
        this.shopManager     = new ShopManager(this);
        this.gameManager     = new GameManager(this);
        this.uiManager       = new UIManager(this);

        // Commands
        var arenaCmd = getCommand("astral");
        var adminCmd = getCommand("astraladmin");
        if (arenaCmd != null) arenaCmd.setExecutor(new ArenaCommand(this));
        if (adminCmd != null) adminCmd.setExecutor(new AdminCommand(this));

        // Listeners
        var pm = getServer().getPluginManager();
        pm.registerEvents(new PlayerListener(this), this);
        pm.registerEvents(new GameListener(this), this);

        // PacketEvents init
        PacketEvents.getAPI().init();

        getLogger().info("AstralClash enabled — let the battles begin!");
    }

    @Override
    public void onDisable() {
        if (gameManager != null)    gameManager.shutdown();
        if (uiManager != null)      uiManager.shutdown();
        if (databaseManager != null) databaseManager.close();
        PacketEvents.getAPI().terminate();
        getLogger().info("AstralClash disabled.");
    }

    // ── Banner ───────────────────────────────────────────────────────────────

    private void printBanner() {
        String[] lines = {
            "  ___        _             _  _____ _           _   ",
            " / _\\  ___ | |_ _ __ __ _| |/ ____| | __ _ ___| |_ ",
            "/ /_\\/  __|| __| '__/ _` | | |    | |/ _` / __| __|",
            "/ /_\\ \\__ \\| |_| | | (_| | | |____| | (_| \\__ \\ |_ ",
            "\\____/ ___/ \\__|_|  \\__,_|_|\\_____|_|\\__,_|___/\\__|",
            "  Honkai: Star Rail × Minecraft TFT  v" + getDescription().getVersion()
        };
        for (String line : lines) getLogger().info(line);
    }

    // ── Accessors ────────────────────────────────────────────────────────────

    public static AstralClash getInstance()         { return instance; }
    public ConfigManager    getConfigManager()      { return configManager; }
    public DatabaseManager  getDatabaseManager()    { return databaseManager; }
    public PlayerManager    getPlayerManager()      { return playerManager; }
    public ChampionManager  getChampionManager()    { return championManager; }
    public EconomyManager   getEconomyManager()     { return economyManager; }
    public ShopManager      getShopManager()        { return shopManager; }
    public GameManager      getGameManager()        { return gameManager; }
    public UIManager        getUIManager()          { return uiManager; }
}
