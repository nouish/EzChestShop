package me.deadlight.ezchestshop.guis;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import dev.triumphteam.gui.guis.Gui;
import dev.triumphteam.gui.guis.GuiItem;
import dev.triumphteam.gui.guis.PaginatedGui;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.data.gui.ContainerGui;
import me.deadlight.ezchestshop.data.gui.ContainerGuiItem;
import me.deadlight.ezchestshop.data.gui.GuiData;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.holograms.ShopHologram;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.deadlight.ezchestshop.utils.objects.ShopSettings;
import net.kyori.adventure.text.Component;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class CustomMessageManageGUI {

    public void showGUI(Player player, Block containerBlock, boolean isAdmin) {
        LanguageManager lm = LanguageManager.getInstance();
        ContainerGui container = GuiData.getMessageManager();
        PaginatedGui paginatedGui = Gui.paginated()
                .title(Component.text(lm.customMessageManagerTitle()))
                .rows(container.getRows())
                .pageSize(container.getRows() * 9 - 9)
                .create();
        paginatedGui.setDefaultClickAction(event -> {
            event.setCancelled(true);
        });

        Map<Location, String> customMessages = ShopSettings.getAllCustomMessages(ShopContainer.getShop(containerBlock.getLocation()).getOwnerID().toString());

        // Fill the bottom bar:
        paginatedGui.getFiller().fillBottom(container.getBackground());

        // Previous item
        if (container.hasItem("previous")) {
        ContainerGuiItem previous = container.getItem("previous")
                .setName(lm.customMessageManagerPreviousPageTitle())
                .setLore(lm.customMessageManagerPreviousPageLore());
        GuiItem previousItem = new GuiItem(previous.getItem(), event -> {
            event.setCancelled(true);
            paginatedGui.previous();
        });
        Utils.addItemIfEnoughSlots(paginatedGui, previous.getSlot(), previousItem);
        }
        // Next item
        if (container.hasItem("next")) {
            ContainerGuiItem next = container.getItem("next")
                    .setName(lm.customMessageManagerNextPageTitle())
                    .setLore(lm.customMessageManagerNextPageLore());
            GuiItem nextItem = new GuiItem(next.getItem(), event -> {
                event.setCancelled(true);
                paginatedGui.next();
            });
            Utils.addItemIfEnoughSlots(paginatedGui, next.getSlot(), nextItem);
        }
        // Back item
        if (container.hasItem("back")) {
            ContainerGuiItem back = container.getItem("back")
                    .setName(lm.backToSettingsButton());
            GuiItem doorItem = new GuiItem(back.getItem(), event -> {
                event.setCancelled(true);
                SettingsGUI settingsGUI = new SettingsGUI();
                settingsGUI.showGUI(player, containerBlock, isAdmin);
            });
            Utils.addItemIfEnoughSlots(paginatedGui, back.getSlot(), doorItem);
        }

        if (container.hasItem("hologram-message-item")) {
            for (Map.Entry<Location, String> entry : customMessages.entrySet()) {
                Location loc = entry.getKey();
                String message = entry.getValue();
                List<String> messages = Arrays.asList(message.split("#,#")).stream().map(s -> Utils.colorify(s)).collect(Collectors.toList());

                ContainerGuiItem item = container.getItem("hologram-message-item");
                EzShop ezShop = ShopContainer.getShop(loc);
                if (ezShop != null) {
                    item.setName(lm.customMessageManagerShopEntryTitle(ezShop.getShopItem()));
                } else {
                    item.setName(lm.customMessageManagerShopEntryUnkownTitle());
                }
                item.setLore(lm.customMessageManagerShopEntryLore(loc, messages));

                GuiItem shopItem = new GuiItem(item.getItem(), event -> {
                    event.setCancelled(true);
                    if (event.isLeftClick()) {
                        showDeleteConfirm(player, containerBlock, isAdmin, loc);
                    } else if (event.isRightClick()) {
                        SettingsGUI.openCustomMessageEditor(player, loc);
                    }
                });

                paginatedGui.addItem(shopItem);
            }
        }

        if (container.hasItem("modify-current-hologram")) {
            ContainerGuiItem modify = container.getItem("modify-current-hologram")
                    .setName(lm.customMessageManagerModifyCurrentHologramTitle())
                    .setLore(lm.customMessageManagerModifyCurrentHologramLore());
            GuiItem modifyItem = new GuiItem(modify.getItem(), event -> {
                event.setCancelled(true);
                if (event.isLeftClick()) {
                    showDeleteConfirm(player, containerBlock, isAdmin, containerBlock.getLocation());
                } else if (event.isRightClick()) {
                    SettingsGUI.openCustomMessageEditor(player, containerBlock.getLocation());
                }
            });
            Utils.addItemIfEnoughSlots(paginatedGui, modify.getSlot(), modifyItem);
        }

        paginatedGui.open(player);


    }

    private void showDeleteConfirm(Player player, Block containerBlock, boolean isAdmin, Location loc) {
        LanguageManager lm = LanguageManager.getInstance();
        Gui gui = Gui.gui()
                .rows(3)
                .title(Component.text(lm.customMessageManagerConfirmDeleteGuiTitle()))
                .disableAllInteractions()
                .create();
        ItemStack glassis = new ItemStack(Material.BLACK_STAINED_GLASS_PANE, 1);
        ItemMeta glassmeta = glassis.getItemMeta();
        glassmeta.setDisplayName(Utils.colorify("&d"));
        glassmeta.setHideTooltip(true);
        glassis.setItemMeta(glassmeta);
        GuiItem glasses = new GuiItem(glassis, event -> {
            event.setCancelled(true);
        });
        gui.getFiller().fill(glasses);

        ItemStack confirm = new ItemStack(Material.LIME_WOOL, 1);
        ItemMeta confirmMeta = confirm.getItemMeta();
        confirmMeta.setDisplayName(lm.customMessageManagerConfirmDeleteTitle());
        confirmMeta.setLore(lm.customMessageManagerConfirmDeleteLore());
        confirm.setItemMeta(confirmMeta);
        GuiItem confirmItem = new GuiItem(confirm, event -> {
            event.setCancelled(true);
            ShopContainer.getShopSettings(loc).setCustomMessages(new ArrayList<>());
            gui.close(player);
            ShopHologram.getHologram(loc, player).setCustomHologramMessage(new ArrayList<>());
        });
        gui.setItem(2, 5, confirmItem);

        ItemStack back = new ItemStack(Material.DARK_OAK_DOOR, 1);
        back.editMeta(backMeta -> {
            backMeta.setDisplayName(lm.customMessageManagerBackToCustomMessageManagerTitle());
            backMeta.setLore(lm.customMessageManagerBackToCustomMessageManagerLore());
        });
        GuiItem backItem = new GuiItem(back, event -> {
            event.setCancelled(true);
            showGUI(player, containerBlock, isAdmin);
        });

        gui.setItem(3, 1, backItem);
        gui.open(player);
    }

}
