package me.deadlight.ezchestshop.listeners;

import java.text.NumberFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.Constants;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.PlayerContainer;
import me.deadlight.ezchestshop.events.PlayerTransactEvent;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.DiscordWebhook;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public class PlayerTransactionListener implements Listener {
    @EventHandler
    public void onTransaction(PlayerTransactEvent event) {
        logProfits(event);
        sendDiscordWebhook(event);
        if (((TileState) event.getContainerBlock().getState(false)).getPersistentDataContainer().getOrDefault(Constants.ENABLE_MESSAGE_KEY, PersistentDataType.INTEGER, 0) == 1) {
            OfflinePlayer owner = event.getOwner();
            List<UUID> getters = event.getAdminsUUID();
            getters.add(owner.getUniqueId());

            if (event.isBuy()) {
                for (UUID adminUUID : getters) {
                    Player admin = Bukkit.getPlayer(adminUUID);
                    if (admin != null) {
                        if (admin.isOnline()) {
                            admin.getPlayer().sendMessage(LanguageManager.getInstance().transactionBuyInform(event.getCustomer().getName(),
                                    event.getCount(), event.getItemName(), event.getPrice()));
                        }
                    }
                }
            } else {
                for (UUID adminUUID : getters) {
                    Player admin = Bukkit.getPlayer(adminUUID);
                    if (admin != null) {
                        if (admin.isOnline()) {
                            if (admin.isOnline()) {
                                admin.getPlayer().sendMessage(LanguageManager.getInstance().transactionSellInform(
                                        event.getCustomer().getName(), event.getCount(), event.getItemName(), event.getPrice()));
                            }
                        }
                    }
                }
            }
        }
    }

    public void sendDiscordWebhook(PlayerTransactEvent event) {
        ItemMeta meta = event.getItem().getItemMeta();
        Block block = event.getContainerBlock();

        final String buyerName;
        final String sellerName;
        final String itemName = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : event.getItemName();
        final String price = EzChestShop.getEconomy().format(event.getPrice());
        final String shopLocation = block.getWorld().getName() + ", " + block.getX() + ", " + block.getY() + ", " + block.getZ();
        final String time = DateTimeFormatter.ISO_DATE_TIME.format(event.getTime()).replace("T", " | ").replace("Z", "").replace("-", "/");
        final String quantity = NumberFormat.getInstance(Locale.ENGLISH).format(event.getCount());
        final String ownerName = event.getOwner().getName();

        if (event.isBuy()) {
            buyerName = event.getCustomer().getName();
            sellerName = event.getOwner().getName();
        } else {
            buyerName = event.getOwner().getName();
            sellerName = event.getCustomer().getName();
        }

        EzChestShop.getScheduler().runTaskAsynchronously(
                () -> DiscordWebhook.queueTransaction(buyerName, sellerName, itemName, price, shopLocation, time, quantity, ownerName));
    }

    private void logProfits(PlayerTransactEvent event) {
        Double price = event.getPrice();
        Integer count = event.getCount();
        // These next 4 are interesting:
        //Integer count = amount / defaultAmount; // How many times were items bought. Considers Stack buying.
        // Double single_price = price / count;
        String id = Utils.LocationtoString(event.getContainerBlock().getLocation());
        ItemStack item = event.getItem(); // Item shop sells
        PlayerContainer owner = PlayerContainer.get(event.getOwner());
        if (event.isBuy()) {
            if (event.isShareIncome()) {
                int admin_count = event.getAdminsUUID().size();
                for (UUID uuid : event.getAdminsUUID()) {
                    if (uuid.equals(event.getOwner().getUniqueId()))
                        continue;
                    PlayerContainer admin = PlayerContainer.get(Bukkit.getOfflinePlayer(uuid));
                    admin.updateProfits(id, item, count, price / (admin_count + 1), price / count, 0, 0.0, 0.0);
                }

                owner.updateProfits(id, item, count, price / (admin_count + 1), event.getBuyPrice(), 0, 0.0, event.getSellPrice());
            } else {
                owner.updateProfits(id, item, count, price, event.getBuyPrice(), 0, 0.0, event.getSellPrice());
            }
        } else {
            owner.updateProfits(id, item, 0, 0.0, event.getBuyPrice(), count, price, event.getSellPrice());
        }
        // ItemStack,BuyAmount,BuyPrice,SellAmount,SellPrice
    }

}
