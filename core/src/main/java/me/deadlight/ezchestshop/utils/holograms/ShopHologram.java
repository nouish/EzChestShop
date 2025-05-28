package me.deadlight.ezchestshop.utils.holograms;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.slf4j.Logger;

/**
 * This class represents a hologram of a shop.
 * <br>
 * Every shop has a BlockBoundHologram that manages all the PlayerBlockBoundHolograms which are per player.
 * This class is a wrapper for the PlayerBlockBoundHologram and provides various methods to show, hide and update the hologram.
 * <br>
 * When working with a shop hologram, this class should always be addressed, as it's a tailored abstraction to the BlockBoundHologram and PlayerBlockBoundHologram.
 */
public class ShopHologram {
    private static final Logger LOGGER = EzChestShop.logger();

    private static final Map<UUID, HashMap<Location, ShopHologram>> playerLocationShopHoloMap = new HashMap<>();
    private static final Map<Location, BlockBoundHologram> locationBlockHoloMap = new HashMap<>();

    /** The Hologram a player is currently inspecting (looking at). Only available if show item first is active. */
    private static final Map<UUID, ShopHologram> hologramInspections = new HashMap<>();

    private final Location location;
    private final Player player;
    private final BlockBoundHologram blockHolo;
    private EzShop shop;

