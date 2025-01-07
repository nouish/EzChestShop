package me.deadlight.ezchestshop.utils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.EzChestShopConstants;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.DatabaseManager;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.data.mysql.MySQL;
import me.deadlight.ezchestshop.data.sqlite.SQLite;
import me.deadlight.ezchestshop.enums.Database;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import net.md_5.bungee.api.ChatMessageType;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.ComponentBuilder;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import org.apache.commons.lang3.Validate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.TileState;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.PermissionAttachmentInfo;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class Utils {
    private Utils() {}

    public static List<Object> onlinePackets = new ArrayList<>();
    public static final List<String> rotations = List.of("up", "north", "east", "south", "west", "down");

    public static HashMap<String, Block> blockBreakMap = new HashMap<>();
    public static ConcurrentHashMap<Integer, BlockOutline> activeOutlines = new ConcurrentHashMap<>(); //player uuid, list of outlines
    public static List<UUID> enabledOutlines = new ArrayList<>();
    private static final LanguageManager lm = new LanguageManager();

    public static NmsHandle nmsHandle;
    public static DatabaseManager databaseManager;

    static {
        VersionUtil.MinecraftVersion version = VersionUtil.getMinecraftVersion().orElse(null);

        if (version == null) {
            throw new AssertionError();
        }

        try {
            Class<?> clazz = Class.forName(version.getHandle());
            nmsHandle = (NmsHandle) clazz.getDeclaredConstructor().newInstance();
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * Store a ItemStack into a persistent Data Container using Base64 encoding.
     *
     * @param item
     * @param data
     * @throws IOException
     */
    public static void storeItem(ItemStack item, PersistentDataContainer data) {
        String encodedItem = encodeItem(item);
        if (encodedItem != null) {
            data.set(EzChestShopConstants.ITEM_KEY, PersistentDataType.STRING, encodedItem);
        }
    }

    /**
     * Encode a ItemStack into a Base64 encoded String
     *
     * @param item
     * @return
     */
    public static String encodeItem(ItemStack item) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            try (BukkitObjectOutputStream os = new BukkitObjectOutputStream(baos)) {
                os.writeObject(item);
            }
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (IOException ex) {
            System.out.println(ex);
            return null;
        }
    }

    /**
     * Decode a ItemStack from Base64 into a ItemStack
     *
     * @param encodedItem
     * @return
     */
    public static ItemStack decodeItem(String encodedItem) {
        byte[] buf = Base64.getDecoder().decode(encodedItem);

        try (ByteArrayInputStream io = new ByteArrayInputStream(buf);
             BukkitObjectInputStream in = new BukkitObjectInputStream(io)) {
            return (ItemStack) in.readObject();
        } catch (IOException | ClassNotFoundException ex) {
            System.out.println(ex);
            return null;
        }
    }

    /**
     * Convert a Item to a Text Compount. Used in Text Component Builders to show
     * items in chat.
     *
     * @param itemStack
     * @return
     * @category ItemUtils
     */
    public static String itemStackToSnbt(@NotNull ItemStack itemStack) {
        // This implementation is temporary before moving to Adventure API, and is simply to avoid
        // using libraries to handle this.
        String components;
        if (itemStack.hasItemMeta()) {
            components = Validate.notNull(itemStack.getItemMeta(), "ItemMeta must not be null").getAsString();
        } else {
            components = "{}";
        }

        return String.format(Locale.ROOT, "{components:%s,count:%d,id:\"%s\"}",
                components, itemStack.getAmount(), itemStack.getType().getKey());
    }

    /**
     * Get the Inventory of the given Block if it is a Chest, Barrel or any Shulker
     *
     * @param block
     * @return
     */
    public static Inventory getBlockInventory(Block block) {
        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            return ((Chest) block.getState()).getInventory();
        } else if (block.getType() == Material.BARREL) {
            return ((Barrel) block.getState()).getInventory();
        } else if (Tag.SHULKER_BOXES.isTagged(block.getType())) {
            return ((ShulkerBox) block.getState()).getInventory();
        } else
            return null;
    }

    /**
     * Check if the given Block is a Shulker box (dye check)
     *
     * @param block
     * @return
     * @deprecated Use {@link Tag#isTagged(Keyed)} on {@link Tag#SHULKER_BOXES} instead.
     */
    @Deprecated
    public static boolean isShulkerBox(@NotNull Block block) {
        return Tag.SHULKER_BOXES.isTagged(block.getType());
    }

    /**
     * Check if the given Material is a Shulker box (dye check)
     *
     * @param type
     * @return
     * @deprecated Use {@link Tag#isTagged(Keyed)} on {@link Tag#SHULKER_BOXES} instead.
     */
    @Deprecated
    public static boolean isShulkerBox(@NotNull Material type) {
        return Tag.SHULKER_BOXES.isTagged(type);
    }

    /**
     * Check if the given Block is a applicable Shop.
     *
     * @param block
     * @return
     */
    public static boolean isApplicableContainer(Block block) {
        return isApplicableContainer(block.getType());
    }

    /**
     * Check if the given Material is a applicable Shop.
     *
     * @param type
     * @return
     */
    public static boolean isApplicableContainer(Material type) {
        return (type == Material.CHEST && Config.container_chests)
                || (type == Material.TRAPPED_CHEST && Config.container_trapped_chests)
                || (type == Material.BARREL && Config.container_barrels)
                || (Tag.SHULKER_BOXES.isTagged(type) && Config.container_shulkers);
    }

    public static List<UUID> getAdminsList(PersistentDataContainer data) {
        String adminList = data.get(EzChestShopConstants.ADMIN_LIST_KEY, PersistentDataType.STRING);
        Preconditions.checkNotNull(adminList);
        // UUID@UUID@UUID
        if (adminList.equalsIgnoreCase("none")) {
            return new ArrayList<>();
        } else {
            String[] stringUUIDS = adminList.split("@");
            List<UUID> finalList = new ArrayList<>();
            for (String uuidInString : stringUUIDS) {
                finalList.add(UUID.fromString(uuidInString));
            }
            return finalList;
        }
    }

    // Suppressed deprecation; new alternative API is not available in MC 1.21/1.21.1.
    @SuppressWarnings({"removal", "deprecation"})
    public static String getFinalItemName(ItemStack item) {
        String itemname;
        if (item.hasItemMeta()) {
            final ItemMeta meta = item.getItemMeta();
            if (meta.hasDisplayName()) {
                itemname = colorify(meta.getDisplayName());
            } else if (item.getType() == Material.ENCHANTED_BOOK
                    && meta instanceof EnchantmentStorageMeta enchMeta
                    && enchMeta.getStoredEnchants().size() == 1) {
                Map.Entry<Enchantment, Integer> entry = enchMeta.getStoredEnchants().entrySet().iterator().next();
                itemname = lm.itemEnchantHologram(entry.getKey(), entry.getValue());
            } else if (meta.hasLocalizedName()) {
                itemname = meta.getLocalizedName();
            } else {
                itemname = Utils.capitalizeFirstSplit(item.getType().toString());
            }
        } else {
            itemname = Utils.capitalizeFirstSplit(item.getType().toString());
        }
        return colorify(itemname).trim();
    }

    /**
     * Convert a Location to a String
     *
     * @param loc
     * @return
     */
    public static String LocationtoString(Location loc) {
        if (loc == null)
            return null;
        String sloc = "";
        sloc += ("W:" + loc.getWorld().getName() + ",");
        sloc += ("X:" + loc.getX() + ",");
        sloc += ("Y:" + loc.getY() + ",");
        sloc += ("Z:" + loc.getZ());
        return sloc;
    }

    /**
     * Convert a Location to a String with the Location rounded as defined via the
     * decimal argument
     *
     * @param loc
     * @param decimals
     * @return
     */
    public static String LocationRoundedtoString(Location loc, int decimals) {
        if (loc == null)
            return null;
        String sloc = "";
        sloc += ("W:" + loc.getWorld().getName() + ",");
        if (decimals <= 0) {
            sloc += ("X:" + (int) round(loc.getX(), decimals) + ",");
            sloc += ("Y:" + (int) round(loc.getY(), decimals) + ",");
            sloc += ("Z:" + (int) round(loc.getZ(), decimals));
        } else {
            sloc += ("X:" + round(loc.getX(), decimals) + ",");
            sloc += ("Y:" + round(loc.getY(), decimals) + ",");
            sloc += ("Z:" + round(loc.getZ(), decimals));
        }
        return sloc;
    }

    /**
     * Convert a String to a Location
     *
     * @param sloc
     * @return
     */
    public static Location StringtoLocation(@Nullable String sloc) {
        if (sloc == null)
            return null;
        String[] slocs = sloc.split(",");
        World w = Bukkit.getWorld(slocs[0].split(":")[1]);
        double x = Double.parseDouble(slocs[1].split(":")[1]);
        double y = Double.parseDouble(slocs[2].split(":")[1]);
        double z = Double.parseDouble(slocs[3].split(":")[1]);
        Location loc = new Location(w, x, y, z);

        if (sloc.contains("Yaw:") && sloc.contains("Pitch:")) {
            loc.setYaw(Float.parseFloat(slocs[4].split(":")[1]));
            loc.setPitch(Float.parseFloat(slocs[5].split(":")[1]));
        }
        return loc;
    }

    private static double round(double value, int precision) {
        int scale = (int) Math.pow(10, precision);
        return (double) Math.round(value * scale) / scale;
    }

    /**
     * Check if a String can be safely converted into a numeric value.
     *
     * @param strNum
     * @return
     */
    public static boolean isNumeric(String strNum) {
        if (strNum == null) {
            return false;
        }
        try {
            Double.parseDouble(strNum);
        } catch (NumberFormatException nfe) {
            return false;
        }
        return true;
    }

    public static <T> List<T> moveListElement(List<T> list, int index, boolean up) {
        if (up) {
            if (index == 0) {
                return list;
            }
            T element = list.get(index);
            list.remove(index);
            list.add(index - 1, element);
        } else {
            if (index == list.size() - 1) {
                return list;
            }
            T element = list.get(index);
            list.remove(index);
            list.add(index + 1, element);
        }
        return list;
    }

    /**
     * Get the max permission level of a permission object (e.g. player)
     *
     * @param permissible a object using the Permissible System e.g. a Player.
     * @param permission  a Permission String to check e.g. ecs.shops.limit.
     * @return the maximum int found, unless user is an Operator or has the
     * ecs.admin permission.
     * Then the returned result will be -1
     */
    public static int getMaxPermission(Permissible permissible, String permission) {
        return getMaxPermission(permissible, permission, 0);
    }

    /**
     * Get the max permission level of a permission object (e.g. player)
     *
     * @param permissible a object using the Permissible System e.g. a Player.
     * @param permission  a Permission String to check e.g. ecs.shops.limit.
     * @param defaultMax  the default max value to return if no permission is found
     * @return the maximum int found, unless user is an Operator or has the
     * ecs.admin permission.
     * Then the returned result will be -1
     */
    public static int getMaxPermission(Permissible permissible, String permission, int defaultMax) {
        if (permissible.isOp() || permissible.hasPermission("ecs.admin"))
            return -1;

        final AtomicInteger max = new AtomicInteger(defaultMax);

        permissible.getEffectivePermissions().stream().map(PermissionAttachmentInfo::getPermission)
                .map(String::toLowerCase).filter(value -> value.startsWith(permission))
                .map(value -> value.replace(permission, "")).forEach(value -> {
                    if (value.equalsIgnoreCase("*")) {
                        max.set(-1);
                        return;
                    }

                    if (max.get() == -1)
                        return;

                    try {
                        int amount = Integer.parseInt(value);

                        if (amount > max.get())
                            max.set(amount);
                    } catch (NumberFormatException ignored) {
                    }
                });

        return max.get();
    }

    //

    /**
     * Split a String by "_" and capitalize each First word, then join them together
     * via " "
     *
     * @param string
     * @return
     */
    public static String capitalizeFirstSplit(String string) {
        string = string.toLowerCase(Locale.ENGLISH);
        StringBuilder sbuf = new StringBuilder();
        for (String s : string.split("_")) {
            sbuf.append(s.subSequence(0, 1).toString().toUpperCase(Locale.ENGLISH))
                .append(s.subSequence(1, s.length()).toString().toLowerCase(Locale.ENGLISH))
                .append(" ");
        }
        return sbuf.toString();
    }

    public static boolean hasEnoughSpace(Player player, int amount, ItemStack item) {
        int emptySlots = 0;
        for (ItemStack content : player.getInventory().getStorageContents()) {
            if (content == null || content.getType() == Material.AIR) {
                emptySlots += item.getMaxStackSize();
            } else if (item.isSimilar(content) && !(content.getAmount() >= content.getMaxStackSize())) {
                int remaining = content.getMaxStackSize() - content.getAmount();
                emptySlots += remaining;
            }
        }

        return emptySlots >= amount;
    }

    public static int playerEmptyCount(ItemStack[] storageContents, ItemStack item) {
        int emptySlots = 0;
        for (ItemStack content : storageContents) {
            if (content == null || content.getType() == Material.AIR) {
                emptySlots += item.getMaxStackSize();
            } else {
                if (item.isSimilar(content) && !(content.getAmount() >= content.getMaxStackSize())) {
                    int remaining = content.getMaxStackSize() - content.getAmount();
                    emptySlots += remaining;
                }
            }
        }
        return emptySlots;
    }

    public static int containerEmptyCount(ItemStack[] storageContents, ItemStack item) {
        if (storageContents == null) {
            return Integer.MAX_VALUE;
        }

        int emptySlots = 0;
        for (ItemStack content : storageContents) {
            if (content == null || content.getType() == Material.AIR) {
                emptySlots += item.getMaxStackSize();
            } else {
                if (item.isSimilar(content) && !(content.getAmount() >= content.getMaxStackSize())) {
                    int remaining = content.getMaxStackSize() - content.getAmount();
                    emptySlots += remaining;
                }
            }
        }
        return emptySlots;
    }

    public static int howManyOfItemExists(ItemStack[] itemStacks, ItemStack mainItem) {
        if (itemStacks == null) {
            return Integer.MAX_VALUE;
        }

        int amount = 0;
        for (ItemStack item : itemStacks) {
            if (item == null || item.getType() == Material.AIR) {
                continue;
            }
            if (mainItem.isSimilar(item)) {
                amount += item.getAmount();
            }

        }
        return amount;
    }

    public static boolean containerHasEnoughSpace(Inventory container, int amount, ItemStack item) {
        int emptySlots = 0;
        for (ItemStack content : container.getStorageContents()) {
            if (content == null || content.getType() == Material.AIR) {
                emptySlots += item.getMaxStackSize();
            } else {
                if (item.isSimilar(content) && !(content.getAmount() >= content.getMaxStackSize())) {
                    int remaining = content.getMaxStackSize() - content.getAmount();
                    emptySlots += remaining;
                }
            }
        }

        return emptySlots >= amount;
    }

    public static List<String> calculatePossibleAmount(OfflinePlayer offlineCustomer, OfflinePlayer offlineSeller,
                                                       ItemStack[] playerInventory, ItemStack[] storageInventory, double eachBuyPrice, double eachSellPrice,
                                                       ItemStack itemStack) {
        List<String> results = new ArrayList<>();
        String buyCount = calculateBuyPossibleAmount(offlineCustomer, playerInventory, storageInventory, eachBuyPrice, itemStack);
        String sellCount = calculateSellPossibleAmount(offlineSeller, playerInventory, storageInventory, eachSellPrice, itemStack);
        results.add(buyCount);
        results.add(sellCount);
        return results;
    }

    public static String calculateBuyPossibleAmount(OfflinePlayer offlinePlayer, ItemStack[] playerInventory,
                                                    ItemStack[] storageInventory, double eachBuyPrice, ItemStack itemStack) {
        // I was going to run this in async but maybe later...
        int possibleCount = 0;
        double buyerBalance =
                Config.useXP ? XPEconomy.getXP(offlinePlayer) : EzChestShop.getEconomy().getBalance(offlinePlayer);
        int emptyCount = playerEmptyCount(playerInventory, itemStack);
        int howManyExists = howManyOfItemExists(storageInventory, itemStack);

        for (int num = 0; num < emptyCount; num++) {
            if (possibleCount + 1 > howManyExists) {
                break;
            }
            possibleCount += 1;
        }

        int result = 0;
        for (int num = 0; num < possibleCount; num++) {
            result += 1;
            if ((num + 1) * eachBuyPrice > buyerBalance) {
                return String.valueOf(num);
            }
        }

        return String.valueOf(result);
    }

    public static HashMap<OfflinePlayer, Boolean> hasPlayedBefore = new HashMap<>();
    public static boolean hasPlayedBefore(OfflinePlayer player) {
        if (player == null) {
            return false;
        }
        if (hasPlayedBefore.containsKey(player)) {
            return hasPlayedBefore.get(player);
        } else {
            boolean result = player.hasPlayedBefore();
            hasPlayedBefore.put(player, result);
            return result;
        }
    }

    public static String calculateSellPossibleAmount(OfflinePlayer offlinePlayer, ItemStack[] playerInventory,
                                                     ItemStack[] storageInventory, double eachSellPrice, ItemStack itemStack) {

        int possibleCount = 0;
        double buyerBalance;
        if (offlinePlayer == null) {
            buyerBalance = Double.MAX_VALUE;
        } else {
            if (hasPlayedBefore(offlinePlayer)) {
                buyerBalance = Config.useXP
                        ? XPEconomy.getXP(offlinePlayer)
                        : EzChestShop.getEconomy().getBalance(offlinePlayer);
            } else {
                buyerBalance = 0;
            }
        }
        int emptyCount = containerEmptyCount(storageInventory, itemStack);
        int howManyExists = howManyOfItemExists(playerInventory, itemStack);

        for (int num = 0; num < emptyCount; num++) {
            if (possibleCount + 1 > howManyExists) {
                break;
            }
            possibleCount += 1;
        }

        int result = 0;
        for (int num = 0; num < possibleCount; num++) {
            result += 1;
            if ((num + 1) * eachSellPrice > buyerBalance) {
                return String.valueOf(num);
            }
        }

        return String.valueOf(result);
    }

    public static boolean containsAtLeast(Inventory inventory, ItemStack item, int amount) {
        return countOf(inventory, item) >= amount;
    }

    private static int countOf(@NotNull Inventory inventory, @NotNull ItemStack target) {
        int count = 0;
        for (@Nullable ItemStack item : inventory.getContents()) {
            if (item == null)
                continue;
            if (target.isSimilar(item)) {
                try {
                    count = Math.addExact(count, item.getAmount());
                } catch (ArithmeticException ignored) {
                    return Integer.MAX_VALUE;
                }
            }
        }
        return count;
    }

    /*
    Removes the given ItemStacks from the inventory.

    It will try to remove 'as much as possible' from the types and amounts you give as arguments.

    The returned HashMap contains what it couldn't remove, where the key is the index of the parameter, and the value is the ItemStack at that index of the varargs parameter. If all the given ItemStacks are removed, it will return an empty HashMap.

    It is known that in some implementations this method will also set the inputted argument amount to the number of that item not removed from slots.
     */
    public static HashMap<Integer, ItemStack> removeItem(@NotNull Inventory inventory, @NotNull ItemStack... stacks) {
        HashMap<Integer, ItemStack> leftover = new HashMap<>();
        for (int i = 0; i < stacks.length; i++) {
            ItemStack stack = stacks[i];
            if (stack.getType() == Material.AIR) {
                continue;
            }
            if (stack.getType() == Material.FIREWORK_ROCKET) {
                int amount = stack.getAmount();
                for (int slot = 0; slot < inventory.getSize(); slot++) {
                    ItemStack item = inventory.getItem(slot);
                    if (item == null || item.getType() == Material.AIR) {
                        continue;
                    }
                    if (item.isSimilar(stack)) {
                        int newAmount = item.getAmount() - amount;
                        if (newAmount > 0) {
                            item.setAmount(newAmount);
                            amount = 0;
                        } else {
                            amount = -newAmount;
                            inventory.setItem(slot, null);
                        }
                    }
                    if (amount <= 0) {
                        break;
                    }
                }
                if (amount > 0) {
                    stack.setAmount(amount);
                    leftover.put(i, stack);
                }
            } else {
                inventory.removeItem(stack);
            }
        }
        return leftover;
    }

    public static OptionalInt tryParseInt(@Nullable String str) {
        try {
            // Integer.parseInt(String) will throw NumberFormatException for null.
            //noinspection DataFlowIssue
            int value = Integer.parseInt(str);
            return OptionalInt.of(value);
        } catch (NumberFormatException ignored) {
            return OptionalInt.empty();
        }
    }

    public static String getNextRotation(String current) {
        if (current == null)
            current = Config.settings_defaults_rotation;
        int index = rotations.indexOf(current);
        return index == rotations.size() - 1 ? rotations.getFirst() : rotations.get(index + 1);
    }

    public static String getPreviousRotation(String current) {
        if (current == null)
            current = Config.settings_defaults_rotation;
        int index = rotations.indexOf(current);
        return index == 0 ? rotations.getLast() : rotations.get(index - 1);
    }

    /**
     * Apply & color translating, as well as #ffffff hex color encoding to a String.
     * Versions below 1.16 will only get the last hex color symbol applied to them.
     *
     * @param str
     * @return
     */
    public static String colorify(String str) {
        if (str == null)
            return null;
        return translateHexColorCodes("#", "", ChatColor.translateAlternateColorCodes('&', str));
    }

    /**
     * Apply hex color coding to a String. possibility to add a special start or end
     * tag to the String.
     * Versions below 1.16 will only get the last hex color symbol applied to them.
     *
     * @param startTag
     * @param endTag
     * @param message
     * @return
     */
    public static String translateHexColorCodes(String startTag, String endTag, String message) {
        final Pattern hexPattern = Pattern.compile(startTag + "([A-Fa-f0-9]{6})" + endTag);
        final char COLOR_CHAR = ChatColor.COLOR_CHAR;
        Matcher matcher = hexPattern.matcher(message);
        StringBuilder builder = new StringBuilder(message.length() + 4 * 8);
        while (matcher.find()) {
            String group = matcher.group(1);
            matcher.appendReplacement(builder, COLOR_CHAR + "x"
                    + COLOR_CHAR + group.charAt(0) + COLOR_CHAR + group.charAt(1)
                    + COLOR_CHAR + group.charAt(2) + COLOR_CHAR + group.charAt(3)
                    + COLOR_CHAR + group.charAt(4) + COLOR_CHAR + group.charAt(5));
        }
        return matcher.appendTail(builder).toString();
    }

    public enum FormatType {
        GUI, CHAT, HOLOGRAM
    }

    public static String formatNumber(double number, FormatType type) {
        String result = "Error";
        DecimalFormat decimalFormat;
        switch (type) {
            case GUI:
                decimalFormat = new DecimalFormat(Config.display_numberformat_gui);
                result = decimalFormat.format(number);
                break;
            case CHAT:
                decimalFormat = new DecimalFormat(Config.display_numberformat_chat);
                result = decimalFormat.format(number);
                break;
            case HOLOGRAM:
                decimalFormat = new DecimalFormat(Config.display_numberformat_holo);
                result = decimalFormat.format(number);
                break;
        }
        if (Config.debug_logging) {
            EzChestShop.logger().info("Formatted number '{}' to: {}.", number, result);
        }
        return result;
    }

    public static void sendVersionMessage(Player player) {
        player.spigot().sendMessage(
                new ComponentBuilder("EzChestShopReborn v" + EzChestShop.getPlugin().getDescription().getVersion())
                        .color(net.md_5.bungee.api.ChatColor.GREEN)
                        .append("\nDiscord: ").color(net.md_5.bungee.api.ChatColor.BLUE).append(EzChestShopConstants.DISCORD_LINK)
                        .color(net.md_5.bungee.api.ChatColor.GRAY)
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                TextComponent.fromLegacyText(colorify("&fClick to join the plugin discord!"))))
                        .event(new ClickEvent(ClickEvent.Action.OPEN_URL, EzChestShopConstants.DISCORD_LINK))
                        .append("\nGitHub: ", ComponentBuilder.FormatRetention.NONE)
                        .color(net.md_5.bungee.api.ChatColor.RED).append(EzChestShopConstants.GITHUB_LINK)
                        .color(net.md_5.bungee.api.ChatColor.GRAY)
                        .event(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                TextComponent.fromLegacyText(colorify("&fClick to browse the GitHub repository!"))))
                        .event(new ClickEvent(ClickEvent.Action.OPEN_URL, EzChestShopConstants.GITHUB_LINK))
                        .create());
    }

    public static PersistentDataContainer getDataContainer(Block block) {
        PersistentDataContainer dataContainer = null;
        TileState state = (TileState) block.getState();
        Inventory inventory = Utils.getBlockInventory(block);

        if (block.getType() == Material.CHEST || block.getType() == Material.TRAPPED_CHEST) {
            if (inventory instanceof DoubleChestInventory) {
                DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
                Chest chestleft = (Chest) doubleChest.getLeftSide();
                Chest chestright = (Chest) doubleChest.getRightSide();

                if (!chestleft.getPersistentDataContainer().isEmpty()) {
                    dataContainer = chestleft.getPersistentDataContainer();
                } else {
                    dataContainer = chestright.getPersistentDataContainer();
                }
            } else {
                dataContainer = state.getPersistentDataContainer();
            }
        } else if (block.getType() == Material.BARREL) {
            dataContainer = state.getPersistentDataContainer();
        } else if (Tag.SHULKER_BOXES.isTagged(block.getType())) {
            dataContainer = state.getPersistentDataContainer();
        }
        return dataContainer;
    }

    public static void recognizeDatabase() {
        if (Config.database_type == Database.SQLITE) {
            //initialize SQLite
            databaseManager = new SQLite(EzChestShop.getPlugin());
            databaseManager.load();
        } else if (Config.database_type == Database.MYSQL) {
            //initialize MySQL
            databaseManager = new MySQL(EzChestShop.getPlugin());
            databaseManager.load();
        } else {
            //shouldn't happen technically
        }
    }

    public static void addItemIfEnoughSlots(Gui gui,int slot, GuiItem item) {
        if ((gui.getRows() * 9) > slot) {
            gui.setItem(slot, item);
        }
    }

    public static void addItemIfEnoughSlots(PaginatedGui gui,int slot, GuiItem item) {
        if ((gui.getRows() * 9) > slot) {
            gui.setItem(slot, item);
        }
    }

    public static EzShop isPartOfTheChestShop(Location location) {
        Block block = location.getBlock();

        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
            return null;
        }

        if (block.getState() instanceof Chest chest && chest.getInventory().getHolder(false) instanceof DoubleChest doubleChest) {
            Chest left = (Chest) Objects.requireNonNull(doubleChest.getLeftSide(), "doubleChest.getLeftSide()");
            EzShop leftShop = ShopContainer.getShop(left.getLocation());
            if (leftShop != null) {
                return leftShop;
            } else {
                Chest right = (Chest) Objects.requireNonNull(doubleChest.getRightSide(), "doubleChest.getRightSide()");
                return ShopContainer.getShop(right.getLocation());
            }
        }
        return null;
    }

    public static List<UUID> getAdminsForShop(EzShop shop) {
        List<UUID> admins = new ArrayList<>();
        admins.add(shop.getOwnerID());
        String adminsString = shop.getSettings().getAdmins();
        if (adminsString == null) {
            return admins;
        }
        String[] adminList = adminsString.split("@");
        for (String admin : adminList) {
            if (!admin.equalsIgnoreCase("") && !admin.equalsIgnoreCase(" ") && !admin.equalsIgnoreCase("null") && !admin.equalsIgnoreCase("NULL")) {
                //check if its a valid uuid
                boolean isValid = true;
                try {
                    UUID.fromString(admin);
                } catch (IllegalArgumentException exception) {
                    isValid = false;
                }
                if (isValid) {
                    admins.add(UUID.fromString(admin));
                }
            }
        }

        return admins;
    }

    public static List<Block> getNearbyEmptyShopForAdmins(Player player) {
        List<Block> emptyShops = new ArrayList<>();
        //We gonna check the area the maximum of 5 chunks away from the player
        //We gonna check if the shop is for the owner or its admins
        //We gonna check if the shop is empty
        //We gonna check if the shop inventory has at least 1 item required for the shop
        //We gonna check if the buy is enabled for the shop (Basically if shop owner is selling)

        //first we get the shops
        List<EzShop> shops = ShopContainer.getShops();
        //then we check if the shop is for the owner or its admins
        for (EzShop shop : shops) {
            if (shop.getSettings().isDbuy()) {
                continue;
            }
            //new check for admin shops
            if (shop.getSettings().isAdminshop()) {
                continue;
            }

            if (shop.getOwnerID().equals(player.getUniqueId()) || getAdminsForShop(shop).contains(player.getUniqueId())) {
                //then we check if the shop is empty

                if (shop.getLocation() == null || shop.getLocation().getWorld() == null) {
                    continue;
                }

                if (!Utils.isApplicableContainer(shop.getLocation().getBlock())) {
                    continue;
                }

                Inventory inventory = Utils.getBlockInventory(shop.getLocation().getBlock());
                if (inventory == null) {
                    continue;
                }

                if (!inventory.isEmpty()) {
                    //then we check if the shop inventory has at least 1 item required for the shop
                    ItemStack shopItem = shop.getShopItem().clone();
                    if (containsAtLeast(inventory, shopItem, 1)) {
                        continue;
                    }
                }

                if (shop.getLocation().getWorld().equals(player.getWorld())) {
                    if (shop.getLocation().distance(player.getLocation()) <= 80) {
                        emptyShops.add(shop.getLocation().getBlock());
                    }
                }
            }
        }

        return emptyShops;
    }

    public static void sendActionBar(Player player, String message) {
        // Apply color codes to the message using ChatColor.translateAlternateColorCodes
        String coloredMessage = ChatColor.translateAlternateColorCodes('&', message);
        player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent.fromLegacyText(coloredMessage));
    }

    public static boolean reInstallNamespacedKeyValues(PersistentDataContainer container, Location containerLocation) {
        EzShop shop = ShopContainer.getShop(containerLocation);
        if (shop == null) {
            return false; //false means the shop doesn't even exist in the database, so we don't need to do anything and send the message
        }

        container.set(EzChestShopConstants.OWNER_KEY, PersistentDataType.STRING, shop.getOwnerID().toString());
        container.set(EzChestShopConstants.BUY_PRICE_KEY, PersistentDataType.DOUBLE, shop.getBuyPrice());
        container.set(EzChestShopConstants.SELL_PRICE_KEY, PersistentDataType.DOUBLE, shop.getSellPrice());
        //add new settings data later
        container.set(EzChestShopConstants.ENABLE_MESSAGE_KEY, PersistentDataType.INTEGER, shop.getSettings().isMsgtoggle() ? 1 : 0);
        container.set(EzChestShopConstants.DISABLE_BUY_KEY, PersistentDataType.INTEGER, shop.getSettings().isDbuy() ?
                (shop.getBuyPrice() == 0 ? 1 : (Config.settings_defaults_dbuy ? 1 : 0))
                : (Config.settings_defaults_dbuy ? 1 : 0));
        container.set(EzChestShopConstants.DISABLE_SELL_KEY, PersistentDataType.INTEGER, shop.getSettings().isDsell() ?
                (shop.getSellPrice() == 0 ? 1 : (Config.settings_defaults_dsell ? 1 : 0))
                : (Config.settings_defaults_dsell ? 1 : 0));
        container.set(EzChestShopConstants.ADMIN_LIST_KEY, PersistentDataType.STRING, shop.getSettings().getAdmins());
        container.set(EzChestShopConstants.ENABLE_SHARED_INCOME_KEY, PersistentDataType.INTEGER, shop.getSettings().isShareincome() ? 1 : 0);
        container.set(EzChestShopConstants.ENABLE_ADMINSHOP_KEY, PersistentDataType.INTEGER, shop.getSettings().isAdminshop() ? 1 : 0);
        container.set(EzChestShopConstants.ROTATION_KEY, PersistentDataType.STRING, shop.getSettings().getRotation());
        return true;
    }
}
