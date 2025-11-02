package me.deadlight.ezchestshop.internal.v1_21_R1;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import io.netty.channel.Channel;
import io.netty.channel.ChannelPipeline;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.utils.NmsHandle;
import me.deadlight.ezchestshop.utils.SignMenuFactory;
import me.deadlight.ezchestshop.utils.UpdateSignListener;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Shulker;
import net.minecraft.world.level.Level;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.craftbukkit.CraftWorld;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.craftbukkit.inventory.CraftItemStack;
import org.bukkit.craftbukkit.util.CraftChatMessage;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.NullMarked;
import org.slf4j.Logger;

@NullMarked
public final class NmsHandleImpl implements NmsHandle {
    private static final Logger LOGGER = EzChestShop.logger();
    private static final Map<SignMenuFactory, UpdateSignListener> listeners = new HashMap<>();
    private static final Map<Integer, Entity> entities = new HashMap<>();

    @Override
    public void destroyEntity(Player player, int entityId) {
        ((CraftPlayer) player).getHandle().connection.send(new ClientboundRemoveEntitiesPacket(entityId));
        entities.remove(entityId);
    }

    @Override
    public void spawnHologram(Player player, Location location, String line, int id) {
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
        armorstand.setId(id); //set entity id

        ClientboundAddEntityPacket ClientboundAddEntityPacket = new ClientboundAddEntityPacket(
                armorstand.getId(), armorstand.getUUID(), armorstand.getX(), armorstand.getY(), armorstand.getZ(), armorstand.getXRot(), armorstand.getYRot(), armorstand.getType(), 0, armorstand.getDeltaMovement(), armorstand.getYHeadRot());
        ServerGamePacketListenerImpl.send(ClientboundAddEntityPacket);
        //------------------------------------------------------
        //create a list of datawatcher objects

        ClientboundSetEntityDataPacket metaPacket = new ClientboundSetEntityDataPacket(id, armorstand.getEntityData().getNonDefaultValues());
        ServerGamePacketListenerImpl.send(metaPacket);
        entities.put(id, armorstand);
    }

    @Override
    public void spawnFloatingItem(Player player, Location location, ItemStack itemStack, int id) {
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ServerPlayer ServerPlayer = craftPlayer.getHandle();
        ServerGamePacketListenerImpl ServerGamePacketListenerImpl = ServerPlayer.connection;
        CraftWorld craftWorld = (CraftWorld) location.getWorld();
        Level world = craftWorld.getHandle();
        //------------------------------------------------------

        ItemEntity floatingItem = new ItemEntity(world, location.getX(), location.getY(), location.getZ(), CraftItemStack.asNMSCopy(itemStack));
        floatingItem.makeFakeItem(); //no merge with other items
        floatingItem.setNoGravity(true); //no gravity
        floatingItem.setId(id); //set entity id
        floatingItem.setDeltaMovement(0, 0, 0); //set velocity

        ClientboundAddEntityPacket ClientboundAddEntityPacket = new ClientboundAddEntityPacket(
                floatingItem.getId(), floatingItem.getUUID(), floatingItem.getX(), floatingItem.getY(), floatingItem.getZ(), floatingItem.getXRot(), floatingItem.getYRot(), floatingItem.getType(), 0, floatingItem.getDeltaMovement(), floatingItem.getYHeadRot());
        ServerGamePacketListenerImpl.send(ClientboundAddEntityPacket);
        //------------------------------------------------------
        // sending meta packet
        ClientboundSetEntityDataPacket metaPacket = new ClientboundSetEntityDataPacket(id, floatingItem.getEntityData().getNonDefaultValues());
        ServerGamePacketListenerImpl.send(metaPacket);

        //sending a velocity packet
        floatingItem.setDeltaMovement(0, 0, 0);
        ClientboundSetEntityMotionPacket velocityPacket = new ClientboundSetEntityMotionPacket(floatingItem);
        ServerGamePacketListenerImpl.send(velocityPacket);
        entities.put(id, floatingItem);
    }

    @Override
    public void renameEntity(Player player, int entityId, String newName) {
        try {
            // the entity only exists on the client, how can I get it?
            Entity e = entities.get(entityId);
            e.setCustomName(CraftChatMessage.fromStringOrNull(newName));
            ClientboundSetEntityDataPacket packet = new ClientboundSetEntityDataPacket(entityId, e.getEntityData().getNonDefaultValues());
            ((CraftPlayer) player).getHandle().connection.send(packet);
        } catch (Exception e) {
            LOGGER.warn("Unable to rename entity", e);
        }
    }

    @Override
    public void teleportEntity(Player player, int entityId, Location location) {
        ServerPlayer ServerPlayer = ((CraftPlayer) player).getHandle();
        Entity e = entities.get(entityId);
        Set<RelativeMovement> set = new HashSet<>();
        e.teleportTo(((CraftWorld) location.getWorld()).getHandle(), location.getX(), location.getY(), location.getZ(), set, 0, 0);
        // not sure if it's needed
        ClientboundTeleportEntityPacket packet = new ClientboundTeleportEntityPacket(e);
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

    @Override
    public void injectConnection(Player player) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        ChannelPipeline pipeline = nmsPlayer.connection.connection.channel.pipeline();
        pipeline.addBefore("packet_handler", "ecs_listener", new ChannelHandler(player));
    }

    @Override
    public void ejectConnection(Player player) {
        ServerPlayer nmsPlayer = ((CraftPlayer) player).getHandle();
        Channel channel = nmsPlayer.connection.connection.channel;
        channel.eventLoop().submit(() -> channel.pipeline().remove("ecs_listener"));
    }

    @Override
    public void showOutline(Player player, Block block, int entityId) {
        ServerLevel ServerLevel = ((CraftWorld) block.getLocation().getWorld()).getHandle();
        CraftPlayer craftPlayer = (CraftPlayer) player;
        ServerPlayer ServerPlayer = craftPlayer.getHandle();
        ServerGamePacketListenerImpl ServerGamePacketListenerImpl = ServerPlayer.connection;

        Shulker shulker = new Shulker(EntityType.SHULKER, ServerLevel);
        shulker.setInvisible(true); //invisible
        shulker.setNoGravity(true); //no gravity
        shulker.setDeltaMovement(0, 0, 0); //set velocity
        shulker.setId(entityId); //set entity id
        shulker.setGlowingTag(true); //set outline
        shulker.setNoAi(true); //set noAI
        Location newLoc = block.getLocation().clone();
        //make location be center of the block vertically and horizontally
        newLoc.add(0.5, 0, 0.5);
        shulker.setPos(newLoc.getX(), newLoc.getY(), newLoc.getZ()); //set position

        ClientboundAddEntityPacket spawnPacket = new ClientboundAddEntityPacket(
                shulker.getId(), shulker.getUUID(), shulker.getX(), shulker.getY(), shulker.getZ(), shulker.getXRot(), shulker.getYRot(), shulker.getType(), 0, shulker.getDeltaMovement(), shulker.getYHeadRot());
        ServerGamePacketListenerImpl.send(spawnPacket);

        ClientboundSetEntityDataPacket metaPacket = new ClientboundSetEntityDataPacket(entityId, shulker.getEntityData().getNonDefaultValues());
        ServerGamePacketListenerImpl.send(metaPacket);
    }

    public static Map<SignMenuFactory, UpdateSignListener> getListeners() {
        return listeners;
    }
}
