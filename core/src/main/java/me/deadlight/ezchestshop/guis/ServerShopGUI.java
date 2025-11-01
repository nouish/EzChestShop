package me.deadlight.ezchestshop.guis;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.UUID;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.Constants;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.data.gui.ContainerGui;
import me.deadlight.ezchestshop.data.gui.ContainerGuiItem;
import me.deadlight.ezchestshop.data.gui.GuiData;
import me.deadlight.ezchestshop.utils.SignMenuFactory;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.slf4j.Logger;

public final class ServerShopGUI {
    private static final Logger LOGGER = EzChestShop.logger();

    public ServerShopGUI() {
    }

    public void showGUI(Player player, PersistentDataContainer data, Block containerBlock) {
        LanguageManager lm = LanguageManager.getInstance();
        String rawId = data.get(Constants.OWNER_KEY, PersistentDataType.STRING);
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
                LOGGER.warn("Unable to resolve player name for {} at {}, {}, {} (admin shop).",
                        rawId,
                        containerBlock.getLocation().getBlockX(),
                        containerBlock.getLocation().getBlockY(),
                        containerBlock.getLocation().getBlockZ()
                );
                return;
            }
        }

        // Double.MAX_VALUE simply represents a large value to effectively render this shop disabled in the event the data is missing.
        double sellPrice = data.getOrDefault(Constants.SELL_PRICE_KEY, PersistentDataType.DOUBLE, Double.MAX_VALUE);
        double buyPrice = data.getOrDefault(Constants.BUY_PRICE_KEY, PersistentDataType.DOUBLE, Double.MAX_VALUE);
        boolean disabledBuy = data.getOrDefault(Constants.DISABLE_BUY_KEY, PersistentDataType.INTEGER, 0) == 1;
        boolean disabledSell = data.getOrDefault(Constants.DISABLE_SELL_KEY, PersistentDataType.INTEGER, 0) == 1;

        ContainerGui container = GuiData.getShop();

        Gui gui = Gui.gui()
                .rows(container.getRows())
                .title(Component.text(lm.adminshopguititle()))
                .disableAllInteractions()
                .create();
        gui.getFiller().fill(container.getBackground());

        ItemStack mainitem = Utils.decodeItem(data.get(Constants.ITEM_KEY, PersistentDataType.STRING));
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
                    } catch (NumberFormatException ignored) {}
                }

                ContainerGuiItem sellItemStack = container.getItem(key).setLore(lm.buttonSellXLore(sellPrice * amount, amount)).setName(lm.buttonSellXTitle(amount));

                final int finalAmount = amount;
                GuiItem sellItem = new GuiItem(disablingCheck(sellItemStack.getItem(), disabledSell), event -> {
                    // sell things
                    event.setCancelled(true);
                    if (disabledSell) {
                        return;
                    }
                    ShopContainer.sellServerItem(containerBlock, sellPrice * finalAmount, finalAmount, mainitem, player, data);
                    showGUI(player, data, containerBlock);
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
                    } catch (NumberFormatException ignored) {}
                }

                ContainerGuiItem buyItemStack = container.getItem(key).setLore(lm.buttonBuyXLore(buyPrice * amount, amount)).setName(lm.buttonBuyXTitle(amount));

                final int finalAmount = amount;
                GuiItem buyItem = new GuiItem(disablingCheck(buyItemStack.getItem(), disabledBuy), event -> {
                    // buy things
                    event.setCancelled(true);
                    if (disabledBuy) {
                        return;
                    }
                    ShopContainer.buyServerItem(containerBlock, buyPrice * finalAmount, finalAmount, player, mainitem, data);
                    showGUI(player, data, containerBlock);
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


        //settings item
        boolean result = isAdmin(((TileState) containerBlock.getState(false)).getPersistentDataContainer(), player.getUniqueId().toString());
        //place moved vvv because of settingsGUI
        if (container.hasItem("settings")) {
            if (player.hasPermission("ecs.admin") || result) {
                ContainerGuiItem settingsItemStack = container.getItem("settings");
                settingsItemStack.setName(lm.settingsButton());
                GuiItem settingsGui = new GuiItem(settingsItemStack.getItem(), event -> {
                    event.setCancelled(true);
                    //opening the settigns menu
                    SettingsGUI settingsGUI = new SettingsGUI();
                    settingsGUI.showGUI(player, containerBlock, result);
                    player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.5f, 0.5f);
                });

                Utils.addItemIfEnoughSlots(gui, settingsItemStack.getSlot(), settingsGui);
            }
        }

        if (container.hasItem("custom-buy-sell")) {
            List<String> possibleCounts = Utils.calculatePossibleAmount(Bukkit.getOfflinePlayer(player.getUniqueId()), null, player.getInventory().getStorageContents(), null, buyPrice, sellPrice, mainitem);
            ContainerGuiItem customBuySellItemStack = container.getItem("custom-buy-sell").setName(lm.customAmountSignTitle())
                    .setLore(lm.customAmountSignLore(possibleCounts.get(0), possibleCounts.get(1)));

            GuiItem guiSignItem = new GuiItem(customBuySellItemStack.getItem(), event -> {
                event.setCancelled(true);
                if (event.isRightClick()) {
                    //buy
                    if (disabledBuy) {
                        player.sendMessage(lm.disabledBuyingMessage());
                        return;
                    }
                    player.closeInventory();
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    SignMenuFactory signMenuFactory = new SignMenuFactory(EzChestShop.getPlugin());
                    SignMenuFactory.Menu menu = signMenuFactory.newMenu(lm.signEditorGuiBuy(possibleCounts.getFirst())).reopenIfFail(false).response((thatplayer, strings) ->
                            {
                                try {
                                    if (strings[0].equalsIgnoreCase("")) {
                                        return false;
                                    }
                                    OptionalInt optionalAmount = Utils.tryParseInt(strings[0]);
                                    if (optionalAmount.isPresent()) {
                                        int amount = optionalAmount.getAsInt();
                                        if (amount < 1) {
                                            player.sendMessage(lm.unsupportedInteger());
                                            return false;
                                        }
                                        EzChestShop.getScheduler().runTask(() -> ShopContainer.buyServerItem(containerBlock, buyPrice * amount, amount, thatplayer, mainitem, data));
                                    } else {
                                        thatplayer.sendMessage(lm.wrongInput());
                                    }
                                } catch (Exception e) {
                                    return false;
                                }
                                return true;
                            });
                    menu.open(player);
                    player.sendMessage(lm.enterTheAmount());
                } else if (event.isLeftClick()) {
                    //sell
                    if (disabledSell) {
                        player.sendMessage(lm.disabledSellingMessage());
                        return;
                    }
                    player.closeInventory();
                    player.playSound(player.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 1.0f, 1.0f);
                    SignMenuFactory signMenuFactory = new SignMenuFactory(EzChestShop.getPlugin());
                    SignMenuFactory.Menu menu = signMenuFactory.newMenu(lm.signEditorGuiSell(possibleCounts.get(1))).reopenIfFail(false).response((thatplayer, strings) ->
                            {
                                try {
                                    if (strings[0].equalsIgnoreCase("")) {
                                        return false;
                                    }
                                    OptionalInt optionalAmount = Utils.tryParseInt(strings[0]);
                                    if (optionalAmount.isPresent()) {
                                        int amount = optionalAmount.getAsInt();
                                        if (amount < 1) {
                                            player.sendMessage(lm.unsupportedInteger());
                                            return false;
                                        }
                                        EzChestShop.getScheduler().runTask(() -> ShopContainer.sellServerItem(containerBlock, sellPrice * amount, amount, mainitem, thatplayer, data));
                                    } else {
                                        thatplayer.sendMessage(lm.wrongInput());
                                    }

                                } catch (Exception e) {
                                    return false;
                                }
                                return true;
                            });
                    menu.open(player);
                    player.sendMessage(lm.enterTheAmount());
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

    private boolean isAdmin(PersistentDataContainer data, String uuid) {
        UUID owneruuid = UUID.fromString(uuid);
        List<UUID> adminsUUID = Utils.getAdminsList(data);
        return adminsUUID.contains(owneruuid);
    }
}
