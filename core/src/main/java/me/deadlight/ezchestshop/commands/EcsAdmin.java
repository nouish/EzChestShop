package me.deadlight.ezchestshop.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.Constants;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.data.LanguageManager;
import me.deadlight.ezchestshop.data.ShopCommandManager;
import me.deadlight.ezchestshop.data.ShopContainer;
import me.deadlight.ezchestshop.data.gui.GuiData;
import me.deadlight.ezchestshop.guis.GuiEditorGUI;
import me.deadlight.ezchestshop.utils.Utils;
import me.deadlight.ezchestshop.utils.holograms.ShopHologram;
import me.deadlight.ezchestshop.utils.objects.EzShop;
import me.deadlight.ezchestshop.utils.worldguard.FlagRegistry;
import me.deadlight.ezchestshop.utils.worldguard.WorldGuardUtils;
import me.mrCookieSlime.Slimefun.api.BlockStorage;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
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
import org.bukkit.entity.Player;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;
import org.jetbrains.annotations.NotNull;

public class EcsAdmin implements CommandExecutor, TabCompleter {
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (sender instanceof Player player) {
            int size = args.length;

            if (player.hasPermission("ecs.admin") || player.hasPermission("ecs.admin.remove") ||
                    player.hasPermission("ecs.admin.reload") || player.hasPermission("ecs.admin.create")) {
                if (size == 0) {
                    sendHelp(player);
                } else {

                    String firstarg = args[0];
                    if (firstarg.equalsIgnoreCase("remove") && (player.hasPermission("ecs.admin.remove") || player.hasPermission("ecs.admin"))) {
                        Block target = getCorrectBlock(player.getTargetBlockExact(6));
                        if (target != null) {
                            removeShop(player, args, target);
                        } else {
                            player.sendMessage(LanguageManager.getInstance().lookAtChest());
                        }
                    } else if (firstarg.equalsIgnoreCase("reload") && (player.hasPermission("ecs.admin.reload") || player.hasPermission("ecs.admin"))) {
                        reload();
                        player.sendMessage(Utils.colorify("&aEzChestShop successfully reloaded!"));
                    } else if (firstarg.equalsIgnoreCase("create") && (player.hasPermission("ecs.admin.create") || player.hasPermission("ecs.admin"))) {
                        Block target = getCorrectBlock(player.getTargetBlockExact(6));
                        if (target != null) {
                            if (size >= 3) {

                                if (isPositive(Double.parseDouble(args[1])) && isPositive(Double.parseDouble(args[2]))) {
                                    try {
                                        createShop(player, args, target);
                                    } catch (IOException e) {
                                        e.printStackTrace();
                                    }
                                } else {
                                    player.sendMessage(LanguageManager.getInstance().negativePrice());
                                }
                            } else {
                                player.sendMessage(LanguageManager.getInstance().notenoughARGS());
                            }
                        } else {
                            player.sendMessage(LanguageManager.getInstance().lookAtChest());
                        }
                    } else if (firstarg.equalsIgnoreCase("transfer-ownership") && (player.hasPermission("ecs.admin.transfer") || player.hasPermission("ecs.admin"))) {
                        Block target = getCorrectBlock(player.getTargetBlockExact(6));
                        if (size == 2) {
                            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);

                            if (op != null && op.hasPlayedBefore()) {
                                BlockState blockState = getLookedAtBlockState(player, true, false, target);
                                if (blockState != null) {
                                    player.spigot().sendMessage(LanguageManager.getInstance().shopTransferConfirm(args[1], true)); // Confirmation message similar to the clearprofit message.
                                }
                            } else {
                                player.sendMessage(LanguageManager.getInstance().noPlayer());
                            }
                        } else if (size == 3 && args[2].equals("-confirm")) {
                            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);

                            if (op != null && op.hasPlayedBefore()) {
                                BlockState blockState = getLookedAtBlockState(player, true, false, target);
                                if (blockState != null) {
                                    ShopContainer.transferOwner(blockState, op);
                                    ShopHologram.getHologram(blockState.getLocation(), player).updateOwner();
                                    player.sendMessage(LanguageManager.getInstance().shopTransferred(args[1]));
                                }
                            } else {
                                player.sendMessage(LanguageManager.getInstance().noPlayer());
                            }
                        } else {
                            sendHelp(player);
                        }
                    } else if (firstarg.equalsIgnoreCase("configure-guis")) {
                        new GuiEditorGUI().showGuiEditorOverview(player);
                    } else if (firstarg.equalsIgnoreCase("shop-commands")) {
                        if (!Config.shopCommandsEnabled) {
                            player.sendMessage(ChatColor.RED + "Enable this setting in the config!");
                            return false;
                        }
                        if (args.length == 1) {
                            Block target = player.getTargetBlockExact(5);
                            if (target != null) {
                                EzShop shop = ShopContainer.getShop(target.getLocation());
                                if (shop != null) {
                                    Config.shopCommandManager.showActionEditor(player, shop.getLocation());
                                } else {
                                    player.sendMessage(LanguageManager.getInstance().notAChestOrChestShop());
                                }
                            } else {
                                player.sendMessage(LanguageManager.getInstance().lookAtChest());
                            }
                        } else {
                            if (args[1].startsWith("W:")) {
                                Location location = Utils.StringtoLocation(args[1]);
                                if (location != null) {
                                    if (args.length < 3) {
                                        Config.shopCommandManager.showActionEditor(player, location);
                                    } else if (args.length < 4) {
                                        ShopCommandManager.ShopAction action = ShopCommandManager.ShopAction.valueOf(args[2]);
                                        if (!Config.shopCommandManager.hasActionOptions(action)) {
                                            // if the command doesn't have any options, directly show the command editor!
                                            Config.shopCommandManager.showCommandEditor(player, location, action, null);
                                        } else {
                                            Config.shopCommandManager.showOptionEditor(player, location, action);
                                        }
                                    } else if (args.length >= 4) {
                                        ShopCommandManager.ShopAction action = ShopCommandManager.ShopAction.valueOf(args[2]);
                                        String option = args[3].equals("none") ? null : args[3];
                                        if (args.length == 4) {
                                            Config.shopCommandManager.showCommandEditor(player, location, action, option);
                                        } else {
                                            // longer then 3 args
                                            if (args[4].equals("add")) {
                                                if (args.length >= 5) {
                                                    // get the command from any further args
                                                    String newCommand = "";
                                                    for (int i = 5; i < args.length; i++) {
                                                        newCommand += args[i] + " ";
                                                    }
                                                    if (!newCommand.trim().equals("")) {
                                                        Config.shopCommandManager.addCommand(player, location, action, option, newCommand.trim());
                                                    }
                                                }
                                            } else if (args[4].equals("move")) {
                                                if (args.length == 7) {
                                                    Config.shopCommandManager.moveCommandIndex(player, location, action, option, Integer.parseInt(args[5]), args[6].equals("up"));
                                                }

                                            } else if (args[4].equals("edit")) {
                                                if (args.length >= 7) {
                                                    // get the command from any further args
                                                    String newCommand = "";
                                                    for (int i = 6; i < args.length; i++) {
                                                        newCommand += args[i] + " ";
                                                    }
                                                    if (newCommand.trim().equals("")) {
                                                        Config.shopCommandManager.removeCommand(player, location, action, option, Integer.parseInt(args[5]));
                                                    } else {
                                                        Config.shopCommandManager.editCommand(player, location, action, option, Integer.parseInt(args[5]), newCommand.trim());
                                                    }
                                                }
                                            } else if (args[4].equals("remove")) {
                                                if (args.length == 6) {
                                                    Config.shopCommandManager.removeCommand(player, location, action, option, Integer.parseInt(args[5]));
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        sendHelp(player);
                    }
                }
            } else {
                Utils.sendVersionMessage(player);
            }
        } else {
            if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
                reload();
                sender.sendMessage(Utils.colorify("&aEzChestShop successfully reloaded!"));
            } else {
                sender.sendMessage(Utils.colorify("&cThis command can only be executed by a player or when used for reloading!"));
            }
        }

        return false;
    }

    private void reload() {
        Config.loadConfig();
        ShopHologram.reloadAll();
        LanguageManager.reloadLanguages();
        GuiData.loadGuiData();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> fList = new ArrayList<>();
        List<String> list_firstarg = Arrays.asList("create", "reload", "remove", "help", "transfer-ownership", "configure-guis", "shop-commands");
        List<String> list_create_1 = Arrays.asList("[BuyPrice]");
        List<String> list_create_2 = Arrays.asList("[SellPrice]");
        List<String> list_transfer_2 = Arrays.asList("-confirm");
        if (sender instanceof Player p) {
            List<String> list_shop_commands_1;
            if (p.getTargetBlockExact(6) != null) {
                list_shop_commands_1 = Arrays.asList(Utils.LocationRoundedtoString(p.getTargetBlockExact(6).getLocation(), 0));
            } else {
                list_shop_commands_1 = Arrays.asList("Look at a shop for auto location completion!");
            }
            List<String> list_shop_commands_2 = Arrays.asList(Arrays.stream(ShopCommandManager.ShopAction.values()).map(Enum::name).toArray(String[]::new));
            List<String> list_shop_commands_3 = Arrays.asList("[option]");
            List<String> list_shop_commands_4 = Arrays.asList("add", "move", "edit", "remove");
            List<String> list_shop_commands_5 = Arrays.asList("[index]");
            List<String> list_shop_commands_editcreate_6 = Arrays.asList("[command]");
            List<String> list_shop_commands_move_6 = Arrays.asList("up", "down");
            if (p.hasPermission("ecs.admin") || p.hasPermission("ecs.admin.reload") || p.hasPermission("ecs.admin.create") || p.hasPermission("ecs.admin.remove")) {
                if (args.length == 1)
                    StringUtil.copyPartialMatches(args[0], list_firstarg, fList);
                if (args.length > 1 && args[0].equalsIgnoreCase("create")) {
                    if (args.length == 2)
                        StringUtil.copyPartialMatches(args[1], list_create_1, fList);
                    if (args.length == 3)
                        StringUtil.copyPartialMatches(args[2], list_create_2, fList);
                } else if (args.length > 1 && args[0].equalsIgnoreCase("transfer-ownership")) {
                    if (args.length == 3) {
                        StringUtil.copyPartialMatches(args[2], list_transfer_2, fList);
                    } else {
                        // If null is returned a list of online players will be suggested
                        return null;
                    }
                } else if (args.length > 1 && args[0].equalsIgnoreCase("shop-commands")) {
                    if (args.length == 2) {
                        StringUtil.copyPartialMatches(args[1], list_shop_commands_1, fList);
                    } else if (args.length == 3) {
                        StringUtil.copyPartialMatches(args[2], list_shop_commands_2, fList);
                    } else if (args.length == 4) {
                        StringUtil.copyPartialMatches(args[3], list_shop_commands_3, fList);
                    } else if (args.length == 5) {
                        StringUtil.copyPartialMatches(args[4], list_shop_commands_4, fList);
                    } else if (args.length == 6) {
                        if (args[4].equalsIgnoreCase("add")) {
                            StringUtil.copyPartialMatches(args[5], list_shop_commands_editcreate_6, fList);
                        } else {
                            StringUtil.copyPartialMatches(args[5], list_shop_commands_5, fList);
                        }
                    } else if (args.length >= 7) {
                        if (args[4].equalsIgnoreCase("add") || args[4].equalsIgnoreCase("edit")) {
                            StringUtil.copyPartialMatches(args[args.length - 1], list_shop_commands_editcreate_6, fList);
                        } else if (args[4].equalsIgnoreCase("move") && args.length == 7) {
                            StringUtil.copyPartialMatches(args[6], list_shop_commands_move_6, fList);
                        }
                    }
                }
            }
        }
        return fList;
    }


    private void sendHelp(Player player) {
        player.spigot().sendMessage(LanguageManager.getInstance().cmdadminHelp());
    }

    private void removeShop(Player player, String[] args, Block target) {
        if (target != null && target.getType() != Material.AIR) {
            //slimefun check
            if (EzChestShop.slimefun) {
                boolean sfresult = BlockStorage.hasBlockInfo(target.getLocation());
                if (sfresult) {
                    player.sendMessage(LanguageManager.getInstance().slimeFunBlockNotSupported());
                    return;
                }
            }
            BlockState blockState = target.getState(false);
            if (blockState instanceof TileState state) {
                if (Utils.isApplicableContainer(target)) {
                    PersistentDataContainer container = state.getPersistentDataContainer();

                    if (container.has(Constants.OWNER_KEY, PersistentDataType.STRING)) {
                        if (EzChestShop.worldguard) {
                            if (container.getOrDefault(Constants.ENABLE_ADMINSHOP_KEY, PersistentDataType.INTEGER, 0) == 1) {
                                if (!WorldGuardUtils.queryStateFlag(FlagRegistry.REMOVE_ADMIN_SHOP, player)) {
                                    player.spigot().sendMessage(LanguageManager.getInstance().notAllowedToCreateOrRemove(player));
                                    return;
                                }
                            } else {
                                if (!WorldGuardUtils.queryStateFlag(FlagRegistry.REMOVE_SHOP, player)) {
                                    player.spigot().sendMessage(LanguageManager.getInstance().notAllowedToCreateOrRemove(player));
                                    return;
                                }
                            }
                        }

                        container.remove(Constants.OWNER_KEY);
                        container.remove(Constants.BUY_PRICE_KEY);
                        container.remove(Constants.SELL_PRICE_KEY);
                        container.remove(Constants.ITEM_KEY);

                        try {
                            container.remove(Constants.ENABLE_MESSAGE_KEY);
                            container.remove(Constants.DISABLE_BUY_KEY);
                            container.remove(Constants.DISABLE_SELL_KEY);
                            container.remove(Constants.ADMIN_LIST_KEY);
                            container.remove(Constants.ENABLE_SHARED_INCOME_KEY);
                            container.remove(Constants.ENABLE_ADMINSHOP_KEY);
                            //msgtoggle 0/1
                            //dbuy 0/1
                            //dsell 0/1
                            //admins [list of uuids seperated with @ in string form]
                            //shareincome 0/1
                            //logs [list of infos seperated by @ in string form]
                            //trans [list of infos seperated by @ in string form]
                            //adminshop 0/1

                        } catch (Exception ex) {
                            //noting really worrying
                        }

                        ShopContainer.deleteShop(blockState.getLocation());
                        ShopHologram.hideForAll(blockState.getLocation());
                        state.update();
                        player.sendMessage(LanguageManager.getInstance().chestShopRemoved());
                    } else {
                        player.sendMessage(LanguageManager.getInstance().notAChestOrChestShop());
                    }
                } else {
                    player.sendMessage(LanguageManager.getInstance().notAChestOrChestShop());
                }
            } else {
                player.sendMessage(LanguageManager.getInstance().notAChestOrChestShop());
            }
        } else {
            player.sendMessage(LanguageManager.getInstance().notAChestOrChestShop());
        }
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
            //slimefun check
            if (EzChestShop.slimefun) {
                boolean sfresult = BlockStorage.hasBlockInfo(target.getLocation());
                if (sfresult) {
                    player.sendMessage(LanguageManager.getInstance().slimeFunBlockNotSupported());
                    return;
                }
            }

            if (EzChestShop.worldguard) {
                if (!WorldGuardUtils.queryStateFlag(FlagRegistry.CREATE_ADMIN_SHOP, player)) {
                    player.spigot().sendMessage(LanguageManager.getInstance().notAllowedToCreateOrRemove(player));
                    return;
                }
            }

            if (blockState instanceof TileState) {
                if (Utils.isApplicableContainer(target)) {
                    TileState state = (TileState) blockState;
                    PersistentDataContainer container = state.getPersistentDataContainer();

                    //owner (String) (player name)
                    //buy (double)
                    //sell (double)
                    //item (String) (itemstack)

                    //already a shop
                    if (container.has(Constants.OWNER_KEY, PersistentDataType.STRING)) {
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

                            int isDBuy = Config.settings_zero_equals_disabled ?
                                    (buyprice == 0 ? 1 : (Config.settings_defaults_dbuy ? 1 : 0))
                                    : (Config.settings_defaults_dbuy ? 1 : 0);
                            int isDSell = Config.settings_zero_equals_disabled ?
                                    (sellprice == 0 ? 1 : (Config.settings_defaults_dsell ? 1 : 0))
                                    : (Config.settings_defaults_dsell ? 1 : 0);

                            container.set(Constants.OWNER_KEY, PersistentDataType.STRING, player.getUniqueId().toString());
                            container.set(Constants.BUY_PRICE_KEY, PersistentDataType.DOUBLE, buyprice);
                            container.set(Constants.SELL_PRICE_KEY, PersistentDataType.DOUBLE, sellprice);
                            container.set(Constants.ENABLE_MESSAGE_KEY, PersistentDataType.INTEGER, Config.settings_defaults_transactions ? 1 : 0);
                            container.set(Constants.DISABLE_BUY_KEY, PersistentDataType.INTEGER, isDBuy);
                            container.set(Constants.DISABLE_SELL_KEY, PersistentDataType.INTEGER, isDSell);
                            container.set(Constants.ADMIN_LIST_KEY, PersistentDataType.STRING, "none");
                            container.set(Constants.ENABLE_SHARED_INCOME_KEY, PersistentDataType.INTEGER, Config.settings_defaults_shareprofits ? 1 : 0);
                            container.set(Constants.ENABLE_ADMINSHOP_KEY, PersistentDataType.INTEGER, 1);
                            container.set(Constants.ROTATION_KEY, PersistentDataType.STRING, Config.settings_defaults_rotation);

                            ShopContainer.createShop(target.getLocation(), player, thatItem, buyprice, sellprice, false,
                                    isDBuy == 1, isDSell == 1, "none", true, true, Config.settings_defaults_rotation);
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
                            player.sendMessage(LanguageManager.getInstance().shopCreated());
                        } else {
                            player.sendMessage(LanguageManager.getInstance().holdSomething());
                        }
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

    public boolean isPositive(double price) {
        if (price < 0) {
            return false;
        } else {
            return true;
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

    private BlockState getLookedAtBlockState(Player player, boolean sendErrors, boolean isCreateOrRemove, Block target) {
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
                            return blockState;
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
}
