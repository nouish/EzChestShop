package me.deadlight.ezchestshop.listeners;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.EzChestShopConstants;
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
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class BlockBreakListener implements Listener {
    private static final LanguageManager lm = new LanguageManager();

    @EventHandler(priority = EventPriority.NORMAL)
    public void onBlockBreak(BlockBreakEvent event) {
        if (Utils.blockBreakMap.containsKey(event.getPlayer().getName())) {
            Collection<Entity> entityList = event.getBlock().getLocation().getWorld().getNearbyEntities(event.getBlock().getLocation(), 2, 2 ,2);

            for (Entity en : entityList) {
                if (en instanceof Item item) {
                    if (item.getItemStack().getType() == Material.CHEST || item.getItemStack().getType() == Material.TRAPPED_CHEST) {
                        en.remove();
                    }
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

            Location loc = event.getBlock().getLocation();
            boolean isPartOfShop = Utils.isPartOfTheChestShop(event.getBlock().getLocation()) != null;
            if (isPartOfShop) {
                loc = Utils.isPartOfTheChestShop(event.getBlock().getLocation()).getLocation();
            }
            if (ShopContainer.isShop(loc) || isPartOfShop) {
                if (Tag.SHULKER_BOXES.isTagged(event.getBlock().getType())) {
                    //first we check nobody is already in the shulker container (viewing it)
                    ShulkerBox shulkerBox = (ShulkerBox) event.getBlock().getState();
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
                        PersistentDataContainer bcontainer = ((TileState) event.getBlock().getState()).getPersistentDataContainer();
                        if (bcontainer.get(EzChestShopConstants.OWNER_KEY, PersistentDataType.STRING) != null) {
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
            List<String> nlore = lm.shulkerboxLore(Bukkit.getOfflinePlayer(UUID.fromString(getContainerString(container, EzChestShopConstants.OWNER_KEY))).getName(),
                    Utils.getFinalItemName(Utils.decodeItem(getContainerString(container, EzChestShopConstants.ITEM_KEY))),
                    getContainerDouble(container, EzChestShopConstants.BUY_PRICE_KEY),
                    getContainerDouble(container, EzChestShopConstants.SELL_PRICE_KEY));
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
        Location loc = block.getLocation();
        EzShop partOfTheChestShop = Utils.isPartOfTheChestShop(loc);

        if (partOfTheChestShop != null) {
            loc = partOfTheChestShop.getLocation();
        }

        if (ShopContainer.isShop(loc) || partOfTheChestShop != null) {
            boolean adminshop = ShopContainer.getShop(loc).getSettings().isAdminshop();
            if (EzChestShop.worldguard) {
                if (adminshop) {
                    if (!WorldGuardUtils.queryStateFlag(FlagRegistry.REMOVE_ADMIN_SHOP, player)) {
                        player.spigot().sendMessage(lm.notAllowedToCreateOrRemove(player));
                        event.setCancelled(true);
                    }
                } else {
                    if (!WorldGuardUtils.queryStateFlag(FlagRegistry.REMOVE_SHOP, player)) {
                        player.spigot().sendMessage(lm.notAllowedToCreateOrRemove(player));
                        event.setCancelled(true);
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
                    }
                }
            }
        }
    }

}
