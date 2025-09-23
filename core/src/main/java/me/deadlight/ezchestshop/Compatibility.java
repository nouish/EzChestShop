package me.deadlight.ezchestshop;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import io.papermc.paper.ServerBuildInfo;
import org.jetbrains.annotations.ApiStatus.Internal;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

@Internal
final class Compatibility {
    private static final Logger LOGGER = EzChestShop.logger();
    private static final String COMPAT_WIKI_URL = "https://github.com/nouish/EzChestShop/wiki/Compatibility";

    static boolean verify() {
        final String serverBrand = ServerBuildInfo.buildInfo().brandName();

        // Leaf - Unreliable
        if (serverBrand.equalsIgnoreCase("Leaf")) {
            LOGGER.error("Leaf is not a supported platform due to unreliable behaviour, which repeatedly leads to unresolved plugin issues.");
            LOGGER.error("For more information: {}", COMPAT_WIKI_URL);
            return false;
        }

        // net.minecraftforge.common.MinecraftForge
        if (findBase64Class("bmV0Lm1pbmVjcmFmdGZvcmdlLmNvbW1vbi5NaW5lY3JhZnRGb3JnZQ==")) {
            detectedHybrid("Forge");
            return false;
        }

        // net.fabricmc.loader.launch.knot.KnotServer
        if (findBase64Class("bmV0LmZhYnJpY21jLmxvYWRlci5sYXVuY2gua25vdC5Lbm90U2VydmVy")) {
            detectedHybrid("Fabric");
            return false;
        }

        return true;
    }

    private static void detectedHybrid(@NotNull String name) {
        LOGGER.error("You seem to be running an incompatible {} hybrid fork.", name);
        LOGGER.error("For more information: {}", COMPAT_WIKI_URL);
    }

    private static boolean findBase64Class(@NotNull String encodedClassname) {
        return findClass(new String(Base64.getDecoder().decode(encodedClassname), StandardCharsets.UTF_8));
    }

    private static boolean findClass(@NotNull String classname) {
        try {
            Class.forName(classname);
            LOGGER.trace("Detected class: {}", classname);
            return true;
        } catch (ClassNotFoundException ignored) {
            LOGGER.trace("No such class: {}", classname);
            return false;
        }
    }
}
