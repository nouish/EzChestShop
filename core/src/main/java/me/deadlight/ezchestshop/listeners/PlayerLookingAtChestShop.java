package me.deadlight.ezchestshop.listeners;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.Constants;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.utils.ASHologram;
import me.deadlight.ezchestshop.utils.FloatingItem;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.holograms.BlockBoundHologram;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.Vector;

public class PlayerLookingAtChestShop implements Listener {

    private final Map<Player, Location> map = new HashMap<>();

    private static final Map<Location, List<Player>> playershopmap = new HashMap<>();

    @EventHandler
    public void onLook(PlayerMoveEvent event) {
        Block target = event.getPlayer().getTargetBlockExact(5);

        if (target == null) {
            return;
        }

        if (!Utils.isApplicableContainer(target)) {
            return;
        }

        Inventory inventory = Utils.getBlockInventory(target);
        if (inventory instanceof DoubleChestInventory) {
            //double chest

            DoubleChest doubleChest = (DoubleChest) inventory.getHolder(false);
            Chest leftchest = (Chest) doubleChest.getLeftSide(false);
            Chest rightchest = (Chest) doubleChest.getRightSide(false);

            if (leftchest.getPersistentDataContainer().has(Constants.OWNER_KEY, PersistentDataType.STRING)
                    || rightchest.getPersistentDataContainer().has(Constants.OWNER_KEY, PersistentDataType.STRING)) {

                PersistentDataContainer rightone = null;

                if (!leftchest.getPersistentDataContainer().isEmpty()) {
                    target = leftchest.getBlock();
                    rightone = leftchest.getPersistentDataContainer();
                } else {
                    target = rightchest.getBlock();
                    rightone = rightchest.getPersistentDataContainer();
                }

                ItemStack thatItem = Utils.decodeItem(rightone.get(Constants.ITEM_KEY, PersistentDataType.STRING));
                double buy = rightone.getOrDefault(Constants.BUY_PRICE_KEY, PersistentDataType.DOUBLE, Double.MAX_VALUE);
                double sell = rightone.getOrDefault(Constants.SELL_PRICE_KEY, PersistentDataType.DOUBLE, Double.MAX_VALUE);
                boolean is_adminshop = rightone.getOrDefault(Constants.ENABLE_ADMINSHOP_KEY, PersistentDataType.INTEGER, 0) == 1;
                boolean is_dbuy = rightone.getOrDefault(Constants.DISABLE_BUY_KEY, PersistentDataType.INTEGER, 0) == 1;
                boolean is_dsell = rightone.getOrDefault(Constants.DISABLE_SELL_KEY, PersistentDataType.INTEGER, 0) == 1;
                OfflinePlayer offlinePlayerOwner = Bukkit.getOfflinePlayer(UUID.fromString(rightone.get(Constants.OWNER_KEY, PersistentDataType.STRING)));
                String shopOwner = offlinePlayerOwner.getName();
                if (shopOwner == null) {
                    shopOwner = ChatColor.RED + "Error";
                }

                Location holoLoc = getHoloLoc(target);

                if (!isAlreadyLooking(event.getPlayer(), target) && Config.showholo && !isAlreadyPresenting(target.getLocation(), event.getPlayer())) {
                    showHologram(holoLoc, target.getLocation().clone(), thatItem, buy, sell, event.getPlayer(), is_adminshop, shopOwner, is_dbuy, is_dsell);
                }
                map.put(event.getPlayer(), target.getLocation());
            }
        } else {
            //not a double chest
            PersistentDataContainer container = ((TileState) target.getState(false)).getPersistentDataContainer();
            if (container.has(Constants.OWNER_KEY, PersistentDataType.STRING)) {
                ItemStack thatItem = Utils.decodeItem(container.get(Constants.ITEM_KEY, PersistentDataType.STRING));
                double buy = container.getOrDefault(Constants.BUY_PRICE_KEY, PersistentDataType.DOUBLE, Double.MAX_VALUE);
                double sell = container.getOrDefault(Constants.SELL_PRICE_KEY, PersistentDataType.DOUBLE, Double.MAX_VALUE);
                boolean is_adminshop = container.getOrDefault(Constants.ENABLE_ADMINSHOP_KEY, PersistentDataType.INTEGER, 0) == 1;
                boolean is_dbuy = container.getOrDefault(Constants.DISABLE_BUY_KEY, PersistentDataType.INTEGER, 0) == 1;
                boolean is_dsell = container.getOrDefault(Constants.DISABLE_SELL_KEY, PersistentDataType.INTEGER, 0) == 1;
                OfflinePlayer offlinePlayerOwner = Bukkit.getOfflinePlayer(UUID.fromString(container.get(Constants.OWNER_KEY, PersistentDataType.STRING)));
                String shopOwner = offlinePlayerOwner.getName();
                if (shopOwner == null) {
                    shopOwner = ChatColor.RED + "Error";
                }
                Location holoLoc = getHoloLoc(target);

                if (!isAlreadyLooking(event.getPlayer(), target) && Config.showholo && !isAlreadyPresenting(target.getLocation(), event.getPlayer())) {
                    showHologram(holoLoc, target.getLocation().clone(), thatItem, buy, sell, event.getPlayer(), is_adminshop, shopOwner, is_dbuy, is_dsell);
                }

                map.put(event.getPlayer(), target.getLocation());
            }
        }
    }

