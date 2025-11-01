package me.deadlight.ezchestshop.data;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.Constants;
import me.deadlight.ezchestshop.enums.Changes;
import me.deadlight.ezchestshop.events.PlayerTransactEvent;
import me.deadlight.ezchestshop.integrations.CoreProtectIntegration;
import me.deadlight.ezchestshop.utils.DiscordWebhook;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.holograms.ShopHologram;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.deadlight.ezchestshop.utils.objects.ShopSettings;
import me.deadlight.ezchestshop.utils.objects.SqlQueue;
import net.kyori.adventure.text.format.NamedTextColor;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.slf4j.Logger;

import static java.util.Objects.requireNonNull;
import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.Component.translatable;

/**
 * ShopContainer - a tool to retrieve and store data regarding shops,
 * in memory for quick access and sqlite for long term storage.
 */

public class ShopContainer {
    private static final Logger LOGGER = EzChestShop.logger();
    private static final Economy econ = EzChestShop.getEconomy();
    private static final HashMap<Location, EzShop> shopMap = new HashMap<>();

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_DATE_TIME;

    /**
     * Save all shops from the Database into memory,
     * so querying all shops is less resource expensive
     */
    public static void queryShopsToMemory() {
        DatabaseManager db = EzChestShop.getPlugin().getDatabase();
        shopMap.clear();
        shopMap.putAll(db.queryShops());
        LOGGER.info("Loaded and cached {} shops.", shopMap.size());
    }

    /**
     * Delete a Shop at a given Location.
     *
     * @param loc the Location of the Shop.
     */
    public static void deleteShop(Location loc) {
        DatabaseManager db = EzChestShop.getPlugin().getDatabase();
        db.deleteEntry("location", Utils.LocationtoString(loc), "shopdata");
        shopMap.remove(loc);

        //This is not workign as intended
//        InventoryHolder inventoryHolder = (InventoryHolder) loc.getBlock();
//        inventoryHolder.getInventory().getViewers().forEach(viewer -> viewer.closeInventory());

        for (Player p : Bukkit.getOnlinePlayers()) {
            if (ShopHologram.hasHologram(loc, p))
                ShopHologram.hideForAll(loc);
        }

        EzChestShop.getPlugin().getComponentLogger().info(text()
                .append(text("Deleted shop at "))
                .append(text(loc.getBlockX(), NamedTextColor.GOLD)).append(text(", "))
                .append(text(loc.getBlockY(), NamedTextColor.GOLD)).append(text(", "))
                .append(text(loc.getBlockZ(), NamedTextColor.GOLD))
                .append(text("."))
                .build());
    }

