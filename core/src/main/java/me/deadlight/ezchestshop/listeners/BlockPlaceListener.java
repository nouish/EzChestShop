package me.deadlight.ezchestshop.listeners;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.EzChestShopConstants;
import me.deadlight.ezchestshop.data.ShopContainer;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Dispenser;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@Internal
public final class BlockPlaceListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(@NotNull BlockPlaceEvent event) {
        Block block = event.getBlock();
        ItemStack item = event.getItemInHand();
        placeBlock(block, item);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockDispense(@NotNull BlockDispenseEvent event) {
        Block block = event.getBlock();
        if (block.getState(false) instanceof Dispenser dispenser) {
            final Directional directional = (Directional) dispenser.getBlockData();
            EzChestShop.getScheduler().runTaskLater(block.getLocation(), () -> {
                Block relativeBlock = block.getRelative(directional.getFacing());
                placeBlock(relativeBlock, event.getItem());
            }, 5);
        }
    }

    private void placeBlock(@NotNull Block block, @NotNull ItemStack item) {
        if (!Tag.SHULKER_BOXES.isTagged(item.getType()) || !Tag.SHULKER_BOXES.isTagged(block.getType())) {
            return;
        }

        if (!item.hasItemMeta()) {
            return;
        }

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer container = meta.getPersistentDataContainer();
        if (container.get(EzChestShopConstants.OWNER_KEY, PersistentDataType.STRING) != null) {
            TileState state = ((TileState) block.getState(false));
            PersistentDataContainer bcontainer = ShopContainer.copyContainerData(container, state.getPersistentDataContainer());
            state.update();
            ShopContainer.loadShop(block.getLocation(), bcontainer);
        }
    }
}
