package me.deadlight.ezchestshop;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import io.papermc.paper.ServerBuildInfo;
import io.papermc.paper.ServerBuildInfo.StringRepresentation;
import io.papermc.paper.plugin.configuration.PluginMeta;
import me.deadlight.ezchestshop.commands.CommandCheckProfits;
import me.deadlight.ezchestshop.commands.EcsAdmin;
import me.deadlight.ezchestshop.commands.MainCommands;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.DatabaseManager;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.data.gui.GuiData;
import me.deadlight.ezchestshop.integrations.AdvancedRegionMarketIntegration;
import me.deadlight.ezchestshop.integrations.CoreProtectIntegration;
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
import me.deadlight.ezchestshop.utils.DiscordWebhook;
import me.deadlight.ezchestshop.utils.FloatingItem;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.VersionUtil;
import me.deadlight.ezchestshop.utils.VersionUtil.MinecraftVersion;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.deadlight.ezchestshop.utils.worldguard.FlagRegistry;
import me.deadlight.ezchestshop.version.BuildInfo;
import me.deadlight.ezchestshop.version.GitHubUtil;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import static java.util.Objects.requireNonNull;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

public final class EzChestShop extends JavaPlugin {
    private static EzChestShop INSTANCE;
    private static Logger LOGGER;

    private static Economy economy = null;
    public static boolean slimefun = false;
    public static boolean towny = false;
    public static boolean worldguard = false;
    public static boolean advancedregionmarket = false;
    private static TaskScheduler scheduler;

    private boolean started = false;

    /**
     * Is this the first time EzChestShopReborn is running on this server?
     */
    private boolean isFirstRun = false;

    /**
     * Get the scheduler of the plugin
     */
    public static TaskScheduler getScheduler() {
        return scheduler;
    }

    public static Logger logger() {
        return LOGGER;
    }

    public EzChestShop() {
        EzChestShop.LOGGER = org.slf4j.LoggerFactory.getLogger(getLogger().getName());
        EzChestShop.INSTANCE = this;
    }

    @Override
    public void onLoad() {
        if (!getDataFolder().exists()) {
            isFirstRun = true;

            if (getDataFolder().mkdirs()) {
                LOGGER.debug("Created data directory.");
            } else {
                LOGGER.debug("Unable to create data directory.");
            }
        }

        // Custom WorldGuard flags must be registered before the plugin is enabled.
        // To achieve this, we create them during onLoad().
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            FlagRegistry.onLoad();
            worldguard = true;
            LOGGER.info("WorldGuard integration enabled.");
        }