    /**
     * Create a new Shop!
     *
     * @param loc the Location of the Shop.
     * @param p   the Owner of the Shop.
     */
    public static void createShop(Location loc, Player p, ItemStack item, double buyprice, double sellprice, boolean msgtoggle,
                                  boolean dbuy, boolean dsell, String admins, boolean shareincome,
                                  boolean adminshop, String rotation) {
        DatabaseManager db = EzChestShop.getPlugin().getDatabase();
        String sloc = Utils.LocationtoString(loc);
        String encodedItem = Utils.encodeItem(item);
        db.insertShop(sloc, p.getUniqueId().toString(), encodedItem == null ? "Error" : encodedItem, buyprice, sellprice, msgtoggle, dbuy, dsell, admins, shareincome, adminshop, rotation, new ArrayList<>());
        ShopSettings settings = new ShopSettings(sloc, msgtoggle, dbuy, dsell, admins, shareincome, adminshop, rotation, new ArrayList<>());
        EzShop shop = new EzShop(loc, p, item, buyprice, sellprice, settings);
        shopMap.put(loc, shop);

        ItemMeta meta = item.getItemMeta();
        World world = requireNonNull(loc.getWorld(), "Location cannot be in null world");
        final String ownerName = p.getName();
        // Show buying price in string if dbuy is false, otherwise show "Disabled"
        final String priceBuy = dbuy ? "Disabled" : String.valueOf(buyprice);
        final String priceSell = dsell ? "Disabled" : String.valueOf(sellprice);
        // Show Item name if it has custom name, otherwise show localized name
        final String itemName = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : item.getType().name();
        final String materialType = item.getType().name();
        // Display Current Time Like This: 2023/5/1 | 23:10:23
        final String time = DATE_TIME_FORMATTER.format(java.time.LocalDateTime.now()).replace("T", " | ");
        // Display shop location as this: world, x, y, z
        final String shopLocation = world.getName() + ", " + loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();

        EzChestShop.getPlugin().getComponentLogger().info(text()
                .append(text(ownerName, NamedTextColor.GOLD))
                .append(text(" created a shop ("))
                .append(translatable(item, NamedTextColor.GOLD))
                .append(text(") at "))
                .append(text(loc.getBlockX(), NamedTextColor.GOLD)).append(text(", "))
                .append(text(loc.getBlockY(), NamedTextColor.GOLD)).append(text(", "))
                .append(text(loc.getBlockZ(), NamedTextColor.GOLD))
                .append(text("."))
                .build());

        EzChestShop.getScheduler().runTaskAsynchronously(
                () -> DiscordWebhook.queueCreation(ownerName, priceBuy, priceSell, itemName, materialType, time, shopLocation));
    }

    public static void loadShop(Location loc, PersistentDataContainer dataContainer) {
        DatabaseManager db = EzChestShop.getPlugin().getDatabase();
        String sloc = Utils.LocationtoString(loc);
        boolean msgtoggle = dataContainer.getOrDefault(Constants.ENABLE_MESSAGE_KEY, PersistentDataType.INTEGER, 0) == 1;
        boolean dbuy = dataContainer.getOrDefault(Constants.DISABLE_BUY_KEY, PersistentDataType.INTEGER, 0) == 1;
        boolean dsell = dataContainer.getOrDefault(Constants.DISABLE_SELL_KEY, PersistentDataType.INTEGER, 0) == 1;
        String admins = dataContainer.get(Constants.ADMIN_LIST_KEY, PersistentDataType.STRING);
        boolean shareincome = dataContainer.getOrDefault(Constants.ENABLE_SHARED_INCOME_KEY, PersistentDataType.INTEGER, 0) == 1;
        boolean adminshop = dataContainer.getOrDefault(Constants.ENABLE_ADMINSHOP_KEY, PersistentDataType.INTEGER, 0) == 1;
        String owner = dataContainer.get(Constants.OWNER_KEY, PersistentDataType.STRING);
        String encodedItem = dataContainer.get(Constants.ITEM_KEY, PersistentDataType.STRING);
        double buyprice = dataContainer.getOrDefault(Constants.BUY_PRICE_KEY, PersistentDataType.DOUBLE, Double.MAX_VALUE);
        double sellprice = dataContainer.getOrDefault(Constants.SELL_PRICE_KEY, PersistentDataType.DOUBLE, Double.MAX_VALUE);
        String rotation = dataContainer.getOrDefault(Constants.ROTATION_KEY, PersistentDataType.STRING, Config.settings_defaults_rotation);
        db.insertShop(sloc, owner, encodedItem == null ? "Error" : encodedItem, buyprice, sellprice, msgtoggle, dbuy, dsell, admins, shareincome, adminshop, rotation, new ArrayList<>());

        ShopSettings settings = new ShopSettings(sloc, msgtoggle, dbuy, dsell, admins, shareincome, adminshop, rotation, new ArrayList<>());
        EzShop shop = new EzShop(loc, owner, Utils.decodeItem(encodedItem), buyprice, sellprice, settings);
        shopMap.put(loc, shop);;
    }

