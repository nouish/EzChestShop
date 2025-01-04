package me.deadlight.ezchestshop.version;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Objects;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.deadlight.ezchestshop.EzChestShop;
import org.jetbrains.annotations.NotNull;

public final class BuildInfo {
    public static final BuildInfo CURRENT = readBuildInfo();

    private final String id;
    private final String name;
    private final String branch;
    private final Instant buildTime;
    private final boolean stable;

    public BuildInfo(String id, String name, String branch, Instant buildTime, boolean stable) {
        this.id = Objects.requireNonNull(id);
        this.name = Objects.requireNonNull(name);
        this.branch = Objects.requireNonNull(branch);
        this.buildTime = Objects.requireNonNull(buildTime);
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

    public @NotNull Instant getBuildTime() {
        return buildTime;
    }

    public boolean isStable() {
        return stable;
    }

    @Override
    public String toString() {
        return "BuildInfo{" + "id='" + id + "', name='" + name + "', branch='" + branch + "', buildTime=" + buildTime + ", stable=" + stable + '}';
    }

    private static BuildInfo readBuildInfo() {
        try (InputStream in = EzChestShop.getPlugin().getResource("version.json")) {
            if (in == null)
                throw new IOException("No input");
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return parseBuildInfo(reader);
            }
        } catch (IOException e) {
            throw new AssertionError("Missing version information!", e);
        }
    }

    private static BuildInfo parseBuildInfo(BufferedReader reader) {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();

        String id = json.get("git.commit.id.abbrev").getAsString();
        String name = json.get("git.build.version").getAsString();
        String branch = json.get("git.branch").getAsString();
        Instant buildTime = Instant.parse(json.get("git.build.time").getAsString());
        // (1): <version core> "-" <pre-release>
        // (2): <version core> "+" <build>
        boolean stable = name.indexOf('-') == -1 && name.indexOf('+') == -1;

        return new BuildInfo(id, name, branch, buildTime, stable);
    }
}
