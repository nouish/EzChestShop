package me.deadlight.ezchestshop.utils;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Range;

@ApiStatus.Internal
public final class VersionUtil {
    private VersionUtil() {}

    public enum MinecraftVersion {
        v1_21_2(4080, "1.21.2", "me.deadlight.ezchestshop.internal.v1_21_R2.NmsHandleImpl"),
        v1_21_1(3955, "1.21.1", "me.deadlight.ezchestshop.internal.v1_21_R1.NmsHandleImpl"),
        v1_21_0(3953, "1.21",   "me.deadlight.ezchestshop.internal.v1_21_R1.NmsHandleImpl"),
        v1_20_6(3839, "1.20.6", "me.deadlight.ezchestshop.internal.v1_20_R4.NmsHandleImpl"),
        v1_20_4(3700, "1.20.4", "me.deadlight.ezchestshop.internal.v1_20_R3.NmsHandleImpl"),
        v1_19_4(3337, "1.19.4", "me.deadlight.ezchestshop.internal.v1_19_R3.NmsHandleImpl"),
        v1_18_2(2975, "1.18.2", "me.deadlight.ezchestshop.internal.v1_18_R2.NmsHandleImpl"),
        v1_17_1(2730, "1.17.1", "me.deadlight.ezchestshop.internal.v1_17_R1.NmsHandleImpl"),
        v1_16_5(2586, "1.16.5", "me.deadlight.ezchestshop.internal.v1_16_R3.NmsHandleImpl");

        private static final Int2ObjectMap<MinecraftVersion> SUPPORTED_VERSIONS = new Int2ObjectOpenHashMap<>();

        static {
            for (MinecraftVersion version : MinecraftVersion.values()) {
                SUPPORTED_VERSIONS.put(version.getDataVersion(), version);
            }
        }

        private final int dataVersion;
        private final String version;
        private final String handle;

        MinecraftVersion(@Range(from = 0, to = Integer.MAX_VALUE) int dataVersion,
                         @NotNull String version,
                         @NotNull String handle) {
            this.dataVersion = dataVersion;
            this.version = Objects.requireNonNull(version);
            this.handle = Objects.requireNonNull(handle);
        }

        public final @Range(from = 0, to = Integer.MAX_VALUE) int getDataVersion() {
            return dataVersion;
        }

        public final @NotNull String getVersion() {
            return version;
        }

        public final @NotNull String getHandle() {
            return handle;
        }

        @Override
        public final String toString() {
            return version;
        }
    }

    public static Collection<MinecraftVersion> getSupportedVersions() {
        return Sets.newHashSet(MinecraftVersion.SUPPORTED_VERSIONS.values());
    }

    public static Optional<MinecraftVersion> getMinecraftVersion() {
        OptionalInt dataVersion = getDataVersion();
        if (dataVersion.isPresent()) {
            return Optional.ofNullable(MinecraftVersion.SUPPORTED_VERSIONS.get(dataVersion.getAsInt()));
        } else {
            return Optional.empty();
        }
    }

    /**
     * @see <a href="https://minecraft.wiki/w/Data_version#List_of_data_versions">Data version - Minecraft Wiki</a>
     */
    @SuppressWarnings("deprecation")
    public static OptionalInt getDataVersion() {
        // UnsafeValues is not considered public API.
        // It is therefore not safe to assume this call signature will exist.
        try {
            return OptionalInt.of(Bukkit.getUnsafe().getDataVersion());
        } catch (Throwable t) {
            return OptionalInt.empty();
        }
    }

}