    public static PersistentDataContainer copyContainerData(PersistentDataContainer oldContainer, PersistentDataContainer newContainer) {
        newContainer.set(Constants.OWNER_KEY, PersistentDataType.STRING,
                oldContainer.get(Constants.OWNER_KEY, PersistentDataType.STRING));
        newContainer.set(Constants.BUY_PRICE_KEY, PersistentDataType.DOUBLE,
                oldContainer.getOrDefault(Constants.BUY_PRICE_KEY, PersistentDataType.DOUBLE, Double.MAX_VALUE));
        newContainer.set(Constants.SELL_PRICE_KEY, PersistentDataType.DOUBLE,
                oldContainer.getOrDefault(Constants.SELL_PRICE_KEY, PersistentDataType.DOUBLE, Double.MAX_VALUE));
        newContainer.set(Constants.ENABLE_MESSAGE_KEY, PersistentDataType.INTEGER,
                oldContainer.getOrDefault(Constants.ENABLE_MESSAGE_KEY, PersistentDataType.INTEGER, 0));
        newContainer.set(Constants.DISABLE_BUY_KEY, PersistentDataType.INTEGER,
                oldContainer.getOrDefault(Constants.DISABLE_BUY_KEY, PersistentDataType.INTEGER, 0));
        newContainer.set(Constants.DISABLE_SELL_KEY, PersistentDataType.INTEGER,
                oldContainer.getOrDefault(Constants.DISABLE_SELL_KEY, PersistentDataType.INTEGER, 0));
        newContainer.set(Constants.ADMIN_LIST_KEY, PersistentDataType.STRING,
                oldContainer.get(Constants.ADMIN_LIST_KEY, PersistentDataType.STRING));
        newContainer.set(Constants.ENABLE_SHARED_INCOME_KEY, PersistentDataType.INTEGER,
                oldContainer.getOrDefault(Constants.ENABLE_SHARED_INCOME_KEY, PersistentDataType.INTEGER, 0));
        newContainer.set(Constants.ENABLE_ADMINSHOP_KEY, PersistentDataType.INTEGER,
                oldContainer.getOrDefault(Constants.ENABLE_ADMINSHOP_KEY, PersistentDataType.INTEGER, 0));
        newContainer.set(Constants.ITEM_KEY, PersistentDataType.STRING,
                oldContainer.get(Constants.ITEM_KEY, PersistentDataType.STRING));
        newContainer.set(Constants.ROTATION_KEY, PersistentDataType.STRING,
                oldContainer.getOrDefault(Constants.ROTATION_KEY, PersistentDataType.STRING, Config.settings_defaults_rotation));
        return newContainer;
    }

    public static List<EzShop> getShopFromOwner(UUID uuid){
        List<EzShop> ezShops = new ArrayList<>();

        for (EzShop shop : shopMap.values()) {
            // no admin shop and shop owned by this player.
            if(!shop.getSettings().isAdminshop() && shop.getOwnerID().equals(uuid)){
                ezShops.add(shop);
            }
        }

        return ezShops;
    }

    /**
     * Query the Database to retrieve all Shops a player owns.
     *
     * @param p the Player to query
     * @return the amount of shops a player owns.
     */
    public static int getShopCount(Player p, World world) {
        return (int) getShopFromOwner(p.getUniqueId()).stream().filter(ezShop -> {
            if (world == null) return false;
            return world.equals(ezShop.getLocation().getWorld());
        }).count();
    }

    /**
     * Query the Database to retrieve all Shops a player owns.
     *
     * @param p the Player to query
     * @return the amount of shops a player owns.
     */
    public static int getShopCount(Player p) {
        return getShopFromOwner(p.getUniqueId()).size();
    }

    /**
     * Check if a Location is a Shop
     *
     * @param loc the Location to be checked
     * @return a boolean based on the outcome.
     */
    public static boolean isShop(Location loc) {
        return shopMap.containsKey(loc);
    }

