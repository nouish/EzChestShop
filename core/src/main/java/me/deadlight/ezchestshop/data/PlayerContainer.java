package me.deadlight.ezchestshop.data;


import java.util.HashMap;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.utils.objects.CheckProfitEntry;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public final class PlayerContainer {
    private static final Cache<UUID, PlayerContainer> cache = CacheBuilder.newBuilder()
            .maximumSize(16)
            .expireAfterAccess(30, TimeUnit.MINUTES)
            .build();

    private final UUID playerId;

    private HashMap<String, CheckProfitEntry> checkProfits = null;

    private PlayerContainer(@NotNull UUID playerId) {
        this.playerId = Objects.requireNonNull(playerId);
    }

    public static PlayerContainer get(OfflinePlayer offlinePlayer) {
        UUID playerId = offlinePlayer.getUniqueId();
        PlayerContainer result = cache.getIfPresent(playerId);

        if (result == null) {
            result = new PlayerContainer(playerId);
            cache.put(playerId, result);
        }

        return result;
    }

    /*
         ▄▀▀ █▄█ ▄▀▄ █▀▄
         ▄██ █ █ ▀▄▀ █▀
     */
    // ShopProfits
    public HashMap<String, CheckProfitEntry> getProfits() {
        if (checkProfits == null) {
            checkProfits = new HashMap<>();
            DatabaseManager db = EzChestShop.getPlugin().getDatabase();
            String checkProfitsList = db.getString("uuid", playerId.toString(), "checkprofits", "playerdata");
            if (checkProfitsList == null || checkProfitsList.equalsIgnoreCase("") || checkProfitsList.equalsIgnoreCase("NULL")) {
                checkProfits = new HashMap<>();
                return checkProfits;
            }
            for (String entry : checkProfitsList.split(CheckProfitEntry.itemSpacer)) {
                CheckProfitEntry profEntry = new CheckProfitEntry(entry);
                checkProfits.put(profEntry.getId(), profEntry);
            }
            return checkProfits;
        } else {
            return checkProfits;
        }
    }

    public void updateProfits(String id, ItemStack item, Integer buyAmount, Double buyPrice, Double buyUnitPrice, Integer sellAmount,
                              Double sellPrice, Double sellUnitPrice) {
        if (checkProfits == null) {
            checkProfits = getProfits();
        }
        if (!checkProfits.containsKey(id)) {
            checkProfits.put(id, new CheckProfitEntry(id, item, buyAmount, buyPrice, buyUnitPrice, sellAmount, sellPrice, sellUnitPrice));
        } else {
            CheckProfitEntry entry = checkProfits.get(id);
            entry.setBuyAmount(entry.getBuyAmount() + buyAmount);
            entry.setBuyPrice(entry.getBuyPrice() + buyPrice);
            entry.setSellAmount(entry.getSellAmount() + sellAmount);
            entry.setSellPrice(entry.getSellPrice() + sellPrice);
            checkProfits.put(id, entry);
        }
        DatabaseManager db = EzChestShop.getPlugin().getDatabase();
        String profit_string = checkProfits.values().stream().map(CheckProfitEntry::toString)
                .collect(Collectors.joining(CheckProfitEntry.itemSpacer));
        if (profit_string == null)
            db.setString("uuid", playerId.toString(), "checkprofits", "playerdata", "NULL");
        else
            db.setString("uuid", playerId.toString(), "checkprofits", "playerdata", profit_string);
    }

    public void clearProfits() {
        DatabaseManager db = EzChestShop.getPlugin().getDatabase();
        checkProfits.clear();
        db.setString("uuid", playerId.toString(), "checkprofits", "playerdata", "NULL");
    }

}
