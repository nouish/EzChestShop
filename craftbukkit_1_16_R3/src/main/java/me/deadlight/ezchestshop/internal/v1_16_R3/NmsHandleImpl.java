package me.deadlight.ezchestshop.internal.v1_16_R3;

import java.util.HashMap;
import java.util.Map;

import io.netty.channel.Channel;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.utils.ImprovedOfflinePlayer;
import me.deadlight.ezchestshop.utils.NmsHandle;
import me.deadlight.ezchestshop.utils.SignMenuFactory;
import me.deadlight.ezchestshop.utils.UpdateSignListener;
import net.minecraft.server.v1_16_R3.Entity;
import net.minecraft.server.v1_16_R3.EntityArmorStand;
import net.minecraft.server.v1_16_R3.EntityItem;
import net.minecraft.server.v1_16_R3.EntityPlayer;
import net.minecraft.server.v1_16_R3.EntityShulker;
import net.minecraft.server.v1_16_R3.EntityTypes;
import net.minecraft.server.v1_16_R3.NBTTagCompound;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityDestroy;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityMetadata;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityTeleport;
import net.minecraft.server.v1_16_R3.PacketPlayOutEntityVelocity;
import net.minecraft.server.v1_16_R3.PacketPlayOutSpawnEntity;
import net.minecraft.server.v1_16_R3.PlayerConnection;
import net.minecraft.server.v1_16_R3.World;
import net.minecraft.server.v1_16_R3.WorldServer;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_16_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_16_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_16_R3.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class NmsHandleImpl extends NmsHandle {
    private static final Map<SignMenuFactory, UpdateSignListener> listeners = new HashMap<>();
    private static final Map<Integer, Entity> entities = new HashMap<>();

    @Override
    public ImprovedOfflinePlayer getImprovedOfflinePlayer() {
        return ImprovedOfflinePlayerImpl.INSTANCE;
    }

    @Override
    public String ItemToTextCompoundString(ItemStack itemStack) {
        // First we convert the item stack into an NMS itemstack
        return CraftItemStack.asNMSCopy(itemStack).save(new NBTTagCompound()).toString();
    }

    @Override
    public void destroyEntity(Player player, int entityID) {
        ((org.bukkit.craftbukkit.v1_16_R3.entity.CraftPlayer) player).getHandle().playerConnection.sendPacket(new PacketPlayOutEntityDestroy(entityID));
        entities.remove(entityID);
    }

    @Override
    public void spawnHologram(Player player, Location location, String line, int ID) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        EntityPlayer entityPlayer = craftPlayer.getHandle();
        PlayerConnection playerConnection = entityPlayer.playerConnection;
        CraftWorld craftWorld = (CraftWorld) location.getWorld();
        World world = craftWorld.getHandle();
        //------------------------------------------------------

        EntityArmorStand armorstand = new EntityArmorStand(world, location.getX(), location.getY(), location.getZ());
        armorstand.setInvisible(true); //invisible
        armorstand.setMarker(true); //Marker
        armorstand.setCustomName(CraftChatMessage.fromStringOrNull(line)); //set custom name
        armorstand.setCustomNameVisible(true); //make custom name visible
        armorstand.setNoGravity(true); //no gravity
        armorstand.e(ID); //set entity id

        PacketPlayOutSpawnEntity packetPlayOutSpawnEntity = new PacketPlayOutSpawnEntity(armorstand, 0);
        playerConnection.sendPacket(packetPlayOutSpawnEntity);
        //------------------------------------------------------
        // sending meta packet
        PacketPlayOutEntityMetadata metaPacket = new PacketPlayOutEntityMetadata(ID, armorstand.getDataWatcher(), true);
        playerConnection.sendPacket(metaPacket);
        entities.put(ID, armorstand);
    }

    @Override
    public void spawnFloatingItem(Player player, Location location, ItemStack itemStack, int ID) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        EntityPlayer entityPlayer = craftPlayer.getHandle();
        PlayerConnection playerConnection = entityPlayer.playerConnection;
        CraftWorld craftWorld = (CraftWorld) location.getWorld();
        World world = craftWorld.getHandle();
        //------------------------------------------------------

        EntityItem floatingItem = new EntityItem(world, location.getX(), location.getY(), location.getZ(), CraftItemStack.asNMSCopy(itemStack));
        floatingItem.setNoGravity(true); //no gravity
        floatingItem.e(ID); //set entity id
        floatingItem.setMot(0, 0, 0); //set velocity

        PacketPlayOutSpawnEntity packetPlayOutSpawnEntity = new PacketPlayOutSpawnEntity(floatingItem, 0);
        playerConnection.sendPacket(packetPlayOutSpawnEntity);
        //------------------------------------------------------
        // sending meta packet
        PacketPlayOutEntityMetadata metaPacket = new PacketPlayOutEntityMetadata(ID, floatingItem.getDataWatcher(), true);
        playerConnection.sendPacket(metaPacket);

        //sending a velocity packet
        floatingItem.setMot(0, 0, 0);
        PacketPlayOutEntityVelocity velocityPacket = new PacketPlayOutEntityVelocity(floatingItem);
        playerConnection.sendPacket(velocityPacket);
        entities.put(ID, floatingItem);
    }

    @Override
    public void renameEntity(Player player, int entityID, String newName) {
        Entity e = entities.get(entityID);
        e.setCustomName(CraftChatMessage.fromStringOrNull(newName));
        PacketPlayOutEntityMetadata packet = new PacketPlayOutEntityMetadata(entityID, e.getDataWatcher(), true);
        ((CraftPlayer) player).getHandle().playerConnection.sendPacket(packet);
    }

    @Override
    public void teleportEntity(Player player, int entityID, Location location) {
        EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();
        Entity e = entities.get(entityID);
        e.teleportAndSync(location.getX(), location.getY(), location.getZ());
        // not sure if it's needed
        PacketPlayOutEntityTeleport packet = new PacketPlayOutEntityTeleport(e);
        entityPlayer.playerConnection.sendPacket(packet);
    }

    @Override
    public void signFactoryListen(SignMenuFactory signMenuFactory) {
        listeners.put(signMenuFactory, new UpdateSignListener() {
            @Override
            public void listen(Player player, String[] array) {
                SignMenuFactory.Menu menu = signMenuFactory.getInputs().remove(player);

                if (menu == null) {
                    return;
                }
                setCancelled(true);

                boolean success = menu.getResponse().test(player, array);

                if (!success && menu.isReopenIfFail() && !menu.isForceClose()) {
                    EzChestShop.getScheduler().runTaskLater(() -> menu.open(player), 2L);
                }

                removeSignMenuFactoryListen(signMenuFactory);

                EzChestShop.getScheduler().runTaskLater(() -> {
                    if (player.isOnline()) {
                        Location location = menu.getLocation();
                        player.sendBlockChange(location, location.getBlock().getBlockData());
                    }
                }, 2L);
            }
        });
    }

    @Override
    public void removeSignMenuFactoryListen(SignMenuFactory signMenuFactory) {
        listeners.remove(signMenuFactory);
    }

    @Override
    public void openMenu(SignMenuFactory.Menu menu, Player player) {
        MenuOpener.openMenu(menu, player);
    }

    @Override
    public void injectConnection(Player player) {
        ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel.pipeline().addBefore("packet_handler", "ecs_listener", new ChannelHandler(player));
    }

    @Override
    public void ejectConnection(Player player) {
        Channel channel = ((CraftPlayer) player).getHandle().playerConnection.networkManager.channel;
        channel.eventLoop().submit(() -> channel.pipeline().remove("ecs_listener"));
    }

    @Override
    public void showOutline(Player player, Block block, int eID) {
        WorldServer worldServer = ((CraftWorld) block.getLocation().getWorld()).getHandle();
        CraftPlayer craftPlayer = (CraftPlayer) player;
        EntityPlayer entityPlayer = craftPlayer.getHandle();
        PlayerConnection playerConnection = entityPlayer.playerConnection;

        EntityShulker shulker = new EntityShulker(EntityTypes.SHULKER, worldServer);
        shulker.setInvisible(true); //invisible
        shulker.setNoGravity(true); //no gravity
        shulker.setMot(0, 0, 0); //set velocity
        shulker.e(eID); //set entity id
        shulker.i(true); //set outline
        shulker.setNoAI(true); //set noAI
        Location newLoc = block.getLocation().clone();
        //make location be center of the block vertically and horizontally
        newLoc.add(0.5, 0, 0.5);
        shulker.setPosition(newLoc.getX(), newLoc.getY(), newLoc.getZ()); //set position

        PacketPlayOutSpawnEntity spawnPacket = new PacketPlayOutSpawnEntity(shulker);
        playerConnection.sendPacket(spawnPacket);

        PacketPlayOutEntityMetadata metaPacket = new PacketPlayOutEntityMetadata(eID, shulker.getDataWatcher(), true);
        playerConnection.sendPacket(metaPacket);
    }

    public static Map<SignMenuFactory, UpdateSignListener> getListeners() {
        return listeners;
    }
}
