package me.deadlight.ezchestshop.api;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import static com.google.common.base.Preconditions.checkArgument;
import static java.util.Objects.requireNonNull;

@Experimental
public final class PlayerShopTransactionEvent extends AbstractShopEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private boolean cancelled;
    private final Type type;
    private final ItemStack item;
    private int count;
    private double price;
    private final boolean infinite;

    @Internal
    public PlayerShopTransactionEvent(
            @NotNull Player player,
            @NotNull UUID ownerId,
            @NotNull Location location,
            @NotNull Type type,
            @NotNull ItemStack item,
            int count,
            double price,
            boolean infinite) {
        super(player, ownerId, location);
        this.type = requireNonNull(type);
        this.item = requireNonNull(item);
        this.count = count;
        this.price = price;
        this.infinite = infinite;
    }

    @NotNull
    public Type getType() {
        return type;
    }

    @NotNull
    public ItemStack getItem() {
        return item;
    }

    public int getCount() {
        return count;
    }

    public void setCount(int count) {
        checkArgument(count >= 1, "Count must be positive: %s", count);
        this.count = count;
    }

    public double getPrice() {
        return price;
    }

    public void setPrice(double price) {
        checkArgument(price >= 0, "Price must be zero or positive: %s", price);
        this.price = price;
    }

    public boolean isInfinite() {
        return infinite;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLER_LIST;
    }

    @NotNull
    public static HandlerList getHandlerList() {
        return HANDLER_LIST;
    }

    public enum Type {
        BUY,
        SELL
    }
}
