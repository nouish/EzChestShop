package me.deadlight.ezchestshop.utils;

import java.util.concurrent.ThreadLocalRandom;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.EzChestShopConstants;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.DoubleChest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.DoubleChestInventory;
import org.bukkit.inventory.Inventory;

import static me.deadlight.ezchestshop.utils.Utils.nmsHandle;

public class BlockOutline {

    public Player player;
    public Block block;
    public int outlineID;
    public int destroyAfter; //set seconds to destroy after
    public boolean muted = false;
    public boolean isMadeFromACheck = false;
    public BlockOutline aParentOrChild;

    public BlockOutline(Player player, Block block) {
        this.player = player;
        this.block = block;
    }

    public void showOutline() {
        if (!isMadeFromACheck) {
            checkForDoubleChestShop();
        }
        outlineID = ThreadLocalRandom.current().nextInt(Integer.MAX_VALUE);
        nmsHandle.showOutline(player, block, outlineID);
        Utils.activeOutlines.put(outlineID, this);

        //check if destroyAfter is not null
        if (destroyAfter != 0) {
            EzChestShop.getScheduler().runTaskLaterAsynchronously(this::hideOutline, destroyAfter * 20L);
        }
    }

    public void hideOutline() {
        nmsHandle.destroyEntity(player, outlineID);
        Utils.activeOutlines.remove(outlineID);

        if (aParentOrChild != null) {
            aParentOrChild.hideRequestedOutline();
            aParentOrChild = null;
        }
    }

    public void hideRequestedOutline() {
        nmsHandle.destroyEntity(player, outlineID);
        Utils.activeOutlines.remove(outlineID);
        this.aParentOrChild = null;
    }

    private void checkForDoubleChestShop() {
        //check if the block is a chest and if it is a double chest
        if (EzChestShopConstants.TAG_CHEST.contains(block.getType())) {
            //now we check if its a double chest instance
            Inventory blockInv = Utils.getBlockInventory(block);
            if (blockInv instanceof DoubleChestInventory) {
                //get the other half of the double chest
                DoubleChest doubleChest = (DoubleChest) blockInv.getHolder(false);
                boolean isLeft = doubleChest.getLeftSide(false).getInventory().getLocation().equals(block.getLocation());
                if (isLeft) {
                    //so if it is left, we get the right side
                    Block rightBlock = doubleChest.getRightSide(false).getInventory().getLocation().getBlock();
                    BlockOutline outline = new BlockOutline(player, rightBlock);
                    outline.destroyAfter = this.destroyAfter;
                    outline.isMadeFromACheck = true;
                    outline.aParentOrChild = this;
                    this.aParentOrChild = outline;
                    outline.showOutline();
                } else {
                    Block leftBlock = doubleChest.getLeftSide(false).getInventory().getLocation().getBlock();
                    BlockOutline outline = new BlockOutline(player, leftBlock);
                    outline.destroyAfter = this.destroyAfter;
                    outline.isMadeFromACheck = true;
                    outline.aParentOrChild = this;
                    this.aParentOrChild = outline;
                    outline.showOutline();
                }
            }
        }
    }
}