    /**
     * THIS CONSTRUCTOR IS MAINLY FOR INTERNAL USAGE, USE getHologram() INSTEAD!
     * Create a new ShopHologram for a shop at a specific location for a player.
     * @param location The location of the shop
     * @param player The player that is viewing the hologram
     */
    public ShopHologram(Location location, Player player) {
        // Save the location and player
        this.location = location;
        this.player = player;

        // Make sure the BockBoundHologram is created
        if (!locationBlockHoloMap.containsKey(location)) {
            // Get the shop and it's hologram structure
            shop = ShopContainer.getShop(location);
            List<String> structure = new ArrayList<>(shop.getSettings().isAdminshop() ?
                    Config.holostructure_admin : Config.holostructure);

            String itemName = Utils.getFinalItemName(shop.getShopItem());
            Inventory shopInventory = Utils.getBlockInventory(location.getBlock());
            if (shopInventory == null) {
                World world = Objects.requireNonNullElse(location.getWorld(), player.getWorld());
                Block blockAtLocation = world.getBlockAt(location);
                // Debugging a specific, unconfirmed, error on Paper 1.21 #130.
                LOGGER.warn(" *".repeat(16));
                LOGGER.warn("Ran into unexpected state for shop at {}, {}, {} (in world {}).",
                        location.getBlockX(), location.getBlockY(), location.getBlockZ(), world);
                LOGGER.warn("Player: {} ({})", player.getName(), player.getUniqueId());
                LOGGER.warn("Block type: {}", blockAtLocation.getType().key());
                LOGGER.warn("Item: {}", itemName);
                LOGGER.warn("Please report this on GitHub; more information about the world and other plugins will be useful.");
                LOGGER.warn(" *".repeat(32));
                // Because I'm looking for a more permanent fix here, we just fall through, knowing it will result in an NPE.
            }
            assert shopInventory != null : "Location should be a valid shop, but has no inventory!";
            int availableSlots = shopInventory.getSize();
            for (ItemStack item : shopInventory.getStorageContents()) {
                // if item is one of the below, then it is a slot that can be used, otherwise subtract from available slots.
                if (!(item == null || item.getType() == Material.AIR || item.isSimilar(shop.getShopItem()))) {
                    availableSlots--;
                }
            }
            List<String> possibleCounts = new ArrayList<>();
            try {
                possibleCounts = Utils.calculatePossibleAmount(Bukkit.getOfflinePlayer(player.getUniqueId()),
                        Bukkit.getOfflinePlayer(shop.getOwnerID()),
                        player.getInventory().getStorageContents(),
                        shopInventory.getStorageContents(),
                        shop.getBuyPrice(), shop.getSellPrice(), shop.getShopItem());
            } catch (Exception e) {
                // If the block is not found or some other error occurs, just set the possible counts to 0
                possibleCounts.add("0");
                possibleCounts.add("0");
            }

            /*
             * Text default placeholders:
             * (%buyprice% or <emptyShopInfo/> for example - simply variables that are replaced with the actual values)
             */
            HashMap<String, String> textReplacements = new HashMap<>();
            textReplacements.put("%item%", itemName);
            textReplacements.put("%buy%", Utils.formatNumber(shop.getBuyPrice(), Utils.FormatType.HOLOGRAM));
            textReplacements.put("%sell%", Utils.formatNumber(shop.getSellPrice(), Utils.FormatType.HOLOGRAM));
            textReplacements.put("%currency%", Config.currency);
            textReplacements.put("%owner%", Bukkit.getOfflinePlayer(shop.getOwnerID()).getName());
            textReplacements.put("%maxbuy%", possibleCounts.get(0));
            textReplacements.put("%maxsell%", possibleCounts.get(1));
            textReplacements.put("%maxStackSize%", shop.getShopItem().getMaxStackSize() + "");
            textReplacements.put("%stock%", Utils.howManyOfItemExists(shopInventory.getStorageContents(), shop.getShopItem()) + "");
            textReplacements.put("%capacity%", availableSlots * shop.getShopItem().getMaxStackSize() + "");
            // the amount of itemdata replacements is defined in the config and may wary
            int itemDataLines = (int) structure.stream()
                    .filter(s -> s.startsWith("<itemdata") && !s.startsWith("<itemdataRest"))
                    .count();
            for (int i = 0; i < itemDataLines; i++) {
                textReplacements.put("<itemdata" + (i + 1) + "/>", "");
            }
            textReplacements.put("<itemdataRest/>", "");
            // Emptyshop should only be shown for non-adminshops.
            // Previous config versions had the placeholder, so this check is needed for backwards compatibility.
            if (!shop.getSettings().isAdminshop() &&
                    (shop.getOwnerID() == player.getUniqueId() ||
                            shop.getSettings().getAdmins().contains(player.getUniqueId().toString()))
            ) {
                // visible if the shop does not contain at least 1 item.
                boolean visible = !Utils.containsAtLeast(shopInventory, shop.getShopItem(), 1);
                textReplacements.put("<emptyShopInfo/>", visible ? LanguageManager.getInstance().emptyShopHologramInfo() : "");
            } else {
                textReplacements.put("<emptyShopInfo/>", "");
            }
            // the amount of custom message replacements is defined in the config and may wary
            int customLines = (int) structure.stream()
                    .filter(s -> s.startsWith("<itemdata") && !s.startsWith("<itemdataRest"))
                    .count();
            for (int i = 0; i < customLines; i++) {
                if (shop.getSettings().getCustomMessages().size() > i) {
                    textReplacements.put("<custom" + (i + 1) + "/>", shop.getSettings().getCustomMessages().get(i));
                } else {
                    textReplacements.put("<custom" + (i + 1) + "/>", "");
                }
            }

            /*
             * Item default placeholders:
             * ([item] for example - variables that are replaced with the an item)
             */
            HashMap<String, ItemStack> itemReplacements = new HashMap<>();
            itemReplacements.put("[item]", shop.getShopItem());

            /*
             * Conditional tags:
             * (buy, sell, separator for example - These tags are placed around content and are only shown if the condition is true)
             */
            HashMap<String, Boolean> conditionalTags = new HashMap<>();
            // buy and sell are inverted because true for the Hologram means it is shown
            // and true for isDbuy/isDsell means it is disabled aka hidden
            conditionalTags.put("buy", !shop.getSettings().isDbuy());
            conditionalTags.put("sell", !shop.getSettings().isDsell());
            // true if both are visible aka false -> buy & sell is enabled
            conditionalTags.put("separator", !shop.getSettings().isDbuy() && !shop.getSettings().isDsell());

            // Also set the separator replacement text if both are disabled
            // This is a special case, because few (only separator atm) of the conditional tags can also act as replacements.
            HashMap<String, String> conditionalTextReplacements = new HashMap<>();
            EzShop shop = ShopContainer.getShop(location);
            if (shop != null && shop.getSettings() != null && shop.getSettings().isDbuy() && shop.getSettings().isDsell()) {
                conditionalTextReplacements.put("separator", LanguageManager.getInstance().disabledButtonTitle());
                conditionalTags.put("separator", true);
            }

            /*
             * Always visible text replacements:
             * (<emptyShopInfo/> for example - These tags are always visible,
             * even if show Item first is enabled - these Hologram texts render regardless)
             */
            List<String> alwaysVisibleTextReplacements = new ArrayList<>();
            alwaysVisibleTextReplacements.add("<emptyShopInfo/>");
            for (int i = 0; i < customLines; i++) {
                alwaysVisibleTextReplacements.add("<custom" + (i + 1) + "/>");
            }


            // Create the hologram
            BlockBoundHologram blockHolo = new BlockBoundHologram(location, structure, textReplacements, itemReplacements,
                    conditionalTags, conditionalTextReplacements, alwaysVisibleTextReplacements);
            // Add the hologram to the map
            locationBlockHoloMap.put(location, blockHolo);
        }
        // else the hologram already exists, so just get it
        this.blockHolo = locationBlockHoloMap.get(location);

        // Save the just created hologram to the player's hologram map
        playerLocationShopHoloMap
                .computeIfAbsent(player.getUniqueId(), ignoredKey -> new HashMap<>())
                .put(location, this);

        // Make sure inventory replacements are up to date for all players.
        ShopHologram.updateInventoryReplacements(location);
    }

