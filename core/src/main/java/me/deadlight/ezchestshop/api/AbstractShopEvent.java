package me.deadlight.ezchestshop.api;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.player.PlayerEvent;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;

import static java.util.Objects.requireNonNull;

@Internal
abstract class AbstractShopEvent extends PlayerEvent {
    private final UUID ownerId;
    private final Location location;

    AbstractShopEvent(
            @NotNull Player player,
            @NotNull UUID ownerId,
            @NotNull Location location) {
        super(Objects.requireNonNull(player));
        this.ownerId = requireNonNull(ownerId);
        this.location = requireNonNull(location);
    }

    @NotNull
    public UUID getOwnerId() {
        return ownerId;
    }

    @NotNull
    public Location getLocation() {
        return location;
    }
}