    /**
     * Get all Shops from memory.
     *
     * @return a copy of all Shops as stored in memory.
     */
    public static List<EzShop> getShops() {
        return new ArrayList<>(shopMap.values());
    }

    public static EzShop getShop(Location location) {
        return shopMap.get(location);
    }

    public static ShopSettings getShopSettings(Location loc) {
        EzShop shop = shopMap.get(loc);
        if (shop != null) {
            return shop.getSettings();
        } else {
            //why we would need to use database data for getting settings? just setting them in database is enough
            PersistentDataContainer dataContainer = Utils.getDataContainer(loc.getBlock());
            String sloc = Utils.LocationtoString(loc);
            boolean msgtoggle = dataContainer.getOrDefault(Constants.ENABLE_MESSAGE_KEY, PersistentDataType.INTEGER, 0) == 1;
            boolean dbuy = dataContainer.getOrDefault(Constants.DISABLE_BUY_KEY, PersistentDataType.INTEGER, 0) == 1;
            boolean dsell = dataContainer.getOrDefault(Constants.DISABLE_SELL_KEY, PersistentDataType.INTEGER, 0) == 1;
            String admins = dataContainer.get(Constants.ADMIN_LIST_KEY, PersistentDataType.STRING);
            boolean shareincome = dataContainer.getOrDefault(Constants.ENABLE_SHARED_INCOME_KEY, PersistentDataType.INTEGER, 0) == 1;
            boolean adminshop = dataContainer.getOrDefault(Constants.ENABLE_ADMINSHOP_KEY, PersistentDataType.INTEGER, 0) == 1;
            String owner = dataContainer.get(Constants.OWNER_KEY, PersistentDataType.STRING);
            String encodedItem = dataContainer.get(Constants.ITEM_KEY, PersistentDataType.STRING);
            double buyprice = dataContainer.getOrDefault(Constants.BUY_PRICE_KEY, PersistentDataType.DOUBLE, Double.MAX_VALUE);
            double sellprice = dataContainer.getOrDefault(Constants.SELL_PRICE_KEY, PersistentDataType.DOUBLE, Double.MAX_VALUE);
            String rotation = dataContainer.getOrDefault(Constants.ROTATION_KEY, PersistentDataType.STRING, Config.settings_defaults_rotation);
            ShopSettings settings = new ShopSettings(sloc, msgtoggle, dbuy, dsell, admins, shareincome, adminshop, rotation, new ArrayList<>());
            shop = new EzShop(loc, owner, Utils.decodeItem(encodedItem), buyprice, sellprice, settings);
            shopMap.put(loc, shop);
            return settings;
        }
    }

    public static void buyItem(Block containerBlock, double price, int count, ItemStack tthatItem, Player player, OfflinePlayer owner, PersistentDataContainer data) {
        final var logger = EzChestShop.getPlugin().getComponentLogger();
        ItemStack thatItem = tthatItem.clone();
        LanguageManager lm = LanguageManager.getInstance();

        //check for money
        Inventory blockInventory = Utils.getBlockInventory(containerBlock);
        if (blockInventory == null) {
            logger.warn("{} attempted to buy item from shop without inventory at {}!", player.getName(), containerBlock.getLocation());
            return;
        }

        if (!Utils.containsAtLeast(blockInventory, thatItem, count)) {
            player.sendMessage(lm.outofStock());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.5f, 0.5f);
            return;
        }

