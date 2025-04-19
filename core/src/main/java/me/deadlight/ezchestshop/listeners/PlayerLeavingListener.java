package me.deadlight.ezchestshop.listeners;

import me.deadlight.ezchestshop.utils.Utils;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerLeavingListener implements Listener {

    @EventHandler
    public void onLeave(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        Utils.nmsHandle.ejectConnection(player);
        ChatListener.chatmap.remove(player.getUniqueId());
        Utils.enabledOutlines.remove(player.getUniqueId());
    }

}
