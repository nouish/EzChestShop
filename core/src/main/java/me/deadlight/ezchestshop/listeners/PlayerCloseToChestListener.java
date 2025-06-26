package me.deadlight.ezchestshop.listeners;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.google.common.base.Preconditions;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.events.PlayerTransactEvent;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.holograms.BlockBoundHologram;
import me.deadlight.ezchestshop.utils.holograms.ShopHologram;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerToggleSneakEvent;
import org.bukkit.util.RayTraceResult;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

@Internal
public final class PlayerCloseToChestListener implements Listener {
    private final Map<UUID, ShopHologram> inspectedShops = new HashMap<>();

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerMove(@NotNull PlayerMoveEvent event) {
        if (!Config.showholo) {
            return;
        }

        boolean alreadyRenderedHologram = false;
        Player player = event.getPlayer();

        if (Config.holodistancing_show_item_first) {
            RayTraceResult result = player.rayTraceBlocks(5);
            boolean isLookingAtSameShop = false;
            // Make sure the player is looking at a shop
            if (result != null) {
                Block target = result.getHitBlock();
                if (target != null && Utils.isApplicableContainer(target)) {
                    Location loc = BlockBoundHologram.getShopChestLocation(target);
                    if (ShopContainer.isShop(loc)) {
                        // Create a shop Hologram, so it can be used later
                        // required to be called here, cause the inspection needs it already.
                        ShopHologram shopHolo = ShopHologram.getHologram(loc, player);

                        // if the player is looking directly at a shop, he is inspecting it.
                        // If he has been inspecting a shop before, then we need to check if he is looking at the same shop
                        // or a different one.
                        if (ShopHologram.isPlayerInspectingShop(player)) {
                            if (ShopHologram.getInspectedShopHologram(player).getLocation().equals(loc)) {
                                // if the player is looking at the same shop, then don't do anything
                                isLookingAtSameShop = true;
                            } else {
                                // if the player is looking at a different shop, then remove the old one
                                // and only show the item
                                ShopHologram inspectedShopHolo = ShopHologram.getInspectedShopHologram(player);
                                inspectedShopHolo.showOnlyItem();
                                inspectedShopHolo.showAlwaysVisibleText();
                                inspectedShopHolo.removeInspectedShop();
                            }
                        }
                        // if the player is looking at a shop, and he is not inspecting it yet, then start inspecting it!
                        if (ShopHologram.hasHologram(loc, player) && !shopHolo.hasInspector()) {
                            shopHolo.showTextAfterItem();
                            shopHolo.setItemDataVisible(player.isSneaking());
                            shopHolo.setAsInspectedShop();
                            alreadyRenderedHologram = true;
                            isLookingAtSameShop = true;
                        }
                    }
                }
            }
            // if the player is not looking at a shop, then remove the old one if he was inspecting one
            if (ShopHologram.isPlayerInspectingShop(player) && !isLookingAtSameShop) {
                ShopHologram shopHolo = ShopHologram.getInspectedShopHologram(player);
                if (ShopContainer.isShop(shopHolo.getLocation())) {
                    shopHolo.showOnlyItem();
                    shopHolo.showAlwaysVisibleText();
                }
                shopHolo.removeInspectedShop();
            }
        }

        if (alreadyRenderedHologram || !hasMovedLocation(event)) {
            return;
        }

        Location loc = player.getLocation();
        List<EzShop> shops = ShopContainer.getShops().stream()
                .filter(ezShop -> ezShop.getLocation() != null
                        && loc.getWorld().equals(ezShop.getLocation().getWorld())
                        && loc.distance(ezShop.getLocation()) < Config.holodistancing_distance + 5)
                .toList();
        for (EzShop ezShop : shops) {
            if (EzChestShop.slimefun) {
                if (BlockStorage.hasBlockInfo(ezShop.getLocation())) {
                    ShopContainer.deleteShop(ezShop.getLocation());
                    continue;
                }
            }
            double dist = loc.distance(ezShop.getLocation());
            // Show the Hologram if Player close enough
            if (dist < Config.holodistancing_distance) {
                if (ShopHologram.hasHologram(ezShop.getLocation(), player))
                    continue;

                Block target = ezShop.getLocation().getWorld().getBlockAt(ezShop.getLocation());
                if (!Utils.isApplicableContainer(target)) {
                    return;
                }
                ShopHologram shopHolo = ShopHologram.getHologram(ezShop.getLocation(), player);
                if (Config.holodistancing_show_item_first) {
                    shopHolo.showOnlyItem();
                    shopHolo.showAlwaysVisibleText();
                } else {
                    shopHolo.show();
                }

            }
            // Hide the Hologram that is too far away from the player
            else if (dist > Config.holodistancing_distance + 1 && dist < Config.holodistancing_distance + 3) {
                // Hide the Hologram
                ShopHologram hologram = ShopHologram.getHologram(ezShop.getLocation(), player);
                if (hologram != null) {
                    hologram.hide();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        ShopHologram.hideAll(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerTeleport(@NotNull PlayerTeleportEvent event) {
        ShopHologram.hideAll(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerToggleSneak(@NotNull PlayerToggleSneakEvent event) {
        Player player = event.getPlayer();
        if (ShopHologram.isPlayerInspectingShop(player)) {
            ShopHologram shopHolo = ShopHologram.getInspectedShopHologram(player);
            shopHolo.setItemDataVisible(event.isSneaking());
        } else if (!Config.holodistancing_show_item_first) {
            // When holodistancing_show_item_first is off, the shop needs to be queried separately.
            // It's less reactive but it works.
            if (!event.isSneaking() && inspectedShops.containsKey(player.getUniqueId())) {
                ShopHologram hologram = inspectedShops.get(player.getUniqueId());
                if (hologram != null) {
                    hologram.setItemDataVisible(false);
                    inspectedShops.remove(player.getUniqueId());
                    return;
                }
            }
            RayTraceResult result = player.rayTraceBlocks(5);
            if (result == null)
                return;
            Block block = result.getHitBlock();
            if (block == null)
                return;
            Location loc = block.getLocation();
            if (ShopContainer.isShop(loc)) {
                ShopHologram hologram = ShopHologram.getHologram(loc, player);
                if (event.isSneaking()) {
                    hologram.setItemDataVisible(true);
                    inspectedShops.put(player.getUniqueId(), hologram);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryMoveItem(@NotNull InventoryMoveItemEvent event) {
        if (ShopContainer.isShop(event.getDestination().getLocation())) {
            EzChestShop.getScheduler().runTaskLater(() -> ShopHologram.updateInventoryReplacements(event.getDestination().getLocation()), 1);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onEntityPickupItem(@NotNull EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player) {
            ShopHologram.getViewedHolograms(player).forEach(shopHolo ->
                    EzChestShop.getScheduler().runTaskLater(() -> ShopHologram.updateInventoryReplacements(shopHolo.getLocation()), 1));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShopCapacityChangeByBlockPlace(@NotNull BlockPlaceEvent event) {
        Block block = event.getBlockPlaced();
        if (block.getType() != Material.CHEST && block.getType() != Material.TRAPPED_CHEST) {
            return;
        }

        EzChestShop.getScheduler().runTask(block.getLocation(), () -> {
            Location location = BlockBoundHologram.getShopChestLocation(block);
            if (ShopContainer.isShop(location)) {
                ShopHologram.updateInventoryReplacements(location);
            }
        });
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onShopTransactionCapacityChange(@NotNull PlayerTransactEvent event) {
        Location location = event.getContainerBlock().getLocation();
        EzChestShop.getScheduler().runTask(location, () -> ShopHologram.updateInventoryReplacements(location));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(@NotNull InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            handleInventoryInteraction(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(@NotNull InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            handleInventoryInteraction(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDropItem(@NotNull PlayerDropItemEvent event) {
        handleInventoryInteraction(event.getPlayer());
    }

    private void handleInventoryInteraction(@NotNull Player player) {
        List<ShopHologram> viewed = ShopHologram.getViewedHolograms(player);
        for (ShopHologram hologram : viewed) {
            EzChestShop.getScheduler().runTaskLater(
                hologram.getLocation(),
                () -> ShopHologram.updateInventoryReplacements(hologram.getLocation()),
                1
            );
        }
    }

    private boolean hasMovedLocation(@NotNull PlayerMoveEvent event) {
        Location from = Preconditions.checkNotNull(event.getFrom(), "from");
        Location to = Preconditions.checkNotNull(event.getTo(), "to");
        return (Math.abs(from.getX() - to.getX()) >= 0.001)
                || (Math.abs(from.getY() - to.getY()) >= 0.001)
                || (Math.abs(from.getZ() - to.getZ()) >= 0.001);
    }
}
