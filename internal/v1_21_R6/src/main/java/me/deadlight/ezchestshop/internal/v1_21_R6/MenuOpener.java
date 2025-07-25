package me.deadlight.ezchestshop.internal.v1_21_R6;

import java.util.Objects;

import me.deadlight.ezchestshop.utils.SignMenuFactory;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundOpenSignEditorPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;

public class MenuOpener {

    public static void openMenu(SignMenuFactory.Menu menu, Player player) {
        Objects.requireNonNull(player, "player");
        if (!player.isOnline()) {
            return;
        }

        Location location = player.getLocation();
        Location newLocation = location.clone().add(0, 4, 0);

        menu.setLocation(newLocation);

        BlockPos position = new BlockPos(newLocation.getBlockX(), newLocation.getBlockY(), newLocation.getBlockZ());

        player.sendBlockChange(newLocation, Material.OAK_SIGN.createBlockData());

        ClientboundOpenSignEditorPacket editorPacket = new ClientboundOpenSignEditorPacket(position, true);
        CompoundTag compound = new CompoundTag();
        CompoundTag frontText = new CompoundTag();
        CompoundTag backText = new CompoundTag();
        ListTag backMessages = new ListTag();
        ListTag frontMessages = new ListTag();


        for (int line = 0; line < SignMenuFactory.SIGN_LINES; line++) {
            String text = menu.getText().size() > line ? String.format(SignMenuFactory.NBT_FORMAT, menu.color(menu.getText().get(line))) : "";
            StringTag nbtString = StringTag.valueOf(text);

            // Assuming you want to set the same text for both back and front
            backMessages.add(nbtString);
            frontMessages.add(nbtString);
        }

        backText.put("messages", backMessages);
        frontText.put("messages", frontMessages);
        compound.put("back_text", backText);
        compound.put("front_text", frontText);

        ClientboundBlockEntityDataPacket tileEntityDataPacket = new ClientboundBlockEntityDataPacket(position, BlockEntityType.SIGN, compound);
        ServerGamePacketListenerImpl connection = ((CraftPlayer) player).getHandle().connection;

        connection.send(tileEntityDataPacket);
        connection.send(editorPacket);

        menu.getFactory().getInputs().put(player, menu);
    }
}
