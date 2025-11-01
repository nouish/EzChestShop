package me.deadlight.ezchestshop.utils.objects;

import me.deadlight.ezchestshop.Constants;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.ShulkerBox;
import org.bukkit.persistence.PersistentDataContainer;

public class ChatWaitObject {

    public String answer;
    public String type;
    public Block containerBlock;
    public PersistentDataContainer dataContainer;

    public ChatWaitObject(String answer, String type, Block containerBlock) {
        this.answer = answer;
        this.type = type;
        this.containerBlock = containerBlock;
        this.dataContainer = getDataContainer(containerBlock.getState(false), containerBlock.getType());
    }

    public ChatWaitObject(String answer, String type, Block containerBlock, PersistentDataContainer dataContainer) {
        this.answer = answer;
        this.type = type;
        this.containerBlock = containerBlock;
        this.dataContainer = dataContainer;
    }

    private PersistentDataContainer getDataContainer(BlockState state, Material type) {
        if (Constants.TAG_CHEST.contains(type)) {
            return ((Chest) state).getPersistentDataContainer();
        } else if (type == Material.BARREL) {
            return ((Barrel) state).getPersistentDataContainer();
        } else if (Tag.SHULKER_BOXES.isTagged(type)) {
            return ((ShulkerBox) state).getPersistentDataContainer();
        }
        return null;
    }
}
