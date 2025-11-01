package me.deadlight.ezchestshop.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.Constants;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.ShopCommandManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.guis.ServerShopGUI;
import me.deadlight.ezchestshop.guis.ShopGUI;
import me.deadlight.ezchestshop.integrations.CoreProtectIntegration;
import me.deadlight.ezchestshop.utils.BlockOutline;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.worldguard.FlagRegistry;
import me.deadlight.ezchestshop.utils.worldguard.WorldGuardUtils;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public class ChestOpeningListener implements Listener {
    private static final Logger LOGGER = EzChestShop.logger();

    // We don't ignore cancelled events because we may have to ignore
    // permission plugins that register on LOWEST priority anyway.
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(@NotNull PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        final Block clickedBlock = event.getClickedBlock();
        if (clickedBlock == null) {
            return;
        }

        final Material clickedType = clickedBlock.getType();
        if (!Utils.isApplicableContainer(clickedType)) {
            return;
        }

        Block chestblock = clickedBlock;
        if (EzChestShop.slimefun) {
            if (BlockStorage.hasBlockInfo(chestblock.getLocation())) {
                ShopContainer.deleteShop(chestblock.getLocation());
                return;
            }
        }

        PersistentDataContainer dataContainer = null;
        Location loc = chestblock.getLocation();
        TileState state = (TileState) chestblock.getState(false);
        Inventory inventory = Utils.getBlockInventory(chestblock);

        if (Constants.TAG_CHEST.contains(clickedType)) {
            if (inventory instanceof DoubleChestInventory) {
                DoubleChest doubleChest = (DoubleChest) inventory.getHolder(false);
                Chest chestleft = (Chest) doubleChest.getLeftSide(false);
                Chest chestright = (Chest) doubleChest.getRightSide(false);

                if (!chestleft.getPersistentDataContainer().isEmpty()) {
                    dataContainer = chestleft.getPersistentDataContainer();
                    chestblock = chestleft.getBlock();
                } else {
                    dataContainer = chestright.getPersistentDataContainer();
                    chestblock = chestright.getBlock();
                }
                loc = chestblock.getLocation();
            } else {
                dataContainer = state.getPersistentDataContainer();
            }
        } else if (clickedType == Material.BARREL) {
            dataContainer = state.getPersistentDataContainer();
        } else if (Tag.SHULKER_BOXES.isTagged(clickedType)) {
            dataContainer = state.getPersistentDataContainer();
        }

        if (dataContainer != null && dataContainer.has(Constants.OWNER_KEY, PersistentDataType.STRING)) {
            // If CoreProtect integration is enabled and the player is
            // in inspect mode, we don't want to open the shop UI.
            if (CoreProtectIntegration.isInspectEnabledFor(event.getPlayer())) {
                LOGGER.trace("{} inspected chest shop at {}, {}, {} (CoreProtect integration).",
                        event.getPlayer().getName(),
                        loc.getBlockX(),
                        loc.getBlockY(),
                        loc.getBlockZ());
                return;
            }

            event.setCancelled(true);
            // Load old shops into the Database when clicked
            if (!ShopContainer.isShop(loc)) {
                ShopContainer.loadShop(loc, dataContainer);
            }

            List<BlockOutline> playerOutlinedShops = new ArrayList<>(Utils.activeOutlines.values());
            for (BlockOutline outline : playerOutlinedShops) {
                if (outline == null) continue;
                if (outline.player.getUniqueId().equals(event.getPlayer().getUniqueId())) {
                    if (outline.block.getLocation().equals(loc)) {
                        outline.hideOutline();
                    }
                }
            }

            String owneruuid = dataContainer.get(Constants.OWNER_KEY, PersistentDataType.STRING);
            boolean isAdminShop = dataContainer.getOrDefault(Constants.ENABLE_ADMINSHOP_KEY, PersistentDataType.INTEGER, 0) == 1;
            Player player = event.getPlayer();

            if (isAdminShop) {
                if (EzChestShop.worldguard) {
                    if (!WorldGuardUtils.queryStateFlag(FlagRegistry.USE_ADMIN_SHOP, player) && !player.isOp()) {
                        return;
                    }
                }
                Config.shopCommandManager.executeCommands(player, loc, ShopCommandManager.ShopType.ADMINSHOP, ShopCommandManager.ShopAction.OPEN, null);
                ServerShopGUI serverShopGUI = new ServerShopGUI();
                serverShopGUI.showGUI(player, dataContainer, chestblock);
                return;
            }

            boolean isAdmin = isAdmin(dataContainer, player.getUniqueId().toString());

            if (EzChestShop.worldguard) {
                if (!WorldGuardUtils.queryStateFlag(FlagRegistry.USE_SHOP, player) && !player.isOp() && !(isAdmin || player.getUniqueId().toString().equalsIgnoreCase(owneruuid))) {
                    return;
                }
            }
            // At this point it is clear that some shop will open, so run opening commands here.
            Config.shopCommandManager.executeCommands(player, loc, ShopCommandManager.ShopType.SHOP, ShopCommandManager.ShopAction.OPEN, null);
            ShopGUI.showGUI(player, dataContainer, chestblock, isAdmin);
        }
    }

    private boolean isAdmin(PersistentDataContainer data, String uuid) {
        UUID owneruuid = UUID.fromString(uuid);
        List<UUID> adminsUUID = Utils.getAdminsList(data);
        return adminsUUID.contains(owneruuid);
    }
}
