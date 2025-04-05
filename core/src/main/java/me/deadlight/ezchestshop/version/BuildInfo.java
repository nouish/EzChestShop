package me.deadlight.ezchestshop.version;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;
import java.util.Properties;

import me.deadlight.ezchestshop.EzChestShop;
import org.jetbrains.annotations.NotNull;

public final class BuildInfo {
    public static final BuildInfo CURRENT = readBuildInfo();

    private final String id;
    private final String name;
    private final String branch;
    private final boolean stable;

    public BuildInfo(String id, String name, String branch, boolean stable) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.branch = Objects.requireNonNull(branch);
        this.stable = stable;
    }

    public @NotNull String getId() {
        return id;
    }

    public @NotNull String getName() {
        return name;
    }

    public @NotNull String getBranch() {
        return branch;
    }

    public boolean isStable() {
        return stable;
    }

    @Override
    public String toString() {
        return "BuildInfo{" + "id='" + id + "', name='" + name + "', branch='" + branch + ", stable=" + stable + '}';
    }

    private static BuildInfo readBuildInfo() {
        try (InputStream in = EzChestShop.getPlugin().getResource("version.properties")) {
            if (in == null)
                throw new IOException("No input");

            Properties properties = new Properties();
            properties.load(in);
            return parseBuildInfo(properties);
        } catch (IOException e) {
            throw new AssertionError("Missing version information!", e);
        }
    }

    private static BuildInfo parseBuildInfo(Properties properties) {
        String id = properties.getProperty("git.commit.id.abbrev");
        String name = properties.getProperty("git.build.version");
        String branch = properties.getProperty("git.branch");
        // (1): <version core> "-" <pre-release>
        // (2): <version core> "+" <build>
        boolean stable = name.indexOf('-') == -1 && name.indexOf('+') == -1;

        return new BuildInfo(id, name, branch, stable);
    }
}
