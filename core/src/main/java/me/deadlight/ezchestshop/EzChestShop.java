package me.deadlight.ezchestshop;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import com.github.Anon8281.universalScheduler.UniversalScheduler;
import com.github.Anon8281.universalScheduler.scheduling.schedulers.TaskScheduler;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
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
import me.deadlight.ezchestshop.utils.exceptions.CommandFetchException;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.deadlight.ezchestshop.utils.worldguard.FlagRegistry;
import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bstats.charts.AdvancedPie;
import org.bstats.charts.SimplePie;
import org.bstats.charts.SingleLineChart;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.ApiStatus;

public final class EzChestShop extends JavaPlugin {
    private static final Set<String> SUPPORTED_VERSIONS = ImmutableSet.<String>builder()
            .add("1.16.5")
            .add("1.17.1")
            .add("1.18.2")
            .add("1.19.4")
            .add("1.20.4")
            .add("1.20.6")
            .add("1.21")
            .add("1.21.1")
            .build();

    private static final Set<String> VERSION_SOFTDEPEND_NBTAPI = ImmutableSet.<String>builder()
            .add("1.20.6")
            .add("1.21")
            .add("1.21.1")
            .build();

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
        String minecraftVersion = Utils.getMinecraftVersion();

        if (SUPPORTED_VERSIONS.contains(minecraftVersion)) {
            getLogger().info("Minecraft version " + minecraftVersion + " detected.");
        } else {
            getLogger().severe("Unsupported version: " + minecraftVersion
                    + ". Supported versions are: " + String.join(", ", SUPPORTED_VERSIONS) + ".");
            getLogger().severe("The server will continue to load, but EzChestShop will be disabled. Be advised that this results in existing chest shops being unprotected, unless within the protected area of a separate plugin running.");
            Bukkit.getPluginManager().disablePlugin(this);
            return;
        }

        // Mojang changed some internals related to NBT and how metadata is saved after MC 1.20.4.
        // Backwards compatibility was restored using tr7zw's NBTAPI plugin for later versions, but is not neccessary for earlier versions.
        if (isItemStackToNbtUnsupported()) {
            getLogger().warning(Strings.repeat("*", 80));
            getLogger().warning("Please install the NBTAPI plugin by tr7zw in order to use the /checkprofits command: https://www.spigotmc.org/resources/nbt-api.7939/");
            getLogger().warning(Strings.repeat("*", 80));
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

        // bStats Metrics
        Metrics metrics = new Metrics(this, 10756);
        metrics.addCustomChart(new SimplePie("database_type", () -> Config.database_type.toString()));
        metrics.addCustomChart(new SimplePie("update_notification", () -> String.valueOf(Config.notify_updates)));
        metrics.addCustomChart(new SimplePie("language", () -> Config.language));

        metrics.addCustomChart(new SingleLineChart("total_shops", () -> {
            // (This is useless as there is already a player chart by default.)
            return ShopContainer.getShops().size();
        }));

        metrics.addCustomChart(new AdvancedPie("materials_used", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
           for (EzShop shop : ShopContainer.getShops()) {
                    String itemMaterial = shop.getShopItem().getType().toString();
                    if (valueMap.containsKey(itemMaterial)) {
                        valueMap.put(itemMaterial, valueMap.get(itemMaterial) + 1);
                    } else {
                        valueMap.put(itemMaterial, 1);
                    }
                }
            return valueMap;
        }));

        metrics.addCustomChart(new AdvancedPie("rotation_used", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            for (EzShop shop : ShopContainer.getShops()) {
                String rotation = shop.getSettings().getRotation();
                if (valueMap.containsKey(rotation)) {
                    valueMap.put(rotation, valueMap.get(rotation) + 1);
                } else {
                    valueMap.put(rotation, 1);
                }
            }
            return valueMap;
        }));

        metrics.addCustomChart(new AdvancedPie("is_admin_shop", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            for (EzShop shop : ShopContainer.getShops()) {
                boolean adminshop = shop.getSettings().isAdminshop();
                if (valueMap.containsKey(String.valueOf(adminshop))) {
                    valueMap.put(String.valueOf(adminshop), valueMap.get(String.valueOf(adminshop)) + 1);
                } else {
                    valueMap.put(String.valueOf(adminshop), 1);
                }
            }
            return valueMap;
        }));

        metrics.addCustomChart(new AdvancedPie("disabled_buy_count", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            for (EzShop shop : ShopContainer.getShops()) {
                boolean disabledBuy = shop.getSettings().isDbuy();
                if (valueMap.containsKey(String.valueOf(disabledBuy))) {
                    valueMap.put(String.valueOf(disabledBuy), valueMap.get(String.valueOf(disabledBuy)) + 1);
                } else {
                    valueMap.put(String.valueOf(disabledBuy), 1);
                }
            }
            return valueMap;
        }));

        metrics.addCustomChart(new AdvancedPie("disabled_sell_count", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            for (EzShop shop : ShopContainer.getShops()) {
                boolean disabledSell = shop.getSettings().isDsell();
                if (valueMap.containsKey(String.valueOf(disabledSell))) {
                    valueMap.put(String.valueOf(disabledSell), valueMap.get(String.valueOf(disabledSell)) + 1);
                } else {
                    valueMap.put(String.valueOf(disabledSell), 1);
                }
            }
            return valueMap;
        }));

        metrics.addCustomChart(new AdvancedPie("message_toggle_count", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            for (EzShop shop : ShopContainer.getShops()) {
                boolean messageToggle = shop.getSettings().isMsgtoggle();
                if (valueMap.containsKey(String.valueOf(messageToggle))) {
                    valueMap.put(String.valueOf(messageToggle), valueMap.get(String.valueOf(messageToggle)) + 1);
                } else {
                    valueMap.put(String.valueOf(messageToggle), 1);
                }
            }
            return valueMap;
        }));

        metrics.addCustomChart(new AdvancedPie("shared_income_count", () -> {
            Map<String, Integer> valueMap = new HashMap<>();
            for (EzShop shop : ShopContainer.getShops()) {
                boolean sharedIncome = shop.getSettings().isShareincome();
                if (valueMap.containsKey(String.valueOf(sharedIncome))) {
                    valueMap.put(String.valueOf(sharedIncome), valueMap.get(String.valueOf(sharedIncome)) + 1);
                } else {
                    valueMap.put(String.valueOf(sharedIncome), 1);
                }
            }
            return valueMap;
        }));

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
        checker.check();

        // The plugin started without encountering unrecoverable problems.
        started = true;
    }


    @ApiStatus.Internal
    public boolean isItemStackToNbtUnsupported() {
        if (VERSION_SOFTDEPEND_NBTAPI.contains(Utils.getMinecraftVersion())) {
            return Bukkit.getPluginManager().getPlugin("NBTAPI") == null;
        }
        // Older versions can use Minecraft internals, and as a result is not dependant on NBTAPI.
        return false;
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