        migrateDataToFork();
    }

    private void migrateDataToFork() {
        File dataFolder = getDataFolder();
        File oldDataFolder = new File(dataFolder.getParentFile(), "EzChestShop");

        if (oldDataFolder.exists()) {
            if (dataFolder.exists()) {
                LOGGER.warn("Skipping automatic migration from EzChestShop because your directory contains data from both plugins.");
            } else if (oldDataFolder.renameTo(dataFolder)) {
                LOGGER.info("Successfully renamed directory '{}' to '{}'", oldDataFolder, dataFolder);
            } else {
                LOGGER.warn("Unable to rename directory '{}' to '{}'", oldDataFolder, dataFolder);
            }
        }
    }

    @Override
    public void onEnable() {
        if (!isPaperPlatform()) {
            getLogger().log(java.util.logging.Level.SEVERE,
                    """


                    *** Spigot is not a supported platform for EzChestShopReborn! ***
                    *** Please consider using Paper. Download Paper from https://papermc.io/downloads/paper ***

                    The server will continue to load with EzChestShopReborn disabled.
                    """
            );
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        if (!Compatibility.verify()) {
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        MinecraftVersion minecraftVersion = VersionUtil.getMinecraftVersion().orElse(null);
        if (minecraftVersion == null) {
            OptionalInt dataVersion = VersionUtil.getDataVersion();
            String dataVersionInfo = dataVersion.isPresent() ? Integer.toString(dataVersion.getAsInt()) : "<Unknown>";
            LOGGER.error("Unsupported version: {} (Data version: {})", Bukkit.getVersion(), dataVersionInfo);
            LOGGER.error("Supported versions: {}", VersionUtil.getSupportedVersions());
            LOGGER.error("The server will continue to load, but EzChestShop will be disabled. "
                    + "Be advised that this results in existing chest shops being unprotected, "
                    + "unless within the protected area of a separate plugin running.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        LOGGER.info("Detected Minecraft version {} (Data version: {})",
                minecraftVersion.getVersion(),
                minecraftVersion.getDataVersion());
        LOGGER.info("Platform: {}/{}",
                ServerBuildInfo.buildInfo().brandName(),
                ServerBuildInfo.buildInfo().asString(StringRepresentation.VERSION_SIMPLE));

        scheduler = UniversalScheduler.getScheduler(this);
        saveDefaultConfig();

        try {
            Config.checkForConfigYMLupdate();
        } catch (Exception e) {
            LOGGER.warn("Uncaught exception checking for config updates", e);
        }
        Config.loadConfig();

        // load database
        if (Config.database_type != null) {
            Utils.recognizeDatabase();
        } else {
            getComponentLogger().error(text("*** Invalid database configuration! EzChestShopReborn will be disabled."), RED);
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            getComponentLogger().error(text("*** Vault and an economy implementation is required! EzChestShopReborn will be disabled.", RED));
            getComponentLogger().error(text("*** For more information, see https://github.com/nouish/EzChestShop/wiki/Integrations#vault", RED));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        if (!setupEconomy()) {
            getComponentLogger().error(text("*** No Vault-compatible economy implementation has been found! EzChestShopReborn will be disabled.", RED));
            getComponentLogger().error(text("*** For more information, see https://github.com/nouish/EzChestShop/wiki/Integrations#vault", RED));
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        PluginVersion economyImpl = findVersion(JavaPlugin.getProvidingPlugin(economy.getClass()));
        if (economyImpl != null) {
            LOGGER.info("Economy: {} ({})", economyImpl.name(), economyImpl.version());
        }

        LanguageManager.loadLanguages();
        try {
            LanguageManager.checkForLanguagesYMLupdate();
        } catch (IOException e) {
            LOGGER.warn("Uncaught IO exception during language update", e);
        }

        GuiData.loadGuiData();
        try {
            GuiData.checkForGuiDataYMLupdate();
        } catch (IOException e) {
            LOGGER.warn("Uncaught IO exception during GUI update", e);
        }

        if (getServer().getPluginManager().getPlugin("AdvancedRegionMarket") != null) {
            advancedregionmarket = true;
            LOGGER.info("AdvancedRegionMarket integration enabled.");
        }

        if (getServer().getPluginManager().getPlugin("Slimefun") != null) {
            slimefun = true;
            LOGGER.info("Slimefun integration enabled.");
        }

        if (getServer().getPluginManager().getPlugin("Towny") != null) {
            towny = true;
            LOGGER.info("Towny integration enabled.");
        }

        CoreProtectIntegration.installIfEnabled();
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

        if (isFirstRun) {
            String line = "-".repeat(40);
            LOGGER.info(line);
            LOGGER.info("");
            LOGGER.info("This seems to be your first time running {}!", getName());
            LOGGER.info("Learn more about the plugin here: {}", Constants.WIKI_LINK);
            LOGGER.info("");
            LOGGER.info(line);
        }

        // The plugin started without encountering unrecoverable problems.
        started = true;
    }

    private void checkForUpdates() {
        String currentBuildName = BuildInfo.CURRENT.isStable()
                ? BuildInfo.CURRENT.getId()
                : BuildInfo.CURRENT.getId() + " (" + BuildInfo.CURRENT.getBranch() + ")";
        LOGGER.info("Checking for updates. Current version: {}.", currentBuildName);
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
            LOGGER.warn("Failed to determine the latest version!", e);
            return;
        }

        if (status.isBehind()) {
            if (BuildInfo.CURRENT.isStable()) {
                LOGGER.warn("A newer version of EzChestShopReborn is available: {}.", latest.getId());
                LOGGER.warn("Download at: https://github.com/nouish/EzChestShop/releases/tag/{}", latest.getId());
            } else {
                LOGGER.warn("You are running an outdated snapshot of EzChestShopReborn! The latest snapshot is {} commits ahead.", status.getDistance());
                LOGGER.warn("Downloads are available from GitHub (must be logged in): {}", Constants.GITHUB_LINK);
                LOGGER.warn("Alternatively, join us on Discord ({}) for developer snapshot builds.", Constants.DISCORD_LINK);
            }
        } else if (status.isIdentical() || status.isAhead()) {
            LOGGER.info("You are running the latest version of EzChestShopReborn.");
        } else {
            LOGGER.warn("EzChestShopReborn was unable to check for newer versions.");
        }
    }

    private record PluginVersion(@NotNull String name, @NotNull String version) {
        // Just used as return type for getEconomyProvider().
    }

    @SuppressWarnings({"deprecation", "ConstantExpression", "ConstantValue"})
    @Nullable
    private static PluginVersion findVersion(@Nullable Plugin plugin) {
        if (plugin == null) {
            return null;
        }
        PluginMeta meta = plugin.getPluginMeta();
        if (meta != null) {
            return new PluginVersion(meta.getName(), meta.getVersion());
        }
        PluginDescriptionFile description = plugin.getDescription();
        return new PluginVersion(description.getName(), description.getVersion());
    }

    @Nullable
    private static PluginVersion findVersion(@NotNull String name) {
        return findVersion(Bukkit.getPluginManager().getPlugin(name));
    }

    private void registerMetrics() {
        Metrics metrics = new Metrics(this, Constants.BSTATS_PROJECT_ID);
        metrics.addCustomChart(new SimplePie("databaseType", () -> Config.database_type.getName()));
        metrics.addCustomChart(new SimplePie("updateNotification", () -> Config.notify_updates ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("discordWebhook", () -> DiscordWebhook.isEnabled() ? "Enabled" : "Disabled"));
        metrics.addCustomChart(new SimplePie("language", () -> Config.language));
        metrics.addCustomChart(new SingleLineChart("totalShopCount", () -> ShopContainer.getShops().size()));

        metrics.addCustomChart(new DrilldownPie("economyProvider", () -> {
            Map<String, Map<String, Integer>> result = new HashMap<>();
            Map<String, Integer> entry = new HashMap<>();
            PluginVersion plugin = findVersion(JavaPlugin.getProvidingPlugin(economy.getClass()));
            if (plugin != null) {
                entry.put(plugin.version(), 1);
                result.put(plugin.name(), entry);
            }
            return result;
        }));

        // Curious about adoptation considering how CoreProtect is distributed these days.
        metrics.addCustomChart(new SimplePie("intCoreProtect", () -> {
            PluginVersion plugin = findVersion("CoreProtect");
            return plugin != null ? plugin.version() : null;
        }));

        metrics.addCustomChart(new SimplePie("intTowny", () -> {
            PluginVersion plugin = findVersion("Towny");
            return plugin != null ? plugin.version() : null;
        }));

        metrics.addCustomChart(new SimplePie("intWorldGuard", () -> {
            PluginVersion plugin = findVersion("WorldGuard");
            return plugin != null ? plugin.version() : null;
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
            pluginManager.registerEvents(new AdvancedRegionMarketIntegration(), this);
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
        return requireNonNull(getCommand(name), () -> "Undefined command: " + name + ".");
    }

    @Override
    public void onDisable() {
        if (!started) {
            return;
        }

        // Plugin shutdown logic
        if (scheduler != null) {
            scheduler.cancelTasks();
        }

        LOGGER.info("Saving remaining items in SQL cache...");
        ShopContainer.saveSqlQueueCache();
        getDatabase().disconnect();
        LOGGER.info("Save completed.");

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

    @NotNull
    public static EzChestShop getPlugin() {
        return requireNonNull(EzChestShop.INSTANCE, "Plugin instance unavailable!");
    }

    private static boolean isPaperPlatform() {
        try {
            Class.forName("io.papermc.paper.ServerBuildInfo");
            return true;
        } catch (ClassNotFoundException ignored) {
            return false;
        }
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
