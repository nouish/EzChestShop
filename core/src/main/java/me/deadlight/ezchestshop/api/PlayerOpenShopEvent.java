package me.deadlight.ezchestshop.api;

import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.ApiStatus.Experimental;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

@Experimental
public final class PlayerOpenShopEvent extends AbstractShopEvent implements Cancellable {
    private static final HandlerList HANDLER_LIST = new HandlerList();

    private boolean cancelled;
    private final View view;

    @Internal
    public PlayerOpenShopEvent(
            @NotNull Player player,
            @NotNull UUID ownerId,
            @NotNull Location location,
            @NotNull View view) {
        super(player, ownerId, location);
        this.view = requireNonNull(view);
    }

    @NotNull
    public View getView() {
        return view;
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

    public enum View {
        REGULAR,
        OWNER,
        ADMIN,
        SERVER
    }
}
