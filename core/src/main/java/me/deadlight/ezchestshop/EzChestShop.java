package me.deadlight.ezchestshop;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import me.deadlight.ezchestshop.commands.CommandCheckProfits;
import me.deadlight.ezchestshop.commands.EcsAdmin;
import me.deadlight.ezchestshop.commands.MainCommands;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.DatabaseManager;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.data.gui.GuiData;
import me.deadlight.ezchestshop.integrations.AdvancedRegionMarket;
import me.deadlight.ezchestshop.listeners.BlockBreakListener;
import me.deadlight.ezchestshop.listeners.BlockPistonExtendListener;
import me.deadlight.ezchestshop.listeners.BlockPlaceListener;
import me.deadlight.ezchestshop.listeners.ChatListener;
import me.deadlight.ezchestshop.listeners.ChestOpeningListener;
import me.deadlight.ezchestshop.listeners.ChestShopBreakPrevention;
import me.deadlight.ezchestshop.listeners.PlayerCloseToChestListener;
import me.deadlight.ezchestshop.listeners.PlayerJoinListener;
import me.deadlight.ezchestshop.listeners.PlayerLeavingListener;
import me.deadlight.ezchestshop.listeners.PlayerLookingAtChestShop;
import me.deadlight.ezchestshop.listeners.PlayerTransactionListener;
import me.deadlight.ezchestshop.listeners.UpdateChecker;
import me.deadlight.ezchestshop.tasks.LoadedChunksTask;
import me.deadlight.ezchestshop.utils.ASHologram;
import me.deadlight.ezchestshop.utils.BlockOutline;
import me.deadlight.ezchestshop.utils.FloatingItem;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.VersionUtil;
import me.deadlight.ezchestshop.utils.VersionUtil.MinecraftVersion;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.deadlight.ezchestshop.utils.worldguard.FlagRegistry;
import me.deadlight.ezchestshop.version.BuildInfo;
import me.deadlight.ezchestshop.version.GitHubUtil;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

public final class EzChestShop extends JavaPlugin {
    private static Economy economy = null;
    public static boolean economyPluginFound = true;

    public static boolean slimefun = false;
    public static boolean towny = false;
    public static boolean worldguard = false;
    public static boolean advancedregionmarket = false;
    private static boolean coreProtect;

    private static CoreProtectAPI coreProtectAPI;

    private static TaskScheduler scheduler;

    private boolean started = false;

    /**
     * Get the scheduler of the plugin
     */
    public static TaskScheduler getScheduler() {
        return scheduler;
    }

    public static Logger logger() {
        return getPlugin().getSLF4JLogger();
    }

    @Override
    public void onLoad() {
        // Adds Custom Flags to WorldGuard!
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            worldguard = true;
            FlagRegistry.onLoad();
        }

