package me.deadlight.ezchestshop.data;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.enums.Database;
import me.deadlight.ezchestshop.utils.DiscordWebhook;
import me.deadlight.ezchestshop.utils.logging.ExtendedLogger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

public final class Config {
    private static final ExtendedLogger LOGGER = EzChestShop.logger();

    public static String currency;
    public static boolean showholo;
    public static List<String> holostructure;
    public static List<String> holostructure_admin;
    public static double holo_linespacing;
    public static int holodelay;
    public static boolean holo_rotation;
    public static boolean holodistancing;
    public static double holodistancing_distance;
    public static boolean holodistancing_show_item_first;


    public static boolean container_chests;
    public static boolean container_trapped_chests;
    public static boolean container_barrels;
    public static boolean container_shulkers;

    public static String display_numberformat_gui;
    public static String display_numberformat_chat;
    public static String display_numberformat_holo;

    public static boolean settings_defaults_transactions;
    public static boolean settings_defaults_dbuy;
    public static boolean settings_defaults_dsell;
    public static String settings_defaults_rotation;
    public static boolean settings_defaults_shareprofits;

    public static boolean settings_zero_equals_disabled;
    public static boolean settings_buy_greater_than_sell;
    public static boolean settings_add_shulkershop_lore;
    public static boolean settings_custom_amout_transactions;

    public static boolean settings_hologram_message_enabled;
    public static boolean settings_hologram_message_show_always;
    public static int settings_hologram_message_line_count_default;
    public static boolean settings_hologram_message_show_empty_shop_always;

    public static int command_checkprofit_lines_pp;

    public static boolean permissions_create_shop_enabled;
    public static boolean permission_hologram_message_limit;
    public static boolean permission_hologram_message_line_count;

    public static boolean check_for_removed_shops;

    public static String language;
    public static boolean debug_logging;

    public static boolean notify_updates;
    public static boolean notify_overlapping_gui_items;
    public static boolean notify_overflowing_gui_items;

    public static boolean coreprotect_integration;
    public static boolean worldguard_integration;
    public static boolean towny_integration_shops_only_in_shop_plots;
    public static Database database_type;
    public static String databasemysql_ip;
    public static int databasemysql_port;
    public static int databasemysql_maxpool;
    public static String databasemysqltables_prefix;
    public static String databasemysql_databasename;
    public static String databasemysql_username;
    public static String databasemysql_password;
    public static boolean databasemysql_use_ssl;
    public static String databasemongodb_connection_string;

    public static boolean shopProtection;
    public static boolean emptyShopNotificationOnJoin;
    public static boolean logTransactions;

    public static boolean isDiscordNotificationEnabled;

    public static URL discordWebhookUrl;

    public static boolean isBuySellWebhookEnabled;

    public static ConfigurationSection buySellWebhookTemplate;

    public static boolean isNewShopWebhookEnabled;

    public static ConfigurationSection newShopWebhookTemplate;

    public static boolean shopCommandsEnabled;
    public static ShopCommandManager shopCommandManager;


