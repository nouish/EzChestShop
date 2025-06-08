package me.deadlight.ezchestshop.tasks;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.jetbrains.annotations.ApiStatus;
import org.slf4j.Logger;

@ApiStatus.Internal
public final class LoadedChunksTask {
    private static final Logger LOGGER = EzChestShop.logger();

    private LoadedChunksTask() {
    }

    public static void startTask() {
        EzChestShop.getScheduler().runTaskTimer(LoadedChunksTask::tick, 250L, 500L);
    }

    private static void tick() {
        //fix credited to Huke
        for (EzShop shop : ShopContainer.getShops()) {
            Location location = shop.getLocation();
            World world = location.getWorld();
            int x = location.getBlockX() >> 4;
            int y = location.getBlockZ() >> 4;

            if (world == null || !world.isChunkLoaded(x, y)) {
                continue;
            }

            Block block = location.getBlock();
            if (block.isEmpty() || !Utils.isApplicableContainer(block)) {
                ShopContainer.deleteShop(location);
                LOGGER.info("Found and removed shop from desynchronized block ({}) at {}, {}, {}.",
                    block.getType().key(),
                    location.getBlockX(),
                    location.getBlockY(),
                    location.getBlockZ());
            }
        }
    }
}
