package me.deadlight.ezchestshop.internal.v1_18_R2;

import java.util.HashMap;
import java.util.Map;

import io.netty.channel.Channel;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.utils.ImprovedOfflinePlayer;
import me.deadlight.ezchestshop.utils.NmsHandle;
import me.deadlight.ezchestshop.utils.SignMenuFactory;
import me.deadlight.ezchestshop.utils.UpdateSignListener;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.protocol.game.PacketPlayOutEntityMetadata;
import net.minecraft.network.protocol.game.PacketPlayOutEntityTeleport;
import net.minecraft.network.protocol.game.PacketPlayOutEntityVelocity;
import net.minecraft.network.protocol.game.PacketPlayOutSpawnEntity;
import net.minecraft.server.level.EntityPlayer;
import net.minecraft.server.level.WorldServer;
import net.minecraft.server.network.PlayerConnection;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.decoration.EntityArmorStand;
import net.minecraft.world.entity.item.EntityItem;
import net.minecraft.world.entity.monster.EntityShulker;
import net.minecraft.world.level.World;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_18_R2.CraftWorld;
import org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_18_R2.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_18_R2.util.CraftChatMessage;
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
        return CraftItemStack.asNMSCopy(itemStack).b(new NBTTagCompound()).toString();
    }

    @Override
    public void destroyEntity(Player player, int entityID) {
        ((org.bukkit.craftbukkit.v1_18_R2.entity.CraftPlayer) player).getHandle().b.a(new net.minecraft.network.protocol.game.PacketPlayOutEntityDestroy(entityID));
        entities.remove(entityID);
    }

    @Override
    public void spawnHologram(Player player, Location location, String line, int ID) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        EntityPlayer entityPlayer = craftPlayer.getHandle();
        PlayerConnection playerConnection = entityPlayer.b;
        CraftWorld craftWorld = (CraftWorld) location.getWorld();
        World world = craftWorld.getHandle();
        //------------------------------------------------------

        EntityArmorStand armorstand = new EntityArmorStand(world, location.getX(), location.getY(), location.getZ());
        armorstand.j(true); //invisible
        armorstand.t(true); //Marker
        armorstand.a(CraftChatMessage.fromStringOrNull(line)); //set custom name
        armorstand.n(true); //make custom name visible
        armorstand.e(true); //no gravity
        armorstand.e(ID); //set entity id

        PacketPlayOutSpawnEntity packetPlayOutSpawnEntity = new PacketPlayOutSpawnEntity(armorstand, 0);
        playerConnection.a(packetPlayOutSpawnEntity);
        //------------------------------------------------------
        // sending meta packet
        PacketPlayOutEntityMetadata metaPacket = new PacketPlayOutEntityMetadata(ID, armorstand.ai(), true);
        playerConnection.a(metaPacket);
        entities.put(ID, armorstand);
    }

    @Override
    public void spawnFloatingItem(Player player, Location location, ItemStack itemStack, int ID) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        EntityPlayer entityPlayer = craftPlayer.getHandle();
        PlayerConnection playerConnection = entityPlayer.b;
        CraftWorld craftWorld = (CraftWorld) location.getWorld();
        World world = craftWorld.getHandle();
        //------------------------------------------------------

        EntityItem floatingItem = new EntityItem(world, location.getX(), location.getY(), location.getZ(), CraftItemStack.asNMSCopy(itemStack));
        floatingItem.e(true); //no gravity
        floatingItem.e(ID); //set entity id
        floatingItem.n(0, 0, 0); //set velocity

        PacketPlayOutSpawnEntity packetPlayOutSpawnEntity = new PacketPlayOutSpawnEntity(floatingItem, 0);
        playerConnection.a(packetPlayOutSpawnEntity);
        //------------------------------------------------------
        // sending meta packet
        PacketPlayOutEntityMetadata metaPacket = new PacketPlayOutEntityMetadata(ID, floatingItem.ai(), true);
        playerConnection.a(metaPacket);

        //sending a velocity packet
        floatingItem.n(0, 0, 0);
        PacketPlayOutEntityVelocity velocityPacket = new PacketPlayOutEntityVelocity(floatingItem);
        playerConnection.a(velocityPacket);
        entities.put(ID, floatingItem);
    }

    @Override
    public void renameEntity(Player player, int entityID, String newName) {
        Entity e = entities.get(entityID);
        e.a(CraftChatMessage.fromStringOrNull(newName));
        PacketPlayOutEntityMetadata packet = new PacketPlayOutEntityMetadata(entityID, e.ai(), true);
        ((CraftPlayer) player).getHandle().b.a(packet);
    }

    @Override
    public void teleportEntity(Player player, int entityID, Location location) {
        EntityPlayer entityPlayer = ((CraftPlayer) player).getHandle();
        Entity e = entities.get(entityID);
        e.b(location.getX(), location.getY(), location.getZ());
        // not sure if it's needed
        PacketPlayOutEntityTeleport packet = new PacketPlayOutEntityTeleport(e);
        entityPlayer.b.a(packet);
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
        ((CraftPlayer) player).getHandle().b.a.m.pipeline().addBefore("packet_handler", "ecs_listener", new ChannelHandler(player));
    }

    @Override
    public void ejectConnection(Player player) {
        Channel channel = ((CraftPlayer) player).getHandle().b.a.m;
        channel.eventLoop().submit(() -> channel.pipeline().remove("ecs_listener"));
    }

    @Override
    public void showOutline(Player player, Block block, int eID) {
        WorldServer worldServer = ((CraftWorld) block.getLocation().getWorld()).getHandle();
        CraftPlayer craftPlayer = (CraftPlayer) player;
        EntityPlayer entityPlayer = craftPlayer.getHandle();
        PlayerConnection playerConnection = entityPlayer.b;

        EntityShulker shulker = new EntityShulker(EntityTypes.ay, worldServer);
        shulker.e(true); //no gravity
        shulker.n(0, 0, 0); //set velocity
        shulker.e(eID); //set entity id

        shulker.j(true); //invisible
        shulker.i(true); //set glow
        shulker.s(true); //set noAI
        Location newLoc = block.getLocation().clone();
        //make location be center of the block vertically and horizontally
        newLoc.add(0.5, 0, 0.5);
        shulker.e(newLoc.getX(), newLoc.getY(), newLoc.getZ()); //set position

        PacketPlayOutSpawnEntity spawnPacket = new PacketPlayOutSpawnEntity(shulker);
        playerConnection.a(spawnPacket);

        PacketPlayOutEntityMetadata metaPacket = new PacketPlayOutEntityMetadata(eID, shulker.ai(), true);
        playerConnection.a(metaPacket);
    }

    public static Map<SignMenuFactory, UpdateSignListener> getListeners() {
        return listeners;
    }
}
