package me.deadlight.ezchestshop.listeners;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.Constants;
import me.deadlight.ezchestshop.data.ShopContainer;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Dispenser;
import org.bukkit.block.TileState;
import org.bukkit.block.data.Directional;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class BlockPlaceListener implements Listener {

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        Block block = event.getBlock();
        ItemStack item = event.getItemInHand();
        placeBlock(block, item);
    }

    @EventHandler
    public void onBlockDispenserPlace(BlockDispenseEvent event) {
        Block block = event.getBlock();
        if (block.getState(false) instanceof Dispenser dispenser) {
            final Directional directional = (Directional) dispenser.getBlockData();
            EzChestShop.getScheduler().runTaskLater(block.getLocation(), () -> {
                Block relativeBlock = block.getRelative(directional.getFacing());
                placeBlock(relativeBlock, event.getItem());
            }, 5);
        }
    }

    private void placeBlock(Block block, ItemStack shulker) {
        if (Tag.SHULKER_BOXES.isTagged(shulker.getType()) && Tag.SHULKER_BOXES.isTagged(block.getType())) {
            if (shulker.hasItemMeta()) {
                ItemMeta meta = shulker.getItemMeta();
                PersistentDataContainer container = meta.getPersistentDataContainer();
                if (container.get(Constants.OWNER_KEY, PersistentDataType.STRING) != null) {
                    TileState state = ((TileState) block.getState(false));
                    PersistentDataContainer bcontainer = ShopContainer.copyContainerData(container, state.getPersistentDataContainer());
                    state.update();
                    ShopContainer.loadShop(block.getLocation(), bcontainer);
                }
            }
        }
    }
}