    public static void loadConfig() {
        //reloading config.yml

        EzChestShop.getPlugin().reloadConfig();
        FileConfiguration config = EzChestShop.getPlugin().getConfig();
        currency = config.getString("economy.server-currency");

        showholo = config.getBoolean("shops.hologram.show-holograms");
        holostructure = config.getStringList("shops.hologram.holo-structure");
        Collections.reverse(holostructure);
        holostructure_admin = config.getStringList("shops.hologram.holo-structure-adminshop");
        Collections.reverse(holostructure_admin);
        holo_linespacing = config.getDouble("shops.hologram.holo-line-spacing");
        holodelay = config.getInt("shops.hologram.hologram-disappearance-delay");
        holo_rotation = config.getBoolean("shops.hologram.allow-rotation");
        holodistancing = config.getBoolean("shops.hologram.distance.toggled");
        holodistancing_distance = config.getDouble("shops.hologram.distance.range");
        holodistancing_show_item_first = config.getBoolean("shops.hologram.distance.show-items-first");

        container_chests = config.getBoolean("shops.container.chests");
        container_trapped_chests = config.getBoolean("shops.container.trapped-chests");
        container_barrels = config.getBoolean("shops.container.barrels");
        container_shulkers = config.getBoolean("shops.container.shulkers");

        display_numberformat_gui = config.getString("shops.display.number-format.gui");
        display_numberformat_chat = config.getString("shops.display.number-format.chat");
        display_numberformat_holo = config.getString("shops.display.number-format.hologram");

        settings_defaults_transactions = config.getBoolean("shops.settings.defaults.transaction-message");
        settings_defaults_dbuy = config.getBoolean("shops.settings.defaults.disable-buying");
        settings_defaults_dsell = config.getBoolean("shops.settings.defaults.disable-selling");
        settings_defaults_rotation = config.getString("shops.settings.defaults.rotation");
        settings_defaults_shareprofits = config.getBoolean("shops.settings.defaults.share-profit");

        settings_zero_equals_disabled = config.getBoolean("shops.settings.zero-price-equals-disabled");
        settings_buy_greater_than_sell = config.getBoolean("shops.settings.buy-greater-than-sell");
        settings_add_shulkershop_lore = config.getBoolean("shops.settings.add-shulkershop-lore");
        settings_custom_amout_transactions = config.getBoolean("shops.settings.custom-amount-transactions");

        settings_hologram_message_enabled = config.getBoolean("shops.settings.hologram-messages.enabled");
        settings_hologram_message_show_always = config.getBoolean("shops.settings.hologram-messages.show-always");
        settings_hologram_message_line_count_default = config.getInt("shops.settings.hologram-messages.line-count-default");
        settings_hologram_message_show_empty_shop_always = config.getBoolean("shops.settings.hologram-messages.show-empty-shop-always");

        if (config.contains("commands.alias.ecs-shop") && config.getBoolean("commands.alias.ecs-shop")) {
            LOGGER.warn("*** The config option 'commands.alias.ecs-shop' has been removed ***");
        }

        if (config.contains("commands.alias.ecsadmin-adminshop") && config.getBoolean("commands.alias.ecsadmin-adminshop")) {
            LOGGER.warn("*** The config option 'commands.alias.ecsadmin-adminshop' has been removed ***");
        }

        command_checkprofit_lines_pp = config.getInt("commands.checkprofit-lines-per-page");

        permissions_create_shop_enabled = config.getBoolean("permissions.create-shops");
        permission_hologram_message_limit = config.getBoolean("permissions.hologram-message-limit");
        permission_hologram_message_line_count = config.getBoolean("permissions.hologram-message-line-count");

        check_for_removed_shops = config.getBoolean("tasks.check-for-removed-shops");

        language = config.getString("language");
        if (!LanguageManager.getSupportedLanguages().contains(language)) {
            if (LanguageManager.getFoundlanguages().contains(language + ".yml")) {
                LOGGER.info("Using external language: {}.", language);
            } else {
                String oldLanguage = language;
                language = "Locale_EN";
                LOGGER.warn("*** Unsupported language '{}'. Using default language instead: '{}' ***", oldLanguage, language);
            }
        }

        debug_logging = config.getBoolean("debug.logging");
        notify_updates = config.getBoolean("other.notify-op-of-updates");
        notify_overlapping_gui_items = config.getBoolean("other.notify-op-of-overlapping-gui-items");
        notify_overflowing_gui_items = config.getBoolean("other.notify-op-of-overflowing-gui-items");
        coreprotect_integration = config.getBoolean("integration.coreprotect", true);
        worldguard_integration = config.getBoolean("integration.worldguard");
        towny_integration_shops_only_in_shop_plots = config.getBoolean("integration.towny.shops-only-in-shop-plots");
        database_type = Database.valueOf(
                Normalizer.normalize(config.getString("database.type"), Normalizer.Form.NFD)
                        .replaceAll("\\p{M}", "").toUpperCase(Locale.ENGLISH));
        databasemysql_ip = config.getString("database.mysql.ip");
        databasemysql_port = config.getInt("database.mysql.port");
        databasemysql_maxpool = config.getInt("database.mysql.max-pool");
        databasemysqltables_prefix = config.getString("database.mysql.tables-prefix");
        databasemysql_databasename = config.getString("database.mysql.database");
        databasemysql_username = config.getString("database.mysql.username");
        databasemysql_password = config.getString("database.mysql.password");
        databasemysql_use_ssl = config.getBoolean("database.mysql.ssl");
        databasemongodb_connection_string = config.getString("database.mongodb.connection-string");

        shopProtection = config.getBoolean("protection.prevent-shop-destruction", true);
        emptyShopNotificationOnJoin = config.getBoolean("notification.notify-empty-shop-on-join", true);
        logTransactions = config.getBoolean("shops.settings.log-transactions", true);
        //
        isDiscordNotificationEnabled = config.getBoolean("notification.discord.enabled", false);
        isBuySellWebhookEnabled = config.getBoolean("notification.discord.buy-sell-webhook.enabled", false);
        buySellWebhookTemplate = config.getConfigurationSection("notification.discord.buy-sell-webhook.template");
        isNewShopWebhookEnabled = config.getBoolean("notification.discord.new-shop-webhook.enabled", false);
        newShopWebhookTemplate = config.getConfigurationSection("notification.discord.new-shop-webhook.template");

        String rawDiscordWebhookUrl = config.getString("notification.discord.webhook-url");
        if (rawDiscordWebhookUrl == null
                || rawDiscordWebhookUrl.equals(DiscordWebhook.WEBHOOK_EXAMPLE_URL)
                // This was used in older versions of the plugin as an example:
                || rawDiscordWebhookUrl.equals("https://discord.com/api/webhooks/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx")) {
            discordWebhookUrl = null;
        } else {
            try {
                discordWebhookUrl = URI.create(rawDiscordWebhookUrl).toURL();
            } catch (IllegalArgumentException | MalformedURLException e) {
                LOGGER.warn("Invalid Discord webhook URL: {}", rawDiscordWebhookUrl, e);
                discordWebhookUrl = null;
            }
        }

        shopCommandsEnabled = config.getBoolean("shops.commands.enabled");
        shopCommandManager = new ShopCommandManager();
    }


