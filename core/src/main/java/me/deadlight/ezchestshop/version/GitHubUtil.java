package me.deadlight.ezchestshop.version;

import javax.net.ssl.HttpsURLConnection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import me.deadlight.ezchestshop.EzChestShopConstants;
import org.jetbrains.annotations.NotNull;

public final class GitHubUtil {
    private static final String MAIN_BRANCH = "main";

    private static final String API_BASE = "https://api.github.com/repos/";

    // https://docs.github.com/en/rest/releases/releases#get-the-latest-release
    private static final String API_LATEST_RELEASE = API_BASE + EzChestShopConstants.REPOSITORY + "/releases/latest";

    // https://docs.github.com/en/rest/commits/commits#compare-two-commits
    private static final String API_COMPARE = API_BASE + EzChestShopConstants.REPOSITORY + "/compare/%s...%s";

    private GitHubUtil() {
    }

    public static BuildInfo lookupLatestRelease() throws IOException {
        URL url = URI.create(API_LATEST_RELEASE).toURL();
        HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
        final int responseCode = https.getResponseCode();

        if (isSuccessfulResponse(responseCode)) {
            try (InputStream in = https.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return parseBuildInfo(reader);
            }
        } else {
            throw new IOException("Bad response status: " + responseCode);
        }
    }

    private static BuildInfo parseBuildInfo(BufferedReader reader) {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
        String id = json.get("tag_name").getAsString();
        Instant buildTime = Instant.parse(json.get("published_at").getAsString());
        return new BuildInfo(id, id, GitHubUtil.MAIN_BRANCH, buildTime, true);
    }

    public static GitHubStatusLookup compare(@NotNull String base, @NotNull String head) throws IOException {
        URL url = URI.create(String.format(API_COMPARE, base, head)).toURL();
        HttpsURLConnection https = (HttpsURLConnection) url.openConnection();
        final int responseCode = https.getResponseCode();

        if (isSuccessfulResponse(responseCode)) {
            try (InputStream in = https.getInputStream();
                 BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return parseComparison(reader);
            }
        } else if (responseCode == HttpsURLConnection.HTTP_NOT_FOUND) {
            return new GitHubStatusLookup(GitHubStatus.UNKNOWN, 0);
        } else {
            return new GitHubStatusLookup(GitHubStatus.FAILURE, 0);
        }
    }

    private static GitHubStatusLookup parseComparison(@NotNull BufferedReader reader) {
        JsonObject json = JsonParser.parseReader(reader).getAsJsonObject();
        GitHubStatus status = GitHubStatus.fromString(json.get("status").getAsString());
        int aheadBy = json.get("ahead_by").getAsInt();
        int behindBy = json.get("behind_by").getAsInt();
        return new GitHubStatusLookup(status, status == GitHubStatus.AHEAD ? aheadBy : behindBy);
    }

    private static boolean isSuccessfulResponse(int code) {
        // https://developer.mozilla.org/en-US/docs/Web/HTTP/Status#successful_responses
        return code >= 200 && code <= 299;
    }

    public static class GitHubStatusLookup {
        private final GitHubStatus status;
        private final int distance;

        private GitHubStatusLookup(@NotNull GitHubStatus status, int distance) {
            this.status = Objects.requireNonNull(status);
            this.distance = distance;
        }

        public GitHubStatus getStatus() {
            return status;
        }

        public int getDistance() {
            return distance;
        }

        public boolean isAhead() {
            return status == GitHubStatus.AHEAD;
        }

        public boolean isIdentical() {
            return status == GitHubStatus.IDENTICAL;
        }

        public boolean isBehind() {
            return status == GitHubStatus.BEHIND;
        }

        @Override
        public String toString() {
            return "GitHubStatusLookup{" + "status=" + status + ", distance=" + distance + '}';
        }
    }

    public enum GitHubStatus {
        // Valid GitHub status types

        DIVERGED,
        AHEAD,
        BEHIND,
        IDENTICAL,

        // Internal types

        /**
         * Represents the case when a BASEHEAD is unknown to GitHub, private builds, for example.
         */
        UNKNOWN,

        /**
         * Represents failure to determine the status due to external failures.
         */
        FAILURE;

        public static GitHubStatus fromString(@NotNull String str) {
            return GitHubStatus.valueOf(str.toUpperCase(Locale.ROOT));
        }
    }
}
