package me.deadlight.ezchestshop.utils.worldguard;

import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.flags.registry.FlagConflictException;
import me.deadlight.ezchestshop.EzChestShop;

public final class FlagRegistry {

    // All the flags:

    public static StateFlag CREATE_SHOP;
    public static StateFlag CREATE_ADMIN_SHOP;
    public static StateFlag REMOVE_SHOP;
    public static StateFlag REMOVE_ADMIN_SHOP;
    public static StateFlag USE_SHOP;
    public static StateFlag USE_ADMIN_SHOP;

    public static void onLoad() {
        CREATE_SHOP = registerStateFlag("ecs-create-shop", true);
        CREATE_ADMIN_SHOP = registerStateFlag("ecs-create-admin-shop", true);
        REMOVE_SHOP = registerStateFlag("ecs-remove-shop", true);
        REMOVE_ADMIN_SHOP = registerStateFlag("ecs-remove-admin-shop", true);
        USE_SHOP = registerStateFlag("ecs-use-shop", true);
        USE_ADMIN_SHOP = registerStateFlag("ecs-use-admin-shop", true);
    }


    // register a Boolean based flag:
    private static StateFlag registerStateFlag(String name, boolean def) {
        com.sk89q.worldguard.protection.flags.registry.FlagRegistry registry = WorldGuard.getInstance().getFlagRegistry();

        if (registry.get(name) instanceof StateFlag flag) {
            return flag;
        }

        try {
            StateFlag flag = new StateFlag(name, def);
            registry.register(flag);
            return flag;
        } catch (FlagConflictException e) {
            // Logging to help gather information regarding this bug: https://github.com/nouish/EzChestShop/issues/50

            // I suspect there probably is no good reason to repeat this now...
            // But this is the previous implementation, so I'll keep it.
            if (registry.get(name) instanceof StateFlag flag) {
                EzChestShop.logger().warn("Conflict creating flag '{}', but found cached match.", name, e);
                return flag;
            }

            EzChestShop.logger().warn("Conflict creating flag '{}'", name, e);
        }

        // This will never run as there's a try catch above.
        // TODO: Should probably throw some kind of exception instead of silent failure.
        return null;
    }
}
