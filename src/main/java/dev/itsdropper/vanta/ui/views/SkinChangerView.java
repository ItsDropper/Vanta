package dev.itsdropper.vanta.ui.views;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.itsdropper.vanta.launcher.account.Account;
import dev.itsdropper.vanta.launcher.service.AccountService;
import dev.itsdropper.vanta.launcher.service.AccountSkinService;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class SkinChangerView extends VBox {

    private static final URI PROFILE_ENDPOINT =
            URI.create("https://api.minecraftservices.com/minecraft/profile");
    private static final URI SKIN_ENDPOINT =
            URI.create("https://api.minecraftservices.com/minecraft/profile/skins");
    private static final long MAX_SKIN_BYTES = 4L * 1024 * 1024;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .build();

    private final AccountService accountService;
    private final Label accountLabel = new Label("Checking account...");
    private final Label fileLabel = new Label("Loading current account skin...");
    private final Label statusLabel = new Label("Loading the skin currently active on your account...");
    private final SkinModel3D modelPreview = new SkinModel3D();
    private final ComboBox<String> modelChoice = new ComboBox<>();
    private final ComboBox<CapeOption> capeChoice = new ComboBox<>();
    private final Button applyCapeButton = new Button("Equip selected cape");
    private final Button hideCapeButton = new Button("Hide cape");
    private final Button chooseButton = new Button("Choose & apply PNG");
    private final Button uploadButton = new Button("Apply skin");

    private Path selectedFile;
    private Image displayedSkin;
    private String currentAccountUuid;
    private int profileRequestGeneration;
    private boolean busy;

    public SkinChangerView(AccountService accountService) {
        this.accountService = accountService;
        getStyleClass().addAll("page", "skin-changer-page");
        setFillWidth(true);

        Label title = new Label("Skin changer");
        title.getStyleClass().add("accounts-title");
        Label subtitle = new Label(
                "Preview your active Minecraft skin in 3D, rotate it, and apply new skins to your profile."
        );
        subtitle.getStyleClass().add("accounts-subtitle");

        VBox header = new VBox(5, title, subtitle);
        header.getStyleClass().add("accounts-header");
        header.setPadding(new Insets(30, 38, 0, 38));

        VBox previewCard = new VBox(10);
        previewCard.setAlignment(Pos.CENTER);
        previewCard.setPadding(new Insets(18, 18, 20, 18));
        previewCard.getStyleClass().add("accounts-hero");
        previewCard.setMinWidth(280);
        previewCard.setPrefWidth(320);
        Label previewHeading = new Label("Player preview");
        previewHeading.getStyleClass().add("accounts-card-name");
        Label rotateHint = new Label("Drag to rotate · Scroll to zoom");
        rotateHint.getStyleClass().add("accounts-subtitle");
        previewCard.getChildren().addAll(previewHeading, modelPreview, rotateHint, fileLabel);

        VBox controls = new VBox(14);
        controls.setPadding(new Insets(24));
        controls.getStyleClass().add("accounts-hero");
        controls.setMaxWidth(Double.MAX_VALUE);

        Label accountHeading = new Label("Minecraft account");
        accountHeading.getStyleClass().add("accounts-card-name");
        accountLabel.setWrapText(true);

        Label modelHeading = new Label("Skin model");
        modelHeading.getStyleClass().add("accounts-card-name");
        modelChoice.getItems().setAll("Classic (Steve)", "Slim (Alex)");
        modelChoice.setValue("Classic (Steve)");
        modelChoice.setMaxWidth(Double.MAX_VALUE);
        modelChoice.valueProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                modelPreview.setSkinModel(newValue.startsWith("Slim"));
            }
        });

        Label capeHeading = new Label("Owned capes");
        capeHeading.getStyleClass().add("accounts-card-name");
        capeChoice.setMaxWidth(Double.MAX_VALUE);
        capeChoice.setPromptText("Loading account capes...");
        capeChoice.valueProperty().addListener((observable, oldValue, newValue) -> {
            modelPreview.setCape(newValue == null ? null : newValue.texture());
            updateAccountControls(accountService.getCurrentAccount());
        });
        applyCapeButton.getStyleClass().add("accounts-primary-button");
        hideCapeButton.getStyleClass().add("accounts-card-use");
        applyCapeButton.setOnAction(event -> applySelectedCape());
        hideCapeButton.setOnAction(event -> hideCape());
        HBox capeActions = new HBox(10, applyCapeButton, hideCapeButton);
        capeActions.setAlignment(Pos.CENTER_LEFT);

        Label formatHint = new Label(
                "Choose Classic or Slim first, then select a 64×64 or legacy 64×32 PNG. Selecting a file uploads it to your signed-in Minecraft Java profile immediately."
        );
        formatHint.setWrapText(true);
        formatHint.getStyleClass().add("accounts-subtitle");

        chooseButton.getStyleClass().add("accounts-card-use");
        uploadButton.getStyleClass().add("accounts-primary-button");
        chooseButton.setOnAction(event -> chooseSkin());
        uploadButton.setOnAction(event -> uploadSkin());

        HBox actions = new HBox(10, chooseButton, uploadButton);
        actions.setAlignment(Pos.CENTER_LEFT);

        statusLabel.setWrapText(true);
        statusLabel.getStyleClass().add("accounts-subtitle");

        controls.getChildren().addAll(
                accountHeading, accountLabel,
                modelHeading, modelChoice,
                capeHeading, capeChoice, capeActions,
                formatHint, actions, statusLabel
        );
        HBox.setHgrow(controls, Priority.ALWAYS);

        HBox body = new HBox(20, previewCard, controls);
        body.setAlignment(Pos.TOP_LEFT);
        body.setPadding(new Insets(24, 38, 36, 38));

        ScrollPane scroll = new ScrollPane(new VBox(header, body));
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("accounts-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);

        accountService.addListener(account ->
                Platform.runLater(() -> onAccountChanged(account)));
        onAccountChanged(accountService.getCurrentAccount());
    }

    private void onAccountChanged(Account account) {
        String nextUuid = account == null ? null : account.getUuid();
        if (nextUuid == null ? currentAccountUuid != null : !nextUuid.equals(currentAccountUuid)) {
            currentAccountUuid = nextUuid;
            profileRequestGeneration++;
            selectedFile = null;
            displayedSkin = null;
            modelPreview.setSkin(null, false);
            fileLabel.setText(account == null
                    ? "Connect a Minecraft account to preview its skin."
                    : "Loading current account skin...");
            if (account != null) {
                statusLabel.setText("Fetching the skin currently active on your Minecraft profile...");
                loadCurrentSkin(account, profileRequestGeneration);
            } else {
                statusLabel.setText("Connect a Minecraft Java account from Accounts first.");
            }
        }
        updateAccountControls(account);
    }

    private void updateAccountControls(Account account) {
        if (account == null) {
            accountLabel.setText("No Minecraft account connected. Connect one from Accounts first.");
            uploadButton.setDisable(true);
            applyCapeButton.setDisable(true);
            hideCapeButton.setDisable(true);
            capeChoice.setDisable(true);
            chooseButton.setDisable(busy);
            modelChoice.setDisable(busy);
        } else {
            accountLabel.setText(account.getUsername() + "  ·  " + account.getUuid());
            uploadButton.setDisable(busy || selectedFile == null);
            applyCapeButton.setDisable(busy || capeChoice.getValue() == null
                    || capeChoice.getValue().id().isBlank());
            hideCapeButton.setDisable(busy);
            capeChoice.setDisable(busy);
            chooseButton.setDisable(busy);
            modelChoice.setDisable(busy);
        }
    }

    private void loadCurrentSkin(Account account, int generation) {
        CompletableFuture.supplyAsync(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(PROFILE_ENDPOINT)
                        .header("Authorization", "Bearer " + account.getAccessToken())
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(25))
                        .GET()
                        .build();
                HttpResponse<String> response = HTTP.send(
                        request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 200) {
                    throw new IOException("Minecraft profile returned HTTP " + response.statusCode());
                }

                JsonObject profile = JsonParser.parseString(response.body()).getAsJsonObject();
                JsonArray skins = profile.has("skins") && profile.get("skins").isJsonArray()
                        ? profile.getAsJsonArray("skins") : new JsonArray();
                JsonObject active = null;
                for (var element : skins) {
                    if (!element.isJsonObject()) continue;
                    JsonObject candidate = element.getAsJsonObject();
                    if (candidate.has("state")
                            && "ACTIVE".equalsIgnoreCase(candidate.get("state").getAsString())) {
                        active = candidate;
                        break;
                    }
                }
                if (active == null && !skins.isEmpty() && skins.get(0).isJsonObject()) {
                    active = skins.get(0).getAsJsonObject();
                }
                if (active == null || !active.has("url") || active.get("url").isJsonNull()) {
                    throw new IOException("Minecraft did not return an active skin for this profile.");
                }

                String skinUrl = active.get("url").getAsString();
                String variant = active.has("variant") && !active.get("variant").isJsonNull()
                        ? active.get("variant").getAsString() : "classic";
                String alias = active.has("alias") && !active.get("alias").isJsonNull()
                        ? active.get("alias").getAsString() : "Active account skin";
                Image image = new Image(skinUrl, false);
                if (image.isError() || image.getPixelReader() == null) {
                    throw new IOException("Minecraft returned a skin URL but its image could not be loaded.");
                }

                List<CapeOption> capes = new ArrayList<>();
                String activeCapeId = "";
                Image activeCapeImage = null;
                JsonArray capeArray = profile.has("capes") && profile.get("capes").isJsonArray()
                        ? profile.getAsJsonArray("capes") : new JsonArray();
                for (var element : capeArray) {
                    if (!element.isJsonObject()) continue;
                    JsonObject capeObject = element.getAsJsonObject();
                    String id = capeObject.has("id") ? capeObject.get("id").getAsString() : "";
                    String capeAlias = capeObject.has("alias") && !capeObject.get("alias").isJsonNull()
                            ? capeObject.get("alias").getAsString() : "Minecraft cape";
                    String capeUrl = capeObject.has("url") && !capeObject.get("url").isJsonNull()
                            ? capeObject.get("url").getAsString() : "";
                    boolean isActive = capeObject.has("state")
                            && "ACTIVE".equalsIgnoreCase(capeObject.get("state").getAsString());
                    Image capeTexture = capeUrl.isBlank() ? null : new Image(capeUrl, false);
                    if (capeTexture != null && (capeTexture.isError() || capeTexture.getPixelReader() == null)) {
                        capeTexture = null;
                    }
                    capes.add(new CapeOption(id, capeAlias, capeUrl, isActive, capeTexture));
                    if (isActive) {
                        activeCapeId = id;
                        activeCapeImage = capeTexture;
                    }
                }
                return new SkinProfile(image, variant, alias, null, capes, activeCapeId, activeCapeImage);
            } catch (Exception ex) {
                return new SkinProfile(null, "classic", null, message(ex), List.of(), "", null);
            }
        }).thenAccept(result -> Platform.runLater(() -> {
            if (generation != profileRequestGeneration
                    || accountService.getCurrentAccount() == null
                    || !account.getUuid().equals(accountService.getCurrentAccount().getUuid())) {
                return;
            }
            if (result.image() == null) {
                fileLabel.setText("Current skin unavailable");
                if (selectedFile == null) {
                    statusLabel.setText("Could not load the active skin: " + result.error());
                }
                return;
            }

            displayedSkin = result.image();
            CapeOption noCape = new CapeOption("", "No cape", "", false, null);
            capeChoice.getItems().setAll(noCape);
            capeChoice.getItems().addAll(result.capes());
            CapeOption selectedCape = result.capes().stream()
                    .filter(CapeOption::active).findFirst().orElse(noCape);
            capeChoice.setValue(selectedCape);
            modelPreview.setCape(result.capeImage());
            boolean slim = "slim".equalsIgnoreCase(result.variant());
            modelChoice.setValue(slim ? "Slim (Alex)" : "Classic (Steve)");
            if (selectedFile == null) {
                modelPreview.setSkin(displayedSkin, slim);
                fileLabel.setText(result.alias());
                statusLabel.setText("Showing the skin currently active on this Minecraft account.");
            }
        }));
    }

    private void chooseSkin() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Minecraft skin");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG skin images", "*.png")
        );

        Window owner = getScene() == null ? null : getScene().getWindow();
        java.io.File file = chooser.showOpenDialog(owner);
        if (file == null) return;

        try {
            Path path = file.toPath();
            long size = Files.size(path);
            if (size <= 0 || size > MAX_SKIN_BYTES) {
                throw new IOException("The skin file must be smaller than 4 MiB.");
            }

            Image image;
            try (InputStream input = Files.newInputStream(path)) {
                image = new Image(input);
            }
            if (image.isError() || image.getPixelReader() == null) {
                throw new IOException("The selected file is not a readable PNG image.");
            }

            int width = (int) image.getWidth();
            int height = (int) image.getHeight();
            if (width != 64 || (height != 64 && height != 32)) {
                throw new IOException("Minecraft skins must be 64×64 or 64×32 pixels.");
            }

            selectedFile = path;
            displayedSkin = image;
            boolean slim = modelChoice.getValue().startsWith("Slim");
            modelPreview.setSkin(image, slim);
            fileLabel.setText("Selected: " + file.getName());
            statusLabel.setText("Skin loaded into the 3D preview. Applying it to your Minecraft account...");
            uploadButton.setDisable(accountService.getCurrentAccount() == null || busy);
            uploadSkin();
        } catch (Exception ex) {
            statusLabel.setText("Could not use this skin: " + message(ex));
        }
    }

    private void uploadSkin() {
        Account account = accountService.getCurrentAccount();
        if (busy || account == null || selectedFile == null) return;

        Path file = selectedFile;
        String variant = modelChoice.getValue().startsWith("Slim") ? "slim" : "classic";
        setBusy(true, "Uploading skin to your Minecraft account...");
        CompletableFuture.runAsync(() -> {
            try {
                upload(account, file, variant);
                AccountSkinService.clear(account);
                Platform.runLater(() -> {
                    selectedFile = null;
                    setBusy(false, "Skin applied to your Minecraft profile. Refreshing the active skin...");
                    fileLabel.setText("Refreshing active account skin...");
                    loadCurrentSkin(account, ++profileRequestGeneration);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> setBusy(false, "Upload failed: " + message(ex)));
            }
        });
    }

    private void applySelectedCape() {
        CapeOption selected = capeChoice.getValue();
        Account account = accountService.getCurrentAccount();
        if (busy || account == null || selected == null || selected.id().isBlank()) return;
        changeCape(account, selected.id(), false);
    }

    private void hideCape() {
        Account account = accountService.getCurrentAccount();
        if (busy || account == null) return;
        changeCape(account, null, true);
    }

    private void changeCape(Account account, String capeId, boolean hide) {
        setBusy(true, hide ? "Hiding cape on your Minecraft profile..." : "Equipping cape on your Minecraft profile...");
        CompletableFuture.runAsync(() -> {
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create("https://api.minecraftservices.com/minecraft/profile/capes/active"))
                        .header("Authorization", "Bearer " + account.getAccessToken())
                        .header("Accept", "application/json")
                        .timeout(Duration.ofSeconds(30));
                if (hide) {
                    builder.DELETE();
                } else {
                    builder.header("Content-Type", "application/json")
                            .PUT(HttpRequest.BodyPublishers.ofString(
                                    "{\"capeId\":\"" + capeId + "\"}", StandardCharsets.UTF_8));
                }
                HttpResponse<String> response = HTTP.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    String detail = response.body() == null ? "" : response.body().strip();
                    if (detail.length() > 250) detail = detail.substring(0, 250);
                    throw new IOException("Minecraft Services returned HTTP " + response.statusCode()
                            + (detail.isBlank() ? "." : ": " + detail));
                }
                AccountSkinService.clear(account);
                Platform.runLater(() -> {
                    setBusy(false, hide ? "Cape hidden. Refreshing profile..." : "Cape equipped. Refreshing profile...");
                    loadCurrentSkin(account, ++profileRequestGeneration);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> setBusy(false, "Cape update failed: " + message(ex)));
            }
        });
    }

    private static void upload(Account account, Path file, String variant) throws Exception {
        String boundary = "VantaSkin" + UUID.randomUUID().toString().replace("-", "");
        byte[] png = Files.readAllBytes(file);
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"variant\"\r\n\r\n");
        write(body, variant + "\r\n");
        write(body, "--" + boundary + "\r\n");
        write(body, "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n");
        write(body, "Content-Type: image/png\r\n\r\n");
        body.write(png);
        write(body, "\r\n--" + boundary + "--\r\n");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(SKIN_ENDPOINT)
                .header("Authorization", "Bearer " + account.getAccessToken())
                .header("Accept", "application/json")
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .timeout(Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = response.body() == null ? "" : response.body().strip();
            if (detail.length() > 300) detail = detail.substring(0, 300);
            throw new IOException("Minecraft Services returned HTTP " + response.statusCode()
                    + (detail.isBlank() ? "." : ": " + detail));
        }
    }

    private static void write(ByteArrayOutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        statusLabel.setText(message);
        updateAccountControls(accountService.getCurrentAccount());
        if (value) {
            chooseButton.setDisable(true);
            uploadButton.setDisable(true);
            modelChoice.setDisable(true);
        }
    }

    private static String message(Throwable error) {
        return error.getMessage() == null || error.getMessage().isBlank()
                ? error.getClass().getSimpleName()
                : error.getMessage();
    }

    private record SkinProfile(Image image, String variant, String alias, String error,
                               List<CapeOption> capes, String activeCapeId, Image capeImage) { }

    private record CapeOption(String id, String alias, String url, boolean active, Image texture) {
        @Override
        public String toString() {
            return alias + (active ? " (equipped)" : "");
        }
    }
}
