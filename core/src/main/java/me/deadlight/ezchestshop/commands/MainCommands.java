package me.deadlight.ezchestshop.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.palmergames.bukkit.towny.utils.ShopPlotUtil;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.Constants;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.DatabaseManager;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.guis.SettingsGUI;
import me.deadlight.ezchestshop.utils.BlockOutline;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.holograms.ShopHologram;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.deadlight.ezchestshop.utils.objects.ShopSettings;
import me.deadlight.ezchestshop.utils.worldguard.FlagRegistry;
import me.deadlight.ezchestshop.utils.worldguard.WorldGuardUtils;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.DoubleChest;
import org.bukkit.block.TileState;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

public class MainCommands implements CommandExecutor, TabCompleter {
    private static final Logger LOGGER = EzChestShop.logger();
    public static HashMap<UUID, ShopSettings> settingsHashMap = new HashMap<>();
    private enum SettingType { TOGGLE_MSG, DBUY, DSELL, ADMINS, SHAREINCOME, ROTATION }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof Player player) {
            if (args.length > 0) {
                String mainarg = args[0];
                Block target = getCorrectBlock(player.getTargetBlockExact(6));

                if (mainarg.equalsIgnoreCase("create") && target != null) {
                    if (args.length >= 3) {
                        if (Utils.isNumeric(args[1]) && Utils.isNumeric(args[2])) {
                            if (isPositive(Double.parseDouble(args[1])) && isPositive(Double.parseDouble(args[2]))) {
                                if (Config.permissions_create_shop_enabled) {
                                    LOGGER.trace(
                                        "Shop limits are enabled - checking status for {} (world name: '{}')",
                                        player.getName(),
                                        player.getWorld().getName()
                                    );
                                    // first check the world, if nothing is found return -2
                                    int maxShopsWorld = Utils.getMaxPermission(player,
                                            "ecs.shops.limit." + player.getWorld().getName() + ".", -2);
                                    if (maxShopsWorld == -2) {
                                        // if nothing is found for the world, check the default permission
                                        int maxShops = Utils.getMaxPermission(player, "ecs.shops.limit.");
                                        maxShops = maxShops == -1 ? 10000 : maxShops;
                                        int shopCount = ShopContainer.getShopCount(player);
                                        if (shopCount >= maxShops) {
                                            LOGGER.debug(
                                                "{} hit the global limit ({}/{}) and could not create a new shop.",
                                                player.getName(),
                                                shopCount,
                                                maxShops
                                            );
                                            player.sendMessage(LanguageManager.getInstance().maxShopLimitReached(maxShops));
                                            return false;
                                        }
                                    } else {
                                        // there is a world limit, so check it
                                        maxShopsWorld = maxShopsWorld == -1 ? 10000 : maxShopsWorld;
                                        int shopCount = ShopContainer.getShopCount(player, player.getWorld());
                                        if (shopCount >= maxShopsWorld) {
                                            LOGGER.debug(
                                                "{} hit the limit in world '{}' ({}/{}) and could not create a new shop here.",
                                                player.getName(),
                                                player.getWorld().getName(),
                                                shopCount,
                                                maxShopsWorld
                                            );
                                            player.sendMessage(LanguageManager.getInstance().maxShopLimitReached(maxShopsWorld));
                                            return false;
                                        }
                                    }
                                } else {
                                    LOGGER.trace("Skipped shop limit check for {} (feature disabled)", player.getName());
                                }
                                try {
                                    createShop(player, args, target);
                                } catch (IOException e) {
                                    LOGGER.warn("Failed to create shop", e);
                                }
                            } else {
                                player.sendMessage(LanguageManager.getInstance().negativePrice());
                            }
                        } else {
                            sendHelp(player);
                        }
                    } else {
                        player.sendMessage(LanguageManager.getInstance().notenoughARGS());
                    }
                } else if (mainarg.equalsIgnoreCase("remove") && target != null) {
                    removeShop(player, args, target);
                } else if (mainarg.equalsIgnoreCase("settings") && target != null) {
                    changeSettings(player, args, target);
                } else if (mainarg.equalsIgnoreCase("version")) {
                    Utils.sendVersionMessage(player);
                } else if (mainarg.equalsIgnoreCase("emptyshops")) {
                    emptyShopsCommand(player);
                } else {
                    sendHelp(player);
                }
            } else {
                sendHelp(player);
            }
        } else {
            sender.sendMessage(LanguageManager.getInstance().consoleNotAllowed());
        }

        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String @NotNull [] args) {
        List<String> fList = new ArrayList<>();
        List<String> list_mainarg = Arrays.asList("create", "remove", "settings", "emptyshops", "version");
        List<String> list_create_1 = Collections.singletonList("[BuyPrice]");
        List<String> list_create_2 = Collections.singletonList("[SellPrice]");
        List<String> list_settings_1 = Arrays.asList("copy", "paste", "toggle-message", "toggle-buying", "toggle-selling", "admins", "toggle-shared-income", "change-rotation", "buyprice", "sellprice"); //, "transfer-ownership"
        List<String> list_settings_admins_2 = Arrays.asList("add", "remove", "list", "clear");
        List<String> list_settings_paste_2 = Arrays.asList("toggle-message", "toggle-buying", "toggle-selling", "admins", "toggle-shared-income", "change-rotation");
        List<String> list_settings_change_rotation_2 = new ArrayList<>(Utils.rotations);
        if (sender instanceof Player player) {
            if (args.length == 1)
                StringUtil.copyPartialMatches(args[0], list_mainarg, fList);
            if (args.length > 1) {
                if (args[0].equalsIgnoreCase("create")) {
                    if (args.length == 2)
                        StringUtil.copyPartialMatches(args[1], list_create_1, fList);
                    if (args.length == 3)
                        StringUtil.copyPartialMatches(args[2], list_create_2, fList);
                } else if (args[0].equalsIgnoreCase("settings")) {
                    if (args.length == 2)
                        StringUtil.copyPartialMatches(args[1], list_settings_1, fList);
                    if (args[1].equalsIgnoreCase("change-rotation")) {
                        if (args.length == 3)
                            StringUtil.copyPartialMatches(args[2], list_settings_change_rotation_2, fList);
                    }
                    if (args[1].equalsIgnoreCase("paste")) {
                        if (args.length == 3) {
                            String[] last = args[2].split(",");
                            List<String> pasteList = new ArrayList<>(list_settings_paste_2);
                            pasteList.removeAll(Arrays.asList(last));
                            if (args[2].endsWith(",")) {
                                for (String s : pasteList) {
                                    fList.add(String.join(",", Arrays.asList(last)) + "," + s);
                                }
                            } else {
                                String lastarg = last[last.length - 1];
                                for (String s : pasteList) {
                                    if (s.startsWith(lastarg)) {
                                        last[last.length - 1] = s;
                                        fList.add(String.join(",", Arrays.asList(last)));
                                    }
                                }
                            }
                        }
                    }
                    if (args[1].equalsIgnoreCase("admins")) {
                        if (args.length > 2) {
                            if (args.length == 3) {
                                StringUtil.copyPartialMatches(args[2], list_settings_admins_2, fList);
                            }
                            BlockState blockState = getLookedAtBlockStateIfOwner(player, false, false,
                                    getCorrectBlock(player.getTargetBlockExact(6)));
                            if (blockState != null) {
                                if (args[2].equalsIgnoreCase("add")) {
                                    if (args.length == 4) {
                                        String adminString = ShopContainer
                                                .getShopSettings(blockState.getLocation()).getAdmins();
                                        List<String> adminList = new ArrayList<>();
                                        if (adminString != null && !adminString.equalsIgnoreCase("none")) {
                                            adminList = Arrays.stream(adminString.split("@"))
                                                    .filter(s -> !s.trim().equalsIgnoreCase(""))
                                                    .map(s -> Bukkit.getOfflinePlayer(UUID.fromString(s)).getName())
                                                    .toList();
                                        }
                                        String[] last = args[3].split(",");
                                        List<String> online = Bukkit.getOnlinePlayers().stream()
                                                .filter(p -> !player.getUniqueId().equals(p.getUniqueId()))
                                                .map(HumanEntity::getName)
                                                .collect(Collectors.toList());
                                        online.removeAll(Arrays.asList(last));
                                        online.removeAll(adminList);

                                        if (args[3].endsWith(",")) {
                                            for (String s : online) {
                                                fList.add(String.join(",", Arrays.asList(last)) + "," + s);
                                            }
                                        } else {
                                            String lastarg = last[last.length - 1];
                                            for (String s : online) {
                                                if (s.startsWith(lastarg)) {
                                                    last[last.length - 1] = s;
                                                    fList.add(String.join(",", Arrays.asList(last)));
                                                }
                                            }
                                        }
                                    }
                                } else if (args[2].equalsIgnoreCase("remove")) {
                                    if (args.length == 4) {
                                        String[] last = args[3].split(",");
                                        String adminString = ShopContainer.getShopSettings(blockState.getLocation()).getAdmins();
                                        List<String> playerList = new ArrayList<>();
                                        if (adminString != null && !adminString.equalsIgnoreCase("none")) {
                                            playerList = Arrays.stream(adminString.split("@"))
                                                    .filter(s -> !s.trim().equalsIgnoreCase(""))
                                                    .map(s -> Bukkit.getOfflinePlayer(UUID.fromString(s)).getName())
                                                    .collect(Collectors.toList());
                                            playerList.removeAll(Arrays.asList(last));
                                        }
                                        if (args[3].endsWith(",")) {
                                            for (String s : playerList) {
                                                fList.add(String.join(",", last) + "," + s);
                                            }
                                        } else {
                                            String lastarg = last[last.length - 1];
                                            for (String s : playerList) {
                                                if (s.startsWith(lastarg)) {
                                                    last[last.length - 1] = s;
                                                    fList.add(String.join(",", Arrays.asList(last)));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else if (args[1].equalsIgnoreCase("buyprice") && args.length == 3) {
                        StringUtil.copyPartialMatches(args[2], list_create_1, fList);
                    } else if (args[1].equalsIgnoreCase("sellprice") && args.length == 3) {
                        StringUtil.copyPartialMatches(args[2], list_create_2, fList);
                    }
                }
            }
        }
        return fList;
    }

    private void sendHelp(Player player) {
        player.spigot().sendMessage(LanguageManager.getInstance().cmdHelp(player.hasPermission("admin")));
    }

    private void createShop(Player player, String[] args, Block target) throws IOException {
        if (target != null && target.getType() != Material.AIR) {
            BlockState blockState = target.getState(false);
            //slimefun check
            if (EzChestShop.slimefun) {
                boolean sfresult = BlockStorage.hasBlockInfo(target.getLocation());
                if (sfresult) {
                    player.sendMessage(LanguageManager.getInstance().slimeFunBlockNotSupported());
                    return;
                }
            }

            if (EzChestShop.worldguard) {
                if (!WorldGuardUtils.queryStateFlag(FlagRegistry.CREATE_SHOP, player)) {
                    player.spigot().sendMessage(LanguageManager.getInstance().notAllowedToCreateOrRemove(player));
                    return;
                }
            }

            if (blockState instanceof TileState state) {
                if (Utils.isApplicableContainer(target)) {
                    if (checkIfLocation(target.getLocation(), player)) {
                        if (EzChestShop.towny && Config.towny_integration_shops_only_in_shop_plots) {
                            if (!ShopPlotUtil.isShopPlot(target.getLocation())) {
                                player.spigot().sendMessage(LanguageManager.getInstance().notAllowedToCreateOrRemove(player));
                                return;
                            }
                            if (!(ShopPlotUtil.doesPlayerOwnShopPlot(player, target.getLocation()) ||
                                    ShopPlotUtil.doesPlayerHaveAbilityToEditShopPlot(player, target.getLocation()))) {
                                player.spigot().sendMessage(LanguageManager.getInstance().notAllowedToCreateOrRemove(player));
                                return;
                            }
                        }

                        PersistentDataContainer container = state.getPersistentDataContainer();

                        //owner (String) (player name)
                        //buy (double)
                        //sell (double)
                        //item (String) (itemstack)

                        //already a shop
                        if (container.has(Constants.OWNER_KEY, PersistentDataType.STRING) || ifItsADoubleChestShop(target) != null) {
                            player.sendMessage(LanguageManager.getInstance().alreadyAShop());
                        } else {
                            //not a shop
                            if (player.getInventory().getItemInMainHand().getType() != Material.AIR) {
                                ItemStack thatIteminplayer = player.getInventory().getItemInMainHand();
                                ItemStack thatItem = thatIteminplayer.clone();
                                thatItem.setAmount(1);
                                if (Tag.SHULKER_BOXES.isTagged(thatItem.getType()) && Tag.SHULKER_BOXES.isTagged(target.getType())) {
                                    player.sendMessage(LanguageManager.getInstance().invalidShopItem());
                                    return;
                                }

                                double buyprice = Double.parseDouble(args[1]);
                                double sellprice = Double.parseDouble(args[2]);

                                if (Config.settings_buy_greater_than_sell && (sellprice > buyprice && buyprice != 0)) {
                                    player.sendMessage(LanguageManager.getInstance().buyGreaterThanSellRequired());
                                    return;
                                }
                                //owner, buy, sell, msgtoggle, dbuy, dsell, admins, shareincome, trans, adminshop, rotation

                                int isDbuy = Config.settings_zero_equals_disabled ?
                                        (buyprice == 0 ? 1 : (Config.settings_defaults_dbuy ? 1 : 0))
                                        : (Config.settings_defaults_dbuy ? 1 : 0);
                                int isDSell = Config.settings_zero_equals_disabled ?
                                        (sellprice == 0 ? 1 : (Config.settings_defaults_dsell ? 1 : 0))
                                        : (Config.settings_defaults_dsell ? 1 : 0);

                                container.set(Constants.OWNER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
                                container.set(Constants.BUY_PRICE_KEY, PersistentDataType.DOUBLE, buyprice);
                                container.set(Constants.SELL_PRICE_KEY, PersistentDataType.DOUBLE, sellprice);
                                container.set(Constants.ENABLE_MESSAGE_KEY, PersistentDataType.INTEGER, Config.settings_defaults_transactions ? 1 : 0);
                                container.set(Constants.DISABLE_BUY_KEY, PersistentDataType.INTEGER, isDbuy);
                                container.set(Constants.DISABLE_SELL_KEY, PersistentDataType.INTEGER, isDSell);
                                container.set(Constants.ADMIN_LIST_KEY, PersistentDataType.STRING, "none");
                                container.set(Constants.ENABLE_SHARED_INCOME_KEY, PersistentDataType.INTEGER, Config.settings_defaults_shareprofits ? 1 : 0);
                                container.set(Constants.ENABLE_ADMINSHOP_KEY, PersistentDataType.INTEGER, 0);
                                container.set(Constants.ROTATION_KEY, PersistentDataType.STRING, Config.settings_defaults_rotation);

                                //msgtoggle 0/1
                                //dbuy 0/1
                                //dsell 0/1
                                //admins [list of uuids seperated with @ in string form]
                                //shareincome 0/1
                                //logs [list of infos seperated by @ in string form]
                                //trans [list of infos seperated by @ in string form]
                                //adminshop 0/1
                                Utils.storeItem(thatItem, container);
                                state.update();
                                ShopContainer.createShop(target.getLocation(), player, thatItem, buyprice, sellprice, false,
                                        isDbuy == 1, isDSell == 1, "none", true, false, Config.settings_defaults_rotation);
                                player.sendMessage(LanguageManager.getInstance().shopCreated());
                            } else {
                                player.sendMessage(LanguageManager.getInstance().holdSomething());
                            }
                        }
                    } else {
                        player.spigot().sendMessage(LanguageManager.getInstance().notAllowedToCreateOrRemove(player));
                    }
                } else {
                    player.sendMessage(LanguageManager.getInstance().noChest());
                }
            } else {
                player.sendMessage(LanguageManager.getInstance().lookAtChest());
            }
        } else {
            player.sendMessage(LanguageManager.getInstance().lookAtChest());
        }
    }

    private void removeShop(Player player, String[] args, Block target) {
        BlockState blockState = getLookedAtBlockStateIfOwner(player, true, true, target);
        if (blockState != null) {
            if (EzChestShop.worldguard) {
                if (!WorldGuardUtils.queryStateFlag(FlagRegistry.REMOVE_SHOP, player)) {
                    player.spigot().sendMessage(LanguageManager.getInstance().notAllowedToCreateOrRemove(player));
                    return;
                }
            }
            //is the owner remove it
            PersistentDataContainer container = ((TileState) blockState).getPersistentDataContainer();
            container.remove(Constants.OWNER_KEY);
            container.remove(Constants.BUY_PRICE_KEY);
            container.remove(Constants.SELL_PRICE_KEY);
            container.remove(Constants.ITEM_KEY);

            //add new settings data later
            try {
                container.remove(Constants.ENABLE_MESSAGE_KEY);
                container.remove(Constants.DISABLE_BUY_KEY);
                container.remove(Constants.DISABLE_SELL_KEY);
                container.remove(Constants.ADMIN_LIST_KEY);
                container.remove(Constants.ENABLE_SHARED_INCOME_KEY);
                container.remove(Constants.ENABLE_ADMINSHOP_KEY);
                container.remove(Constants.ROTATION_KEY);
                //msgtoggle 0/1
                //dbuy 0/1
                //dsell 0/1
                //admins [list of uuids seperated with @ in string form]
                //shareincome 0/1
                //logs [list of infos seperated by @ in string form]
                //trans [list of infos seperated by @ in string form]
                //adminshop 0/1
            } catch (Exception ex) {
                //nothing really worrying...
            }

            ShopContainer.deleteShop(blockState.getLocation());
            ShopHologram.hideForAll(blockState.getLocation());
            blockState.update();
            player.sendMessage(LanguageManager.getInstance().chestShopRemoved());
        }
    }

    private void changeSettings(Player player, String[] args, Block target) {
        if (args.length == 1) {
            BlockState blockState = getLookedAtBlockStateIfOwner(player, true, false, target);

            if (blockState != null) {
                SettingsGUI settingsGUI = new SettingsGUI();
                settingsGUI.showGUI(player, blockState.getBlock(), false);
                player.playSound(player.getLocation(), Sound.BLOCK_PISTON_EXTEND, 0.5f, 0.5f);
            }
        } else if (args.length >= 2) {
            String settingarg = args[1];

            if (settingarg.equalsIgnoreCase("copy")) {
                copyShopSettings(player, target);
            } else if (settingarg.equalsIgnoreCase("paste")) {
                if (args.length == 3) {
                    pasteShopSettings(player, args[2], target);
                } else {
                    pasteShopSettings(player, target);
                }
            } else if (settingarg.equalsIgnoreCase("toggle-message")) {
                modifyShopSettings(player, SettingType.TOGGLE_MSG, "", target);
            } else if (settingarg.equalsIgnoreCase("toggle-buying")) {
                modifyShopSettings(player, SettingType.DBUY, "", target);
            } else if (settingarg.equalsIgnoreCase("toggle-selling")) {
                modifyShopSettings(player, SettingType.DSELL, "", target);
            } else if (settingarg.equalsIgnoreCase("toggle-shared-income")) {
                modifyShopSettings(player, SettingType.SHAREINCOME, "", target);
            } else if (settingarg.equalsIgnoreCase("change-rotation")) {
                if (args.length == 3) {
                    modifyShopSettings(player, SettingType.ROTATION, args[2], target);
                } else {
                    modifyShopSettings(player, SettingType.ROTATION, "", target);
                }
            } else if (settingarg.equalsIgnoreCase("admins")) {
                if (args.length == 3) {
                    if (args[2].equalsIgnoreCase("clear")) {
                        modifyShopSettings(player, SettingType.ADMINS, "clear", target);
                    } else if (args[2].equalsIgnoreCase("list")) {
                        BlockState blockState = getLookedAtBlockStateIfOwner(player, true, false, target);
                        if (blockState != null) {
                            String adminString = ShopContainer.getShopSettings(
                                    blockState.getLocation()).getAdmins();
                            if (adminString != null && !adminString.equalsIgnoreCase("none")) {
                                List<String> adminList = Arrays.asList(adminString.split("@"));
                                if (!adminList.isEmpty()) {
                                    player.sendMessage(ChatColor.GREEN + "Admins:\n" + ChatColor.GRAY + " - " + ChatColor.YELLOW + adminList.stream().map(s -> Bukkit.getOfflinePlayer(
                                            UUID.fromString(s)).getName()).collect(
                                            Collectors.joining("\n" + ChatColor.GRAY + " - " + ChatColor.YELLOW)));
                                } else {
                                    player.sendMessage(ChatColor.GREEN + "Admins:\n" + ChatColor.GRAY + " - " + ChatColor.YELLOW + LanguageManager.getInstance().nobodyStatusAdmins());
                                }
                            } else {
                                player.sendMessage(ChatColor.GREEN + "Admins:\n" + ChatColor.GRAY + " - " + ChatColor.YELLOW + LanguageManager.getInstance().nobodyStatusAdmins());
                            }
                        }
                    }
                } else if (args.length == 4) {
                    if (args[2].equalsIgnoreCase("add")) {
                        modifyShopSettings(player, SettingType.ADMINS, "+" + args[3], target);
                    } else if (args[2].equalsIgnoreCase("remove")) {
                        modifyShopSettings(player, SettingType.ADMINS, "-" + args[3], target);
                    }
                }
            } else if (settingarg.equalsIgnoreCase("buyprice") || settingarg.equalsIgnoreCase("sellprice")) {
                if (args.length == 3) {
                    BlockState blockState = getLookedAtBlockStateIfOwner(player, true, false, target);
                    boolean isBuy = settingarg.equalsIgnoreCase("buyprice");
                    try {
                        if (blockState != null) {
                            double price = Double.parseDouble(args[2]);
                            if (price < 0) {
                                player.sendMessage(LanguageManager.getInstance().negativePrice());
                                return;
                            }
                            EzShop shop = ShopContainer.getShop(blockState.getLocation());
                            // Enforce buy > sell.
                            if (Config.settings_buy_greater_than_sell) {
                                if (
                                        (isBuy && shop.getSellPrice() > price && price != 0) ||
                                                (!isBuy && price > shop.getBuyPrice() && shop.getBuyPrice() != 0)
                                ) {
                                    player.sendMessage(LanguageManager.getInstance().buyGreaterThanSellRequired());
                                    return;
                                }
                            }
                            // Revert from disabling buy sell.
                            if (Config.settings_zero_equals_disabled && isBuy && shop.getBuyPrice() == 0 && price != 0) {
                                modifyShopSettings(player, SettingType.DBUY, "false", target);
                            }
                            if (Config.settings_zero_equals_disabled && !isBuy && shop.getSellPrice() == 0 && price != 0) {
                                modifyShopSettings(player, SettingType.DSELL, "false", target);
                            }
                            // Disable buy/sell
                            if (price == 0 && Config.settings_zero_equals_disabled) {
                                if (isBuy && shop.getBuyPrice() != 0) {
                                    modifyShopSettings(player, SettingType.DBUY, "true", target);
                                }
                                if (!isBuy && shop.getSellPrice() != 0) {
                                    modifyShopSettings(player, SettingType.DSELL, "true", target);
                                }
                            }
                            // if any update happend get the block again.
                            blockState = getLookedAtBlockStateIfOwner(player, true, false, target);
                            // Change the price
                            ShopContainer.changePrice(blockState, price, isBuy);
                            // Update the hologram
                            ShopHologram hologram = ShopHologram.getHologram(blockState.getLocation(), player);
                            if (isBuy) {
                                hologram.updateBuyPrice();
                            } else {
                                hologram.updateSellPrice();
                            }
                            player.sendMessage(isBuy ? LanguageManager.getInstance().shopBuyPriceUpdated()
                                                     : LanguageManager.getInstance().shopSellPriceUpdated());
                        }
                    } catch (NumberFormatException e) {
                        player.sendMessage(LanguageManager.getInstance().wrongInput());
                    }
                } else {
                    sendHelp(player);
                }
            }
        }
    }

    private void copyShopSettings(Player player, Block target) {
        var lm = LanguageManager.getInstance();
        BlockState blockState = getLookedAtBlockStateIfOwner(player, true, false, target);
        if (blockState != null) {
            ShopSettings settings = ShopContainer.getShopSettings(blockState.getLocation());
            List<String> adminList = (settings.getAdmins() == null || settings.getAdmins().equalsIgnoreCase("none")) ? null : Arrays.asList(settings.getAdmins().split("@"));
            String adminString;
            if (adminList == null || adminList.isEmpty()) {
                adminString = lm.nobodyStatusAdmins();
            } else {
                adminString = adminList.stream().map(id -> Bukkit.getOfflinePlayer(UUID.fromString(id)).getName()).collect(Collectors.joining(", "));
            }
            settings.setRotation(settings.getRotation() == null ? Config.settings_defaults_rotation : settings.getRotation());
            settingsHashMap.put(player.getUniqueId(), settings.clone());
            player.spigot().sendMessage(LanguageManager.getInstance().copiedShopSettings(LanguageManager.getInstance().toggleTransactionMessageButton() + ": " + (settings.isMsgtoggle() ? lm.statusOn() : lm.statusOff()) + "\n" +
                    lm.disableBuyingButtonTitle() + ": " + (settings.isDbuy() ? lm.statusOn() : lm.statusOff()) + "\n" +
                    lm.disableSellingButtonTitle() + ": " + (settings.isDsell() ? lm.statusOn() : lm.statusOff()) + "\n" +
                    lm.shopAdminsButtonTitle() + ": " + net.md_5.bungee.api.ChatColor.GREEN + adminString + "\n" +
                    lm.shareIncomeButtonTitle() + ": " + (settings.isShareincome() ? lm.statusOn() : lm.statusOff()) + "\n" +
                    lm.rotateHologramButtonTitle() + ": " + lm.rotationFromData(settings.getRotation())));
        }
    }

    private void pasteShopSettings(Player player, Block target) {
        BlockState blockState = getLookedAtBlockStateIfOwner(player, true, false, target);
        if (blockState != null) {
            // owner confirmed
            PersistentDataContainer container = ((TileState) blockState).getPersistentDataContainer();
            ShopSettings settings = settingsHashMap.get(player.getUniqueId());
            DatabaseManager db = EzChestShop.getPlugin().getDatabase();
            String sloc = Utils.LocationtoString(blockState.getLocation());
            String admins = settings.getAdmins() == null ? "none" : settings.getAdmins();
            db.setBool("location", sloc, "msgToggle", "shopdata", settings.isMsgtoggle());
            db.setBool("location", sloc, "buyDisabled", "shopdata", settings.isDbuy());
            db.setBool("location", sloc, "sellDisabled", "shopdata", settings.isDbuy());
            db.setString("location", sloc, "admins", "shopdata", admins);
            db.setBool("location", sloc, "shareIncome", "shopdata", settings.isShareincome());
            db.setString("location", sloc, "rotation", "shopdata", settings.getRotation());
            container.set(Constants.ENABLE_MESSAGE_KEY, PersistentDataType.INTEGER, settings.isMsgtoggle() ? 1 : 0);
            container.set(Constants.DISABLE_BUY_KEY, PersistentDataType.INTEGER, settings.isDbuy() ? 1 : 0);
            container.set(Constants.DISABLE_SELL_KEY, PersistentDataType.INTEGER, settings.isDsell() ? 1 : 0);
            container.set(Constants.ADMIN_LIST_KEY, PersistentDataType.STRING, admins);
            container.set(Constants.ENABLE_SHARED_INCOME_KEY, PersistentDataType.INTEGER, settings.isShareincome() ? 1 : 0);
            container.set(Constants.ROTATION_KEY, PersistentDataType.STRING, settings.getRotation());
            ShopHologram shopHologram = ShopHologram.getHologram(blockState.getLocation(), player);
            shopHologram.updatePosition();
            shopHologram.updateDsell();
            shopHologram.updateDbuy();
            ShopSettings newSettings = ShopContainer.getShopSettings(blockState.getLocation());
            newSettings.setMsgtoggle(settings.isMsgtoggle());
            newSettings.setDbuy(settings.isDbuy());
            newSettings.setDsell(settings.isDsell());
            newSettings.setAdmins(settings.getAdmins());
            newSettings.setShareincome(settings.isShareincome());
            newSettings.setRotation(settings.getRotation());
            blockState.update();
            player.sendMessage(LanguageManager.getInstance().pastedShopSettings());
        }
    }

    private void pasteShopSettings(Player player, String args, Block target) {
        BlockState blockState = getLookedAtBlockStateIfOwner(player, true, false, target);
        if (blockState != null) {
            // owner confirmed
            PersistentDataContainer container = ((TileState) blockState).getPersistentDataContainer();
            ShopSettings settings = settingsHashMap.get(player.getUniqueId());
            DatabaseManager db = EzChestShop.getPlugin().getDatabase();
            String sloc = Utils.LocationtoString(blockState.getLocation());

            for (String arg : args.split(",")) {
                ShopSettings newSettings = ShopContainer.getShopSettings(blockState.getLocation());
                switch (arg) {
                    case "toggle-message": {
                        db.setBool("location", sloc, "msgToggle", "shopdata", settings.isMsgtoggle());
                        container.set(Constants.ENABLE_MESSAGE_KEY, PersistentDataType.INTEGER, settings.isMsgtoggle() ? 1 : 0);
                        newSettings.setMsgtoggle(settings.isMsgtoggle());
                        break;
                    }
                    case "toggle-buying": {
                        db.setBool("location", sloc, "buyDisabled", "shopdata", settings.isDbuy());
                        container.set(Constants.DISABLE_BUY_KEY, PersistentDataType.INTEGER, settings.isDbuy() ? 1 : 0);
                        newSettings.setDbuy(settings.isDbuy());
                        ShopHologram.getHologram(blockState.getLocation(), player).updateDbuy();
                        break;
                    }
                    case "toggle-selling": {
                        db.setBool("location", sloc, "sellDisabled", "shopdata", settings.isDsell());
                        container.set(Constants.DISABLE_SELL_KEY, PersistentDataType.INTEGER, settings.isDsell() ? 1 : 0);
                        newSettings.setDsell(settings.isDsell());
                        ShopHologram.getHologram(blockState.getLocation(), player).updateDsell();
                        break;
                    }
                    case "admins": {
                        String admins = settings.getAdmins() == null ? "none" : settings.getAdmins();
                        db.setString("location", sloc, "admins", "shopdata", admins);
                        container.set(Constants.ADMIN_LIST_KEY, PersistentDataType.STRING, admins);
                        newSettings.setAdmins(settings.getAdmins());
                        break;
                    }
                    case "toggle-shared-income": {
                        db.setBool("location", sloc, "shareIncome", "shopdata", settings.isShareincome());
                        container.set(Constants.ENABLE_SHARED_INCOME_KEY, PersistentDataType.INTEGER, settings.isShareincome() ? 1 : 0);
                        newSettings.setShareincome(settings.isShareincome());
                        break;
                    }
                    case "change-rotation": {
                        db.setString("location", sloc, "rotation", "shopdata", settings.getRotation());
                        container.set(Constants.ROTATION_KEY, PersistentDataType.STRING, settings.getRotation());
                        ShopHologram.getHologram(blockState.getLocation(), player).updatePosition();
                        newSettings.setRotation(settings.getRotation());
                        break;
                    }
                }
            }
            blockState.update();
            player.sendMessage(LanguageManager.getInstance().pastedShopSettings());
        }
    }

    private void modifyShopSettings(Player player, SettingType type, String data, Block target) {
        BlockState blockState = getLookedAtBlockStateIfOwner(player, true, false, target);
        if (blockState != null) {
            ShopSettings settings = ShopContainer.getShopSettings(blockState.getLocation());
            DatabaseManager db = EzChestShop.getPlugin().getDatabase();
            String sloc = Utils.LocationtoString(blockState.getLocation());
            PersistentDataContainer container = ((TileState) blockState).getPersistentDataContainer();
            ShopHologram shopHologram = ShopHologram.getHologram(blockState.getLocation(), player);
            switch (type) {
                case DBUY:
                    settings.setDbuy(data.isEmpty() ? !settings.isDbuy() : data.equals("true"));
                    db.setBool("location", sloc, "buyDisabled", "shopdata", settings.isDbuy());
                    container.set(Constants.DISABLE_BUY_KEY, PersistentDataType.INTEGER, settings.isDbuy() ? 1 : 0);
                    if (settings.isDbuy()) {
                        player.sendMessage(LanguageManager.getInstance().disableBuyingOnInChat());
                    } else {
                        player.sendMessage(LanguageManager.getInstance().disableBuyingOffInChat());
                    }
                    shopHologram.updateDbuy();
                    break;
                case DSELL:
                    settings.setDsell(data.isEmpty() ? !settings.isDsell() : data.equals("true"));
                    db.setBool("location", sloc, "sellDisabled", "shopdata", settings.isDsell());
                    container.set(Constants.DISABLE_SELL_KEY, PersistentDataType.INTEGER, settings.isDsell() ? 1 : 0);
                    if (settings.isDsell()) {
                        player.sendMessage(LanguageManager.getInstance().disableSellingOnInChat());
                    } else {
                        player.sendMessage(LanguageManager.getInstance().disableSellingOffInChat());
                    }
                    shopHologram.updateDsell();
                    break;
                case ADMINS:
                    if (data.equalsIgnoreCase("clear")) {
                        data = null;
                        player.sendMessage(LanguageManager.getInstance().clearedAdmins());
                    } else if (data.startsWith("+")) {
                        data = data.replace("+", "");
                        List<UUID> oldData = (settings.getAdmins() == null || settings.getAdmins().equals("none")) ? new ArrayList<>() :
                                new ArrayList<>(Arrays.asList(settings.getAdmins().split("@")))
                                        .stream().map(UUID::fromString).toList();
                        List<UUID> newPlayers = Arrays.stream(data.split(","))
                                .map(Bukkit::getOfflinePlayer)
                                .filter(OfflinePlayer::hasPlayedBefore)
                                .map(OfflinePlayer::getUniqueId)
                                .filter(id -> !oldData.contains(id))
                                .toList();
                        String newData = newPlayers.stream().map(UUID::toString).collect(Collectors.joining("@"));
                        if (!newData.equalsIgnoreCase("")) {
                            if (!newPlayers.contains(player.getUniqueId())) {
                                if (settings.getAdmins() == null || settings.getAdmins().equalsIgnoreCase("")) {
                                    data = newData;
                                } else {
                                    data = settings.getAdmins() + "@" + newData;
                                }
                                player.sendMessage(LanguageManager.getInstance().sucAdminAdded(newPlayers.stream()
                                        .map(s -> Bukkit.getOfflinePlayer(s).getName())
                                        .collect(Collectors.joining(", "))));
                            } else {
                                data = settings.getAdmins();
                                player.sendMessage(LanguageManager.getInstance().selfAdmin());
                            }
                        } else {
                            data = settings.getAdmins();
                            player.sendMessage(LanguageManager.getInstance().noPlayer());
                        }

                    } else if (data.startsWith("-")) {
                        data = data.replace("-", "");
                        List<String> oldData = (settings.getAdmins() == null || settings.getAdmins().equalsIgnoreCase("none"))
                                ? new ArrayList<>() : new ArrayList<>(Arrays.asList(settings.getAdmins().split("@")));
                        List<UUID> newPlayers = Arrays.stream(data.split(","))
                                .map(Bukkit::getOfflinePlayer)
                                .filter(OfflinePlayer::hasPlayedBefore)
                                .map(OfflinePlayer::getUniqueId)
                                .toList();
                        if (!newPlayers.isEmpty()) {
                            List<String> newData = newPlayers.stream().map(UUID::toString).toList();
                            oldData.removeAll(newData);
                            data = String.join("@", oldData);
                            player.sendMessage(LanguageManager.getInstance().sucAdminRemoved(newPlayers.stream()
                                    .map(s -> Bukkit.getOfflinePlayer(s).getName())
                                    .collect(Collectors.joining(", "))));
                            if (data.trim().equalsIgnoreCase("")) {
                                data = null;
                            }
                        } else {
                            data = settings.getAdmins();
                            player.sendMessage(LanguageManager.getInstance().noPlayer());
                        }
                    }
                    if (data == null || data.equalsIgnoreCase("none")) {
                        data = null;
                    } else if (data.contains("none@")) {
                        data = data.replace("none@", "");
                    }
                    settings.setAdmins(data);
                    String admins = settings.getAdmins() == null ? "none" : settings.getAdmins();
                    db.setString("location", sloc, "admins", "shopdata", admins);
                    container.set(Constants.ADMIN_LIST_KEY, PersistentDataType.STRING, admins);
                    break;
                case TOGGLE_MSG:
                    settings.setMsgtoggle(!settings.isMsgtoggle());
                    db.setBool("location", sloc, "msgToggle", "shopdata", settings.isMsgtoggle());
                    container.set(Constants.ENABLE_MESSAGE_KEY, PersistentDataType.INTEGER, settings.isMsgtoggle() ? 1 : 0);
                    if (settings.isMsgtoggle()) {
                        player.sendMessage(LanguageManager.getInstance().toggleTransactionMessageOnInChat());
                    } else {
                        player.sendMessage(LanguageManager.getInstance().toggleTransactionMessageOffInChat());
                    }
                    break;
                case SHAREINCOME:
                    settings.setShareincome(!settings.isShareincome());
                    db.setBool("location", sloc, "shareIncome", "shopdata", settings.isShareincome());
                    container.set(Constants.ENABLE_SHARED_INCOME_KEY, PersistentDataType.INTEGER, settings.isShareincome() ? 1 : 0);
                    if (settings.isShareincome()) {
                        player.sendMessage(LanguageManager.getInstance().sharedIncomeOnInChat());
                    } else {
                        player.sendMessage(LanguageManager.getInstance().sharedIncomeOffInChat());
                    }
                    break;
                case ROTATION:
                    settings.setRotation(Utils.rotations.contains(data) ? data : Utils.getNextRotation(settings.getRotation()));
                    db.setString("location", sloc, "rotation", "shopdata", settings.getRotation());
                    container.set(Constants.ROTATION_KEY, PersistentDataType.STRING, settings.getRotation());
                    player.sendMessage(LanguageManager.getInstance().rotateHologramInChat(settings.getRotation()));
                    ShopHologram.getHologram(blockState.getLocation(), player).updatePosition();
                    break;
            }

            blockState.update();
        }
    }

    private boolean checkIfLocation(Location location, Player player) {
        Block exactBlock = player.getTargetBlockExact(6);
        if (exactBlock == null || exactBlock.getType() == Material.AIR || !(Utils.isApplicableContainer(exactBlock))) {
            return false;
        }

        BlockBreakEvent newevent = new BlockBreakEvent(exactBlock, player);
        Utils.blockBreakMap.put(player.getName(), exactBlock);
        Bukkit.getServer().getPluginManager().callEvent(newevent);

        boolean result = true;
        if (!Utils.blockBreakMap.containsKey(player.getName()) || Utils.blockBreakMap.get(player.getName()) != exactBlock) {
            result = false;
        }
        if (player.hasPermission("ecs.admin")) {
            result = true;
        }
        Utils.blockBreakMap.remove(player.getName());
        return result;
    }

    public boolean isPositive(double price) {
        return price >= 0;
    }

    private Chest ifItsADoubleChestShop(Block block) {
        //double chest
        if (block instanceof Chest) {
            Chest chest = (Chest) block.getState(false);
            Inventory inventory = chest.getInventory();
            if (inventory instanceof DoubleChestInventory) {
                DoubleChest doubleChest = (DoubleChest) chest.getInventory().getHolder(false);
                Chest leftchest = (Chest) doubleChest.getLeftSide(false);
                Chest rightchest = (Chest) doubleChest.getRightSide(false);

                if (leftchest.getPersistentDataContainer().has(Constants.OWNER_KEY, PersistentDataType.STRING)
                        || rightchest.getPersistentDataContainer().has(Constants.OWNER_KEY, PersistentDataType.STRING)) {
                    Chest rightone;

                    if (!leftchest.getPersistentDataContainer().isEmpty()) {
                        rightone = leftchest;
                    } else {
                        rightone = rightchest;
                    }

                    return rightone;
                }
            }
        }
        return null;
    }

    private BlockState getLookedAtBlockStateIfOwner(Player player, boolean sendErrors, boolean isCreateOrRemove, Block target) {
        if (target != null && target.getType() != Material.AIR) {
            BlockState blockState = target.getState(false);
            if (EzChestShop.slimefun) {
                boolean sfresult = BlockStorage.hasBlockInfo(blockState.getBlock().getLocation());
                if (sfresult) {
                    player.sendMessage(LanguageManager.getInstance().slimeFunBlockNotSupported());
                    return null;
                }
            }
            if (blockState instanceof TileState) {
                if (Utils.isApplicableContainer(target)) {
                    if (checkIfLocation(target.getLocation(), player)) {
                        if (Constants.TAG_CHEST.contains(target.getType())) {
                            Inventory inventory = Utils.getBlockInventory(target);
                            if (Utils.getBlockInventory(target) instanceof DoubleChestInventory) {
                                DoubleChest doubleChest = (DoubleChest) inventory.getHolder(false);
                                Chest chestleft = (Chest) doubleChest.getLeftSide(false);
                                Chest chestright = (Chest) doubleChest.getRightSide(false);

                                if (!chestleft.getPersistentDataContainer().isEmpty()) {
                                    blockState = chestleft.getBlock().getState(false);
                                } else {
                                    blockState = chestright.getBlock().getState(false);
                                }
                            }
                        }

                        PersistentDataContainer container = ((TileState) blockState).getPersistentDataContainer();
                        Chest chkIfDCS = ifItsADoubleChestShop(target);

                        if (container.has(Constants.OWNER_KEY, PersistentDataType.STRING) || chkIfDCS != null) {
                            if (chkIfDCS != null) {
                                BlockState newBlockState = chkIfDCS.getBlock().getState(false);
                                container = ((TileState) newBlockState).getPersistentDataContainer();
                            }

                            String owner = Bukkit.getOfflinePlayer(UUID.fromString(container.get(Constants.OWNER_KEY, PersistentDataType.STRING))).getName();

                            if (player.getName().equalsIgnoreCase(owner)) {
                                return blockState;
                            } else if (sendErrors) {
                                player.sendMessage(LanguageManager.getInstance().notOwner());
                            }
                        } else if (sendErrors) {
                            player.sendMessage(LanguageManager.getInstance().notAChestOrChestShop());
                        }
                    } else if (sendErrors) {
                        if (isCreateOrRemove) {
                            player.spigot().sendMessage(LanguageManager.getInstance().notAllowedToCreateOrRemove(player));
                        } else {
                            player.sendMessage(LanguageManager.getInstance().notAChestOrChestShop());
                        }
                    }
                } else if (sendErrors) {
                    player.sendMessage(LanguageManager.getInstance().notAChestOrChestShop());
                }
            } else if (sendErrors) {
                player.sendMessage(LanguageManager.getInstance().notAChestOrChestShop());
            }
        } else if (sendErrors) {
            player.sendMessage(LanguageManager.getInstance().notAChestOrChestShop());
        }
        return null;
    }

    private Block getCorrectBlock(Block target) {
        if (target == null) return null;
        Inventory inventory = Utils.getBlockInventory(target);
        if (inventory instanceof DoubleChestInventory) {
            //double chest

            DoubleChest doubleChest = (DoubleChest) inventory.getHolder(false);
            Chest leftchest = (Chest) doubleChest.getLeftSide(false);
            Chest rightchest = (Chest) doubleChest.getRightSide(false);

            if (leftchest.getPersistentDataContainer().has(Constants.OWNER_KEY, PersistentDataType.STRING)
                    || rightchest.getPersistentDataContainer().has(Constants.OWNER_KEY, PersistentDataType.STRING)) {
                if (!leftchest.getPersistentDataContainer().isEmpty()) {
                    target = leftchest.getBlock();
                } else {
                    target = rightchest.getBlock();
                }
            }
        }
        return target;
    }

    private void emptyShopsCommand(Player player) {
        if (Utils.enabledOutlines.contains(player.getUniqueId())) {
            List<BlockOutline> playerOutlinedShops = new ArrayList<>(Utils.activeOutlines.values());
            for (BlockOutline outline : playerOutlinedShops) {
                if (outline == null) continue;
                if (outline.player.getUniqueId().equals(player.getUniqueId())) {
                    outline.hideOutline();
                }
            }
            Utils.enabledOutlines.remove(player.getUniqueId());
            player.sendMessage(LanguageManager.getInstance().emptyShopHighlightedDisabled());

        } else {
            Utils.enabledOutlines.add(player.getUniqueId());
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

            List<Block> blocks = Utils.getNearbyEmptyShopForAdmins(player);
            player.sendMessage(LanguageManager.getInstance().emptyShopHighlightedEnabled(blocks.size()));
            AtomicInteger actionBarCounter = new AtomicInteger();
            EzChestShop.getScheduler().runTaskLaterAsynchronously(() -> {
                //Iterate through each block with an asychronous delay of 5 ticks
                blocks.forEach(b -> {
                    BlockOutline outline = new BlockOutline(player, b);
                    int index = blocks.indexOf(b);
                    EzChestShop.getScheduler().runTaskLater(player, () -> {
                        outline.showOutline();
                        if (outline.muted) {
                            return;
                        }
                        actionBarCounter.getAndIncrement();
                        Utils.sendActionBar(player, LanguageManager.getInstance().emptyShopActionBar(actionBarCounter.get()));
                        player.playNote(player.getLocation(), Instrument.BIT, Note.flat(1, tones.get(noteIndex.get())));
                        noteIndex.getAndIncrement();
                        if (noteIndex.get() == 7) {
                            noteIndex.set(0);
                        }
                    }, 2L * index);
                });
            }, 1L);
        }
    }
}