    //this one checks for the config.yml ima make one for language.yml
    public static void checkForConfigYMLupdate() throws IOException {
        YamlConfiguration fc = YamlConfiguration.loadConfiguration(new File(EzChestShop.getPlugin().getDataFolder(), "config.yml"));

        //1.5.0 config update
        if (!fc.isBoolean("shops.settings.hologram-messages.enabled")) {
            fc.set("shops.settings.hologram-messages.enabled", true);
            fc.set("shops.settings.hologram-messages.show-always", false);
            fc.set("shops.settings.hologram-messages.line-count-default", 1);
            fc.set("permissions.hologram-message-limit", false);
            fc.set("permissions.hologram-message-line-count", false);

            fc.set("other.notify-op-of-updates", true); // has been in the plugin for a while, but never added to existing configs
            fc.set("other.notify-op-of-overlapping-gui-items", true);
            fc.set("other.notify-op-of-overflowing-gui-items", true);

            List<String> structure = new ArrayList<>(fc.getStringList("shops.hologram.holo-structure"));
            structure.addAll(0, Arrays.asList("<emptyShopInfo/>", "<custom1/>", "<custom2/>", "<custom3/>", "<custom4/>"));
            Integer index = structure.indexOf("[item]");
            structure.addAll(index == null ? structure.size() - 1 : index, Arrays.asList("<itemdata1/>", "<itemdata2/>", "<itemdata3/>", "<itemdata4/>", "<itemdataRest/>"));
            fc.set("shops.hologram.holo-structure", structure);

            List<String> structureAdmin = new ArrayList<>(fc.getStringList("shops.hologram.holo-structure-adminshop"));
            structureAdmin.addAll(0, Arrays.asList("<emptyShopInfo/>", "<custom1/>", "<custom2/>", "<custom3/>", "<custom4/>"));
            Integer indexAdmin = structureAdmin.indexOf("[item]");
            structureAdmin.addAll(indexAdmin == null ? structureAdmin.size() - 1 : indexAdmin, Arrays.asList("<itemdata1/>", "<itemdata2/>", "<itemdata3/>", "<itemdata4/>", "<itemdataRest/>"));
            fc.set("shops.hologram.holo-structure-adminshop", structureAdmin);

            fc.set("shop.settings.hologram-messages.show-empty-shop-always", true);
            //database update
            fc.set("database.type", "SQLite");
            fc.set("database.mysql.ip", "127.0.0.1");
            fc.set("database.mysql.port", 3306);
            fc.set("database.mysql.tables-prefix", "ecs_");
            fc.set("database.mysql.database", "TheDatabaseName");
            fc.set("database.mysql.usernmae", "TheUsername");
            fc.set("database.mysql.password", "ThePassword");
            fc.set("database.mysql.ssl", false);
            fc.set("database.mongodb.connection-string", "connection string");

            //integration update
            fc.set("integration.worldguard", true);

            fc.save(new File(EzChestShop.getPlugin().getDataFolder(), "config.yml"));
            Config.loadConfig();
        }

        //1.5.3 config update
        boolean updated1_5_3 = fc.isBoolean("protection.prevent-shop-destruction");
        if (!updated1_5_3) {
            fc.set("protection.prevent-shop-destruction", true);
            fc.save(new File(EzChestShop.getPlugin().getDataFolder(), "config.yml"));
            Config.loadConfig();
        }

        boolean updated1_5_6 = fc.isBoolean("notification.notify-empty-shop-on-join");
        if (!updated1_5_6) {
            fc.set("notification.notify-empty-shop-on-join", true);
            fc.set("notification.notify-empty-shop-on-join", true);
            fc.set("notification.discord.enabled", false);
            fc.set("notification.discord.webhook-url", "https://discord.com/api/webhooks/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx");

            fc.set("notification.discord.buy-sell-webhook.enabled", true);
            fc.set("notification.discord.buy-sell-webhook.template.content", null);

            List<Map<String, Object>> buySellEmbeds = new ArrayList<>();
            Map<String, Object> buySellEmbed = new HashMap<>();
            buySellEmbed.put("title", "New chest shop transaction is made!");
            buySellEmbed.put("description", "Player %BUYER% has bought %ITEM_NAME% from %SELLER% for %PRICE% %CURRENCY%.");
            buySellEmbed.put("url", "https://github.com/nouish/EzChestShop");
            buySellEmbed.put("color", 16753454);

// Fields for buy-sell embed
            List<Map<String, Object>> buySellFields = new ArrayList<>();
// ... Add the remaining fields in a similar manner
            String[] fieldNames = {"Shop Location:", "Shop Owner:", "Buyer:", "Seller:", "Amount:", "Time:"};
            String[] fieldValues = {"%SHOP_LOCATION%", "%OWNER%", "%BUYER%", "%SELLER%", "%COUNT% %ITEM_NAME%", "%TIME%"};
            boolean[] fieldInlines = {true, true, true, true, true, true};

            for (int i = 0; i < fieldNames.length; i++) {
                Map<String, Object> field = new HashMap<>();
                field.put("name", fieldNames[i]);
                field.put("value", fieldValues[i]);
                field.put("inline", fieldInlines[i]);
                buySellFields.add(field);
            }

            buySellEmbed.put("fields", buySellFields);

// Author for buy-sell embed
            Map<String, Object> buySellAuthor = new HashMap<>();
            buySellAuthor.put("name", "EzChestShop");
            buySellAuthor.put("url", "https://github.com/nouish/EzChestShop");
            buySellAuthor.put("icon_url", "https://cdn.discordapp.com/icons/902975048514678854/3f77b7a41dd80f018988d4a5d676273e.webp?size=128");
            buySellEmbed.put("author", buySellAuthor);

// Thumbnail for buy-sell embed
            Map<String, Object> buySellThumbnail = new HashMap<>();
            buySellThumbnail.put("url", "https://user-images.githubusercontent.com/20891968/235449301-7a12b967-a837-4e64-8e0b-c871a53e854e.png");
            buySellEmbed.put("thumbnail", buySellThumbnail);

            buySellEmbeds.add(buySellEmbed);
            fc.set("notification.discord.buy-sell-webhook.template.embeds", buySellEmbeds);
            fc.set("notification.discord.buy-sell-webhook.template.attachments", new ArrayList<>());

            fc.set("notification.discord.new-shop-webhook.enabled", true);
            fc.set("notification.discord.new-shop-webhook.template.content", null);

            List<Map<String, Object>> newShopEmbeds = new ArrayList<>();
            Map<String, Object> newShopEmbed = new HashMap<>();
            newShopEmbed.put("title", "New shop has been created!");
            newShopEmbed.put("description", "Player %OWNER% created a new shop for %ITEM_NAME%");
            newShopEmbed.put("url", "https://github.com/nouish/EzChestShop");
            newShopEmbed.put("color", 16753454);

// Fields for new-shop embed
            List<Map<String, Object>> newShopFields = new ArrayList<>();
// ... Add the remaining fields in a similar manner
            String[] newShopFieldNames = {"Shop Location:", "Buying Price", "Selling Price:", "Item Name:", "Material:", "Time:"};
            String[] newShopFieldValues = {"%SHOP_LOCATION%", "%BUYING_PRICE%", "%SELLING_PRICE%", "%ITEM_NAME%", "%MATERIAL%", "%TIME%"};
            boolean[] newShopFieldInlines = {true, true, true, true, true, true};

            for (int i = 0; i < newShopFieldNames.length; i++) {
                Map<String, Object> field = new HashMap<>();
                field.put("name", newShopFieldNames[i]);
                field.put("value", newShopFieldValues[i]);
                field.put("inline", newShopFieldInlines[i]);
                newShopFields.add(field);
            }

            newShopEmbed.put("fields", newShopFields);

// Author for new-shop embed
            Map<String, Object> newShopAuthor = new HashMap<>();
            newShopAuthor.put("name", "EzChestShop");
            newShopAuthor.put("url", "https://github.com/nouish/EzChestShop");
            newShopAuthor.put("icon_url", "https://cdn.discordapp.com/icons/902975048514678854/3f77b7a41dd80f018988d4a5d676273e.webp?size=128");
            newShopEmbed.put("author", newShopAuthor);

// Thumbnail for new-shop embed
            Map<String, Object> newShopThumbnail = new HashMap<>();
            newShopThumbnail.put("url", "https://user-images.githubusercontent.com/20891968/235449309-ead31b66-7a06-4c1a-b79d-1c5cf2c41ed8.png");
            newShopEmbed.put("thumbnail", newShopThumbnail);

            newShopEmbeds.add(newShopEmbed);
            fc.set("notification.discord.new-shop-webhook.template.embeds", newShopEmbeds);
            fc.set("notification.discord.new-shop-webhook.template.attachments", new ArrayList<>());

            fc.save(new File(EzChestShop.getPlugin().getDataFolder(), "config.yml"));
            Config.loadConfig();
        }

        if (!fc.isBoolean("shops.commands.enabled")) {
            fc.set("shops.commands.enabled", false);
            fc.set("shops.commands.shop.buy.*", List.of("/tell %player_name% You bought an Item!", "/tell %player_name% Thanks for shopping!"));
            fc.set("shops.commands.shop.sell.*", List.of("/tell %player_name% You sold an Item!"));
            fc.set("shops.commands.shop.open", List.of("/tell %player_name% Opening shop!"));
            fc.set("shops.commands.adminshop.buy.*", List.of("/tell %player_name% You bought an admin Item!"));
            fc.set("shops.commands.adminshop.sell.*", List.of("/tell %player_name% You sold an admin Item!"));
            fc.set("shops.commands.adminshop.open", List.of("/tell %player_name% Opening adminshop!"));
            fc.save(new File(EzChestShop.getPlugin().getDataFolder(), "config.yml"));
            Config.loadConfig();
        }

        if (!fc.isBoolean("integration.towny.shops-only-in-shop-plots")) {
            fc.set("integration.towny.shops-only-in-shop-plots", true);
            fc.save(new File(EzChestShop.getPlugin().getDataFolder(), "config.yml"));
            Config.loadConfig();
        }

        if (!fc.isBoolean("shops.settings.log-transactions")) {
            boolean defaultValue = true;
            fc.set("shops.settings.log-transactions", defaultValue);
            fc.setComments("shops.settings.log-transactions", List.of("When true, all transactions will be logged to the console."));
            fc.save(new File(EzChestShop.getPlugin().getDataFolder(), "config.yml"));
            Config.loadConfig();
            LOGGER.trace("Added the 'shops.settings.log-transactions' config option (default: {}).", defaultValue);
        }
    }
}