        if (!hasBalance(Bukkit.getOfflinePlayer(player.getUniqueId()), price)) {
            player.sendMessage(lm.cannotAfford());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.5f, 0.5f);
            return;
        }

        if (!Utils.hasEnoughSpace(player, count, thatItem)) {
            player.sendMessage(lm.fullinv());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.5f, 0.5f);
            return;
        }

        int stacks = (int) Math.ceil(count / (double) thatItem.getMaxStackSize());
        int maxSize = thatItem.getMaxStackSize();

        for (int i = 0; i < stacks; i++) {
            if (i + 1 == stacks) {
                thatItem.setAmount(count % maxSize == 0 ? maxSize : count % maxSize);
            } else {
                thatItem.setAmount(maxSize);
            }
            player.getInventory().addItem(thatItem);
        }

        //For the transaction event
        thatItem.setAmount(count);
        CoreProtectIntegration.expectInventoryChange(player, containerBlock.getLocation());
        Utils.removeItem(blockInventory, thatItem);
        transfer(Bukkit.getOfflinePlayer(player.getUniqueId()), owner, price);
        sharedIncomeCheck(data, price);
        transactionMessage(data, owner, player, price, true, tthatItem, count, containerBlock.getLocation().getBlock());
        player.sendMessage(lm.messageSuccBuy(price));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.5f);
        Config.shopCommandManager.executeCommands(player, containerBlock.getLocation(),
                ShopCommandManager.ShopType.SHOP, ShopCommandManager.ShopAction.BUY, count + "");

        if (Config.logTransactions) {
            logger.info(text()
                    .append(text(player.getName(), NamedTextColor.GOLD))
                    .append(text(" bought "))
                    .append(text(String.format(Locale.ROOT, "%,d", count), NamedTextColor.GOLD))
                    .append(text(" x "))
                    .append(translatable(thatItem, NamedTextColor.GOLD))
                    .append(text(" from "))
                    .append(text(owner != null ? Objects.requireNonNullElse(owner.getName(), "<Unnamed>") : "<Unknown>", NamedTextColor.GOLD))
                    .append(text(" for "))
                    .append(text(String.format(Locale.ROOT, "$%,.2f", price), NamedTextColor.GOLD))
                    .append(text("."))
                    .build());
        }
    }

    public static void sellItem(Block containerBlock, double price, int count, ItemStack tthatItem, Player player, OfflinePlayer owner, PersistentDataContainer data) {
        final var logger = EzChestShop.getPlugin().getComponentLogger();
        LanguageManager lm = LanguageManager.getInstance();
        ItemStack thatItem = tthatItem.clone();

        if (!Utils.containsAtLeast(player.getInventory(), thatItem, count)) {
            player.sendMessage(lm.notEnoughItemToSell());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.5f, 0.5f);
            return;
        }

        if (!hasBalance(owner, price)) {
            player.sendMessage(lm.shopCannotAfford());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.5f, 0.5f);
            return;
        }

        Inventory blockInventory = Utils.getBlockInventory(containerBlock);

        if (blockInventory == null) {
            logger.warn("{} attempted to sell item to shop without inventory at {}!", player.getName(), containerBlock.getLocation());
            return;
        }

        if (!Utils.containerHasEnoughSpace(blockInventory, count, thatItem)) {
            player.sendMessage(lm.chestIsFull());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.5f, 0.5f);
            return;
        }

        CoreProtectIntegration.expectInventoryChange(player, containerBlock.getLocation());
        int stacks = (int) Math.ceil(count / (double) thatItem.getMaxStackSize());
        int maxSize = thatItem.getMaxStackSize();

        for (int i = 0; i < stacks; i++) {
            if (i + 1 == stacks) {
                thatItem.setAmount(count % maxSize == 0 ? maxSize : count % maxSize);
            } else {
                thatItem.setAmount(maxSize);
            }
            blockInventory.addItem(thatItem);
        }

        //For the transaction event
        thatItem.setAmount(count);
        Utils.removeItem(player.getInventory(), thatItem);
        transfer(owner, Bukkit.getOfflinePlayer(player.getUniqueId()), price);
        transactionMessage(data, owner, Bukkit.getOfflinePlayer(player.getUniqueId()), price, false, tthatItem, count, containerBlock.getLocation().getBlock());
        player.sendMessage(lm.messageSuccSell(price));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.5f);
        Config.shopCommandManager.executeCommands(player, containerBlock.getLocation(),
                ShopCommandManager.ShopType.SHOP, ShopCommandManager.ShopAction.SELL, Integer.toString(count));

        if (Config.logTransactions) {
            logger.info(text()
                    .append(text(player.getName(), NamedTextColor.GOLD))
                    .append(text(" sold "))
                    .append(text(String.format(Locale.ROOT, "%,d", count), NamedTextColor.GOLD))
                    .append(text(" x "))
                    .append(translatable(thatItem, NamedTextColor.GOLD))
                    .append(text(" to "))
                    .append(text(owner != null ? Objects.requireNonNullElse(owner.getName(), "<Unnamed>") : "<Unknown>", NamedTextColor.GOLD))
                    .append(text(" for "))
                    .append(text(String.format(Locale.ROOT, "$%,.2f", price), NamedTextColor.GOLD))
                    .append(text("."))
                    .build());
        }
    }

    public static void buyServerItem(Block containerBlock, double price, int count, Player player, ItemStack tthatItem, PersistentDataContainer data) {
        final var logger = EzChestShop.getPlugin().getComponentLogger();
        ItemStack thatItem = tthatItem.clone();
        LanguageManager lm = LanguageManager.getInstance();

        //check for money
        if (!hasBalance(Bukkit.getOfflinePlayer(player.getUniqueId()), price)) {
            player.sendMessage(lm.cannotAfford());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.5f, 0.5f);
            return;
        }

        if (!Utils.hasEnoughSpace(player, count, thatItem)) {
            player.sendMessage(lm.fullinv());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.5f, 0.5f);
            return;
        }

        int stacks = (int) Math.ceil(count / (double) thatItem.getMaxStackSize());
        int maxSize = thatItem.getMaxStackSize();

        for (int i = 0; i < stacks; i++) {
            if (i + 1 == stacks) {
                thatItem.setAmount(count % maxSize == 0 ? maxSize : count % maxSize);
            } else {
                thatItem.setAmount(maxSize);
            }
            player.getInventory().addItem(thatItem);
        }

        //For the transaction event
        thatItem.setAmount(count);
        withdraw(Bukkit.getOfflinePlayer(player.getUniqueId()), price);
        transactionMessage(data, Bukkit.getOfflinePlayer(UUID.fromString(
                data.get(Constants.OWNER_KEY, PersistentDataType.STRING))),
                Bukkit.getOfflinePlayer(player.getUniqueId()), price, true, tthatItem, count, containerBlock);
        player.sendMessage(lm.messageSuccBuy(price));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.5f);
        Config.shopCommandManager.executeCommands(player, containerBlock.getLocation(),
                ShopCommandManager.ShopType.ADMINSHOP, ShopCommandManager.ShopAction.BUY, Integer.toString(count));

        if (Config.logTransactions) {
            logger.info(text()
                    .append(text(player.getName(), NamedTextColor.GOLD))
                    .append(text(" bought "))
                    .append(text(String.format(Locale.ROOT, "%,d", count), NamedTextColor.GOLD))
                    .append(text(" x "))
                    .append(translatable(thatItem, NamedTextColor.GOLD))
                    .append(text(" from "))
                    .append(text("** admin shop **", NamedTextColor.RED))
                    .append(text(" for "))
                    .append(text(String.format(Locale.ROOT, "$%,.2f", price), NamedTextColor.GOLD))
                    .append(text("."))
                    .build());
        }
    }

    public static void sellServerItem(Block containerBlock, double price, int count, ItemStack tthatItem, Player player, PersistentDataContainer data) {
        final var logger = EzChestShop.getPlugin().getComponentLogger();
        LanguageManager lm = LanguageManager.getInstance();
        ItemStack thatItem = tthatItem.clone();

        if (!Utils.containsAtLeast(player.getInventory(), thatItem, count)) {
            player.sendMessage(lm.notEnoughItemToSell());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE, 0.5f, 0.5f);
            return;
        }

        thatItem.setAmount(count);
        deposit(Bukkit.getOfflinePlayer(player.getUniqueId()), price);
        transactionMessage(data, Bukkit.getOfflinePlayer(UUID.fromString(
                data.get(Constants.OWNER_KEY, PersistentDataType.STRING))),
                Bukkit.getOfflinePlayer(player.getUniqueId()), price, false, tthatItem, count, containerBlock);
        Utils.removeItem(player.getInventory(), thatItem);
        player.sendMessage(lm.messageSuccSell(price));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.5f, 0.5f);
        Config.shopCommandManager.executeCommands(player, containerBlock.getLocation(),
                ShopCommandManager.ShopType.ADMINSHOP, ShopCommandManager.ShopAction.SELL, Integer.toString(count));

        if (Config.logTransactions) {
            logger.info(text()
                    .append(text(player.getName(), NamedTextColor.GOLD))
                    .append(text(" sold "))
                    .append(text(String.format(Locale.ROOT, "%,d", count), NamedTextColor.GOLD))
                    .append(text(" x "))
                    .append(translatable(thatItem, NamedTextColor.GOLD))
                    .append(text(" to "))
                    .append(text("** admin shop **", NamedTextColor.RED))
                    .append(text(" for "))
                    .append(text(String.format(Locale.ROOT, "$%,.2f", price), NamedTextColor.GOLD))
                    .append(text("."))
                    .build());
        }
    }

    private static boolean deposit(OfflinePlayer target, double amount) {
        var response = econ.depositPlayer(target, amount);
        if (response.transactionSuccess()) {
            LOGGER.debug("Deposited {} to {}.", econ.format(amount), target.getName());
        } else {
            LOGGER.info("Failed to deposit {} to {}.", econ.format(amount), target.getName());
        }
        return response.transactionSuccess();
    }

    private static boolean withdraw(OfflinePlayer target, double amount) {
        var response = econ.withdrawPlayer(target, amount);
        if (response.transactionSuccess()) {
            LOGGER.debug("Withdrew {} from {}.", econ.format(amount), target.getName());
        } else {
            LOGGER.info("Failed to withdraw {} from {}.", econ.format(amount), target.getName());
        }
        return response.transactionSuccess();
    }

    private static boolean hasBalance(OfflinePlayer target, double minBalance) {
        double balance = econ.getBalance(target);
        LOGGER.debug("{}'s balance is: {} (check minimum: {})", target.getName(), econ.format(balance), econ.format(minBalance));
        return balance >= minBalance;
    }

    private static boolean transfer(OfflinePlayer withdraw, OfflinePlayer deposit, double price) {
        return withdraw(withdraw, price) && deposit(deposit, price);
    }

    private static void transactionMessage(PersistentDataContainer data, OfflinePlayer owner, OfflinePlayer customer, double price, boolean isBuy, ItemStack item, int count, Block containerBlock) {
        //buying = True, Selling = False
        PlayerTransactEvent transactEvent = new PlayerTransactEvent(owner, customer, price, isBuy, item, count, Utils.getAdminsList(data), containerBlock);
        Bukkit.getPluginManager().callEvent(transactEvent);
    }

    private static void sharedIncomeCheck(PersistentDataContainer data, double price) {
        boolean isSharedIncome = data.getOrDefault(Constants.ENABLE_SHARED_INCOME_KEY, PersistentDataType.INTEGER, 0) == 1;
        if (isSharedIncome) {
            UUID ownerUUID = UUID.fromString(data.get(Constants.OWNER_KEY, PersistentDataType.STRING));
            List<UUID> adminsList = Utils.getAdminsList(data);
            double profit = price / (adminsList.size() + 1);
            if (!adminsList.isEmpty()) {
                if (hasBalance(Bukkit.getOfflinePlayer(ownerUUID), profit * adminsList.size())) {
                    boolean succesful = withdraw(Bukkit.getOfflinePlayer(ownerUUID), profit * adminsList.size());
                    if (succesful) {
                        for (UUID adminUUID : adminsList) {
                            deposit(Bukkit.getOfflinePlayer(adminUUID), profit);
                        }
                    }
                }
            }
        }
    }

    public static void transferOwner(BlockState state, OfflinePlayer newOwner) {
        Location loc = state.getLocation();
        if (isShop(loc)) {
            PersistentDataContainer container = ((TileState) state).getPersistentDataContainer();
            container.set(Constants.OWNER_KEY, PersistentDataType.STRING, newOwner.getUniqueId().toString());
            EzShop shop = getShop(loc);
            shop.getSqlQueue().setChange(Changes.SHOP_OWNER, newOwner.getUniqueId().toString());
            shop.setOwner(newOwner);
            state.update();
        }
    }

    public static void changePrice(BlockState state, double newPrice, boolean isBuyPrice) {
        Location loc = state.getLocation();
        if (isShop(loc)) {
            PersistentDataContainer container = ((TileState) state).getPersistentDataContainer();
            NamespacedKey key = isBuyPrice ? Constants.BUY_PRICE_KEY : Constants.SELL_PRICE_KEY;
            container.set(key, PersistentDataType.DOUBLE, newPrice);
            EzShop shop = getShop(loc);
            shop.getSqlQueue().setChange(isBuyPrice ? Changes.BUY_PRICE : Changes.SELL_PRICE, newPrice);
            if (isBuyPrice) {
                shop.setBuyPrice(newPrice);
            } else {
                shop.setSellPrice(newPrice);
            }
            state.update();
        }
    }

    public static void startSqlQueueTask() {
        EzChestShop.getScheduler().runTaskTimer(() -> {
            //now looping through all shops and executing mysql commands
            for (EzShop shop : shopMap.values()) {
                if (shop.getSettings().getSqlQueue().isChanged()) {
                    runSqlTask(shop, shop.getSettings().getSqlQueue());
                }
                if (shop.getSqlQueue().isChanged()) {
                    runSqlTask(shop, shop.getSqlQueue());
                }
            }
        }, 0, 20 * 60); //for now leaving it as non-editable value
    }

    public static void saveSqlQueueCache() { //This part needs to change, it causes lag for big servers, have to save all changes in one query only!
        for (EzShop shop : shopMap.values()) {
            if (shop.getSettings().getSqlQueue().isChanged()) {
                runSqlTask(shop, shop.getSettings().getSqlQueue());
            }
            if (shop.getSqlQueue().isChanged()) {
                runSqlTask(shop, shop.getSqlQueue());
            }
        }
    }

    private static void runSqlTask(EzShop shop, SqlQueue queue) {
        DatabaseManager db = EzChestShop.getPlugin().getDatabase();
        //ok then it's time to execute the mysql thingys
        HashMap<Changes, Object> changes = queue.getChangesList();
        String sloc = shop.getSettings().getSloc();

        for (Changes change : changes.keySet()) {
            Object valueObject = changes.get(change);

            //mysql job / you can get the value using Changes.
            if (change.theClass == String.class) {
                //well its string
                String value = (String) valueObject;
                db.setString("location", sloc, change.databaseValue, "shopdata", value);

            } else if (change.theClass == Boolean.class) {
                //well its boolean
                boolean value = (Boolean) valueObject;
                db.setBool("location", sloc, change.databaseValue, "shopdata", value);
            } else if (change.theClass == Double.class) {
                //well its double
                double value = (Double) valueObject;
                db.setDouble("location", sloc, change.databaseValue, "shopdata", value);
            }
        }

        //the last thing has to be clearing the SqlQueue object so don't remove this
        queue.resetChangeList(shop.getSettings(), shop); //giving new shop settings to keep the queue updated
    }

}
