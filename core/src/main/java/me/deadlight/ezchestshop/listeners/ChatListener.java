package me.deadlight.ezchestshop.listeners;

import java.util.HashMap;
import java.util.List;
import java.util.UUID;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.EzChestShopConstants;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.guis.SettingsGUI;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.objects.ChatWaitObject;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public class ChatListener implements Listener {

    public static HashMap<UUID, ChatWaitObject> chatmap = new HashMap<>();
    public static LanguageManager lm = new LanguageManager();

    public static void updateLM(LanguageManager languageManager) {
        ChatListener.lm = languageManager;
    }

    @EventHandler
    public void onAsyncChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (chatmap.containsKey(player.getUniqueId())) {
            //waiting for the answer
            event.setCancelled(true);
            ChatWaitObject waitObject = chatmap.get(player.getUniqueId());
            Block waitChest = waitObject.containerBlock;
            if (waitChest == null) return;
            String owneruuid = waitObject.dataContainer.get(EzChestShopConstants.OWNER_KEY, PersistentDataType.STRING);
            if (event.getMessage().equalsIgnoreCase(player.getName())) {
                OfflinePlayer ofplayer = Bukkit.getOfflinePlayer(UUID.fromString(owneruuid));
                if (player.getName().equalsIgnoreCase(ofplayer.getName())) {
                    chatmap.remove(player.getUniqueId());
                    player.sendMessage(lm.selfAdmin());
                    return;
                }
            }

            String type = chatmap.get(player.getUniqueId()).type;
            Block chest = chatmap.get(player.getUniqueId()).containerBlock;
            chatmap.put(player.getUniqueId(), new ChatWaitObject(event.getMessage(), type, chest, waitObject.dataContainer));
            SettingsGUI guiInstance = new SettingsGUI();

            if (checkIfPlayerExists(event.getMessage())) {
                if (type.equalsIgnoreCase("add")) {
                    chatmap.remove(player.getUniqueId());
                    EzChestShop.getScheduler().runTask(() -> {
                        addThePlayer(event.getMessage(), chest, player);
                        guiInstance.showGUI(player, chest, false);
                    });
                } else {
                    chatmap.remove(player.getUniqueId());
                    EzChestShop.getScheduler().runTask(() -> {
                        removeThePlayer(event.getMessage(), chest, player);
                        guiInstance.showGUI(player, chest, false);
                    });
                }
            } else {
                player.sendMessage(lm.noPlayer());
                chatmap.remove(player.getUniqueId());
            }
        }
    }


    // We are taking user input here, and are checking if the player played before.
    @SuppressWarnings("deprecation")
    public boolean checkIfPlayerExists(String name) {
        Player player = Bukkit.getPlayer(name);

        if (player != null) {
            if (player.isOnline()) {
                return true;
            } else {
                OfflinePlayer thaPlayer = Bukkit.getOfflinePlayer(name);
                return thaPlayer.hasPlayedBefore();
            }
        } else {
            OfflinePlayer thaPlayer = Bukkit.getOfflinePlayer(name);
            return thaPlayer.hasPlayedBefore();
        }
    }

    public void addThePlayer(String answer, Block chest, Player player) {
        UUID answerUUID = Bukkit.getOfflinePlayer(answer).getUniqueId();
        List<UUID> admins = Utils.getAdminsList(((TileState)chest.getState()).getPersistentDataContainer());
        if (!admins.contains(answerUUID)) {
            admins.add(answerUUID);
            String adminsString = convertListUUIDtoString(admins);
            TileState state = ((TileState)chest.getState());
            PersistentDataContainer data = state.getPersistentDataContainer();
            data.set(EzChestShopConstants.ADMIN_LIST_KEY, PersistentDataType.STRING, adminsString);
            state.update();
            ShopContainer.getShopSettings(chest.getLocation()).setAdmins(adminsString);
            player.sendMessage(lm.sucAdminAdded(answer));
        } else {
            player.sendMessage(lm.alreadyAdmin());
        }
    }


    public void removeThePlayer(String answer, Block chest, Player player) {
        UUID answerUUID = Bukkit.getOfflinePlayer(answer).getUniqueId();
        List<UUID> admins = Utils.getAdminsList(((TileState)chest.getState()).getPersistentDataContainer());
        if (admins.contains(answerUUID)) {
            TileState state = ((TileState)chest.getState());
            admins.remove(answerUUID);
            if (admins.isEmpty()) {
                PersistentDataContainer data = state.getPersistentDataContainer();
                data.set(EzChestShopConstants.ADMIN_LIST_KEY, PersistentDataType.STRING, "none");
                state.update();
                player.sendMessage(lm.sucAdminRemoved(answer));
                return;
            }
            String adminsString = convertListUUIDtoString(admins);
            PersistentDataContainer data = state.getPersistentDataContainer();
            data.set(EzChestShopConstants.ADMIN_LIST_KEY, PersistentDataType.STRING, adminsString);
            state.update();
            ShopContainer.getShopSettings(chest.getLocation()).setAdmins(adminsString);
            player.sendMessage(lm.sucAdminRemoved(answer));
        } else {
            player.sendMessage(lm.notInAdminList());
        }
    }

    public String convertListUUIDtoString(List<UUID> uuidList) {
        StringBuilder finalString = new StringBuilder();
        boolean first = false;
        if (uuidList.isEmpty()) {
            return "none";
        }
        for (UUID uuid : uuidList) {
            if (first) {
                finalString.append("@").append(uuid.toString());
            } else {
                first = true;
                finalString = new StringBuilder(uuid.toString());
            }
        }
        //if there is no admins, then set the string to none
        if (finalString.toString().equalsIgnoreCase("")) {
            finalString = new StringBuilder("none");
        }
        return finalString.toString();
    }

}
