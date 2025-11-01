package me.deadlight.ezchestshop.listeners;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import io.papermc.paper.block.TileStateInventoryHolder;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.Constants;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.holograms.ShopHologram;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.deadlight.ezchestshop.utils.worldguard.FlagRegistry;
import me.deadlight.ezchestshop.utils.worldguard.WorldGuardUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public class BlockBreakListener implements Listener {
    private static final LanguageManager lm = LanguageManager.getInstance();

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        if (Utils.blockBreakMap.containsKey(event.getPlayer().getName())) {
            Collection<Entity> entityList = event.getBlock().getLocation().getWorld().getNearbyEntities(event.getBlock().getLocation(), 2, 2 ,2);

            for (Entity entity : entityList) {
                if (entity instanceof Item item && Constants.TAG_CHEST.contains(item.getItemStack().getType())) {
                    entity.remove();
                }
            }

            if (event.isCancelled()) {
                Utils.blockBreakMap.remove(event.getPlayer().getName());
            } else if (Utils.blockBreakMap.containsKey(event.getPlayer().getName())) {
                event.setCancelled(true);
            }
        }

        if (!event.isCancelled()) {
            preventShopBreak(event);
            if (event.isCancelled()) {
                return;
            }

            EzShop shop = Utils.isPartOfTheChestShop(event.getBlock());
            Location loc = shop != null ? shop.getLocation() : event.getBlock().getLocation();

            if (ShopContainer.isShop(loc) || shop != null) {
                if (Tag.SHULKER_BOXES.isTagged(event.getBlock().getType())) {
                    //first we check nobody is already in the shulker container (viewing it)
                    ShulkerBox shulkerBox = (ShulkerBox) event.getBlock().getState(false);
                    int viewerCount = shulkerBox.getInventory().getViewers().size();
                    if (viewerCount > 0) {
                        event.setCancelled(true);
                        event.getPlayer().sendMessage(lm.noBreakingWhileShopOpen());
                        return;
                    }

                    if (event.isDropItems()) {
                        event.setDropItems(false);
                        ItemStack shulker = event.getBlock().getDrops().stream().findFirst().get();
                        ItemMeta meta = shulker.getItemMeta();
                        PersistentDataContainer container = meta.getPersistentDataContainer();
                        PersistentDataContainer bcontainer = ((TileState) event.getBlock().getState(false)).getPersistentDataContainer();
                        if (bcontainer.get(Constants.OWNER_KEY, PersistentDataType.STRING) != null) {
                            container = ShopContainer.copyContainerData(bcontainer, container);
                            meta = addLore(meta, container);
                            shulker.setItemMeta(meta);
                            loc.getWorld().dropItemNaturally(loc, shulker);
                            if (Config.holodistancing) {
                                ShopHologram.hideForAll(event.getBlock().getLocation());
                            }
                        }
                    }
                }

                ShopContainer.deleteShop(loc);
            }
        }
    }

    private ItemMeta addLore(ItemMeta meta, PersistentDataContainer container) {
        if (Config.settings_add_shulkershop_lore) {
            List<String> nlore = lm.shulkerboxLore(Bukkit.getOfflinePlayer(UUID.fromString(getContainerString(container, Constants.OWNER_KEY))).getName(),
                    Utils.getFinalItemName(Utils.decodeItem(getContainerString(container, Constants.ITEM_KEY))),
                    getContainerDouble(container, Constants.BUY_PRICE_KEY),
                    getContainerDouble(container, Constants.SELL_PRICE_KEY));
            meta.setLore(nlore);
        }
        return meta;
    }

    private String getContainerString(PersistentDataContainer container, NamespacedKey key) {
        return container.get(key, PersistentDataType.STRING);
    }

    private Double getContainerDouble(PersistentDataContainer container, NamespacedKey key) {
        return container.get(key, PersistentDataType.DOUBLE);
    }

    private void preventShopBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        EzShop partOfTheChestShop = Utils.isPartOfTheChestShop(block);
        Location loc = partOfTheChestShop != null ? partOfTheChestShop.getLocation() : block.getLocation();

        if (ShopContainer.isShop(loc) || partOfTheChestShop != null) {
            boolean adminshop = ShopContainer.getShop(loc).getSettings().isAdminshop();
            if (EzChestShop.worldguard) {
                if (adminshop) {
                    if (!WorldGuardUtils.queryStateFlag(FlagRegistry.REMOVE_ADMIN_SHOP, player)) {
                        player.spigot().sendMessage(lm.notAllowedToCreateOrRemove(player));
                        event.setCancelled(true);
                        return;
                    }
                } else {
                    if (!WorldGuardUtils.queryStateFlag(FlagRegistry.REMOVE_SHOP, player)) {
                        player.spigot().sendMessage(lm.notAllowedToCreateOrRemove(player));
                        event.setCancelled(true);
                        return;
                    }
                }
            }

            //shop protection section
            if (Config.shopProtection) {
                if (!player.hasPermission("ecs.admin")) {
                    //check if player is owner of shop
                    EzShop shop = ShopContainer.getShop(loc);
                    if (!shop.getOwnerID().equals(player.getUniqueId())) {
                        event.setCancelled(true);
                        player.sendMessage(lm.cannotDestroyShop());
                        return;
                    }
                }
            }

            if (isShopBusy(event.getBlock())) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(lm.noBreakingWhileShopOpen());
                return;
            }
        }
    }

    /**
     * Check if there is anybody currently viewing the stock.
     */
    private boolean isShopBusy(@NotNull Block block) {
        // We check for active viewers first (actual chest stock, not shop GUI).
        if (block.getState(false) instanceof TileStateInventoryHolder inventoryHolder) {
            Inventory inventory = inventoryHolder.getInventory();
            if (inventory.getHolder(false) instanceof DoubleChest doubleChest
                    && doubleChest.getLeftSide(false) instanceof Chest leftSide
                    && doubleChest.getRightSide(false) instanceof Chest rightSide) {
                if (!leftSide.getInventory().getViewers().isEmpty()) {
                    return true;
                } else if (!rightSide.getInventory().getViewers().isEmpty()) {
                    return true;
                }
            } else if (!inventory.getViewers().isEmpty()) {
                return true;
            }
        }

        // We then also check everyone's active inventories.
        // This is because there is always the chance somebody turned a single chest
        // into a double chest, leaving some other player with an "outdated" single inventory.
        Location location = block.getLocation();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getOpenInventory().getTopInventory().getHolder(false) instanceof Chest chest) {
                if (chest.getInventory().getHolder(false) instanceof DoubleChest doubleChest
                        && doubleChest.getLeftSide(false) instanceof Chest leftSide
                        && doubleChest.getRightSide(false) instanceof Chest rightSide) {
                    if (location.equals(leftSide.getLocation())) {
                        return true;
                    } else if (location.equals(rightSide.getLocation())) {
                        return true;
                    }
                } else if (location.equals(chest.getLocation())) {
                    return true;
                }
            }
        }

        return false;
    }
}
