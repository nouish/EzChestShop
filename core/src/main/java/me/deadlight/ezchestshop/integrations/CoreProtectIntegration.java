package me.deadlight.ezchestshop.integrations;

import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

@ApiStatus.Internal
public final class CoreProtectIntegration {
    private static final Logger LOGGER = EzChestShop.logger();

    private static final String CORE_PROTECT_NAME = "CoreProtect";

    private static CoreProtectAPI api;

    public static void installIfEnabled() {
        Plugin plugin = EzChestShop.getPlugin().getServer().getPluginManager().getPlugin(CORE_PROTECT_NAME);

        if (plugin != null
                && plugin.isEnabled()
                && plugin instanceof CoreProtect co) {
            api = co.getAPI();
            // noinspection UnstableApiUsage
            LOGGER.info("CoreProtect: {}", co.getPluginMeta().getVersion());
        }
    }

    public static void expectInventoryChange(@NotNull Player player, @NotNull Location location) {
        if (!Config.coreprotect_integration || api == null) {
            return;
        }

        api.logContainerTransaction(player.getName(), location);
    }

    private CoreProtectIntegration() {
    }
}
