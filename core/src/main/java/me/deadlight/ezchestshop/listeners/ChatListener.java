package me.deadlight.ezchestshop.listeners;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import io.papermc.paper.event.player.AsyncChatEvent;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.Constants;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.guis.SettingsGUI;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.objects.ChatWaitObject;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.block.Block;
import org.bukkit.block.TileState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.slf4j.Logger;

public class ChatListener implements Listener {
    private static final Logger LOGGER = EzChestShop.logger();
    private static final LanguageManager lm = LanguageManager.getInstance();
    public static final HashMap<UUID, ChatWaitObject> chatmap = new HashMap<>();

    @EventHandler
    public void onAsyncChat(AsyncChatEvent event) {
        Player player = event.getPlayer();
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (chatmap.containsKey(player.getUniqueId())) {
            //waiting for the answer
            event.setCancelled(true);
            ChatWaitObject waitObject = chatmap.get(player.getUniqueId());
            Block waitChest = waitObject.containerBlock;
            if (waitChest == null) return;
            String owneruuid = waitObject.dataContainer.get(Constants.OWNER_KEY, PersistentDataType.STRING);
            if (message.equalsIgnoreCase(player.getName())) {
                OfflinePlayer ofplayer = Bukkit.getOfflinePlayer(UUID.fromString(owneruuid));
                if (player.getName().equalsIgnoreCase(ofplayer.getName())) {
                    chatmap.remove(player.getUniqueId());
                    player.sendMessage(lm.selfAdmin());
                    return;
                }
            }

            String type = chatmap.get(player.getUniqueId()).type;
            Block chest = chatmap.get(player.getUniqueId()).containerBlock;
            chatmap.put(player.getUniqueId(), new ChatWaitObject(message, type, chest, waitObject.dataContainer));
            SettingsGUI guiInstance = new SettingsGUI();

            if (checkIfPlayerExists(message)) {
                if (type.equalsIgnoreCase("add")) {
                    chatmap.remove(player.getUniqueId());
                    EzChestShop.getScheduler().runTask(player, () -> {
                        addThePlayer(message, chest, player);
                        guiInstance.showGUI(player, chest, false);
                    });
                } else {
                    chatmap.remove(player.getUniqueId());
                    EzChestShop.getScheduler().runTask(player, () -> {
                        removeThePlayer(message, chest, player);
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
        OfflinePlayer target = Bukkit.getOfflinePlayer(answer);
        UUID answerUUID = target.getUniqueId();
        List<UUID> admins = Utils.getAdminsList(((TileState) chest.getState(false)).getPersistentDataContainer());
        if (!admins.contains(answerUUID)) {
            admins.add(answerUUID);
            String formattedName = Objects.requireNonNullElse(target.getName(), answer);
            LOGGER.info("{} made {} a shop admin at {}, {}, {}.",
                    player.getName(),
                    formattedName,
                    chest.getLocation().getBlockX(),
                    chest.getLocation().getBlockY(),
                    chest.getLocation().getBlockZ()
            );
            String adminsString = convertListUUIDtoString(admins);
            TileState state = ((TileState) chest.getState(false));
            PersistentDataContainer data = state.getPersistentDataContainer();
            data.set(Constants.ADMIN_LIST_KEY, PersistentDataType.STRING, adminsString);
            state.update();
            ShopContainer.getShopSettings(chest.getLocation()).setAdmins(adminsString);
            player.sendMessage(lm.sucAdminAdded(formattedName));
        } else {
            player.sendMessage(lm.alreadyAdmin());
        }
    }

    public void removeThePlayer(String answer, Block chest, Player player) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(answer);
        UUID answerUUID = target.getUniqueId();
        List<UUID> admins = Utils.getAdminsList(((TileState) chest.getState(false)).getPersistentDataContainer());
        if (admins.contains(answerUUID)) {
            TileState state = ((TileState) chest.getState(false));
            admins.remove(answerUUID);
            String formattedName = Objects.requireNonNullElse(target.getName(), answer);
            LOGGER.info("{} removed {} as shop admin at {}, {}, {}.",
                    player.getName(),
                    formattedName,
                    chest.getLocation().getBlockX(),
                    chest.getLocation().getBlockY(),
                    chest.getLocation().getBlockZ()
            );
            if (admins.isEmpty()) {
                PersistentDataContainer data = state.getPersistentDataContainer();
                data.set(Constants.ADMIN_LIST_KEY, PersistentDataType.STRING, "none");
                state.update();
                player.sendMessage(lm.sucAdminRemoved(formattedName));
                return;
            }
            String adminsString = convertListUUIDtoString(admins);
            PersistentDataContainer data = state.getPersistentDataContainer();
            data.set(Constants.ADMIN_LIST_KEY, PersistentDataType.STRING, adminsString);
            state.update();
            ShopContainer.getShopSettings(chest.getLocation()).setAdmins(adminsString);
            player.sendMessage(lm.sucAdminRemoved(formattedName));
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