    /**
     * Get the hologram of a shop, if it does not exist, create it.
     * @param location The location of the shop
     * @param player The player that is viewing the hologram
     * @return The hologram
     */
    public static ShopHologram getHologram(Location location, Player player) {
        if (!playerLocationShopHoloMap.containsKey(player.getUniqueId()) || !playerLocationShopHoloMap.get(player.getUniqueId()).containsKey(location)) {
            new ShopHologram(location, player);
        }
        return playerLocationShopHoloMap.get(player.getUniqueId()).get(location);
    }

    /**
     * Check if a hologram exists for the shop at this given location.
     * @param location The location of the shop
     * @param player The player that is viewing the hologram
     * @return True if the hologram exists
     */
    public static boolean hasHologram(Location location, Player player) {
        return playerLocationShopHoloMap.containsKey(player.getUniqueId()) &&
                playerLocationShopHoloMap.get(player.getUniqueId()).containsKey(location);
    }

    /**
     * Get all holograms a player is currently viewing.
     * @param player The player
     * @return The ShopHolograms
     */
    public static List<ShopHologram> getViewedHolograms(Player player) {
        if (playerLocationShopHoloMap.containsKey(player.getUniqueId())) {
            return new ArrayList<>(playerLocationShopHoloMap.get(player.getUniqueId()).values());
        }
        return new ArrayList<>();
    }

    // public static List<EzShop> getShopsInRadius(Location location, int radius) {
    //     //TODO: Consider using a more efficient algorithm like a kd-tree
    //     return ShopContainer.getShops().stream()
    //             .filter(ezShop -> ezShop.getLocation() != null
    //                     && location.getWorld().equals(ezShop.getLocation().getWorld())
    //                     && location.distance(ezShop.getLocation()) < Config.holodistancing_distance + 5)
    //             .collect(Collectors.toList());
    // }


    /**
     * Reload all holograms. Called when the plugin is reloaded via /ecsadmin reload.
     */
    public static void reloadAll() {
        List<ShopHologram> shopHolos = playerLocationShopHoloMap.values().stream()
                .map(HashMap::values)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
        shopHolos.forEach(hologram -> {
            hologram.blockHolo.updateContents(Config.holostructure);
            hideForAll(hologram.getLocation());
        });
    }

    /**
     * Hide all holograms that are currently visible to a player.
     * @param player The player
     */
    public static void hideAll(Player player) {
        if (playerLocationShopHoloMap.containsKey(player.getUniqueId())) {
            List<ShopHologram> shopHolos = new ArrayList<>(playerLocationShopHoloMap.get(player.getUniqueId()).values());
            shopHolos.forEach(ShopHologram::hide);
            playerLocationShopHoloMap.remove(player.getUniqueId());

            if (ShopHologram.isPlayerInspectingShop(player)) {
                ShopHologram.getInspectedShopHologram(player).removeInspectedShop();
            }
        }
    }

    /**
     * Hide a hologram at a specific location for all players.
     * <br>
     * (each shop block has a hologram for each player that is viewing it, so all of those need to be hidden)
     * @param location The location of the shop
     */
    public static void hideForAll(Location location) {
        playerLocationShopHoloMap.values().forEach(locationShopHoloMap -> {
            ShopHologram hologram = locationShopHoloMap.get(location);
            if (hologram != null) {
                hologram.hide();
            }
        });
        ShopHologram.hologramInspections.values().stream()
                .filter(shopHolo -> shopHolo.getLocation().equals(location))
                .collect(Collectors.toSet())
                .forEach(ShopHologram::removeInspectedShop);
        locationBlockHoloMap.remove(location);
    }