        migrateDataToFork();
    }

    private void migrateDataToFork() {
        Logger logger = LoggerFactory.getLogger(getLogger().getName());
        File dataFolder = getDataFolder();
        File oldDataFolder = new File(dataFolder.getParentFile(), "EzChestShop");

        if (oldDataFolder.exists()) {
            if (dataFolder.exists()) {
                logger.warn("Skipping automatic migration from EzChestShop because your directory contains data from both plugins.");
            } else if (oldDataFolder.renameTo(dataFolder)) {
                logger.info("Successfully renamed directory '{}' to '{}'", oldDataFolder, dataFolder);
            } else {
                logger.warn("Unable to rename directory '{}' to '{}'", oldDataFolder, dataFolder);
            }
        }
    }

    @Override
    public void onEnable() {
        if (Bukkit.getName().equalsIgnoreCase("Spigot")) {
            // Use JUL logger because Spigot doesn't have getSLF4JLogger().
            getLogger().log(Level.SEVERE,
                    """


                    *** Spigot is not a supported platform for EzChestShopReborn! ***
                    *** Please consider using Paper. Download Paper from https://papermc.io/downloads/paper ***

                    The server will continue to load with EzChestShopReborn disabled.
                    """
            );
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        MinecraftVersion minecraftVersion = VersionUtil.getMinecraftVersion().orElse(null);

        if (minecraftVersion == null) {
            OptionalInt dataVersion = VersionUtil.getDataVersion();
            String dataVersionInfo = dataVersion.isPresent() ? Integer.toString(dataVersion.getAsInt()) : "<Unknown>";
            logger().error("Unsupported version: {} (Data version: {})", Bukkit.getVersion(), dataVersionInfo);
            logger().error("Supported versions: {}", VersionUtil.getSupportedVersions());
            logger().error("The server will continue to load, but EzChestShop will be disabled. "
                    + "Be advised that this results in existing chest shops being unprotected, "
                    + "unless within the protected area of a separate plugin running.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        logger().info("Detected Minecraft version {} (Data version: {})", minecraftVersion.getVersion(), minecraftVersion.getDataVersion());

        scheduler = UniversalScheduler.getScheduler(this);
        saveDefaultConfig();

        try {
            Config.checkForConfigYMLupdate();
        } catch (Exception e) {
            logger().warn("Uncaught exception checking for config updates", e);
        }
        Config.loadConfig();

        // load database
        if (Config.database_type != null) {
            Utils.recognizeDatabase();
        } else {
            logger().error("Database type not specified/or is wrong in config.yml! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economyPluginFound = setupEconomy();
        if (!economyPluginFound) {
            Config.useXP = true;
            logger().warn("*** Cannot find vault or economy plugin. Switching to XP based economy... Please note that you need vault and at least one economy plugin installed to use a money based system.");
            getComponentLogger().warn(text("*** This fallback feature (XP based economy) will be removed in future versions of EzChestShopReborn!", RED));
        }

        LanguageManager.loadLanguages();
        try {
            LanguageManager.checkForLanguagesYMLupdate();
        } catch (IOException e) {
            logger().warn("Uncaught IO exception during language update", e);
        }

        GuiData.loadGuiData();
        try {
            GuiData.checkForGuiDataYMLupdate();
        } catch (IOException e) {
            logger().warn("Uncaught IO exception during GUI update", e);
        }

        if (getServer().getPluginManager().getPlugin("AdvancedRegionMarket") != null) {
            advancedregionmarket = true;
            logger().info("AdvancedRegionMarket integration enabled.");
        }

        if (getServer().getPluginManager().getPlugin("Slimefun") != null) {
            slimefun = true;
            logger().info("Slimefun integration enabled.");
        }

        if (getServer().getPluginManager().getPlugin("Towny") != null) {
            towny = true;
            logger().info("Towny integration enabled.");
        }

        Plugin coPlugin = getServer().getPluginManager().getPlugin("CoreProtect");
        // Must check if null first, because the class CoreProtect will otherwise be unavailable on classpath.
        //noinspection ConditionCoveredByFurtherCondition
        if (coPlugin != null && coPlugin instanceof CoreProtect co) {
            coreProtectAPI = co.getAPI();
            coreProtect = true;
            logger().info("CoreProtect integration enabled.");
        }

        registerListeners();
        registerCommands();
        registerTabCompleters();
        registerMetrics();

        ShopContainer.queryShopsToMemory();
        ShopContainer.startSqlQueueTask();

        if (Config.check_for_removed_shops) {
            LoadedChunksTask.startTask();
        }

        UpdateChecker checker = new UpdateChecker();
        checker.checkGuiUpdate();

        // TODO automatic version check
        if (Config.notify_updates) {
            final long tickDurationInMillis = 50;
            getScheduler().runTaskTimerAsynchronously(
                    this::checkForUpdates,
                    Math.divideExact(TimeUnit.SECONDS.toMillis(30), tickDurationInMillis),
                    Math.divideExact(TimeUnit.HOURS.toMillis(6), tickDurationInMillis));
        }

        // The plugin started without encountering unrecoverable problems.
        started = true;
    }

    public void tellCoreProtectToTrackChangesAt(@NotNull Player player, @NotNull Location location) {
        if (!coreProtect || !Config.coreprotect_integration || coreProtectAPI == null) {
            // CoreProtect integration disabled.
            return;
        }

        // This indicates to CoreProtect that it should track changes to the inventory at this
        // location immediately following this call.
        coreProtectAPI.logContainerTransaction(player.getName(), location);
    }

    private void checkForUpdates() {
        String currentBuildName = BuildInfo.CURRENT.isStable()
                ? BuildInfo.CURRENT.getId()
                : BuildInfo.CURRENT.getId() + " (" + BuildInfo.CURRENT.getBranch() + ")";
        logger().info("Checking for updates. Current version: {}.", currentBuildName);
        BuildInfo latest = null;
        GitHubUtil.GitHubStatusLookup status;

        try {
            if (BuildInfo.CURRENT.isStable()) {
                latest = GitHubUtil.lookupLatestRelease();
                status = GitHubUtil.compare(latest.getId(), BuildInfo.CURRENT.getId());
            } else {
                status = GitHubUtil.compare(BuildInfo.CURRENT.getBranch(), BuildInfo.CURRENT.getId());
            }
        } catch (IOException e) {
            logger().warn("Failed to determine the latest version!", e);
            return;
        }

        if (status.isBehind()) {
            if (BuildInfo.CURRENT.isStable()) {
                logger().warn("A newer version of EzChestShopReborn is available: {}.", latest.getId());
                logger().warn("Download at: https://github.com/nouish/EzChestShop/releases/tag/{}", latest.getId());
            } else {
                logger().warn("You are running an outdated snapshot of EzChestShopReborn! The latest snapshot is {} commits ahead.", status.getDistance());
                logger().warn("Downloads are available from GitHub (must be logged in): {}", EzChestShopConstants.GITHUB_LINK);
            }
        } else if (status.isIdentical() || status.isAhead()) {
            logger().info("You are running the latest version of EzChestShopReborn.");
        } else {
            logger().warn("EzChestShopReborn was unable to check for newer versions.");
        }
    }

    private void registerMetrics() {
        Metrics metrics = new Metrics(this, EzChestShopConstants.BSTATS_PROJECT_ID);
        metrics.addCustomChart(new SimplePie("databaseType", () -> Config.database_type.getName()));
        metrics.addCustomChart(new SimplePie("updateNotification", () -> Config.notify_updates ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("language", () -> Config.language));
        metrics.addCustomChart(new SingleLineChart("totalShopCount", () -> ShopContainer.getShops().size()));

        metrics.addCustomChart(new DrilldownPie("economyBackend", () -> {
            Map<String, Map<String, Integer>> result = new HashMap<>();
            Map<String, Integer> entry = new HashMap<>();
            if (economyPluginFound) {
                entry.put(economy.getName(), 1);
                result.put("Vault", entry);
            } else {
                entry.put("XP", 1);
                result.put("XP", entry);
            }
            return result;
        }));

        metrics.addCustomChart(new DrilldownPie("playerLanguage", () -> {
            // Player language metrics are used to understand what translations would have the biggest impact.
            Map<String, Map<String, Integer>> result = new HashMap<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                Locale locale = player.locale();
                String localeString = locale.toString().toLowerCase(Locale.ROOT);
                String language = locale.getDisplayLanguage(Locale.ENGLISH);
                Map<String, Integer> entry = result.computeIfAbsent(language, ignored -> new HashMap<>());
                entry.merge(localeString, 1, Integer::sum);
            }

            return result;
        }));

        metrics.addCustomChart(new AdvancedPie("stockMaterial", () -> {
            Map<String, Integer> result = new HashMap<>();
            for (EzShop shop : ShopContainer.getShops()) {
                String material = shop.getShopItem().getType().getKey().toString();
                result.merge(material, 1, Integer::sum);
            }
            return result;
        }));

        metrics.addCustomChart(new AdvancedPie("rotation", () -> {
            Map<String, Integer> result = new HashMap<>();
            for (EzShop shop : ShopContainer.getShops()) {
                String rotation = shop.getSettings().getRotation().toLowerCase(Locale.ROOT);
                result.merge(rotation, 1, Integer::sum);
            }
            return result;
        }));

        metrics.addCustomChart(new AdvancedPie("stockType", () -> {
            Map<String, Integer> result = new HashMap<>();
            for (EzShop shop : ShopContainer.getShops()) {
                String stock = shop.getSettings().isAdminshop() ? "Admin" : "Regular";
                result.merge(stock, 1, Integer::sum);
            }
            return result;
        }));

        metrics.addCustomChart(new AdvancedPie("buyToggle", () -> {
            Map<String, Integer> result = new HashMap<>();
            for (EzShop shop : ShopContainer.getShops()) {
                String state = shop.getSettings().isDbuy() ? "Disabled" : "Enabled";
                result.merge(state, 1, Integer::sum);
            }
            return result;
        }));

        metrics.addCustomChart(new AdvancedPie("sellToggle", () -> {
            Map<String, Integer> result = new HashMap<>();
            for (EzShop shop : ShopContainer.getShops()) {
                String state = shop.getSettings().isDsell() ? "Disabled" : "Enabled";
                result.merge(state, 1, Integer::sum);
            }
            return result;
        }));

        metrics.addCustomChart(new AdvancedPie("shareIncomeToggle", () -> {
            Map<String, Integer> result = new HashMap<>();
            for (EzShop shop : ShopContainer.getShops()) {
                String state = shop.getSettings().isShareincome() ? "Enabled" : "Disabled";
                result.merge(state, 1, Integer::sum);
            }
            return result;
        }));

        metrics.addCustomChart(new AdvancedPie("notificationToggle", () -> {
            Map<String, Integer> result = new HashMap<>();
            for (EzShop shop : ShopContainer.getShops()) {
                String state = shop.getSettings().isMsgtoggle() ? "Enabled" : "Disabled";
                result.merge(state, 1, Integer::sum);
            }
            return result;
        }));
    }

    private void registerListeners() {
        PluginManager pluginManager = getServer().getPluginManager();
        pluginManager.registerEvents(new ChestOpeningListener(), this);
        pluginManager.registerEvents(new BlockBreakListener(), this);
        pluginManager.registerEvents(new BlockPlaceListener(), this);
        pluginManager.registerEvents(new PlayerTransactionListener(), this);
        pluginManager.registerEvents(new ChatListener(), this);
        pluginManager.registerEvents(new BlockPistonExtendListener(), this);
        pluginManager.registerEvents(new CommandCheckProfits(), this);
        pluginManager.registerEvents(new UpdateChecker(), this);
        pluginManager.registerEvents(new PlayerJoinListener(), this);
        pluginManager.registerEvents(new ChestShopBreakPrevention(), this);
        // Add Config check over here, to change the Shop display varient.
        // PlayerLooking is less laggy but probably harder to spot.
        if (Config.holodistancing) {
            pluginManager.registerEvents(new PlayerCloseToChestListener(), this);
        } else {
            pluginManager.registerEvents(new PlayerLookingAtChestShop(), this);
            pluginManager.registerEvents(new PlayerLeavingListener(), this);
        }
        //This is for integration with AdvancedRegionMarket
        if (advancedregionmarket) {
            pluginManager.registerEvents(new AdvancedRegionMarket(), this);
        }
    }

    private void registerCommands() {
        PluginCommand ecs = getCommandOrThrow("ecs");
        PluginCommand ecsadmin = getCommandOrThrow("ecsadmin");
        ecs.setExecutor(new MainCommands());
        ecsadmin.setExecutor(new EcsAdmin());
        getCommandOrThrow("checkprofits").setExecutor(new CommandCheckProfits());
    }

    private void registerTabCompleters() {
        getCommandOrThrow("ecs").setTabCompleter(new MainCommands());
        getCommandOrThrow("ecsadmin").setTabCompleter(new EcsAdmin());
        getCommandOrThrow("checkprofits").setTabCompleter(new CommandCheckProfits());
    }

    private @NotNull PluginCommand getCommandOrThrow(@NotNull String name) {
        return Objects.requireNonNull(getCommand(name), () -> "Undefined command: " + name + ".");
    }

    @Override
    public void onDisable() {
        if (!started) {
            return;
        }

        // Plugin shutdown logic
        if (scheduler != null)
            scheduler.cancelTasks();

        logger().info("Saving remaining items in SQL cache...");
        ShopContainer.saveSqlQueueCache();
        getDatabase().disconnect();
        logger().info("Save completed.");

        try {
            for (Object object : Utils.onlinePackets) {
                if (object instanceof ASHologram hologram) {
                    hologram.destroy();
                    continue;
                }
                if (object instanceof FloatingItem floatingItem) {
                    floatingItem.destroy();
                }
            }
        } catch (Exception ignored) {
        }

        try {
            for (BlockOutline outline : Utils.activeOutlines.values()) {
                outline.hideOutline();
            }

            Utils.activeOutlines.clear();
            Utils.enabledOutlines.clear();
        } catch (Exception ignored) {
        }
    }

    public static EzChestShop getPlugin() {
        return getPlugin(EzChestShop.class);
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return true;
    }

    public static Economy getEconomy() {
        return economy;
    }

    public DatabaseManager getDatabase() {
        return Utils.databaseManager;
    }

}
