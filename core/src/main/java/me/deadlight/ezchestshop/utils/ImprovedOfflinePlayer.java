package me.deadlight.ezchestshop.utils;

import org.bukkit.OfflinePlayer;

public abstract class ImprovedOfflinePlayer {
    protected OfflinePlayer player;
    protected boolean isOnline;
    protected boolean exists;

    public ImprovedOfflinePlayer() {
    }

    public ImprovedOfflinePlayer(OfflinePlayer player) {
        this.player = player;
        this.isOnline = player.isOnline();
        if (!isOnline) {
            exists = loadPlayerData();
        }
    }

    public boolean isOnline() {
        return isOnline;
    }

    public boolean hasPlayedBefore() {
        return player.hasPlayedBefore();
    }

    public abstract ImprovedOfflinePlayer fromOfflinePlayer(OfflinePlayer player);

    public abstract int getLevel();

    public abstract void setLevel(int level);

    public abstract float getExp();

    public abstract void setExp(float exp);

    public abstract int getExpToLevel();

    public abstract boolean loadPlayerData();

    public abstract boolean savePlayerData();

}
