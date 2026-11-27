package dev.itsdropper.vanta.launcher.social;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.itsdropper.vanta.launcher.account.Account;
import dev.itsdropper.vanta.launcher.account.AccountManager;
import dev.itsdropper.vanta.launcher.security.CredentialManager;
import dev.itsdropper.vanta.ui.LauncherSettings;
import net.raphimc.minecraftauth.MinecraftAuth;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Authenticated client for the launcher-native Vanta Social API.
 *
 * Minecraft access tokens are only sent to the configured Social API during
 * account verification. The API session token is stored using the operating
 * system credential vault (DPAPI, macOS Keychain or Linux Secret Service).
 */
public final class VantaSocialClient {

    /**
     * Production endpoint is injected here by Vanta when the Worker is deployed.
     * End users never configure or see the service address.
     */
    private static final String PRODUCTION_BASE_URL = "https://vanta-social-api.hugohakonen.workers.dev";

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(6))
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    public record SocialUser(String id, String displayName, String minecraftUsername) {}
    public record FriendRequest(String id, SocialUser user, long createdAt) {}
    public record Friend(String id, String displayName, String minecraftUsername, long friendsSince) {}
    public record Conversation(
            String id, String type, String title, long updatedAt,
            SocialUser otherUser, String lastMessage, Long lastMessageAt
    ) {}
    public record Message(
            String id, String senderId, String senderDisplayName,
            String senderMinecraftUsername, String content, long createdAt,
            Long editedAt, boolean deleted
    ) {}

    public static final class ApiException extends IOException {
        private final int status;
        private final String code;

        public ApiException(int status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }

        public int getStatus() { return status; }
        public String getCode() { return code; }
    }

    private final String baseUrl;
    private volatile String sessionToken;
    private volatile String sessionAccountUuid;

    public VantaSocialClient() {
        this(resolveBaseUrl());
    }

    private static String resolveBaseUrl() {
        // This override is supplied only by Gradle's local developer run task.
        String developmentOverride = System.getProperty("vanta.social.api.baseUrl");
        if (developmentOverride != null && !developmentOverride.isBlank()) {
            return developmentOverride.trim();
        }
        if (PRODUCTION_BASE_URL.isBlank()) {
            throw new IllegalStateException("Vanta Social is not available in this launcher build yet.");
        }
        return PRODUCTION_BASE_URL;
    }

    private VantaSocialClient(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("Vanta Social API URL is required.");
        }
        String normalized = baseUrl.trim().replaceAll("/+$", "");
        URI uri = URI.create(normalized);
        if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
                || uri.getHost() == null || uri.getUserInfo() != null
                || uri.getQuery() != null || uri.getFragment() != null) {
            throw new IllegalArgumentException("Vanta Social API URL must be a valid HTTP(S) base URL.");
        }
        if ("http".equalsIgnoreCase(uri.getScheme())
                && !("127.0.0.1".equals(uri.getHost())
                || "localhost".equalsIgnoreCase(uri.getHost())
                || "[::1]".equals(uri.getHost()))) {
            throw new IllegalArgumentException("Remote Vanta Social API endpoints must use HTTPS.");
        }
        this.baseUrl = normalized;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public JsonObject getMe(Account account) throws IOException, InterruptedException {
        return authorized("GET", "/v1/me", null, account);
    }

    public List<SocialUser> searchUsers(Account account, String query) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(query.trim(), StandardCharsets.UTF_8);
        JsonArray values = authorized("GET", "/v1/users/search?q=" + encoded, null, account)
                .getAsJsonArray("users");
        List<SocialUser> result = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject item = value.getAsJsonObject();
            result.add(new SocialUser(
                    string(item, "id"), string(item, "displayName"), string(item, "minecraftUsername")));
        }
        return result;
    }

    public List<Friend> getFriends(Account account) throws IOException, InterruptedException {
        JsonArray values = authorized("GET", "/v1/friends", null, account).getAsJsonArray("friends");
        List<Friend> result = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject item = value.getAsJsonObject();
            result.add(new Friend(
                    string(item, "id"), string(item, "displayName"),
                    string(item, "minecraftUsername"), number(item, "friendsSince")));
        }
        return result;
    }

    public List<FriendRequest> getIncomingRequests(Account account) throws IOException, InterruptedException {
        JsonArray values = authorized("GET", "/v1/friends/requests?direction=incoming", null, account)
                .getAsJsonArray("requests");
        List<FriendRequest> result = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject item = value.getAsJsonObject();
            result.add(new FriendRequest(string(item, "id"), readUser(item.getAsJsonObject("user")),
                    number(item, "createdAt")));
        }
        return result;
    }

    public List<FriendRequest> getOutgoingRequests(Account account) throws IOException, InterruptedException {
        JsonArray values = authorized("GET", "/v1/friends/requests?direction=outgoing", null, account)
                .getAsJsonArray("requests");
        List<FriendRequest> result = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject item = value.getAsJsonObject();
            result.add(new FriendRequest(string(item, "id"), readUser(item.getAsJsonObject("user")),
                    number(item, "createdAt")));
        }
        return result;
    }

    public void sendFriendRequest(Account account, String minecraftUsername)
            throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("minecraftUsername", minecraftUsername);
        authorized("POST", "/v1/friends/requests", body, account);
    }

    public void acceptFriendRequest(Account account, String requestId)
            throws IOException, InterruptedException {
        authorized("POST", "/v1/friends/requests/" + safeId(requestId) + "/accept", new JsonObject(), account);
    }

    public void declineFriendRequest(Account account, String requestId)
            throws IOException, InterruptedException {
        authorized("POST", "/v1/friends/requests/" + safeId(requestId) + "/decline", new JsonObject(), account);
    }

    public void removeFriend(Account account, String userId) throws IOException, InterruptedException {
        authorized("DELETE", "/v1/friends/" + safeId(userId), null, account);
    }

    public List<Conversation> getConversations(Account account) throws IOException, InterruptedException {
        JsonArray values = authorized("GET", "/v1/conversations", null, account)
                .getAsJsonArray("conversations");
        List<Conversation> result = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject item = value.getAsJsonObject();
            JsonObject other = optionalObject(item, "otherUser");
            result.add(new Conversation(
                    string(item, "id"), string(item, "type"), nullableString(item, "title"),
                    number(item, "updatedAt"), other == null ? null : readUser(other),
                    nullableString(item, "lastMessage"), nullableNumber(item, "lastMessageAt")));
        }
        return result;
    }

    public String openDirectConversation(Account account, String userId)
            throws IOException, InterruptedException {
        JsonObject body = new JsonObject();
        body.addProperty("userId", userId);
        return string(authorized("POST", "/v1/conversations/direct", body, account), "conversationId");
    }

    public List<Message> getMessages(Account account, String conversationId)
            throws IOException, InterruptedException {
        JsonArray values = authorized("GET",
                "/v1/conversations/" + safeId(conversationId) + "/messages?limit=100",
                null, account).getAsJsonArray("messages");
        List<Message> result = new ArrayList<>();
        for (JsonElement value : values) {
            JsonObject item = value.getAsJsonObject();
            result.add(new Message(
                    string(item, "id"), string(item, "senderId"),
                    string(item, "senderDisplayName"), string(item, "senderMinecraftUsername"),
                    string(item, "content"), number(item, "createdAt"),
                    nullableNumber(item, "editedAt"), bool(item, "deleted")));
        }
        return result;
    }

    public void sendMessage(Account account, String conversationId, String content)
            throws IOException, InterruptedException {
        sendMessage(account, conversationId, content, UUID.randomUUID().toString());
    }

    public void sendMessage(Account account, String conversationId, String content, String clientMessageId)
            throws IOException, InterruptedException {
        if (clientMessageId == null || !clientMessageId.matches("[A-Za-z0-9_-]{8,64}")) {
            throw new IllegalArgumentException("Invalid client message identifier.");
        }
        JsonObject body = new JsonObject();
        body.addProperty("content", content);
        body.addProperty("clientMessageId", clientMessageId);
        authorized("POST", "/v1/conversations/" + safeId(conversationId) + "/messages", body, account);
    }

    public void logout(Account account) throws IOException, InterruptedException {
        authorized("POST", "/v1/auth/logout", new JsonObject(), account);
        clearStoredSession(account);
    }

    private JsonObject authorized(String method, String path, JsonObject body, Account account)
            throws IOException, InterruptedException {
        if (account == null) throw new IOException("Sign in to a Minecraft account before using Vanta Social.");
        String token = ensureSession(account);
        HttpResponse<String> response = send(method, path, body, token);
        if (response.statusCode() == 401) {
            synchronized (this) {
                sessionToken = null;
                sessionAccountUuid = null;
                token = createSession(account);
            }
            response = send(method, path, body, token);
        }
        return unwrap(response);
    }

    private synchronized String ensureSession(Account account) throws IOException, InterruptedException {
        String uuid = account.getUuid();
        if (sessionToken != null && uuid.equals(sessionAccountUuid)) return sessionToken;

        String stored = LauncherSettings.getVantaSocialSessionCredentialReference(uuid);
        String token = null;
        if (stored != null && !stored.isBlank()) {
            try {
                token = CredentialManager.decrypt(stored);
            } catch (IOException invalidCredential) {
                // The selected Minecraft account can safely establish a replacement token.
            }
        }
        if (token != null && !token.isBlank()) {
            HttpResponse<String> check = send("GET", "/v1/me", null, token);
            if (check.statusCode() >= 200 && check.statusCode() < 300) {
                unwrap(check);
                sessionToken = token;
                sessionAccountUuid = uuid;
                return token;
            }
            if (check.statusCode() != 401) unwrap(check);
        }
        return createSession(account);
    }

    private synchronized String createSession(Account account) throws IOException, InterruptedException {
        // Account sessions are persisted between launcher runs. Their cached Minecraft
        // access token can expire even while the Microsoft refresh-token chain remains
        // valid, so refresh the chain before sending any token to the Social API.
        try {
            var refreshedSession = MinecraftAuth.JAVA_DEVICE_CODE_LOGIN.refresh(
                    MinecraftAuth.createHttpClient(), account.getSession());
            account.setSession(refreshedSession);
            AccountManager.saveAccount(account);
        } catch (Exception refreshFailure) {
            throw new IOException(
                    "Could not refresh the Minecraft account session for Vanta Social. " +
                            "Sign in to the Minecraft account again if the session refresh fails.",
                    refreshFailure);
        }

        String accessToken = account.getAccessToken();
        if (accessToken == null || accessToken.isBlank()) {
            throw new IOException("Minecraft authentication did not provide an access token after refresh.");
        }

        JsonObject body = new JsonObject();
        body.addProperty("accessToken", accessToken);
        JsonObject data = unwrap(send("POST", "/v1/auth/minecraft", body, null));
        JsonObject session = data.getAsJsonObject("session");
        if (session == null) throw new IOException("The Social API did not return a session.");
        String token = string(session, "token");
        if (token.isBlank()) throw new IOException("The Social API returned an empty session token.");

        String reference = CredentialManager.encrypt(token, "social-" + account.getUuid());
        LauncherSettings.setVantaSocialSessionCredentialReference(account.getUuid(), reference);
        sessionToken = token;
        sessionAccountUuid = account.getUuid();
        return token;
    }

    private void clearStoredSession(Account account) throws IOException {
        if (account == null) return;
        String stored = LauncherSettings.getVantaSocialSessionCredentialReference(account.getUuid());
        CredentialManager.delete(stored);
        LauncherSettings.setVantaSocialSessionCredentialReference(account.getUuid(), "");
        sessionToken = null;
        sessionAccountUuid = null;
    }

    private HttpResponse<String> send(String method, String path, JsonObject body, String bearer)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json");
        if (bearer != null) builder.header("Authorization", "Bearer " + bearer);
        if (body != null) {
            builder.header("Content-Type", "application/json; charset=utf-8");
            builder.method(method, HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8));
        } else if ("GET".equals(method)) {
            builder.GET();
        } else {
            builder.method(method, HttpRequest.BodyPublishers.noBody());
        }
        return HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    }

    private static JsonObject unwrap(HttpResponse<String> response) throws IOException {
        JsonObject envelope;
        try {
            envelope = JsonParser.parseString(response.body()).getAsJsonObject();
        } catch (RuntimeException malformed) {
            throw new IOException("The Social API returned an invalid response (HTTP "
                    + response.statusCode() + ").", malformed);
        }
        if (response.statusCode() < 200 || response.statusCode() >= 300
                || !envelope.has("success") || !envelope.get("success").getAsBoolean()) {
            JsonObject error = optionalObject(envelope, "error");
            String code = error == null ? "api_error" : nullableString(error, "code");
            String message = error == null ? "The Social API request failed." : nullableString(error, "message");
            throw new ApiException(response.statusCode(), code, message);
        }
        JsonObject data = optionalObject(envelope, "data");
        if (data == null) throw new IOException("The Social API response did not contain data.");
        return data;
    }

    private static SocialUser readUser(JsonObject item) {
        return new SocialUser(string(item, "id"), nullableString(item, "displayName"),
                nullableString(item, "minecraftUsername"));
    }

    private static String safeId(String id) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,64}")) {
            throw new IllegalArgumentException("Invalid Vanta Social identifier.");
        }
        return id;
    }

    private static JsonObject optionalObject(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonObject() ? object.getAsJsonObject(key) : null;
    }

    private static String string(JsonObject object, String key) {
        return nullableString(object, key) == null ? "" : nullableString(object, key);
    }

    private static String nullableString(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull()
                ? object.get(key).getAsString() : null;
    }

    private static long number(JsonObject object, String key) {
        Long value = nullableNumber(object, key);
        return value == null ? 0L : value;
    }

    private static Long nullableNumber(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() ? object.get(key).getAsLong() : null;
    }

    private static boolean bool(JsonObject object, String key) {
        return object.has(key) && !object.get(key).isJsonNull() && object.get(key).getAsBoolean();
    }

    /** Compatibility probe retained on Experimental for diagnosing local Worker setup. */
    public static LoginStatus testMinecraftLogin(Account account) throws IOException, InterruptedException {
        VantaSocialClient client = new VantaSocialClient();
        try {
            client.getMe(account);
            client.logout(account);
            return LoginStatus.SUCCESS;
        } catch (ApiException failure) {
            return switch (failure.getStatus()) {
                case 401, 403 -> LoginStatus.MINECRAFT_AUTH_REJECTED;
                case 429 -> LoginStatus.RATE_LIMITED;
                default -> failure.getStatus() >= 500 ? LoginStatus.API_ERROR : LoginStatus.UNEXPECTED_RESPONSE;
            };
        }
    }

    public enum LoginStatus {
        SUCCESS, MINECRAFT_AUTH_REJECTED, RATE_LIMITED, API_ERROR, UNEXPECTED_RESPONSE
    }
}
