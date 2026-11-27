package dev.itsdropper.vanta.launcher.social;

import com.google.gson.JsonObject;
import com.google.gson.Gson;
import dev.itsdropper.vanta.launcher.account.Account;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Temporary local integration probe for the Vanta Social Worker.
 *
 * The Minecraft access token is sent only to the loopback development Worker.
 * It is never logged, persisted, or included in an exception message. The
 * Worker response body is deliberately discarded because it contains the
 * newly issued Vanta session token; this probe only verifies authentication.
 */
public final class VantaSocialClient {

    private static final URI LOCAL_MINECRAFT_LOGIN_ENDPOINT =
            URI.create("http://127.0.0.1:8787/v1/auth/minecraft");

    private static final Gson GSON = new Gson();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public enum LoginStatus {
        SUCCESS,
        MINECRAFT_AUTH_REJECTED,
        RATE_LIMITED,
        API_ERROR,
        UNEXPECTED_RESPONSE
    }

    private VantaSocialClient() {
    }

    /**
     * Verifies the active Minecraft session with the local Social API.
     *
     * The API's Vanta session response is intentionally not retained yet:
     * this method is a development probe, not the production Social sign-in
     * lifecycle.
     */
    public static LoginStatus testMinecraftLogin(Account account)
            throws IOException, InterruptedException {
        if (account == null) {
            return LoginStatus.MINECRAFT_AUTH_REJECTED;
        }

        String minecraftAccessToken = account.getAccessToken();
        if (minecraftAccessToken == null || minecraftAccessToken.isBlank()) {
            return LoginStatus.MINECRAFT_AUTH_REJECTED;
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("accessToken", minecraftAccessToken);

        HttpRequest request = HttpRequest.newBuilder(LOCAL_MINECRAFT_LOGIN_ENDPOINT)
                .timeout(Duration.ofSeconds(12))
                .header("Accept", "application/json")
                .header("Content-Type", "application/json; charset=utf-8")
                .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(payload)))
                .build();

        // Do not read or log the response body: successful responses include
        // a bearer credential for the newly created Vanta Social session.
        HttpResponse<Void> response = HTTP.send(
                request,
                HttpResponse.BodyHandlers.discarding()
        );

        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return LoginStatus.SUCCESS;
        }
        if (status == 401 || status == 403) {
            return LoginStatus.MINECRAFT_AUTH_REJECTED;
        }
        if (status == 429) {
            return LoginStatus.RATE_LIMITED;
        }
        if (status >= 500) {
            return LoginStatus.API_ERROR;
        }
        return LoginStatus.UNEXPECTED_RESPONSE;
    }
}
