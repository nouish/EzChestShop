package me.deadlight.ezchestshop.utils.worldguard;

import java.util.Objects;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.util.Location;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import me.deadlight.ezchestshop.EzChestShop;
import org.bukkit.entity.Player;

public class WorldGuardUtils {

    public static Location convertLocation(org.bukkit.Location loc) {
        return BukkitAdapter.adapt(loc);
    }

    public static ApplicableRegionSet queryRegionSet(org.bukkit.Location loc) {
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        return container.createQuery().getApplicableRegions(convertLocation(loc));
    }

    public static boolean queryStateFlag(StateFlag flag, Player player) {
        Objects.requireNonNull(player, "player");
        if (flag == null) {
            // TODO: Throw on null, but first we need to solve https://github.com/nouish/EzChestShop/issues/50.
            EzChestShop.logger().warn("Attempted to check null flag!", new Throwable("Reveal your callstack!"));
            return true; // Just skip WorldGuard integration at this point.
        }
        LocalPlayer localPlayer = WorldGuardPlugin.inst().wrapPlayer(player);
        return queryRegionSet(player.getLocation()).testState(localPlayer, flag);
    }

    public static boolean queryStateFlag(StateFlag flag, org.bukkit.Location location) {
        Objects.requireNonNull(location, "location");
        if (flag == null) {
            // TODO: Throw on null, but first we need to solve https://github.com/nouish/EzChestShop/issues/50.
            EzChestShop.logger().warn("Attempted to check null flag!", new Throwable("Reveal your callstack!"));
            return true; // Just skip WorldGuard integration at this point.
        }
        return queryRegionSet(location).testState(null, flag);
    }
}
