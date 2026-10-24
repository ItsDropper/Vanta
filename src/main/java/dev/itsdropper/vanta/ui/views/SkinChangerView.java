package dev.itsdropper.vanta.ui.views;

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
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class SkinChangerView extends VBox {

    private static final URI SKIN_ENDPOINT =
            URI.create("https://api.minecraftservices.com/minecraft/profile/skins");
    private static final long MAX_SKIN_BYTES = 4L * 1024 * 1024;

    private final AccountService accountService;
    private final Label accountLabel = new Label("Checking account...");
    private final Label fileLabel = new Label("No skin selected");
    private final Label statusLabel = new Label("Choose a PNG skin file to get started.");
    private final ImageView preview = new ImageView();
    private final ComboBox<String> modelChoice = new ComboBox<>();
    private final Button chooseButton = new Button("Choose PNG");
    private final Button uploadButton = new Button("Apply skin");
    private Path selectedFile;
    private boolean busy;

    public SkinChangerView(AccountService accountService) {
        this.accountService = accountService;
        getStyleClass().addAll("page", "skin-changer-page");
        setFillWidth(true);

        Label title = new Label("Skin changer");
        title.getStyleClass().add("accounts-title");
        Label subtitle = new Label(
                "Change the skin on your authenticated Minecraft Java profile."
        );
        subtitle.getStyleClass().add("accounts-subtitle");

        VBox header = new VBox(5, title, subtitle);
        header.getStyleClass().add("accounts-header");
        header.setPadding(new Insets(30, 38, 0, 38));

        preview.setFitWidth(192);
        preview.setFitHeight(192);
        preview.setPreserveRatio(false);
        preview.setSmooth(false);
        preview.setImage(createPlaceholder());

        VBox previewCard = new VBox(14);
        previewCard.setAlignment(Pos.CENTER);
        previewCard.setPadding(new Insets(24));
        previewCard.getStyleClass().add("accounts-hero");
        previewCard.setMinWidth(250);
        previewCard.getChildren().addAll(preview, fileLabel);

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

        Label formatHint = new Label(
                "Use a 64×64 or legacy 64×32 PNG. The model setting must match your skin."
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

        accountService.addListener(account -> Platform.runLater(() -> updateAccount(account)));
        updateAccount(accountService.getCurrentAccount());
    }

    private void updateAccount(Account account) {
        if (account == null) {
            accountLabel.setText("No Minecraft account connected. Connect one from Accounts first.");
            uploadButton.setDisable(true);
            chooseButton.setDisable(busy);
        } else {
            accountLabel.setText(account.getUsername() + "  ·  " + account.getUuid());
            uploadButton.setDisable(busy || selectedFile == null);
            chooseButton.setDisable(busy);
        }
    }

    private void chooseSkin() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose Minecraft skin");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("PNG skin images", "*.png")
        );

        Window owner = getScene() == null ? null : getScene().getWindow();
        java.io.File file = chooser.showOpenDialog(owner);
        if (file == null) {
            return;
        }

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
            preview.setImage(image);
            fileLabel.setText(file.getName() + "  ·  " + width + "×" + height);
            statusLabel.setText("Ready to upload. Your current profile skin will be replaced.");
            uploadButton.setDisable(accountService.getCurrentAccount() == null || busy);
        } catch (Exception ex) {
            statusLabel.setText("Could not use this skin: " + message(ex));
        }
    }

    private void uploadSkin() {
        Account account = accountService.getCurrentAccount();
        if (busy || account == null || selectedFile == null) {
            return;
        }

        Path file = selectedFile;
        String variant = modelChoice.getValue().startsWith("Slim") ? "slim" : "classic";
        setBusy(true, "Uploading skin to Minecraft...");
        CompletableFuture.runAsync(() -> {
            try {
                upload(account, file, variant);
                AccountSkinService.clear(account);
                Platform.runLater(() -> {
                    setBusy(false, "Skin uploaded successfully. It may take a short time to appear in-game.");
                });
            } catch (Exception ex) {
                Platform.runLater(() -> setBusy(false, "Upload failed: " + message(ex)));
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
                .timeout(java.time.Duration.ofSeconds(45))
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();

        HttpResponse<String> response = HttpClient.newHttpClient().send(
                request, HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            String detail = response.body() == null ? "" : response.body().strip();
            if (detail.length() > 300) {
                detail = detail.substring(0, 300);
            }
            throw new IOException(
                    "Minecraft Services returned HTTP " + response.statusCode()
                            + (detail.isBlank() ? "." : ": " + detail)
            );
        }
    }

    private static void write(ByteArrayOutputStream output, String text) throws IOException {
        output.write(text.getBytes(StandardCharsets.UTF_8));
    }

    private void setBusy(boolean value, String message) {
        busy = value;
        statusLabel.setText(message);
        chooseButton.setDisable(value);
        uploadButton.setDisable(value || selectedFile == null
                || accountService.getCurrentAccount() == null);
        modelChoice.setDisable(value);
        updateAccount(accountService.getCurrentAccount());
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

    private static Image createPlaceholder() {
        // A neutral placeholder is used until the user selects a local skin.
        return new Image(
                "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+/lWQAAAAASUVORK5CYII=",
                192, 192, false, false
        );
    }
}
