package me.deadlight.ezchestshop.listeners;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.gui.ContainerGui;
import me.deadlight.ezchestshop.data.gui.ContainerGuiItem;
import me.deadlight.ezchestshop.data.gui.GuiData;
import me.deadlight.ezchestshop.version.BuildInfo;
import me.deadlight.ezchestshop.version.GitHubUtil;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import static net.kyori.adventure.text.Component.text;
import static net.kyori.adventure.text.event.ClickEvent.openUrl;
import static net.kyori.adventure.text.format.NamedTextColor.GOLD;
import static net.kyori.adventure.text.format.NamedTextColor.RED;

public class UpdateChecker implements Listener {

    LanguageManager lm = new LanguageManager();

    private static boolean isGuiUpdateAvailable;

    private static final HashMap<GuiData.GuiType, List<List<String>>> overlappingItems = new HashMap<>();
    private static final HashMap<GuiData.GuiType, Integer> requiredOverflowRows = new HashMap<>();

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (player.isOp()) {
            if (isGuiUpdateAvailable) {
                if (Config.notify_overflowing_gui_items && !requiredOverflowRows.isEmpty()) {
                    EzChestShop.getScheduler().runTaskLater(() -> player.spigot().sendMessage(lm.overflowingGuiItemsNotification(requiredOverflowRows)), 10L);
                }
                if (Config.notify_overlapping_gui_items && !overlappingItems.isEmpty()) {
                    EzChestShop.getScheduler().runTaskLater(() -> player.spigot().sendMessage(lm.overlappingItemsNotification(overlappingItems)), 10L);
                }
            }
        }

        if (Config.notify_updates && player.hasPermission("ecs.version.notify")) {
            EzChestShop.getScheduler().runTaskLaterAsynchronously(() -> checkForUpdate(player), 10);
        }
    }

    private void checkForUpdate(Player player) {
        BuildInfo current = BuildInfo.CURRENT;
        BuildInfo latest;
        GitHubUtil.GitHubStatusLookup status;

        try {
            if (current.isStable()) {
                latest = GitHubUtil.lookupLatestRelease();
                status = GitHubUtil.compare(latest.getId(), current.getId());
            } else {
                latest = null; // Not a named release
                status = GitHubUtil.compare(current.getBranch(), current.getId());
            }
        } catch (IOException e) {
            EzChestShop.logger().warn("Failed to determine the latest version!", e);
            return;
        }

        EzChestShop.getScheduler().runTask(player, () -> {
            if (status.isBehind()) {
                if (current.isStable()) {
                    String link = "https://github.com/nouish/EzChestShop/releases/tag/" + latest.getId();
                    player.sendMessage(text()
                            .append(text("You are using an outdated version of ", RED))
                            .append(text("EzChestShopReborn", GOLD))
                            .append(text("!", RED))
                            .appendNewline()
                            .append(text("Download version ", RED))
                            .append(text(latest.getId(), GOLD))
                            .append(text(" from GitHub: ", RED))
                            .append(text(link, GOLD)
                                    .hoverEvent(text("Click here to read more.", GOLD))
                                    .clickEvent(openUrl(link)))
                            .append(text(".", RED))
                            .build());
                } else {
                    String link = String.format(Locale.ROOT, "https://github.com/nouish/EzChestShop/compare/%s...%s", current.getId(), current.getBranch());
                    int behindBy = status.getDistance();
                    player.sendMessage(text()
                            .append(text("You are using an outdated snapshot of ", RED))
                            .append(text("EzChestShopReborn", GOLD))
                            .append(text("!", RED))
                            .appendNewline()
                            .append(text("The latest build is ", RED))
                            .append(text(String.format(Locale.ROOT, "%,d commit%s", behindBy, behindBy > 1 ? "s" : ""), GOLD)
                                    .hoverEvent(text("Click here to compare changes.", GOLD))
                                    .clickEvent(openUrl(link)))
                            .append(text(" ahead.", RED))
                            .build());
                }
            }
        });
    }

    public void resetGuiCheck() {
        overlappingItems.clear();
        requiredOverflowRows.clear();
        isGuiUpdateAvailable = false;
        checkGuiUpdate();
    }

    public static int getGuiOverflow(GuiData.GuiType guiType) {
        Integer value = requiredOverflowRows.get(guiType);
        if (value != null) {
            return value;
        } else {
            return -1;
        }
    }

    public void checkGuiUpdate() {
        // Check all GUIs (GuiData.getViaType()) for updates. See if items are outside bounds and if items that should not overlap suddenly overlap.
        // If any of these are true, return true.

        for (GuiData.GuiType type : GuiData.GuiType.values()) {
            ContainerGui container = GuiData.getViaType(type);
            if (container == null) continue;

            // Save the item keys for the items and check them against a list of item keys that may be combined.
            // If the overlapping items are not in this list, remember them!

            HashMap<Integer, List<String>> items = new HashMap<>();

            container.getItemKeys().forEach(key -> {
                ContainerGuiItem item = container.getItem(key);
                if (item == null) return;
                if (item.getRow() > container.getRows()) {
                    Integer row = requiredOverflowRows.get(type);
                    if (row == null) {
                        row = item.getRow();
                    } else {
                        row = Math.max(row, item.getRow());
                    }
                    requiredOverflowRows.put(type, row);
                    isGuiUpdateAvailable = true;
                }
                // Save the items to the hashmap
                if (items.containsKey(item.getSlot())) {
                    List<String> list = new ArrayList<>(items.get(item.getSlot()));
                    list.add(key);
                    items.put(item.getSlot(), list);
                } else {
                    items.put(item.getSlot(), Arrays.asList(key));
                }
            });

            // Filter out all items that don't overlap
            items.entrySet().removeIf(entry -> entry.getValue().size() == 1);

            // Check if the overlapping items are allowed to overlap
            // get the List from GuiData (so I don't forget to update it) and
            // loop over the list and try to match the entry to the list with containsAll!
            if (GuiData.getAllowedDefaultOverlappingItems(type) != null) {
                List<List<String>> overlapping = items.values().stream().filter(list -> {
                    // Make sure that the list doesn't contain values that are not allowed to overlap
                    // The list may contain all allowed values, some allowed values, no allowed values or a mix of allowed and not allowed values
                    if (list.isEmpty()) return false;

                    // Check and see if a value is not contained in the allowed lists at all by converting the list of lists to a list of strings
                    List<String> containing = GuiData.getAllowedDefaultOverlappingItems(type).stream().flatMap(List::stream).collect(Collectors.toList());
                    List<String> subtractList = new ArrayList<>(list);
                    subtractList.removeAll(containing);
                    if (!subtractList.isEmpty()) {
                        return true;
                    }

                    // Check if the items is in the overlap allowlist.
                    AtomicBoolean returnValue = new AtomicBoolean(false);
                    GuiData.getAllowedDefaultOverlappingItems(type).forEach(allowedList -> {
                        List<String> subtractList2 = new ArrayList<>(list);
                        // Only run this check if there is at least a connection between the two lists
                        if (!Collections.disjoint(allowedList, subtractList2)) {
                            subtractList2.removeAll(allowedList);
                            if (!subtractList2.isEmpty()) {
                                returnValue.set(true);
                            }
                        }
                    });
                    return returnValue.get();
                }).collect(Collectors.toList());

                if (!overlapping.isEmpty()) {
                    overlappingItems.put(type, overlapping);
                    isGuiUpdateAvailable = true;
                }
            }
        }
    }

}
