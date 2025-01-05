package me.deadlight.ezchestshop.internal.v1_21_R3;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import io.netty.channel.Channel;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.utils.ImprovedOfflinePlayer;
import me.deadlight.ezchestshop.utils.NmsHandle;
import me.deadlight.ezchestshop.utils.SignMenuFactory;
import me.deadlight.ezchestshop.utils.UpdateSignListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.v1_21_R3.CraftWorld;
import org.bukkit.craftbukkit.v1_21_R3.entity.CraftPlayer;
import org.bukkit.craftbukkit.v1_21_R3.inventory.CraftItemStack;
import org.bukkit.craftbukkit.v1_21_R3.util.CraftChatMessage;
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
    public void destroyEntity(Player player, int entityID) {
        ((CraftPlayer) player).getHandle().connection.send(new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(entityID));
        entities.remove(entityID);
    }

    @Override
    public void spawnHologram(Player player, Location location, String line, int ID) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ServerPlayer ServerPlayer = craftPlayer.getHandle();
        ServerGamePacketListenerImpl ServerGamePacketListenerImpl = ServerPlayer.connection;
        CraftWorld craftWorld = (CraftWorld) location.getWorld();
        Level world = craftWorld.getHandle();
        //------------------------------------------------------

        ArmorStand armorstand = new ArmorStand(world, location.getX(), location.getY(), location.getZ());
        armorstand.setInvisible(true); //invisible
        armorstand.setMarker(true); //Marker
        armorstand.setCustomName(CraftChatMessage.fromStringOrNull(line)); //set custom name
        armorstand.setCustomNameVisible(true); //make custom name visible
        armorstand.setNoGravity(true); //no gravity
        armorstand.setId(ID); //set entity id

        ClientboundAddEntityPacket ClientboundAddEntityPacket = new ClientboundAddEntityPacket(
                armorstand.getId(), armorstand.getUUID(), armorstand.getX(), armorstand.getY(), armorstand.getZ(), armorstand.getXRot(), armorstand.getYRot(), armorstand.getType(), 0, armorstand.getDeltaMovement(), armorstand.getYHeadRot());
        ServerGamePacketListenerImpl.send(ClientboundAddEntityPacket);
        //------------------------------------------------------
        //create a list of datawatcher objects

        ClientboundSetEntityDataPacket metaPacket = new ClientboundSetEntityDataPacket(ID, armorstand.getEntityData().getNonDefaultValues());
        ServerGamePacketListenerImpl.send(metaPacket);
        entities.put(ID, armorstand);
    }

    @Override
    public void spawnFloatingItem(Player player, Location location, ItemStack itemStack, int ID) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ServerPlayer ServerPlayer = craftPlayer.getHandle();
        ServerGamePacketListenerImpl ServerGamePacketListenerImpl = ServerPlayer.connection;
        CraftWorld craftWorld = (CraftWorld) location.getWorld();
        Level world = craftWorld.getHandle();
        //------------------------------------------------------

        ItemEntity floatingItem = new ItemEntity(world, location.getX(), location.getY(), location.getZ(), CraftItemStack.asNMSCopy(itemStack));
        floatingItem.setNoGravity(true); //no gravity
        floatingItem.setId(ID); //set entity id
        floatingItem.setDeltaMovement(0, 0, 0); //set velocity

        ClientboundAddEntityPacket ClientboundAddEntityPacket = new ClientboundAddEntityPacket(
                floatingItem.getId(), floatingItem.getUUID(), floatingItem.getX(), floatingItem.getY(), floatingItem.getZ(), floatingItem.getXRot(), floatingItem.getYRot(), floatingItem.getType(), 0, floatingItem.getDeltaMovement(), floatingItem.getYHeadRot());
        ServerGamePacketListenerImpl.send(ClientboundAddEntityPacket);
        //------------------------------------------------------
        // sending meta packet
        ClientboundSetEntityDataPacket metaPacket = new ClientboundSetEntityDataPacket(ID, floatingItem.getEntityData().getNonDefaultValues());
        ServerGamePacketListenerImpl.send(metaPacket);

        //sending a velocity packet
        floatingItem.setDeltaMovement(0, 0, 0);
        ClientboundSetEntityMotionPacket velocityPacket = new ClientboundSetEntityMotionPacket(floatingItem);
        ServerGamePacketListenerImpl.send(velocityPacket);
        entities.put(ID, floatingItem);
    }

    @Override
    public void renameEntity(Player player, int entityID, String newName) {
        try {
            // the entity only exists on the client, how can I get it?
            Entity e = entities.get(entityID);
            e.setCustomName(CraftChatMessage.fromStringOrNull(newName));
            ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(entityID, e.getEntityData().getNonDefaultValues());
            ((CraftPlayer) player).getHandle().connection.send(packet);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void teleportEntity(Player player, int entityID, Location location) {
        ServerPlayer ServerPlayer = ((CraftPlayer) player).getHandle();
        Entity e = entities.get(entityID);
        e.teleportTo(ServerPlayer.serverLevel(), location.getX(), location.getY(), location.getZ(), new HashSet<>(), 0, 0, false);
        // not sure if it's needed
        ClientboundTeleportEntityPacket packet = new ClientboundTeleportEntityPacket(e.getId(), PositionMoveRotation.of(e), Set.of(), e.onGround());
        ServerPlayer.connection.send(packet);
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

    private Connection getConnection(Player player) {
        Objects.requireNonNull(player, "player");

        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        Class<?> cls = nmsPlayer.connection.getClass().getSuperclass();
        Field field;

        try {
            field = cls.getDeclaredField("e");
        } catch (NoSuchFieldException e1) {
            try {
                field = cls.getDeclaredField("connection");
            } catch (NoSuchFieldException e2) {
                e2.addSuppressed(e1);
                EzChestShop.logger().error("Unable to locate connection field of {}", cls.getName(), e2);
                throw new AssertionError("Unable to inject connection!");
            }
        }

        field.setAccessible(true);

        try {
            return (Connection) field.get(nmsPlayer.connection);
        } catch (IllegalAccessException e) {
            throw new AssertionError("Unable to get Connection from ServerGamePacketListenerImpl", e);
        }
    }

    @Override
    public void injectConnection(Player player) {
        getConnection(player).channel.pipeline().addBefore("packet_handler", "ecs_listener", new ChannelHandler(player));
    }

    @Override
    public void ejectConnection(Player player) {
        Channel channel = getConnection(player).channel;
        channel.eventLoop().submit(() -> channel.pipeline().remove("ecs_listener"));
    }

    @Override
    public void showOutline(Player player, Block block, int eID) {
        ServerLevel ServerLevel = ((CraftWorld) block.getLocation().getWorld()).getHandle();
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ServerPlayer ServerPlayer = craftPlayer.getHandle();
        ServerGamePacketListenerImpl ServerGamePacketListenerImpl = ServerPlayer.connection;

        Shulker shulker = new Shulker(EntityType.SHULKER, ServerLevel);
        shulker.setInvisible(true); //invisible
        shulker.setNoGravity(true); //no gravity
        shulker.setDeltaMovement(0, 0, 0); //set velocity
        shulker.setId(eID); //set entity id
        shulker.setGlowingTag(true); //set outline
        shulker.setNoAi(true); //set noAI
        Location newLoc = block.getLocation().clone();
        //make location be center of the block vertically and horizontally
        newLoc.add(0.5, 0, 0.5);
        shulker.setPos(newLoc.getX(), newLoc.getY(), newLoc.getZ()); //set position

        ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(
                shulker.getId(), shulker.getUUID(), shulker.getX(), shulker.getY(), shulker.getZ(), shulker.getXRot(), shulker.getYRot(), shulker.getType(), 0, shulker.getDeltaMovement(), shulker.getYHeadRot());
        ServerGamePacketListenerImpl.send(spawnPacket);

        ClientboundSetEntityDataPacket metaPacket = new ClientboundSetEntityDataPacket(eID, shulker.getEntityData().getNonDefaultValues());
        ServerGamePacketListenerImpl.send(metaPacket);
    }

    public static Map<SignMenuFactory, UpdateSignListener> getListeners() {
        return listeners;
    }
}
