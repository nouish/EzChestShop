package me.deadlight.ezchestshop.utils.objects;

import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;

public class EzShop {

    private Location location;
    private ShopSettings settings;
    private OfflinePlayer owner;
    private ItemStack shopItem;
    private double buyPrice;
    private double sellPrice;
    private SqlQueue sqlQueue;

    public EzShop(Location location, OfflinePlayer owner, ItemStack shopItem, double buyPrice, double sellPrice, ShopSettings settings) {
        this.location = location;
        this.owner = owner;
        this.shopItem = shopItem;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.settings = settings;
        this.settings.assignShop(this);
        this.settings.createSqlQueue();
        this.createSqlQueue();
    }

    public EzShop(Location location, String ownerID, ItemStack shopItem, double buyPrice, double sellPrice, ShopSettings settings) {
        this.location = location;
        this.owner = Bukkit.getOfflinePlayer(UUID.fromString(ownerID));
        this.shopItem = shopItem;
        this.buyPrice = buyPrice;
        this.sellPrice = sellPrice;
        this.settings = settings;
        this.settings.assignShop(this);
        this.settings.createSqlQueue();
        this.createSqlQueue();
    }

    public Location getLocation() {
        return location;
    }

    public ShopSettings getSettings() {
        return settings;
    }

    public ItemStack getShopItem() {
        return shopItem.clone();
    }

    public void setLocation(Location location) {
        this.location = location;
    }

    public void setSettings(ShopSettings settings) {
        this.settings = settings;
    }

    public UUID getOwnerID() {
        return owner.getUniqueId();
    }

    public SqlQueue getSqlQueue() {
        return sqlQueue;
    }

    public void createSqlQueue() {
        this.sqlQueue = new SqlQueue(this.getLocation(), getSettings(), this);
    }

    public void setOwner(OfflinePlayer owner) {
        this.owner = owner;
    }

    public double getBuyPrice() {
        return buyPrice;
    }

    public void setBuyPrice(double buyPrice) {
        this.buyPrice = buyPrice;
    }

    public double getSellPrice() {
        return sellPrice;
    }

    public void setSellPrice(double sellPrice) {
        this.sellPrice = sellPrice;
    }
}
