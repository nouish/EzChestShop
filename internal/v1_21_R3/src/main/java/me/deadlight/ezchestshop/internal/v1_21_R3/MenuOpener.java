package me.deadlight.ezchestshop.internal.v1_21_R3;

import me.deadlight.ezchestshop.utils.SignMenuFactory;
import me.deadlight.ezchestshop.utils.VersionUtil;
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

import java.util.List;

public class MenuOpener {
    private static final int MINECRAFT_1_21_5 = 4325;

    public static void openMenu(SignMenuFactory.Menu menu, Player player) {
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
        List<String> text = menu.getText();

        for (int i = 0; i < Math.min(text.size(), SignMenuFactory.SIGN_LINES); i++) {
            String rawLine = text.get(i);
            String coloredLine = menu.color(rawLine);
            // The internals changed in Minecraft 1.21.5
            StringTag nbtString = VersionUtil.getDataVersion().orElse(-1) == MINECRAFT_1_21_5
                    ? StringTag.valueOf(coloredLine)
                    : StringTag.valueOf(String.format(SignMenuFactory.NBT_FORMAT, coloredLine));

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