    /**
     * Hide this hologram for the player.
     */
    public void hide() {
        blockHolo.getPlayerHologram(player).hide();
        playerLocationShopHoloMap.get(player.getUniqueId()).remove(location);
    }

    /**
     * Check if this hologram is currently inspected (looked at) by the player.
     * @return True if the player is inspecting this hologram
     */
    public boolean hasInspector() {
        return blockHolo.hasInspector(player);
    }

    /**
     * Shows only the item of the hologram, nothing more. It will also remove any text that is not always visible.
     */
    public void showOnlyItem() {
        blockHolo.getPlayerHologram(player).showOnlyItem();
    }

    /**
     * Should be run after showOnlyItem() to show the text of the hologram.
     */
    public void showTextAfterItem() {
        blockHolo.getPlayerHologram(player).showTextAfterItem();
    }

    /**
     * Should be run after showOnlyItem() and will only show the always visible text of the hologram.
     */
    public void showAlwaysVisibleText() {
        blockHolo.getPlayerHologram(player).showAlwaysVisibleText();
    }

    /**
     * Shows the entire hologram with texts and items.
     */
    public void show() {
        blockHolo.getPlayerHologram(player).show();
    }


    public void setCustomHologramMessage(List<String> messages) {
        PlayerBlockBoundHologram playerHolo = blockHolo.getPlayerHologram(player);
        if (playerHolo != null) {
            int lines = (int) playerHolo.getBlockHologram().getContents().stream()
                    .filter(s -> s.startsWith("<custom"))
                    .count();
            // Update at most x lines
            for (int i = 0; i < lines; i++) {
                if (i >= messages.size()) {
                    playerHolo.updateTextReplacement("<custom" + (i + 1) + "/>", "", true, true);
                } else {
                    playerHolo.updateTextReplacement("<custom" + (i + 1) + "/>", messages.get(i), true, true);
                }
            }
        }
    }

    public void setItemDataVisible(boolean visible) {
        PlayerBlockBoundHologram hologram = blockHolo.getPlayerHologram(player);
        int lines = (int) hologram.getBlockHologram().getContents().stream()
                .filter(s -> s.startsWith("<itemdata") && !s.startsWith("<itemdataRest"))
                .count();
        shop = ShopContainer.getShop(location);
        if (shop == null) {
            // Just in case the shop was deleted while someone else was inspecting it.
            return;
        }
        for (int i = -1; i < lines; i++) {
            ItemStack item = shop.getShopItem();
            if (Tag.SHULKER_BOXES.isTagged(item.getType())
                    || !item.getEnchantments().isEmpty()
                    || (item.getType() == Material.ENCHANTED_BOOK
                    && ((EnchantmentStorageMeta) item.getItemMeta()).getStoredEnchants().size() > 1)) {
                if (i == -1) {
                    hologram.updateTextReplacement("<itemdataRest/>", visible ?
                            BlockBoundHologram.getHologramItemData(i, item, lines) : "", false, true);
                } else {
                    hologram.updateTextReplacement("<itemdata" + (i + 1) + "/>", visible ?
                            BlockBoundHologram.getHologramItemData(i, item, lines) : "", false, true);
                }
            }
        }
    }

    public void updateEmptyShopInfo() {
        if (!shop.getSettings().isAdminshop() && (
                shop.getOwnerID().equals(player.getUniqueId()) ||
                shop.getSettings().getAdmins().contains(player.getUniqueId().toString())
            )) {
            PlayerBlockBoundHologram playerHolo = blockHolo.getPlayerHologram(player);
            if (playerHolo != null) {
                Inventory shopInventory = Utils.getBlockInventory(location.getBlock());
                // visible if the shop does not contain at least 1 item.
                boolean visible = !Utils.containsAtLeast(shopInventory, shop.getShopItem(), 1);
                playerHolo.updateTextReplacement("<emptyShopInfo/>", visible ?
                        LanguageManager.getInstance().emptyShopHologramInfo() : "", false, true);
            }
        }
    }

