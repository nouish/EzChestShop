package me.deadlight.ezchestshop;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.OptionalInt;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import de.tr7zw.changeme.nbtapi.NBT;
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
import me.deadlight.ezchestshop.utils.CommandRegister;
import me.deadlight.ezchestshop.utils.FloatingItem;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.VersionUtil;
import me.deadlight.ezchestshop.utils.VersionUtil.MinecraftVersion;
import me.deadlight.ezchestshop.utils.exceptions.CommandFetchException;
import me.deadlight.ezchestshop.utils.worldguard.FlagRegistry;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.DrilldownPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class EzChestShop extends JavaPlugin {
    private static Economy econ = null;
    public static boolean economyPluginFound = true;

    public static boolean slimefun = false;
    public static boolean towny = false;
    public static boolean worldguard = false;
    public static boolean advancedregionmarket = false;

    private static TaskScheduler scheduler;

    private boolean started = false;

    /**
     * Get the scheduler of the plugin
     */
    public static TaskScheduler getScheduler() {
        return scheduler;
    }

    @Override
    public void onLoad() {
        // Adds Custom Flags to WorldGuard!
        if (getServer().getPluginManager().getPlugin("WorldGuard") != null) {
            worldguard = true;
            FlagRegistry.onLoad();
        }
    }

    @Override
    public void onEnable() {
        Logger logger = LoggerFactory.getLogger(getLogger().getName());
        MinecraftVersion minecraftVersion = VersionUtil.getMinecraftVersion().orElse(null);

        if (minecraftVersion == null) {
            OptionalInt dataVersion = VersionUtil.getDataVersion();
            String dataVersionInfo = dataVersion.isPresent() ? String.valueOf(dataVersion.getAsInt()) : "Unknown";
            logger.error("Unsupported version: {} (Data version: {})", Bukkit.getVersion(), dataVersionInfo);
            logger.error("Supported versions: {}", VersionUtil.getSupportedVersions());
            logger.error("The server will continue to load, but EzChestShop will be disabled. "
                    + "Be advised that this results in existing chest shops being unprotected, "
                    + "unless within the protected area of a separate plugin running.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        logger.info("Detected Minecraft version {} (Data version: {})", minecraftVersion.getVersion(), minecraftVersion.getDataVersion());

        if (!NBT.preloadApi()) {
            logger.warn("The bundled NBT API is not compatible with this Minecraft version.");
        }


        scheduler = UniversalScheduler.getScheduler(this);
        saveDefaultConfig();

        try {
            Config.checkForConfigYMLupdate();
        } catch (Exception e) {
            e.printStackTrace();
        }
        Config.loadConfig();

        // load database
        if (Config.database_type != null) {
            Utils.recognizeDatabase();
        } else {
            logConsole(
                    "&c[&eEzChestShop&c] &cDatabase type not specified/or is wrong in config.yml! Disabling plugin...");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        economyPluginFound = setupEconomy();
        if (!economyPluginFound) {
            Config.useXP = true;
            logConsole(
            "&c[&eEzChestShop&c] &4Cannot find vault or economy plugin. Switching to XP based economy... " +
                "&ePlease note that you need vault and at least one economy plugin installed to use a money based system.");
//            Bukkit.getPluginManager().disablePlugin(this);
//            return;
        }

        LanguageManager.loadLanguages();
        try {
            LanguageManager.checkForLanguagesYMLupdate();
        } catch (IOException e) {
            e.printStackTrace();
        }

        GuiData.loadGuiData();
        try {
            GuiData.checkForGuiDataYMLupdate();
        } catch (IOException e) {
            e.printStackTrace();
        }

        //check if plugin "AdvancedRegionMarket" is installed
        if (getServer().getPluginManager().getPlugin("AdvancedRegionMarket") != null) {
            advancedregionmarket = true;
            logConsole("&c[&eEzChestShop&c] &eAdvancedRegionMarket integration initialized.");
        }

        registerListeners();
        registerCommands();
        registerTabCompleters();
        registerMetrics();

        if (getServer().getPluginManager().getPlugin("Slimefun") != null) {
            slimefun = true;
            logConsole("&c[&eEzChestShop&c] &eSlimefun integration initialized.");
        }

        if (getServer().getPluginManager().getPlugin("Towny") != null) {
            towny = true;
            logConsole("&c[&eEzChestShop&c] &eTowny integration initialized.");
        }

        ShopContainer.queryShopsToMemory();
        ShopContainer.startSqlQueueTask();
        if (Config.check_for_removed_shops) {
            LoadedChunksTask.startTask();
        }

        UpdateChecker checker = new UpdateChecker();
        checker.checkGuiUpdate();

        // TODO automatic version check
        if (Config.notify_updates) {
            logger.warn("Automatic version check is enabled, but temporarily unavailable.");
            logger.warn("Please follow either of these sources for up to date versions of EzChestShopReborn:");
            logger.warn("GitHub: https://github.com/nouish/EzChestShop");
            logger.warn("Discord: https://discord.gg/invite/gjV6BgKxFV");
        }

        // The plugin started without encountering unrecoverable problems.
        started = true;
    }

    private void registerMetrics() {
        Metrics metrics = new Metrics(this, EzChestShopConstants.BSTATS_PROJECT_ID);

        metrics.addCustomChart(new DrilldownPie("economyBackend", () -> {
            Map<String, Map<String, Integer>> result = new HashMap<>();
            Map<String, Integer> entry = new HashMap<>();
            if (economyPluginFound) {
                entry.put(econ.getName(), 1);
                result.put("Vault", entry);
            } else {
                entry.put("XP", 1);
                result.put("XP", entry);
            }
            return result;
        }));

        metrics.addCustomChart(new AdvancedPie("language", () -> {
            Map<String, Integer> result = new HashMap<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                //noinspection deprecation
                String locale = player.getLocale();
                result.put(locale, result.getOrDefault(locale, 0) + 1);
            }
            return result;
        }));


        metrics.addCustomChart(new SimplePie("databaseType", () -> Config.database_type.getName()));
        metrics.addCustomChart(new SimplePie("update_notification", () -> String.valueOf(Config.notify_updates)));
        metrics.addCustomChart(new SimplePie("language", () -> Config.language));
        metrics.addCustomChart(new SingleLineChart("totalShopCount", () -> ShopContainer.getShops().size()));
    }

    private void registerListeners() {
        getServer().getPluginManager().registerEvents(new ChestOpeningListener(), this);
        getServer().getPluginManager().registerEvents(new BlockBreakListener(), this);
        getServer().getPluginManager().registerEvents(new BlockPlaceListener(), this);
        getServer().getPluginManager().registerEvents(new PlayerTransactionListener(), this);
        getServer().getPluginManager().registerEvents(new ChatListener(), this);
        getServer().getPluginManager().registerEvents(new BlockPistonExtendListener(), this);
        getServer().getPluginManager().registerEvents(new CommandCheckProfits(), this);
        getServer().getPluginManager().registerEvents(new UpdateChecker(), this);
        getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
        getServer().getPluginManager().registerEvents(new ChestShopBreakPrevention(), this);
        // Add Config check over here, to change the Shop display varient.
        // PlayerLooking is less laggy but probably harder to spot.
        if (Config.holodistancing) {
            getServer().getPluginManager().registerEvents(new PlayerCloseToChestListener(), this);
        } else {
            getServer().getPluginManager().registerEvents(new PlayerLookingAtChestShop(), this);
            getServer().getPluginManager().registerEvents(new PlayerLeavingListener(), this);
        }
        //This is for integration with AdvancedRegionMarket
        if (advancedregionmarket) {
            getServer().getPluginManager().registerEvents(new AdvancedRegionMarket(), this);
        }

    }

    private void registerCommands() {
        PluginCommand ecs = getCommand("ecs");
        PluginCommand ecsadmin = getCommand("ecsadmin");
        CommandRegister register = new CommandRegister();
        try {
            if (Config.command_shop_alias) {
                register.registerCommandAlias(ecs, "shop");
            }
            if (Config.command_adminshop_alias) {
                register.registerCommandAlias(ecsadmin, "adminshop");
            }
        } catch (CommandFetchException e) {
            e.printStackTrace();
        }
        ecs.setExecutor(new MainCommands());
        ecsadmin.setExecutor(new EcsAdmin());
        getCommand("checkprofits").setExecutor(new CommandCheckProfits());
    }

    private void registerTabCompleters() {
        getCommand("ecs").setTabCompleter(new MainCommands());
        getCommand("ecsadmin").setTabCompleter(new EcsAdmin());
        getCommand("checkprofits").setTabCompleter(new CommandCheckProfits());
    }

    @Override
    public void onDisable() {
        if (!started) {
            return;
        }

        // Plugin shutdown logic
        if(scheduler != null)
            scheduler.cancelTasks();
        logConsole("&c[&eEzChestShop&c] &bSaving remained sql cache...");
        ShopContainer.saveSqlQueueCache();

        getDatabase().disconnect();

        logConsole("&c[&eEzChestShop&c] &aCompleted. ");

        try {
            for (Object object : Utils.onlinePackets) {

                if (object instanceof ASHologram) {
                    ASHologram hologram = (ASHologram) object;
                    hologram.destroy();
                    continue;
                }
                if (object instanceof FloatingItem) {
                    FloatingItem floatingItem = (FloatingItem) object;
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

        logConsole("&c[&eEzChestShop&c] &4Plugin is now disabled. ");
    }

    public static EzChestShop getPlugin() {
        return getPlugin(EzChestShop.class);
    }

    public static void logConsole(String str) {
        EzChestShop.getPlugin().getServer().getConsoleSender().sendMessage(Utils.colorify(str));
    }

    public static void logDebug(String str) {
        if (Config.debug_logging)
            EzChestShop.getPlugin().getServer().getConsoleSender().sendMessage("[Ecs-Debug] " + Utils.colorify(str));
    }

    private boolean setupEconomy() {
        if (getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        econ = rsp.getProvider();
        return econ != null;
    }

    public static Economy getEconomy() {
        return econ;
    }

    public DatabaseManager getDatabase() {
        return Utils.databaseManager;
    }

}
