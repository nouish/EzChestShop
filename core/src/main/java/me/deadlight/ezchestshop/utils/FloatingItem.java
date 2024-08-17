package me.deadlight.ezchestshop.utils;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class FloatingItem {

    private final int entityID;
    private final Player player;
    private Location location;

    public FloatingItem(Player player, ItemStack itemStack, Location location) {
        this.player = player;
        this.entityID = (int) (Math.random() * Integer.MAX_VALUE);
        this.location = location;
        Utils.nmsHandle.spawnFloatingItem(player, location, itemStack, entityID);
    }

    public void destroy() {
        Utils.nmsHandle.destroyEntity(player, entityID);
    }

    public void teleport(Location location) {
        this.location = location;
        Utils.nmsHandle.teleportEntity(player, entityID, location);
    }

    public Location getLocation() {
        return location;
    }

}
