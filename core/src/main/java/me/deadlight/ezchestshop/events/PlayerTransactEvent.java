package me.deadlight.ezchestshop.events;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import me.deadlight.ezchestshop.Constants;
import me.deadlight.ezchestshop.utils.Utils;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.jetbrains.annotations.NotNull;

public class PlayerTransactEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final OfflinePlayer owner;
    private final OfflinePlayer customer;
    private final double price;
    private final LocalDateTime time;
    private final boolean isBuy;
    private final ItemStack item;
    private final String itemName;
    private final int count;
    private final List<UUID> admins;
    private final Block containerBlock;

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public PlayerTransactEvent(OfflinePlayer owner, OfflinePlayer customer,
            double price, boolean isBuy, ItemStack item, int count, List<UUID> admins, Block containerBlock) {
        this.owner = owner;
        this.customer = customer;
        this.price = price;
        this.time = LocalDateTime.now();
        this.isBuy = isBuy;
        this.item = item;
        this.itemName = Utils.getFinalItemName(item);
        this.count = count;
        this.admins = admins;
        this.containerBlock = containerBlock;
    }

    public OfflinePlayer getOwner() {
        return this.owner;
    }

    public OfflinePlayer getCustomer() {
        return this.customer;
    }

    public double getPrice() {
        return this.price;
    }

    public LocalDateTime getTime() {
        return this.time;
    }

    public boolean isBuy() {
        return this.isBuy;
    }

    public ItemStack getItem() {
        return this.item;
    }

    public String getItemName() {
        return this.itemName;
    }

    public int getCount() {
        return this.count;
    }

    public List<UUID> getAdminsUUID() {
        return this.admins;
    }

    public Block getContainerBlock() {
        return this.containerBlock;
    }

    public boolean isShareIncome() {
        return getBoolean(containerBlock, Constants.ENABLE_SHARED_INCOME_KEY);
    }

    public double getBuyPrice() {
        return getDouble(containerBlock, Constants.BUY_PRICE_KEY);
    }

    public double getSellPrice() {
        return getDouble(containerBlock, Constants.SELL_PRICE_KEY);
    }

    private boolean getBoolean(Block containerBlock, NamespacedKey key) {
        TileState state = (TileState) containerBlock.getState(false);
        PersistentDataContainer container = state.getPersistentDataContainer();
        return container.getOrDefault(key, PersistentDataType.INTEGER, 0) == 1;
    }

    private double getDouble(Block containerBlock, NamespacedKey key) {
        TileState state = (TileState) containerBlock.getState(false);
        PersistentDataContainer container = state.getPersistentDataContainer();
        return container.getOrDefault(key, PersistentDataType.DOUBLE, 0.0);
    }
}