    public void updateBuyPrice() {
        PlayerBlockBoundHologram playerHolo = blockHolo.getPlayerHologram(player);
        if (playerHolo != null) {
            shop = ShopContainer.getShop(location);
            playerHolo.updateTextReplacement("%buy%", Utils.formatNumber(shop.getBuyPrice(), Utils.FormatType.HOLOGRAM), true, true);
        }
    }

    public void updateSellPrice() {
        PlayerBlockBoundHologram playerHolo = blockHolo.getPlayerHologram(player);
        if (playerHolo != null) {
            shop = ShopContainer.getShop(location);
            playerHolo.updateTextReplacement("%sell%", Utils.formatNumber(shop.getSellPrice(), Utils.FormatType.HOLOGRAM), true, true);
        }
    }

    public void updateDbuy() {
        PlayerBlockBoundHologram playerHolo = blockHolo.getPlayerHologram(player);
        if (playerHolo != null) {
            shop = ShopContainer.getShop(location);
            if (shop.getSettings().isDbuy() && shop.getSettings().isDsell()) {
                updateBuySellSeparator(playerHolo);
                playerHolo.updateConditionalTag("buy", !shop.getSettings().isDbuy(), true);
            } else {
                playerHolo.updateConditionalTag("buy", !shop.getSettings().isDbuy(), true);
                updateBuySellSeparator(playerHolo);
            }

        }
    }

    public void updateDsell() {
        PlayerBlockBoundHologram playerHolo = blockHolo.getPlayerHologram(player);
        if (playerHolo != null) {
            shop = ShopContainer.getShop(location);
            if (shop.getSettings().isDbuy() && shop.getSettings().isDsell()) {
                updateBuySellSeparator(playerHolo);
                playerHolo.updateConditionalTag("sell", !shop.getSettings().isDsell(), true);
            } else {
                playerHolo.updateConditionalTag("sell", !shop.getSettings().isDsell(), true);
                updateBuySellSeparator(playerHolo);
            }
        }
    }

    public void updateOwner() {
        PlayerBlockBoundHologram playerHolo = blockHolo.getPlayerHologram(player);
        if (playerHolo != null) {
            shop = ShopContainer.getShop(location);
            playerHolo.updateTextReplacement("%owner%", Bukkit.getOfflinePlayer(shop.getOwnerID()).getName(), true, true);
        }
    }

