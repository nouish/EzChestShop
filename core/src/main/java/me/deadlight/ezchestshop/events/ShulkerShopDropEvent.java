package me.deadlight.ezchestshop.events;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

//only trigger via piston movement
public final class ShulkerShopDropEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();

    private final Item droppedShulker;
    private final Location previousShulkerLocation;

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public ShulkerShopDropEvent(Item droppedShulker, Location previousShulkerLocation) {
        this.droppedShulker = droppedShulker;
        this.previousShulkerLocation = previousShulkerLocation;
    }

    public Item getDroppedShulker() {
        return this.droppedShulker;
    }

    public Location getPreviousShulkerLocation() {
        return this.previousShulkerLocation;
    }
}