    private void showHologram(Location spawnLocation, Location shopLocation, ItemStack thatItem, double buy, double sell,
                              Player player, boolean is_adminshop, String shop_owner, boolean is_dbuy, boolean is_dsell) {
        List<ASHologram> holoTextList = new ArrayList<>();
        List<FloatingItem> holoItemList = new ArrayList<>();

        Location lineLocation = spawnLocation.clone().subtract(0, 0.1, 0);
        String itemname = Utils.getFinalItemName(thatItem);
        List<String> possibleCounts = Utils.calculatePossibleAmount(Bukkit.getOfflinePlayer(player.getUniqueId()),
                Bukkit.getOfflinePlayer(UUID.fromString(((TileState) shopLocation.getBlock().getState(false)).getPersistentDataContainer()
                        .get(Constants.OWNER_KEY, PersistentDataType.STRING))), player.getInventory().getStorageContents(),
                Utils.getBlockInventory(shopLocation.getBlock()).getStorageContents(),
                buy, sell, thatItem);

        List<String> structure = new ArrayList<>(is_adminshop ? Config.holostructure_admin : Config.holostructure);
        if (ShopContainer.getShopSettings(shopLocation).getRotation().equals("down")) Collections.reverse(structure);
        int lines = structure.stream().filter(s -> s.startsWith("<itemdata") && !s.startsWith("<itemdataRest")).toList().size();
        for (String element : structure) {
            if (element.equalsIgnoreCase("[Item]")) {
                lineLocation.add(0, 0.15 * Config.holo_linespacing, 0);
                FloatingItem floatingItem = new FloatingItem(player, thatItem, lineLocation);
                Utils.onlinePackets.add(floatingItem);
                holoItemList.add(floatingItem);
                lineLocation.add(0, 0.35 * Config.holo_linespacing, 0);
            } else {
                String line = Utils.colorify(element.replace("%item%", itemname).replace("%buy%", Utils.formatNumber(buy, Utils.FormatType.HOLOGRAM)).
                        replace("%sell%", Utils.formatNumber(sell, Utils.FormatType.HOLOGRAM)).replace("%currency%", Config.currency)
                        .replace("%owner%", shop_owner).replace("%maxbuy%", possibleCounts.get(0)).replace("%maxsell%", possibleCounts.get(1))
                        .replace("%maxStackSize%", thatItem.getMaxStackSize() + "")
                        .replace("%stock%", Utils.howManyOfItemExists(Utils.getBlockInventory(shopLocation.getBlock()).getStorageContents(), thatItem) + "")
                        .replace("%capacity%", Utils.getBlockInventory(shopLocation.getBlock()).getSize() + ""));
                if (is_dbuy || is_dsell) {
                    line = line.replaceAll("<separator>.*?<\\/separator>", "");
                    if (is_dbuy && is_dsell) {
                        line = LanguageManager.getInstance().disabledButtonTitle();
                    } else if (is_dbuy) {
                        line = line.replaceAll("<buy>.*?<\\/buy>", "").replaceAll("<sell>|<\\/sell>", "");
                    } else if (is_dsell) {
                        line = line.replaceAll("<sell>.*?<\\/sell>", "").replaceAll("<buy>|<\\/buy>", "");
                    }
                } else {
                    line = line.replaceAll("<separator>|<\\/separator>", "").replaceAll("<buy>|<\\/buy>", "").replaceAll("<sell>|<\\/sell>", "");
                }
                if (line.startsWith("<custom")) {
                    if (Config.settings_hologram_message_enabled) {
                        int customNum = Integer.parseInt(line.replaceAll("\\D", ""));
                        List<String> customMessages = ShopContainer.getShopSettings(shopLocation).getCustomMessages();

                        if (customNum > customMessages.size()) continue;
                        line = Utils.colorify(customMessages.get(customNum - 1));
                    } else {
                        continue;
                    }
                }
                if (line.startsWith("<itemdata")) {
                    if (player.isSneaking()) {
                        ItemStack item = ShopContainer.getShop(shopLocation).getShopItem();
                        if (Tag.SHULKER_BOXES.isTagged(item.getType()) || !item.getEnchantments().isEmpty()) {
                            try {
                                int lineNum = Integer.parseInt(line.replaceAll("\\D", ""));
                                line = BlockBoundHologram.getHologramItemData(lineNum, item, lines);
                            } catch (NumberFormatException e) {
                                line = BlockBoundHologram.getHologramItemData(-1, item, lines);
                            }
                        } else {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }
                if (line.contains("<emptyShopInfo/>")) {
                    EzShop shop = ShopContainer.getShop(shopLocation);
                    // Shops that are not selling anything should not show this message.
                    if (player.getUniqueId().equals(shop.getOwnerID()) && !is_dbuy && !is_adminshop) {
                        // Check if the shop is empty by getting the inventory of the block
                        Inventory inv = Utils.getBlockInventory(shopLocation.getBlock());
                        if (!Utils.containsAtLeast(inv, shop.getShopItem(), 1)) {
                            line = line.replace("<emptyShopInfo/>", LanguageManager.getInstance().emptyShopHologramInfo());
                        } else {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }
                if (line.trim().isEmpty())
                    continue;
                if (!line.equals("<empty/>")) {
                    ASHologram hologram = new ASHologram(player, line, lineLocation);
                    Utils.onlinePackets.add(hologram);
                    holoTextList.add(hologram);
                }
                lineLocation.add(0, 0.3 * Config.holo_linespacing, 0);
            }
        }

        List<Player> players = Objects.requireNonNullElseGet(playershopmap.get(shopLocation), ArrayList::new);
        players.add(player);
        playershopmap.put(shopLocation, players);

        EzChestShop.getScheduler().runTaskLater(() -> {
            for (ASHologram holo : holoTextList) {
                holo.destroy();
                Utils.onlinePackets.remove(holo);
            }
            for (FloatingItem item : holoItemList) {
                item.destroy();
                Utils.onlinePackets.remove(item);
            }
            List<Player> players1 = playershopmap.get(shopLocation);
            if (players1 == null || players1.isEmpty()) {
                playershopmap.remove(shopLocation);
            } else {
                players1.remove(player);
                if (players1.isEmpty()) {
                    playershopmap.remove(shopLocation);
                } else {
                    playershopmap.put(shopLocation, players1);
                }
            }
        }, 20L * Config.holodelay);
    }

    private boolean isAlreadyLooking(Player player, Block block) {
        return map.get(player) != null && block.getLocation().equals(map.get(player));
    }

    private boolean isAlreadyPresenting(Location location, Player player) {
        return playershopmap.containsKey(location) && playershopmap.get(location).contains(player);
    }

    private Location getHoloLoc(Block containerBlock) {
        Inventory inventory = Utils.getBlockInventory(containerBlock);
        PersistentDataContainer container = ((TileState) containerBlock.getState(false)).getPersistentDataContainer();
        String rotation = container.getOrDefault(Constants.ROTATION_KEY, PersistentDataType.STRING, Config.settings_defaults_rotation);
        rotation = Config.holo_rotation ? rotation : Config.settings_defaults_rotation;
        //Add rotation checks
        return switch (rotation) {
            case "north" -> getCentralLocation(containerBlock, inventory, new Vector(0, 0, -0.8));
            case "east"  -> getCentralLocation(containerBlock, inventory, new Vector(0.8, 0, 0));
            case "south" -> getCentralLocation(containerBlock, inventory, new Vector(0, 0, 0.8));
            case "west"  -> getCentralLocation(containerBlock, inventory, new Vector(-0.8, 0, 0));
            case "down"  -> getCentralLocation(containerBlock, inventory, new Vector(0, -1.5, 0));
            default      -> getCentralLocation(containerBlock, inventory, new Vector(0, 1, 0));
        };
    }

    private Location getCentralLocation(Block containerBlock, Inventory inventory, Vector direction) {
        Location holoLoc;
        if (inventory instanceof DoubleChestInventory doubleChestInventory) {
            DoubleChest doubleChest = (DoubleChest) Objects.requireNonNull(doubleChestInventory.getHolder(false), "Double Chest");
            Chest leftchest = (Chest) Objects.requireNonNull(doubleChest.getLeftSide(false), "Left Chest");
            Chest rightchest = (Chest) Objects.requireNonNull(doubleChest.getRightSide(false), "Right Chest");
            holoLoc = leftchest.getLocation().clone().add(0.5D, 0, 0.5D).add(rightchest.getLocation().add(0.5D, 0, 0.5D)).multiply(0.5);
            if (direction.getY() == 0) {
                Location lloc = leftchest.getLocation().clone().add(0.5D, 0, 0.5D);
                Location hloc = holoLoc.clone();
                double angle = (Math.atan2(hloc.getX() - lloc.getX(), hloc.getZ() - lloc.getZ()));
                angle = (-(angle / Math.PI) * 360.0d) / 2.0d + 180.0d;
                hloc = hloc.add(direction);
                double angle2 = (Math.atan2(hloc.getX() - lloc.getX(), hloc.getZ() - lloc.getZ()));
                angle2 = (-(angle2 / Math.PI) * 360.0d) / 2.0d + 180.0d;
                if (angle == angle2 || angle == angle2 - 180 || angle == angle2 + 180) {
                    holoLoc.add(direction.multiply(1.625));
                } else {
                    holoLoc.add(direction);
                }
            } else {
                holoLoc.add(direction);
            }
        } else {
            holoLoc = containerBlock.getLocation().clone().add(0.5D, 0, 0.5D).add(direction);
        }
        return holoLoc;
    }

}
