package me.deadlight.ezchestshop.utils;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class ASHologram {

    private int entityID;
    private String name;
    private Player handler;
    private Location location;

    public ASHologram(Player p, String name, Location location) {
        this.name = name;
        this.entityID = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
        this.handler = p;
        this.location = location;
        Utils.nmsHandle.spawnHologram(p, location, name, entityID);
    }

    public void destroy() {
        Utils.nmsHandle.destroyEntity(handler, entityID);
    }

    public String getName() {
        return name;
    }

    public Location getLocation() {
        return location;
    }

    public void teleport(Location location) {
        this.location = location;
        Utils.nmsHandle.teleportEntity(handler, entityID, location);
    }

    public void rename(String name) {
        this.name = name;
        Utils.nmsHandle.renameEntity(handler, entityID, name);
    }
}