    public void updateStockAndCapacity() {
        PlayerBlockBoundHologram playerHolo = blockHolo.getPlayerHologram(player);
        if (playerHolo == null) {
            return;
        }

        shop = ShopContainer.getShop(location);
        Inventory shopInventory = Utils.getBlockInventory(location.getBlock());
        if (shopInventory == null) {
            EzChestShop.logger().warn("Unexpected {} block at {}, {}, {}.",
                    player.getWorld().getBlockAt(location).getType().key(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ());
            return;
        }
        int availableSlots = shopInventory.getSize();

        //check if the hologram text actually contains the %stock% and %capacity% placeholders
        boolean containsStock = false;
        boolean containsCapacity = false;

        for (String content : playerHolo.getBlockHologram().getContents()) {
            if (content.contains("%stock%")) {
                containsStock = true;
            }
            if (content.contains("%capacity%")) {
                containsCapacity = true;
            }
        }

        if (containsStock) {
            playerHolo.updateTextReplacement("%stock%", Utils.howManyOfItemExists(shopInventory.getStorageContents(),
                    shop.getShopItem()) + "", true, false);
        }
        if (containsCapacity) {
            playerHolo.updateTextReplacement("%capacity%", availableSlots * shop.getShopItem().getMaxStackSize() + "",
                    true, false);
        }
    }

    public void updateMaxBuyAndSell() {
        PlayerBlockBoundHologram playerHolo = blockHolo.getPlayerHologram(player);
        if (playerHolo != null) {
            shop = ShopContainer.getShop(location);
            List<String> possibleCounts = Utils.calculatePossibleAmount(Bukkit.getOfflinePlayer(player.getUniqueId()),
                    Bukkit.getOfflinePlayer(shop.getOwnerID()), player.getInventory().getStorageContents(),
                    Utils.getBlockInventory(shop.getLocation().getBlock()).getStorageContents(),
                    shop.getBuyPrice(), shop.getSellPrice(), shop.getShopItem());

            //check if the hologram text actually contains the %maxbuy% and %maxsell% placeholders
            boolean containsMaxBuy = false;
            boolean containsMaxSell = false;

            for (String content : playerHolo.getBlockHologram().getContents()) {
                if (content.contains("%maxbuy%")) {
                    containsMaxBuy = true;
                }
                if (content.contains("%maxsell%")) {
                    containsMaxSell = true;
                }
            }

            if (containsMaxBuy) {
                playerHolo.updateTextReplacement("%maxbuy%", possibleCounts.get(0) + "", true, false);
            }

            if (containsMaxSell) {
                playerHolo.updateTextReplacement("%maxsell%", possibleCounts.get(1) + "", true, false);
            }
        }
    }

    public static void updateInventoryReplacements(Location location) {
        BlockBoundHologram hologram = locationBlockHoloMap.get(location);
        // Ignore if the shop's Hologram doesn't exist
        if (hologram == null) {
            return;
        }
        //laggy part
        hologram.getViewerHolograms().forEach(playerBlockBoundHologram -> {
            ShopHologram shopHolo = ShopHologram.getHologram(location, playerBlockBoundHologram.getPlayer());
            shopHolo.updateStockAndCapacity(); //second we gonna look into this
            shopHolo.updateEmptyShopInfo();
            shopHolo.updateMaxBuyAndSell(); //first we gonna look into this
        });
    }

    /**
     * Update the position of the hologram. Used when the shop is rotated or expanded to/reduced from a double chest.
     */
    public void updatePosition() {
        PlayerBlockBoundHologram playerHolo = blockHolo.getPlayerHologram(player);
        if (playerHolo != null) {
            playerHolo.updatePosition();
        }
    }

    /**
     * Set this hologram as the hologram a player is currently inspecting (looking at).
     */
    public void setAsInspectedShop() {
        if (!hologramInspections.containsKey(player.getUniqueId())) {
            hologramInspections.put(player.getUniqueId(), this);
        }
    }

    /**
     * Remove the hologram a player is currently inspecting (looking at).
     */
    public void removeInspectedShop() {
        if (hologramInspections.remove(player.getUniqueId()) != null) {
            blockHolo.removeInspector(player);
        }
    }

    /**
     * Check if a player is currently inspecting a shop (looking at it).
     * @param player The player
     * @return True if the player is inspecting a shop
     */
    public static boolean isPlayerInspectingShop(Player player) {
        return hologramInspections.containsKey(player.getUniqueId());
    }

    /**
     * Get the hologram a player is currently inspecting (looking at).
     * @param player The player
     * @return The hologram
     */
    public static ShopHologram getInspectedShopHologram(Player player) {
        return hologramInspections.get(player.getUniqueId());
    }


    public Location getLocation() {
        return location;
    }

    /*
     █████   █████          ████
    ░░███   ░░███          ░░███
     ░███    ░███   ██████  ░███  ████████   ██████  ████████   █████
     ░███████████  ███░░███ ░███ ░░███░░███ ███░░███░░███░░███ ███░░
     ░███░░░░░███ ░███████  ░███  ░███ ░███░███████  ░███ ░░░ ░░█████
     ░███    ░███ ░███░░░   ░███  ░███ ░███░███░░░   ░███      ░░░░███
     █████   █████░░██████  █████ ░███████ ░░██████  █████     ██████
    ░░░░░   ░░░░░  ░░░░░░  ░░░░░  ░███░░░   ░░░░░░  ░░░░░     ░░░░░░
                                  ░███
                                  █████
                                 ░░░░░
        */

    /**
     * Update the separator between the buy and sell button.
     * <br>
     * The separator should only be visible if both buttons are enabled.
     * <br>
     * If both are disabled, the separator should be replaced with a text that says "disabled".
     * @param playerHolo The hologram
     */
    private void updateBuySellSeparator(PlayerBlockBoundHologram playerHolo) {
        if (!shop.getSettings().isDbuy() && !shop.getSettings().isDsell()) {
            playerHolo.getBlockHologram().removeConditionalText("separator");
            playerHolo.updateConditionalTag("separator", true, true);
        } else if (shop.getSettings().isDbuy() && shop.getSettings().isDsell())  {
            playerHolo.getBlockHologram().setConditionalText("separator", LanguageManager.getInstance().disabledButtonTitle());
            playerHolo.updateConditionalTag("separator", true, true);
        } else {
            playerHolo.getBlockHologram().removeConditionalText("separator");
            playerHolo.updateConditionalTag("separator", false, true);
        }
    }
}
