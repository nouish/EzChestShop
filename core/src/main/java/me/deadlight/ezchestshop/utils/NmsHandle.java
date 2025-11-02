package me.deadlight.ezchestshop.utils;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;

@NullMarked
public interface NmsHandle {
    void destroyEntity(Player player, int entityId);

    void spawnHologram(Player player, Location location, String line, int id);

    void spawnFloatingItem(Player player, Location location, ItemStack itemStack, int id);

    void renameEntity(Player player, int entityId, String name);

    void teleportEntity(Player player, int entityId, Location location);

    void signFactoryListen(SignMenuFactory signMenuFactory);

    void removeSignMenuFactoryListen(SignMenuFactory signMenuFactory);

    void openMenu(SignMenuFactory.Menu menu, Player player);

    void injectConnection(Player player);

    void ejectConnection(Player player);

    void showOutline(Player player, Block block, int entityId);
}
