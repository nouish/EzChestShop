package me.deadlight.ezchestshop.listeners;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.EzChestShopConstants;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.PlayerContainer;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.events.PlayerTransactEvent;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.WebhookSender;
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

    LanguageManager lm = new LanguageManager();

    static DateTimeFormatter formatter = DateTimeFormatter.ISO_DATE_TIME;

    @EventHandler
    public void onTransaction(PlayerTransactEvent event) {
        // Comprobar si la funcionalidad de limitación de permisos está habilitada
        boolean isAdminShop = event.getAdminsUUID().contains(event.getOwner().getUniqueId());

        // Si es una tienda admin, no hacemos los cálculos de límite de permisos
        if (!isAdminShop) {
            // Comprobar si la funcionalidad de limitación de permisos está habilitada
            if (Config.permissions_create_shop_enabled) {
                int maxShopsWorld = Utils.getMaxPermission(Objects.requireNonNull(event.getOwner().getPlayer()),
                        "ecs.shops.limit." + event.getContainerBlock().getWorld().getName() + ".", -2);
                int maxShops;

                if (maxShopsWorld == -2) {
                    maxShops = Utils.getMaxPermission(Objects.requireNonNull(event.getOwner().getPlayer()), "ecs.shops.limit.", 0);
                } else {
                    maxShops = maxShopsWorld;
                }

                maxShops = maxShops == -1 ? 10000 : maxShops; // Si tiene permisos ilimitados, se define un valor alto.

                int shops = ShopContainer.getShopCount(event.getOwner().getPlayer()); // Número de tiendas actuales del jugador.

                // Si el jugador ha superado el límite
                if (shops > maxShops) {
                    Player customer = event.getCustomer().getPlayer();
                    if (customer != null) {
                        customer.sendMessage(lm.transactionMaxShopsCancelation(event.getOwner().getName()));
                    }
                    return; // Cancelamos la ejecución del resto del evento.
                }
            }
        }
        logProfits(event);
        sendDiscordWebhook(event);
        if (((TileState) event.getContainerBlock().getState()).getPersistentDataContainer().getOrDefault(EzChestShopConstants.ENABLE_MESSAGE_KEY, PersistentDataType.INTEGER, 0) == 1) {
            OfflinePlayer owner = event.getOwner();
            List<UUID> getters = event.getAdminsUUID();
            getters.add(owner.getUniqueId());

            if (event.isBuy()) {
                for (UUID adminUUID : getters) {
                    Player admin = Bukkit.getPlayer(adminUUID);
                    if (admin != null) {
                        if (admin.isOnline()) {
                            admin.getPlayer().sendMessage(lm.transactionBuyInform(event.getCustomer().getName(), event.getCount(),
                                    event.getItemName(), event.getPrice()));
                        }
                    }
                }
            } else {
                for (UUID adminUUID : getters) {
                    Player admin = Bukkit.getPlayer(adminUUID);
                    if (admin != null) {
                        if (admin.isOnline()) {
                            if (admin.isOnline()) {
                                admin.getPlayer().sendMessage(lm.transactionSellInform(event.getCustomer().getName(), event.getCount(),
                                        event.getItemName(), event.getPrice()));
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
        final String price = String.valueOf(event.getPrice());
        final String shopLocation = block.getWorld().getName() + ", " + block.getX() + ", " + block.getY() + ", " + block.getZ();
        final String time = formatter.format(event.getTime()).replace("T", " | ").replace("Z", "").replace("-", "/");
        final String quantity = String.valueOf(event.getCount());
        final String ownerName = event.getOwner().getName();

        if (event.isBuy()) {
            buyerName = event.getCustomer().getName();
            sellerName = event.getOwner().getName();
        } else {
            buyerName = event.getOwner().getName();
            sellerName = event.getCustomer().getName();
        }

        EzChestShop.getScheduler().runTaskAsynchronously(
                () -> WebhookSender.sendDiscordNewTransactionAlert(buyerName, sellerName, itemName, price, Config.currency, shopLocation, time, quantity, ownerName));
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
