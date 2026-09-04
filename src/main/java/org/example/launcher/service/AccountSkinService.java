package org.example.launcher.service;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.example.launcher.account.Account;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

public class AccountSkinService {

    private static final String PROFILE_URL =
            "https://api.minecraftservices.com/minecraft/profile";

    private static final HttpClient HTTP =
            HttpClient.newHttpClient();

    private static final Map<UUID, Image> SKIN_CACHE =
            new ConcurrentHashMap<>();

    private static final Map<UUID, String> URL_CACHE =
            new ConcurrentHashMap<>();

    private AccountSkinService() {
    }

    public static void loadHead(
            Account account,
            Consumer<Image> callback
    ) {

        if (account == null) {
            callback.accept(null);
            return;
        }

        UUID uuid =
                UUID.fromString(
                        account.getUuid()
                );

        Image cached =
                SKIN_CACHE.get(uuid);

        if (cached != null && !cached.isError()) {

            Platform.runLater(() ->
                    callback.accept(
                            createHead(cached)
                    )
            );

            return;
        }

        Thread thread = new Thread(() -> {

            try {

                String skinUrl =
                        URL_CACHE.get(uuid);

                if (skinUrl == null) {

                    skinUrl =
                            fetchSkinUrl(account);

                    if (skinUrl != null) {

                        URL_CACHE.put(
                                uuid,
                                skinUrl
                        );
                    }
                }

                if (skinUrl == null ||
                        skinUrl.isBlank()) {

                    Platform.runLater(() ->
                            callback.accept(null)
                    );

                    return;
                }

                Image skin =
                        new Image(
                                skinUrl,
                                64,
                                64,
                                false,
                                false
                        );

                if (skin.isError()) {

                    throw new IOException(
                            "Skin image could not be loaded."
                    );
                }

                SKIN_CACHE.put(
                        uuid,
                        skin
                );

                Image head =
                        createHead(skin);

                Platform.runLater(() ->
                        callback.accept(head)
                );

            } catch (Exception ex) {

                System.err.println(
                        "Failed to load Minecraft skin for "
                                + account.getUsername()
                );

                ex.printStackTrace();

                Platform.runLater(() ->
                        callback.accept(null)
                );
            }

        });

        thread.setDaemon(true);
        thread.start();
    }

    private static String fetchSkinUrl(
            Account account
    ) throws IOException, InterruptedException {

        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(
                                URI.create(
                                        PROFILE_URL
                                )
                        )
                        .header(
                                "Authorization",
                                "Bearer "
                                        + account.getAccessToken()
                        )
                        .header(
                                "Accept",
                                "application/json"
                        )
                        .GET()
                        .build();

        HttpResponse<String> response =
                HTTP.send(
                        request,
                        HttpResponse.BodyHandlers.ofString()
                );

        if (response.statusCode() != 200) {

            throw new IOException(
                    "Minecraft profile request failed: HTTP "
                            + response.statusCode()
            );
        }

        JsonObject profile =
                JsonParser
                        .parseString(response.body())
                        .getAsJsonObject();

        if (!profile.has("skins")) {
            return null;
        }

        JsonArray skins =
                profile.getAsJsonArray("skins");

        if (skins.isEmpty()) {
            return null;
        }

        // Prefer the currently active skin.
        for (var element : skins) {

            JsonObject skin =
                    element.getAsJsonObject();

            if (skin.has("state")
                    && "ACTIVE".equalsIgnoreCase(
                    skin.get("state").getAsString()
            )
                    && skin.has("url")) {

                return skin
                        .get("url")
                        .getAsString();
            }
        }

        // Fallback to the first available skin.
        JsonObject firstSkin =
                skins
                        .get(0)
                        .getAsJsonObject();

        if (firstSkin.has("url")) {

            return firstSkin
                    .get("url")
                    .getAsString();
        }

        return null;
    }

    private static Image createHead(
            Image skin
    ) {

        if (skin == null ||
                skin.getPixelReader() == null ||
                skin.getWidth() < 64 ||
                skin.getHeight() < 64) {

            return null;
        }

        /*
         * Create a 64x64 image rather than an 8x8 image.
         *
         * This keeps the Minecraft pixels crisp when the
         * ImageView scales the result down to 22x22 or 28x28.
         */
        WritableImage head =
                new WritableImage(
                        64,
                        64
                );

        var reader =
                skin.getPixelReader();

        var writer =
                head.getPixelWriter();

        /*
         * Minecraft skin layout:
         *
         * Base head:
         * X = 8..15
         * Y = 8..15
         *
         * Hat layer:
         * X = 40..47
         * Y = 8..15
         *
         * The hat layer is drawn OVER the base head.
         */

        for (int y = 0; y < 8; y++) {

            for (int x = 0; x < 8; x++) {

                int baseArgb =
                        reader.getArgb(
                                8 + x,
                                8 + y
                        );

                int hatArgb =
                        reader.getArgb(
                                40 + x,
                                8 + y
                        );

                int hatAlpha =
                        (hatArgb >>> 24) & 0xFF;

                int pixelArgb;

                if (hatAlpha > 0) {
                    pixelArgb = hatArgb;
                } else {
                    pixelArgb = baseArgb;
                }

                /*
                 * Turn each original Minecraft pixel into
                 * an 8x8 block.
                 *
                 * 8 original pixels × 8 = 64 pixels.
                 */

                for (int py = 0; py < 8; py++) {

                    for (int px = 0; px < 8; px++) {

                        writer.setArgb(
                                x * 8 + px,
                                y * 8 + py,
                                pixelArgb
                        );
                    }
                }
            }
        }

        return head;
    }

    public static void clear(
            Account account
    ) {

        if (account == null) {
            return;
        }

        UUID uuid =
                UUID.fromString(
                        account.getUuid()
                );

        SKIN_CACHE.remove(uuid);
        URL_CACHE.remove(uuid);
    }

    public static void clearAll() {

        SKIN_CACHE.clear();
        URL_CACHE.clear();
    }
}