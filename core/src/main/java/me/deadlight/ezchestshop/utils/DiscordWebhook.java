package me.deadlight.ezchestshop.utils;

import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Locale;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import com.google.common.net.HttpHeaders;
import io.papermc.paper.ServerBuildInfo;
import me.deadlight.ezchestshop.EzChestShop;
import me.deadlight.ezchestshop.data.Config;
import me.deadlight.ezchestshop.utils.logging.ExtendedLogger;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.jetbrains.annotations.ApiStatus;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

@ApiStatus.Internal
public final class DiscordWebhook {
    private static final ExtendedLogger LOGGER = EzChestShop.logger();
    private static final Queue<JSONObject> messageQueue = new ArrayBlockingQueue<>(128);

    private static final long PERIOD_DELAY_IN_SECONDS = 5;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    public static final String WEBHOOK_EXAMPLE_URL =
            "https://discord.com/api/webhooks/xxxxxxxxxxxxxxxxxxx/xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx";

    static {
        if (isEnabled()) {
            Bukkit.getAsyncScheduler().runAtFixedRate(
                    EzChestShop.getPlugin(),
                    ignored -> tick(),
                    PERIOD_DELAY_IN_SECONDS,
                    PERIOD_DELAY_IN_SECONDS,
                    TimeUnit.SECONDS);
        }
    }

    private static int rateLimitLimit = 5;
    private static int rateLimitRemaining = 5;
    private static long rateLimitReset = 0L;

    private static void tick() {
        if (rateLimitReset > 0L
                && Clock.systemUTC().instant().getEpochSecond() >= rateLimitReset) {
            if (rateLimitRemaining < rateLimitLimit) {
                // We only care if this actually has any effect.
                LOGGER.trace("Webhook rate limit reset (limit: {})", rateLimitLimit);
            }
            rateLimitReset = 0L;
            rateLimitRemaining = rateLimitLimit;
        }

        int process = Math.min(rateLimitRemaining, messageQueue.size());
        if (process >= 1) {
            for (int i = 0; i < process; i++) {
                JSONObject message = messageQueue.poll();
                if (message == null) {
                    break;
                }
                sendDiscordWebhookInternal(message); // Sets rate limit remainder based on API response.
                LOGGER.trace("Webhook rate limit: {} / {}.", rateLimitRemaining, rateLimitLimit);
            }
        }
    }

    private static void queue(JSONObject messageJson) {
        if (!messageQueue.offer(messageJson)) {
            throw new IllegalStateException("The queue is at capacity!");
        }
    }

    private static void sendDiscordWebhookInternal(JSONObject messageJson) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) Config.discordWebhookUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty(HttpHeaders.CONTENT_TYPE, "application/json; charset=utf-8");
            connection.setRequestProperty(HttpHeaders.USER_AGENT, userAgent());
            connection.setDoOutput(true);

            try (OutputStreamWriter writer = new OutputStreamWriter(connection.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(messageJson.toString());
            }

            final int responseCode = connection.getResponseCode();
            if (responseCode >= 200 && responseCode <= 299) {
                LOGGER.debug("Successful webhook call (status: {})", responseCode);
            } else if (responseCode == HTTP_TOO_MANY_REQUESTS) {
                LOGGER.warn("Hit webhook rate limit (status: {})", responseCode);
                // Requeue the message
                messageQueue.offer(messageJson);
            } else {
                LOGGER.warn("Unexpected webhook response code: {}.", responseCode);
                return;
            }

            rateLimitLimit = Integer.parseInt(connection.getHeaderField("X-RateLimit-Limit"));
            rateLimitRemaining = Integer.parseInt(connection.getHeaderField("X-RateLimit-Remaining"));
            rateLimitReset = Long.parseLong(connection.getHeaderField("X-RateLimit-Reset"));
        } catch (Throwable t) {
            LOGGER.warn("Webhook failed", t);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    public static void queueTransaction(
            String buyer,
            String seller,
            String item_name,
            String price,
            String currency,
            String shop_location,
            String time,
            String count,
            String owner) {
        if (!isEnabled() || !Config.isBuySellWebhookEnabled) {
            return;
        }

        ConfigurationSection webhookSection = Config.buySellWebhookTemplate;
        String jsonString = configurationSectionToJsonString(webhookSection);

        // Replace the placeholders in the JSON string
        jsonString = jsonString
            .replace("%BUYER%", buyer)
            .replace("%SELLER%", seller)
            .replace("%ITEM_NAME%", item_name)
            .replace("%PRICE%", price)
            .replace("%CURRENCY%", currency)
            .replace("%SHOP_LOCATION%", shop_location)
            .replace("%TIME%", time)
            .replace("%COUNT%", count)
            .replace("%OWNER%", owner);

        // Parse the JSON string into a JSONObject
        JSONParser parser = new JSONParser();
        JSONObject webhookData;
        try {
            webhookData = (JSONObject) parser.parse(jsonString);
        } catch (ParseException e) {
            LOGGER.warn("Error parsing webhook data from config.yml.", e);
            return;
        }

        queue(webhookData);
    }

    public static void queueCreation(
            String owner,
            String buying_price,
            String selling_price,
            String item_name,
            String material,
            String time,
            String shop_location) {
        if (!isEnabled() || !Config.isNewShopWebhookEnabled) {
            return;
        }

        ConfigurationSection webhookSection = Config.newShopWebhookTemplate;
        String jsonString = configurationSectionToJsonString(webhookSection);

        // Replace the placeholders in the JSON string
        jsonString = jsonString
            .replace("%OWNER%", owner)
            .replace("%BUYING_PRICE%", buying_price)
            .replace("%SELLING_PRICE%", selling_price)
            .replace("%ITEM_NAME%", item_name)
            .replace("%MATERIAL%", material)
            .replace("%TIME%", time)
            .replace("%SHOP_LOCATION%", shop_location);

        // Parse the JSON string into a JSONObject
        JSONParser parser = new JSONParser();
        JSONObject webhookData;
        try {
            webhookData = (JSONObject) parser.parse(jsonString);
        } catch (ParseException e) {
            LOGGER.warn("Error parsing webhook data from config.yml", e);
            return;
        }

        queue(webhookData);
    }

    @SuppressWarnings("unchecked")
    private static String configurationSectionToJsonString(ConfigurationSection section) {
        JSONObject jsonObject = new JSONObject();
        for (String key : section.getKeys(true)) {
            Object value = section.get(key);
            jsonObject.put(key, value);
        }
        return jsonObject.toJSONString();
    }

    private static String userAgent() {
        //noinspection UnstableApiUsage
        return String.format(Locale.ENGLISH, "%s/%s %s/%s",
            EzChestShop.getPlugin().getName(),
            EzChestShop.getPlugin().getPluginMeta().getVersion(),
            ServerBuildInfo.buildInfo().brandName(),
            ServerBuildInfo.buildInfo().asString(ServerBuildInfo.StringRepresentation.VERSION_SIMPLE)
        );
    }

    public static boolean isEnabled() {
        return Config.discordWebhookUrl != null && Config.isDiscordNotificationEnabled;
    }
}
