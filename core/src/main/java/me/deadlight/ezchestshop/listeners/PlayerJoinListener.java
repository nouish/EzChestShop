package me.deadlight.ezchestshop.listeners;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.DatabaseManager;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.mysql.MySQL;
import me.deadlight.ezchestshop.data.sqlite.SQLite;
import me.deadlight.ezchestshop.utils.BlockOutline;
import me.deadlight.ezchestshop.utils.Utils;
import org.bukkit.Instrument;
import org.bukkit.Note;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.Internal
public final class PlayerJoinListener implements Listener {
    @EventHandler(priority = EventPriority.MONITOR)
    public void onAsyncPlayerPreLogin(@NotNull AsyncPlayerPreLoginEvent event) {
        if (event.getLoginResult() != AsyncPlayerPreLoginEvent.Result.ALLOWED) {
            // We only need to prepare player data if the player is allowed to join.
            return;
        }

        DatabaseManager db = EzChestShop.getPlugin().getDatabase();
        UUID uuid = event.getUniqueId();

        switch (Config.database_type) {
            case MYSQL -> MySQL.playerTables.forEach(t -> {
                if (db.hasTable(t) && !db.hasPlayer(t, uuid)) {
                    db.preparePlayerData(t, uuid.toString());
                }
            });
            case SQLITE -> SQLite.playerTables.forEach(t -> {
                if (db.hasTable(t) && !db.hasPlayer(t, uuid)) {
                    db.preparePlayerData(t, uuid.toString());
                }
            });
            default -> throw new AssertionError("Unknown database implementation: " + Config.database_type);
        }
    }

    @EventHandler(priority = EventPriority.NORMAL)
    public void onPlayerJoin(@NotNull PlayerJoinEvent event) {
        Player player = event.getPlayer();
        Utils.nmsHandle.injectConnection(player);

        if (Config.emptyShopNotificationOnJoin) {
            List<Block> blocks = Utils.getNearbyEmptyShopForAdmins(player);
            if (blocks.isEmpty()) {
                return;
            }
            List<Note.Tone> tones = new ArrayList<>();
            //add the tones to the list altogether
            AtomicInteger noteIndex = new AtomicInteger();
            tones.add(Note.Tone.A);
            tones.add(Note.Tone.B);
            tones.add(Note.Tone.C);
            tones.add(Note.Tone.D);
            tones.add(Note.Tone.E);
            tones.add(Note.Tone.F);
            tones.add(Note.Tone.G);
            AtomicInteger actionBarCounter = new AtomicInteger();
            EzChestShop.getScheduler().runTaskLaterAsynchronously(() -> {
                //Iterate through each block with an asychronous delay of 5 ticks
                blocks.forEach(b -> {
                    BlockOutline outline = new BlockOutline(player, b);
                    outline.destroyAfter = 10;
                    int index = blocks.indexOf(b);
                    EzChestShop.getScheduler().runTaskLater(player, () -> {
                        outline.showOutline();
                        if (outline.muted) {
                            return;
                        }
                        actionBarCounter.getAndIncrement();
                        Utils.sendActionBar(player, LanguageManager.getInstance().emptyShopActionBar(actionBarCounter.get()));
                        player.playNote(b.getLocation(), Instrument.BIT, Note.flat(1, tones.get(noteIndex.get())));
                        noteIndex.getAndIncrement();
                        if (noteIndex.get() == 7) {
                            noteIndex.set(0);
                        }
                    }, 2L * index);
                });
            }, 80L);
        }
    }
}
