package me.deadlight.ezchestshop.internal.v1_21_R6;

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

    public ChannelHandler(Player player) {
        this.player = player;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        if (msg instanceof ServerboundInteractPacket packet) {
            if (!Utils.enabledOutlines.contains(player.getUniqueId())) {
                ctx.fireChannelRead(msg);
                return;
            }

            int entityId = packet.getEntityId();
            if (Utils.activeOutlines.containsKey(entityId)) {
                BlockOutline outline = Utils.activeOutlines.get(entityId);
                outline.hideOutline();
                //Then it means somebody is clicking on the outline shulkerbox
                EzChestShop.getScheduler().runTaskLater(outline.block.getLocation(), () -> Bukkit.getPluginManager().callEvent(
                        new PlayerInteractEvent(player, Action.RIGHT_CLICK_BLOCK, player.getActiveItem(), outline.block, outline.block.getFace(outline.block), null)), 1);
            }
        }

        if (msg instanceof ServerboundSignUpdatePacket packet) {
            for (Map.Entry<SignMenuFactory, UpdateSignListener> entry : NmsHandleImpl.getListeners().entrySet()) {
                UpdateSignListener listener = entry.getValue();
                listener.listen(player, packet.getLines());

                if (listener.isCancelled()) {
                    ctx.fireChannelRead(msg);
                    return;
                }
            }
        }

        ctx.fireChannelRead(msg);
    }

}
