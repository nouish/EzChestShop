package me.deadlight.ezchestshop.listeners;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.Constants;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.events.ShulkerShopDropEvent;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.holograms.ShopHologram;
import me.deadlight.ezchestshop.utils.worldguard.FlagRegistry;
import me.deadlight.ezchestshop.utils.worldguard.WorldGuardUtils;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.ShulkerBox;
import org.bukkit.block.TileState;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class BlockPistonExtendListener implements Listener {
    private static final HashMap<String, String> lockMap = new HashMap<>();
    private static final List<String> lockList = new ArrayList<>();
    private static final HashMap<String, PersistentDataContainer> lockContainerMap = new HashMap<>();
    private static final HashMap<String, Location> lockLocationMap = new HashMap<>();

    @EventHandler
    public void onExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            if (Tag.SHULKER_BOXES.isTagged(block.getType())) {
                BlockState blockState = block.getState(false);
                TileState state = (TileState) blockState;
                PersistentDataContainer container = state.getPersistentDataContainer();

                //first we check nobody is already in the shulker container (viewing it)
                ShulkerBox shulkerBox = (ShulkerBox) block.getState(false);
                int viewerCount = shulkerBox.getInventory().getViewers().size();
                if (viewerCount > 0) {
                    event.setCancelled(true);
                    return;
                }

                if (Tag.SHULKER_BOXES.isTagged(block.getType())) {
                    //it is a shulkerbox, now checking if its a shop
                    Location shulkerLoc = block.getLocation();
                    if (ShopContainer.isShop(shulkerLoc)) {
                        boolean adminshop = container.getOrDefault(Constants.ENABLE_ADMINSHOP_KEY, PersistentDataType.INTEGER, 0) == 1;
                        if (EzChestShop.worldguard) {
                            if (adminshop) {
                                if (!WorldGuardUtils.queryStateFlag(FlagRegistry.REMOVE_ADMIN_SHOP, shulkerLoc)) {
                                    event.setCancelled(true);
                                    return;
                                }
                            } else {
                                if (!WorldGuardUtils.queryStateFlag(FlagRegistry.REMOVE_SHOP, shulkerLoc)) {
                                    event.setCancelled(true);
                                    return;
                                }
                            }
                        }
                        //congrats
                        //Add the Lock for dropped item recognition later
                        UUID uuid = UUID.randomUUID();
                        lockMap.put(uuid.toString(), ((ShulkerBox) state).getLock());
                        lockList.add(uuid.toString());
                        lockContainerMap.put(uuid.toString(), container);
                        lockLocationMap.put(uuid.toString(), shulkerLoc);
                        ((ShulkerBox) state).setLock(uuid.toString());
                        state.update();

                        ShopContainer.deleteShop(shulkerLoc);
                        if (Config.holodistancing) {
                            ShopHologram.hideForAll(event.getBlock().getLocation());
                        }
                        EzChestShop.getScheduler().runTaskLater(() -> {
                            Collection<Entity> entitiyList = block.getWorld().getNearbyEntities(shulkerLoc, 2, 2, 2);
                            entitiyList.forEach(entity -> {
                                if (entity instanceof Item item) {
                                    ItemStack itemStack = item.getItemStack();
                                    if (Tag.SHULKER_BOXES.isTagged(itemStack.getType())) {
                                        //get the lock
                                        BlockStateMeta bsm = (BlockStateMeta) itemStack.getItemMeta();
                                        ShulkerBox box = (ShulkerBox) bsm.getBlockState();
                                        String lock = box.getLock();
                                        //good, now validate that its the same shulker box that it was before
                                        if (lockList.contains(lock)) {
                                            //it is surely that shulker
                                            //reset the lock
                                            box.setLock(lockMap.get(lock));
                                            box.update();
                                            bsm.setBlockState(box);
                                            itemStack.setItemMeta(bsm);
                                            lockList.remove(lock);
                                            lockMap.remove(lock);
                                            lockContainerMap.remove(lock);
                                            lockLocationMap.remove(lock);

                                            //copy the new data over
                                            ItemMeta meta = itemStack.getItemMeta();
                                            PersistentDataContainer metaContainer = meta.getPersistentDataContainer();
                                            metaContainer = ShopContainer.copyContainerData(container, metaContainer);
                                            meta = addLore(meta, metaContainer);
                                            itemStack.setItemMeta(meta);

                                            //Call the Event
                                            item.setItemStack(itemStack);
                                            ShulkerShopDropEvent shopDropEvent = new ShulkerShopDropEvent(item, shulkerLoc);
                                            //idk if item also needs update after removing a persistent value (Have to check later) ^^^^
                                            Bukkit.getPluginManager().callEvent(shopDropEvent);
                                        }
                                    }
                                }
                            });
                        }, 5);
                    }
                }
            }
        }
    }

    @EventHandler
    public void InventoryItemPickup(InventoryPickupItemEvent event) {
        Item item = event.getItem();
        ItemStack itemStack = item.getItemStack();
        if (Tag.SHULKER_BOXES.isTagged(itemStack.getType())) {
            //get the lock
            BlockStateMeta bsm = (BlockStateMeta) itemStack.getItemMeta();
            ShulkerBox box = (ShulkerBox) bsm.getBlockState();
            String lock = box.getLock();
            //good, now validate that its the same shulker box that it was before
            if (lock != null && lockList.contains(lock)) {
                //it is surely that shulker
                PersistentDataContainer container = lockContainerMap.get(lock);
                Location shulkerLoc = lockLocationMap.get(lock);
                //reset the lock
                box.setLock(lockMap.get(lock));
                box.update();
                bsm.setBlockState(box);
                itemStack.setItemMeta(bsm);
                lockList.remove(lock);
                lockMap.remove(lock);
                lockContainerMap.remove(lock);
                lockLocationMap.remove(lock);

                //copy the new data over
                ItemMeta meta = itemStack.getItemMeta();
                PersistentDataContainer metaContainer = meta.getPersistentDataContainer();
                metaContainer = ShopContainer.copyContainerData(container, metaContainer);
                meta = addLore(meta, metaContainer);
                itemStack.setItemMeta(meta);

                //Call the Event
                item.setItemStack(itemStack);
                ShulkerShopDropEvent shopDropEvent = new ShulkerShopDropEvent(item, shulkerLoc);
                //idk if item also needs update after removing a persistent value (Have to check later) ^^^^
                Bukkit.getPluginManager().callEvent(shopDropEvent);
            }
        }
    }


    private ItemMeta addLore(ItemMeta meta, PersistentDataContainer container) {
        if (Config.settings_add_shulkershop_lore) {
            List<String> nlore = LanguageManager.getInstance().shulkerboxLore(Bukkit.getOfflinePlayer(UUID.fromString(getContainerString(container, Constants.OWNER_KEY))).getName(),
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
}
