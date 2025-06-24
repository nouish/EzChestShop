package me.deadlight.ezchestshop.listeners;

import me.deadlight.ezchestshop.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PlayerLeavingListener implements Listener {
    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerQuit(@NotNull PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Utils.nmsHandle.ejectConnection(player);
        ChatListener.chatmap.remove(player.getUniqueId());
        Utils.enabledOutlines.remove(player.getUniqueId());
    }
}
