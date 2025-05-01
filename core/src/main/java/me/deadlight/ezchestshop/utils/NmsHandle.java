package me.deadlight.ezchestshop.utils;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public abstract class NmsHandle {

    public abstract void destroyEntity(Player player, int entityID);

    public abstract void spawnHologram(Player player, Location location, String line, int ID);

    public abstract void spawnFloatingItem(Player player, Location location, ItemStack itemStack, int ID);

    public abstract void renameEntity(Player player, int entityID, String name);

    public abstract void teleportEntity(Player player, int entityID, Location location);

    public abstract void signFactoryListen(SignMenuFactory signMenuFactory);

    public abstract void removeSignMenuFactoryListen(SignMenuFactory signMenuFactory);

    public abstract void openMenu(SignMenuFactory.Menu menu, Player player);

    public abstract void injectConnection(Player player);

    public abstract void ejectConnection(Player player);

    public abstract void showOutline(Player player, Block block, int eID);

}
