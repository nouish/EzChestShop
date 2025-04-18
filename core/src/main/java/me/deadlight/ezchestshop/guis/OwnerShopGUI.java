package me.deadlight.ezchestshop.guis;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.EzChestShopConstants;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.data.gui.ContainerGui;
import me.deadlight.ezchestshop.data.gui.ContainerGuiItem;
import me.deadlight.ezchestshop.data.gui.GuiData;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class OwnerShopGUI {
    public OwnerShopGUI() {}

    public void showGUI(Player player, PersistentDataContainer data, Block containerBlock, boolean isAdmin) {
        LanguageManager lm = LanguageManager.getInstance();
        String rawId = data.get(EzChestShopConstants.OWNER_KEY, PersistentDataType.STRING);
        if (rawId == null) {
            throw new NullPointerException("Null owner id");
        }
        OfflinePlayer offlinePlayerOwner = Bukkit.getOfflinePlayer(UUID.fromString(rawId));
        String shopOwner = offlinePlayerOwner.getName();
        if (shopOwner == null) {
            boolean result = Utils.reInstallNamespacedKeyValues(data, containerBlock.getLocation());
            if (!result) {
                player.sendMessage(lm.chestShopProblem());
                return;
            }
            containerBlock.getState(false).update();
            EzShop shop = ShopContainer.getShop(containerBlock.getLocation());
            shopOwner = Bukkit.getOfflinePlayer(shop.getOwnerID()).getName();
            if (shopOwner == null) {
                player.sendMessage(lm.chestShopProblem());
                System.out.println("EzChestShop ERROR: Shop owner is STILL null. Please report this to the EzChestShop developer for furthur investigation.");
                return;
            }
        }

        // Double.MAX_VALUE simply represents a large value effectively rendering it disabled in the event this data is missing.
        double sellPrice = data.getOrDefault(EzChestShopConstants.SELL_PRICE_KEY, PersistentDataType.DOUBLE, Double.MAX_VALUE);
        double buyPrice = data.getOrDefault(EzChestShopConstants.BUY_PRICE_KEY, PersistentDataType.DOUBLE, Double.MAX_VALUE);
        boolean disabledBuy = data.getOrDefault(EzChestShopConstants.DISABLE_BUY_KEY, PersistentDataType.INTEGER, 0) == 1;
        boolean disabledSell = data.getOrDefault(EzChestShopConstants.DISABLE_SELL_KEY, PersistentDataType.INTEGER, 0) == 1;

        ContainerGui container = GuiData.getShop();

        Gui gui = new Gui(container.getRows(), lm.guiOwnerTitle(shopOwner));
        gui.getFiller().fill(container.getBackground());

        ItemStack mainitem = Utils.decodeItem(data.get(EzChestShopConstants.ITEM_KEY, PersistentDataType.STRING));
        if (container.hasItem("shop-item")) {
            ItemStack guiMainItem = mainitem.clone();
            ItemMeta mainmeta = guiMainItem.getItemMeta();
            // Set the lore and keep the old one if available
            if (mainmeta.hasLore()) {
                List<String> prevLore = mainmeta.getLore();
                prevLore.add("");
                List<String> mainItemLore = Arrays.asList(lm.initialBuyPrice(buyPrice), lm.initialSellPrice(sellPrice));
                prevLore.addAll(mainItemLore);
                mainmeta.setLore(prevLore);
            } else {
                List<String> mainItemLore = Arrays.asList(lm.initialBuyPrice(buyPrice), lm.initialSellPrice(sellPrice));
                mainmeta.setLore(mainItemLore);
            }
            guiMainItem.setItemMeta(mainmeta);
            GuiItem guiitem = new GuiItem(guiMainItem, event -> {
                event.setCancelled(true);
            });
            Utils.addItemIfEnoughSlots(gui, container.getItem("shop-item").getSlot(), guiitem);
        }

        container.getItemKeys().forEach(key -> {
            if (key.startsWith("sell-")) {
                String amountString = key.split("-")[1];
                int amount = 1;
                if (amountString.equals("all")) {
                    amount = Integer.parseInt(Utils.calculateSellPossibleAmount(Bukkit.getOfflinePlayer(player.getUniqueId()), player.getInventory().getStorageContents(), Utils.getBlockInventory(containerBlock).getStorageContents(), sellPrice, mainitem));
                } else if (amountString.equals("maxStackSize")) {
                    amount = mainitem.getMaxStackSize();
                    container.getItem(key).setAmount(amount);
                } else {
                    try {
                        amount = Integer.parseInt(amountString);
                    } catch (NumberFormatException e) {}
                }

                ContainerGuiItem sellItemStack = container.getItem(key).setLore(lm.buttonSellXLore(sellPrice * amount, amount)).setName(lm.buttonSellXTitle(amount));

                final int finalAmount = amount;
                GuiItem sellItem = new GuiItem(disablingCheck(sellItemStack.getItem(), disabledSell), event -> {
                    // sell things
                    event.setCancelled(true);
                    if (disabledSell) {
                        return;
                    }
                    if (offlinePlayerOwner.getUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage(lm.selfTransaction());
                        return;
                    }
                    ShopContainer.sellItem(containerBlock, sellPrice * finalAmount, finalAmount, mainitem, player, offlinePlayerOwner, data);
                    showGUI(player, data, containerBlock, isAdmin);
                });

                Utils.addItemIfEnoughSlots(gui, sellItemStack.getSlot(), sellItem);
            } else if (key.startsWith("buy-")) {
                String amountString = key.split("-")[1];
                int amount = 1;
                if (amountString.equals("all")) {
                    amount = Integer.parseInt(Utils.calculateBuyPossibleAmount(Bukkit.getOfflinePlayer(player.getUniqueId()), player.getInventory().getStorageContents(), Utils.getBlockInventory(containerBlock).getStorageContents(), buyPrice, mainitem));
                } else if (amountString.equals("maxStackSize")) {
                    amount = mainitem.getMaxStackSize();
                    container.getItem(key).setAmount(amount);
                } else {
                    try {
                        amount = Integer.parseInt(amountString);
                    } catch (NumberFormatException e) {}
                }

                ContainerGuiItem buyItemStack = container.getItem(key).setLore(lm.buttonBuyXLore(buyPrice * amount, amount)).setName(lm.buttonBuyXTitle(amount));

                final int finalAmount = amount;
                GuiItem buyItem = new GuiItem(disablingCheck(buyItemStack.getItem(), disabledBuy), event -> {
                    // buy things
                    event.setCancelled(true);
                    if (disabledBuy) {
                        return;
                    }
                    if (offlinePlayerOwner.getUniqueId().equals(player.getUniqueId())) {
                        player.sendMessage(lm.selfTransaction());
                        return;
                    }
                    ShopContainer.buyItem(containerBlock, buyPrice * finalAmount, finalAmount, mainitem, player, offlinePlayerOwner, data);
                    showGUI(player, data, containerBlock, isAdmin);
                });

                Utils.addItemIfEnoughSlots(gui, buyItemStack.getSlot(), buyItem);
            } else if (key.startsWith("decorative-")) {

                ContainerGuiItem decorativeItemStack = container.getItem(key).setName(Utils.colorify("&d"));

                GuiItem buyItem = new GuiItem(decorativeItemStack.getItem(), event -> {
                    event.setCancelled(true);
                });

                Utils.addItemIfEnoughSlots(gui, decorativeItemStack.getSlot(), buyItem);
            }
        });

        if (container.hasItem("storage")) {
            ContainerGuiItem guiStorageItem = container.getItem("storage").setName(lm.buttonAdminView());

            GuiItem storageGUI = new GuiItem(guiStorageItem.getItem(), event -> {
                event.setCancelled(true);

                Block theBlock = player.getWorld().getBlockAt(containerBlock.getLocation());
                if (theBlock.getState(false) instanceof TileState state) {
                    PersistentDataContainer pdc = state.getPersistentDataContainer();
                    if (!pdc.has(EzChestShopConstants.OWNER_KEY, PersistentDataType.STRING)) {
                        // https://www.youtube.com/watch?v=Kbllpg9PGJw
                        EzChestShop.logger().warn("{} attempted to duplicate items!", player.getName());
                        player.closeInventory();
                        return;
                    }
                }

                Inventory lastinv = Utils.getBlockInventory(theBlock);
                if (lastinv == null) {
                    player.closeInventory();
                    return;
                }

                if (lastinv instanceof DoubleChestInventory) {
                    DoubleChest doubleChest = (DoubleChest) lastinv.getHolder(false);
                    lastinv = doubleChest.getInventory();
                }

                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BIT, 0.5f, 0.5f);
                player.openInventory(lastinv);
            });

            //containerBlock storage
            Utils.addItemIfEnoughSlots(gui, guiStorageItem.getSlot(), storageGUI);
        }

        //settings item
        if (container.hasItem("settings")) {
            ContainerGuiItem settingsItemStack = container.getItem("settings");
            settingsItemStack.setName(lm.settingsButton());
            GuiItem settingsGui = new GuiItem(settingsItemStack.getItem(), event -> {
               event.setCancelled(true);
               //opening the settigns menu
                SettingsGUI settingsGUI = new SettingsGUI();
                settingsGUI.showGUI(player, containerBlock, isAdmin);
                player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.5f, 0.5f);
            });
            //settings item
            Utils.addItemIfEnoughSlots(gui, settingsItemStack.getSlot(), settingsGui);
        }

        if (container.hasItem("custom-buy-sell")) {
            List<String> possibleCounts = Utils.calculatePossibleAmount(Bukkit.getOfflinePlayer(player.getUniqueId()), offlinePlayerOwner, player.getInventory().getStorageContents(), Utils.getBlockInventory(containerBlock).getStorageContents(), buyPrice, sellPrice, mainitem);
            ContainerGuiItem customBuySellItemStack = container.getItem("custom-buy-sell").setName(lm.customAmountSignTitle()).setLore(lm.customAmountSignLore(possibleCounts.get(0), possibleCounts.get(1)));

            GuiItem guiSignItem = new GuiItem(customBuySellItemStack.getItem(), event -> {
                event.setCancelled(true);
                if (event.isRightClick()) {
                    //buy
                    player.sendMessage(lm.selfTransaction());


                } else if (event.isLeftClick()) {
                    //sell
                    player.sendMessage(lm.selfTransaction());


                }
            });

            if (Config.settings_custom_amout_transactions) {
                //sign item
                Utils.addItemIfEnoughSlots(gui, customBuySellItemStack.getSlot(), guiSignItem);
            }
        }

        gui.open(player);
    }

    private ItemStack disablingCheck(ItemStack mainItem, boolean disabling) {
        if (disabling) {
            //disabled Item
            ItemStack disabledItemStack = new ItemStack(Material.BARRIER, mainItem.getAmount());
            disabledItemStack.editMeta(disabledItemMeta -> {
                disabledItemMeta.setDisplayName(LanguageManager.getInstance().disabledButtonTitle());
                disabledItemMeta.setLore(LanguageManager.getInstance().disabledButtonLore());
            });
            return disabledItemStack;
        }

        return mainItem;
    }

}
