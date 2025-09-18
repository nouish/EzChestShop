package me.deadlight.ezchestshop.commands;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.google.common.collect.Lists;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.PlayerContainer;
import me.deadlight.ezchestshop.utils.objects.CheckProfitEntry;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.jetbrains.annotations.NotNull;

public class CommandCheckProfits implements CommandExecutor, Listener, TabCompleter {

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof Player p) {
            if (!p.hasPermission("ecs.checkprofits"))
                return false;
            if (!Config.enableCheckProfits) {
                p.sendMessage(LanguageManager.getInstance().featureDisabled());
                return false;
            }

            // Send stuff (multi pages), but first send a overview page. Then add a option
            // for details
            PlayerContainer pc = PlayerContainer.get(p);
            List<CheckProfitEntry> checkprofits = pc.getProfits().values().stream()
                    .filter(x -> x.getItem() != null)
                    .collect(Collectors.toList());

            // Branch of the different menus here!
            /*
             * ./checkprofits ./checkprofits p 1 ./checkprofits clear ./checkprofits clear
             * -confirm
             */
            if (args.length == 0) {
                int buyAmount = 0;
                double buyCost = 0.0;
                int sellAmount = 0;
                double sellCost = 0.0;
                if (!checkprofits.isEmpty()) {
                    buyAmount = checkprofits.stream().mapToInt(x -> {
                        if (x.getBuyAmount() == null)
                            return 0;
                        else
                            return x.getBuyAmount();
                    }).sum();
                    buyCost = checkprofits.stream().mapToDouble(x -> {
                        if (x.getBuyPrice() == null)
                            return 0;
                        else
                            return x.getBuyPrice();
                    }).sum();
                    sellAmount = checkprofits.stream().mapToInt(x -> {
                        if (x.getSellAmount() == null)
                            return 0;
                        else
                            return x.getSellAmount();
                    }).sum();
                    sellCost = checkprofits.stream().mapToDouble(x -> {
                        if (x.getSellPrice() == null)
                            return 0;
                        else
                            return x.getSellPrice();
                    }).sum();
                }
                p.spigot().sendMessage(LanguageManager.getInstance().checkProfitsLandingpage(p, buyCost, buyAmount, sellCost, sellAmount));
            } else if (args.length == 1) {
                if (args[0].equals("clear")) {
                    // Send message that asks to confirm
                    p.spigot().sendMessage(LanguageManager.getInstance().confirmProfitClear());
                }
            } else if (args.length == 2) {
                if (args[0].equals("clear") && args[1].equals("-confirm")) {
                    // Clear data & send cleared message
                    pc.clearProfits();
                    p.sendMessage(LanguageManager.getInstance().confirmProfitClearSuccess());
                } else if (args[0].equals("p")) {
                    // ShopChest sc = ShopChest.getInstance();
                    int page;
                    try {
                        page = Integer.parseInt(args[1]);
                    } catch (NumberFormatException e) {
                        page = 1;
                    }

                    // Sort checkprofits:
                    checkprofits.sort((cp1, cp2) ->
                            Double.compare(Math.floor(cp2.getBuyPrice() - cp2.getSellPrice()), cp1.getBuyPrice() - cp1.getSellPrice()));
                    // how many pages will there be? x entries per page:
                    int pages = (int) Math.floor(checkprofits.size() / 4.0)
                            + ((checkprofits.size() % Config.command_checkprofit_lines_pp == 0) ? 0 : 1);// add 1 if not divideable by 4
                    if (page > pages || page < 1) {
                        p.sendMessage(LanguageManager.getInstance().wrongInput());
                        return false;
                    }
                    p.spigot().sendMessage(LanguageManager.getInstance().checkProfitsDetailpage(p, checkprofits, page, pages));
                }
            }
        }
        return false;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent evt) {
        Player p = evt.getPlayer();
        if (!p.hasPermission("ecs.checkprofits") || !Config.enableCheckProfits)
            return;
        PlayerContainer pc = PlayerContainer.get(p);
        List<CheckProfitEntry> checkprofits = pc.getProfits().values().stream()
                .filter(x -> x.getItem() != null)
                .toList();
        if (checkprofits.isEmpty())
            return;
        else if (checkprofits.getFirst().getItem() == null)
            return;
        EzChestShop.getScheduler().runTaskLater(() -> p.spigot().sendMessage(LanguageManager.getInstance().joinProfitNotification()), 4L);
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
        List<String> s1 = Arrays.asList("clear", "p");
        List<String> fList = Lists.newArrayList();
        if (sender instanceof Player p) {
            if (args.length == 1) {
                for (String s : s1) {
                    if (s.startsWith(args[0]))
                        fList.add(s);
                }
            } else if (args.length == 2) {
                if (args[0].equals("clear")) {
                    if ("-confirm".startsWith(args[1]))
                        fList.add("-confirm");
                } else if (args[0].equals("p")) {
                    PlayerContainer pc = PlayerContainer.get(p);
                    List<CheckProfitEntry> checkprofits = pc.getProfits().values().stream()
                            .filter(x -> x.getItem() != null)
                            .toList();
                    int pages = (int) Math.floor(checkprofits.size() / 4.0) + 1;
                    List<String> range = IntStream.range(1, pages + 1).boxed()
                            .map(Object::toString)
                            .toList();
                    for (String s : range) {
                        if (s.startsWith(args[1]))
                            fList.add(s);
                    }
                }
            }
        }
        return fList;
    }
}
