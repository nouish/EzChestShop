package me.deadlight.ezchestshop.internal.v1_20_R4;

import java.lang.reflect.Field;
import java.util.Map;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.utils.BlockOutline;
import me.deadlight.ezchestshop.utils.SignMenuFactory;
import me.deadlight.ezchestshop.utils.UpdateSignListener;
import me.deadlight.ezchestshop.utils.Utils;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundSignUpdatePacket;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class ChannelHandler extends ChannelInboundHandlerAdapter {
    private final Player player;
    private static Field updateSignArrays;

    static {
        try {
            updateSignArrays = ServerboundSignUpdatePacket.class.getDeclaredField("d");
            updateSignArrays.setAccessible(true);
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
    }

    public ChannelHandler(Player player) {
        this.player = player;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws IllegalAccessException {
        if (msg instanceof ServerboundInteractPacket) {

            if (!Utils.enabledOutlines.contains(player.getUniqueId())) {
                ctx.fireChannelRead(msg);
                return;
            }

            //now we check if player is right clicking on the outline shulkerbox, if so we open the shop for them
            ServerboundInteractPacket packet = (ServerboundInteractPacket) msg;
            Field field;
            try {
                field = packet.getClass().getDeclaredField("b"); //The field a is entity ID
            } catch (NoSuchFieldException e) {
                ctx.fireChannelRead(msg);
                return; //This is for ModelEngine
            }

            field.setAccessible(true);
            int entityID = (int) field.get(packet);
            if (Utils.activeOutlines.containsKey(entityID)) {
                BlockOutline outline = Utils.activeOutlines.get(entityID);
                outline.hideOutline();
                //Then it means somebody is clicking on the outline shulkerbox
                EzChestShop.getScheduler().runTaskLater(outline.block.getLocation(), () -> Bukkit.getPluginManager().callEvent(
                        new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, player.getItemInUse(), outline.block, outline.block.getFace(outline.block), null)), 1);
            }
        }

        if (msg instanceof ServerboundSignUpdatePacket) {
            for (Map.Entry<SignMenuFactory, UpdateSignListener> entry : NmsHandleImpl.getListeners().entrySet()) {
                UpdateSignListener listener = entry.getValue();

                try {
                    listener.listen(player, (String[]) updateSignArrays.get(msg));
                } catch (IllegalAccessException e) {
                    e.printStackTrace();
                }

                if (listener.isCancelled()) {
                    ctx.fireChannelRead(msg);
                    return;
                }
            }
        }

        ctx.fireChannelRead(msg);
    }
}
