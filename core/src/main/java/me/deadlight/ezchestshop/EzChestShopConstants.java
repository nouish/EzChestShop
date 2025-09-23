package me.deadlight.ezchestshop;

import java.util.Set;

import com.google.common.collect.ImmutableSet;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jspecify.annotations.NullMarked;

@Internal
@NullMarked
public final class EzChestShopConstants {
    public static final int BSTATS_PROJECT_ID = 23732;

    public static final String REPOSITORY = "nouish/EzChestShop";
    public static final String DISCORD_LINK = "https://discord.gg/invite/gjV6BgKxFV";
    public static final String GITHUB_LINK = "https://github.com/" + REPOSITORY;
    public static final String WIKI_LINK = GITHUB_LINK + "/wiki";

    public static final Set<Material> TAG_CHEST = ImmutableSet.<Material> builder()
            .add(Material.CHEST)
            .add(Material.TRAPPED_CHEST)
            .build();

    // Continue to use the old namespace to ensure existing shops work.
    private static final String NAMESPACE = "ezchestshop";
    public static final NamespacedKey OWNER_KEY = createKey("owner");
    public static final NamespacedKey ADMIN_LIST_KEY = createKey("admins");
    public static final NamespacedKey ITEM_KEY = createKey("item");
    public static final NamespacedKey ENABLE_ADMINSHOP_KEY = createKey("adminshop");
    public static final NamespacedKey ENABLE_MESSAGE_KEY = createKey("msgtoggle");
    public static final NamespacedKey ENABLE_SHARED_INCOME_KEY = createKey("shareincome");
    public static final NamespacedKey DISABLE_BUY_KEY = createKey("dbuy");
    public static final NamespacedKey DISABLE_SELL_KEY = createKey("dsell");
    public static final NamespacedKey BUY_PRICE_KEY = createKey("buy");
    public static final NamespacedKey SELL_PRICE_KEY = createKey("sell");
    public static final NamespacedKey ROTATION_KEY = createKey("rotation");

    private static NamespacedKey createKey(String key) {
        return new NamespacedKey(EzChestShopConstants.NAMESPACE, key);
    }

    private EzChestShopConstants() {
    }
}
