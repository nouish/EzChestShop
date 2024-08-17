package me.deadlight.ezchestshop.internal.v1_17_R1;

import me.deadlight.ezchestshop.utils.ImprovedOfflinePlayer;
import me.deadlight.ezchestshop.utils.XPEconomy;
import net.minecraft.nbt.NBTCompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;

public class ImprovedOfflinePlayerImpl extends ImprovedOfflinePlayer {
    public static final ImprovedOfflinePlayer INSTANCE = new ImprovedOfflinePlayerImpl();
    private File file;
    private NBTTagCompound compound;

    public ImprovedOfflinePlayerImpl() {
        super();
    }

    public ImprovedOfflinePlayerImpl(OfflinePlayer player) {
        super(player);
    }

    @Override
    public ImprovedOfflinePlayer fromOfflinePlayer(OfflinePlayer player) {
        return new ImprovedOfflinePlayerImpl(player);
    }

    @Override
    public boolean loadPlayerData() {
        try {
            for(World w : Bukkit.getWorlds()) {
                this.file = new File(w.getWorldFolder(), "playerdata" + File.separator + this.player.getUniqueId() + ".dat");
                if(this.file.exists()){
                    this.compound = NBTCompressedStreamTools.a(new FileInputStream(this.file));
                    return true;
                }
            }
        }
        catch(Exception e) {
            e.printStackTrace();
        }
        return false;
    }


    public boolean savePlayerData() {
        if(this.exists) {
            try {
                NBTCompressedStreamTools.a(this.compound, new FileOutputStream(this.file));
                return true;
            }
            catch(Exception e) {
                e.printStackTrace();
            }
        }
        return false;
    }

    @Override
    public int getLevel() {
        if (isOnline) {
            return player.getPlayer().getLevel();
        } else {
            return compound.getInt("XpLevel");
        }
    }

    @Override
    public void setLevel(int level) {
        if (isOnline) {
            player.getPlayer().setLevel(level);
        } else {
            compound.setInt("XpLevel", level);
        }
    }

    @Override
    public float getExp() {
        if (isOnline) {
            return player.getPlayer().getExp();
        } else {
            return compound.getFloat("XpP");
        }
    }

    @Override
    public void setExp(float exp) {
        if (isOnline) {
            player.getPlayer().setExp(exp);
        } else {
            compound.setFloat("XpP", exp);
        }
    }

    @Override
    public int getExpToLevel() {
        if (isOnline) {
            return player.getPlayer().getExpToLevel();
        } else {
            int level = getLevel();
            return XPEconomy.getRequiredPointsToLevelUp(level);
        }
    }


}
