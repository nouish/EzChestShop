package me.deadlight.ezchestshop.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.google.common.base.Preconditions;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.EzChestShopConstants;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopCommandManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.guis.AdminShopGUI;
import me.deadlight.ezchestshop.guis.NonOwnerShopGUI;
import me.deadlight.ezchestshop.guis.OwnerShopGUI;
import me.deadlight.ezchestshop.guis.ServerShopGUI;
import me.deadlight.ezchestshop.utils.BlockOutline;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.worldguard.FlagRegistry;
import me.deadlight.ezchestshop.utils.worldguard.WorldGuardUtils;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
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

public class ChestOpeningListener implements Listener {

    private final NonOwnerShopGUI nonOwnerShopGUI= new NonOwnerShopGUI();
    private final OwnerShopGUI ownerShopGUI = new OwnerShopGUI();
    private final AdminShopGUI adminShopGUI = new AdminShopGUI();
    LanguageManager lm = new LanguageManager();
    @EventHandler(priority = EventPriority.MONITOR)
    public void onChestOpening(PlayerInteractEvent event) {
        if (event.getClickedBlock() == null || event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Material clickedType = event.getClickedBlock().getType();

        if (Utils.isApplicableContainer(clickedType)) {
            Block chestblock = event.getClickedBlock();
            if (EzChestShop.slimefun) {
                if (BlockStorage.hasBlockInfo(chestblock.getLocation())) {
                    ShopContainer.deleteShop(chestblock.getLocation());
                    return;
                }
            }
            PersistentDataContainer dataContainer = null;
            Location loc = chestblock.getLocation();
            TileState state = (TileState) chestblock.getState();
            Inventory inventory = Utils.getBlockInventory(chestblock);

            if (clickedType == Material.CHEST || clickedType == Material.TRAPPED_CHEST) {
                if (inventory instanceof DoubleChestInventory) {
                    DoubleChest doubleChest = (DoubleChest) inventory.getHolder();
                    Chest chestleft = (Chest) doubleChest.getLeftSide();
                    Chest chestright = (Chest) doubleChest.getRightSide();

                    if (!chestleft.getPersistentDataContainer().isEmpty()) {
                        dataContainer = chestleft.getPersistentDataContainer();
                        chestblock = chestleft.getBlock();
                    } else {
                        dataContainer = chestright.getPersistentDataContainer();
                        chestblock = chestright.getBlock();
                    }
                    loc  = chestblock.getLocation();
                } else {
                    dataContainer = state.getPersistentDataContainer();
                }
            } else if (clickedType == Material.BARREL) {
                dataContainer = state.getPersistentDataContainer();
            } else if (Tag.SHULKER_BOXES.isTagged(clickedType)) {
                dataContainer = state.getPersistentDataContainer();
            }

            if (dataContainer != null && dataContainer.has(EzChestShopConstants.OWNER_KEY, PersistentDataType.STRING)) {
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

                String owneruuid = dataContainer.get(EzChestShopConstants.OWNER_KEY, PersistentDataType.STRING);
                boolean isAdminShop = dataContainer.getOrDefault(EzChestShopConstants.ENABLE_ADMINSHOP_KEY, PersistentDataType.INTEGER, 0) == 1;
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
                if (player.hasPermission("ecs.admin") || player.hasPermission("ecs.admin.view")) {
                    adminShopGUI.showGUI(player, dataContainer, chestblock);
                    return;
                }

                if (player.getUniqueId().toString().equalsIgnoreCase(owneruuid) || isAdmin) {
                    ownerShopGUI.showGUI(player, dataContainer, chestblock, isAdmin);
                } else {
                    //not owner show default
                    if (player.getUniqueId().toString().equalsIgnoreCase(owneruuid) || isAdmin) {
                        ownerShopGUI.showGUI(player, dataContainer, chestblock, isAdmin);
                    } else {System.out.println("isAdminShop: " + isAdminShop);
// If it is an admin shop, we do not perform the permission limit calculations
// Check if the permission limitation functionality is enabled
                        if (Config.permissions_create_shop_enabled) {
                            int maxShopsWorld = Utils.getMaxPermission(Objects.requireNonNull(player),
                                    "ecs.shops.limit." + chestblock.getWorld().getName() + ".", -2);
                            int maxShops;

                            if (maxShopsWorld == -2) {
                                maxShops = Utils.getMaxPermission(Objects.requireNonNull(player), "ecs.shops.limit.", 0);
                            } else {
                                maxShops = maxShopsWorld;
                            }

                            maxShops = maxShops == -1 ? 10000 : maxShops; // If the player has unlimited permissions, set a high value.
                            int shops = ShopContainer.getShopCount(player); // Current number of shops owned by the player.
                            // If the player has exceeded the limit
                            if (shops > maxShops) {
                                Player customer = event.getPlayer();
                                String rawId = dataContainer.get(EzChestShopConstants.OWNER_KEY, PersistentDataType.STRING);
                                Preconditions.checkNotNull(rawId);
                                OfflinePlayer offlinePlayerOwner = Bukkit.getOfflinePlayer(UUID.fromString(rawId));
                                customer.sendMessage(lm.shopOwnerExceedsPermission(offlinePlayerOwner.getName()));
                                customer.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, 0.5f);
                                return;
                            }
                        }
                        nonOwnerShopGUI.showGUI(player, dataContainer, chestblock);
                    }
                }
            }
        }
    }

    private boolean isAdmin(PersistentDataContainer data, String uuid) {
        UUID owneruuid = UUID.fromString(uuid);
        List<UUID> adminsUUID = Utils.getAdminsList(data);
        return adminsUUID.contains(owneruuid);
    }

}
