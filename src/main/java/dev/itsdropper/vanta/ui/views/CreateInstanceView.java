package dev.itsdropper.vanta.ui.views;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import dev.itsdropper.vanta.launcher.instance.FabricInstaller;
import dev.itsdropper.vanta.launcher.instance.ForgeInstaller;
import dev.itsdropper.vanta.launcher.instance.InstanceInstaller;
import dev.itsdropper.vanta.ui.LauncherSettings;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;


public class CreateInstanceView extends VBox {

    private static final String VERSION_MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private static final HttpClient HTTP =
            HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .build();

    private final TextField nameField;
    private final ComboBox<String> versionBox;
    private final ComboBox<String> loaderBox;

    private final Button createButton;
    private final Button backButton;

    private final Label statusLabel;
    private final Button retryVersionsButton;

    private final Runnable onBack;
    private final Runnable onCreated;
    private final Runnable onBrowseModpacks;

    /*
     * Every version change gets a new check ID.
     *
     * If the user changes versions quickly, an older Fabric
     * check is ignored when it finishes.
     */
    private final AtomicInteger manifestRequestId = new AtomicInteger();

    private final AtomicInteger fabricCheckId =
            new AtomicInteger();

    private volatile boolean fabricCheckRunning;
    private final AtomicInteger forgeCheckId = new AtomicInteger();
    private volatile boolean forgeCheckRunning;

    public CreateInstanceView(
            Runnable onBack,
            Runnable onCreated,
            Runnable onBrowseModpacks
    ) {

        this.onBack = onBack;
        this.onCreated = onCreated;
        this.onBrowseModpacks = onBrowseModpacks;

        // =========================================================
        // PAGE
        // =========================================================

        getStyleClass().add("page");

        setPadding(
                new Insets(32, 36, 36, 36)
        );

        setSpacing(
                20
        );

        // =========================================================
        // HEADER
        // =========================================================

        Label title =
                new Label("Create instance");

        title.getStyleClass().add(
                "page-title"
        );

        Label subtitle =
                new Label(
                        "Set up a new Minecraft installation."
                );

        subtitle.getStyleClass().add(
                "page-subtitle"
        );

        VBox header =
                new VBox(
                        6,
                        title,
                        subtitle
                );

        // =========================================================
        // INSTANCE PANEL
        // =========================================================

        VBox panel =
                new VBox(16);

        panel.getStyleClass().add(
                "create-instance-panel"
        );

        panel.setPadding(
                new Insets(28)
        );

        panel.setMaxWidth(
                680
        );

        // =========================================================
        // PANEL TITLE
        // =========================================================

        Label panelTitle =
                new Label("Instance details");

        panelTitle.getStyleClass().add(
                "create-panel-title"
        );

        Label panelSubtitle = new Label(
                "Choose a name, Minecraft version, and loader. You can change instance settings later."
        );
        panelSubtitle.getStyleClass().add("create-panel-subtitle");

        // =========================================================
        // NAME
        // =========================================================

        Label nameLabel =
                createLabel("Name");

        nameField =
                new TextField();

        nameField.setPromptText(
                "e.g. Vanta PvP"
        );

        nameField.setPrefHeight(
                44
        );

        nameField.getStyleClass().add(
                "create-field"
        );

        // =========================================================
        // VERSION
        // =========================================================

        Label versionLabel =
                createLabel("Minecraft version");

        versionBox =
                new ComboBox<>();

        versionBox.setPromptText(
                "Loading versions..."
        );

        versionBox.setMaxWidth(
                Double.MAX_VALUE
        );

        versionBox.setPrefHeight(
                44
        );

        versionBox.getStyleClass().add(
                "create-combo"
        );

        /*
         * When the Minecraft version changes, check whether
         * Fabric supports that exact version.
         */
        versionBox.valueProperty().addListener(
                (observable, oldValue, newValue) ->
                        checkLoaderAvailability(newValue)
        );

        // =========================================================
        // LOADER
        // =========================================================

        Label loaderLabel =
                createLabel("Mod loader");

        loaderBox =
                new ComboBox<>();

        /*
         * Vanilla is always valid.
         *
         * Fabric and Forge are added only after we verify that the
         * selected Minecraft version supports them.
         */
        loaderBox.getItems().addAll(
                "Vanilla",
                "Fabric",
                "Forge"
        );

        loaderBox.getSelectionModel()
                .select("Vanilla");

        loaderBox.setMaxWidth(
                Double.MAX_VALUE
        );

        loaderBox.setPrefHeight(
                44
        );

        loaderBox.getStyleClass().add(
                "create-combo"
        );

        // =========================================================
        // FORM
        // =========================================================

        VBox form =
                new VBox(
                        12
                );

        form.getChildren().addAll(
                nameLabel,
                nameField,

                versionLabel,
                versionBox,

                loaderLabel,
                loaderBox
        );

        // =========================================================
        // STATUS
        // =========================================================

        statusLabel =
                new Label(
                        "Loading Minecraft versions..."
                );

        statusLabel.getStyleClass().add(
                "create-status"
        );
        statusLabel.setWrapText(true);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        retryVersionsButton = new Button("RETRY");
        retryVersionsButton.getStyleClass().add("secondary-button");
        retryVersionsButton.setAccessibleText("Retry loading Minecraft versions");
        retryVersionsButton.setOnAction(event -> loadVersions());
        retryVersionsButton.setVisible(false);
        retryVersionsButton.setManaged(false);

        statusLabel.setMinWidth(0);
        FlowPane statusRow = new FlowPane(10, 8, statusLabel, retryVersionsButton);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setMaxWidth(Double.MAX_VALUE);

        // =========================================================
        // ACTIONS
        // =========================================================

        backButton =
                new Button(
                        "Back"
                );

        backButton.getStyleClass().add(
                "secondary-button"
        );

        backButton.setPrefHeight(
                46
        );

        backButton.setPrefWidth(
                92
        );

        backButton.setOnAction(
                event ->
                        onBack.run()
        );

        createButton =
                new Button(
                        "Create instance"
                );

        createButton.getStyleClass().add(
                "primary-button"
        );

        createButton.setPrefHeight(
                46
        );

        createButton.setPrefWidth(
                150
        );

        createButton.setOnAction(
                event ->
                        createInstance()
        );

        Button browseModpacksButton =
                new Button(
                        "Browse modpacks"
                );

        browseModpacksButton.getStyleClass().add(
                "secondary-button"
        );

        browseModpacksButton.setOnAction(
                event -> onBrowseModpacks.run()
        );

        FlowPane actions = new FlowPane(
                12, 8,
                backButton,
                browseModpacksButton,
                createButton
        );
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMaxWidth(Double.MAX_VALUE);

        // =========================================================
        // PANEL BUILD
        // =========================================================

        panel.getChildren().addAll(
                panelTitle,
                panelSubtitle,
                form,
                statusRow,
                actions
        );

        // =========================================================
        // BUILD PAGE
        // =========================================================

        getChildren().addAll(
                header,
                panel
        );

        loadVersions();
    }

    // =============================================================
    // LABEL
    // =============================================================

    private Label createLabel(
            String text
    ) {

        Label label =
                new Label(text);

        label.getStyleClass().add(
                "create-label"
        );

        return label;
    }

    // =============================================================
    // VERSIONS
    // =============================================================

    private void loadVersions() {
        int requestId = manifestRequestId.incrementAndGet();
        createButton.setDisable(true);
        versionBox.setDisable(true);
        versionBox.setPromptText("Loading Minecraft releases...");
        statusLabel.setText("Loading Minecraft version manifest...");

        UiExecutor.shared().execute(() -> {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(VERSION_MANIFEST_URL))
                        .timeout(Duration.ofSeconds(15))
                        .header("User-Agent", "VantaLauncher/1.0")
                        .GET()
                        .build();
                HttpResponse<String> response = HTTP.send(
                        request, HttpResponse.BodyHandlers.ofString()
                );

                if (response.statusCode() < 200 || response.statusCode() >= 300) {
                    throw new IllegalStateException(
                            "Minecraft version service returned HTTP " + response.statusCode()
                    );
                }

                JsonNode manifest = MAPPER.readTree(response.body());
                JsonNode versions = manifest.get("versions");
                if (versions == null || !versions.isArray()) {
                    throw new IllegalStateException("Invalid Minecraft version manifest.");
                }

                java.util.List<String> releases = new java.util.ArrayList<>();
                for (JsonNode version : versions) {
                    JsonNode idNode = version.get("id");
                    JsonNode typeNode = version.get("type");
                    if (idNode == null || typeNode == null || !typeNode.isTextual()
                            || !"release".equals(typeNode.asText()) || !idNode.isTextual()) {
                        continue;
                    }
                    releases.add(idNode.asText());
                }

                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != manifestRequestId.get()) return;
                    versionBox.getItems().setAll(releases);
                    if (releases.isEmpty()) {
                        versionBox.setDisable(true);
                        loaderBox.setDisable(true);
                        createButton.setDisable(true);
                        versionBox.setPromptText("No releases available");
                        statusLabel.setText("No Minecraft releases were returned. Check your connection and retry.");
                        retryVersionsButton.setVisible(true);
                        retryVersionsButton.setManaged(true);
                        return;
                    }

                    versionBox.setDisable(false);
                    loaderBox.setDisable(false);
                    retryVersionsButton.setVisible(false);
                    retryVersionsButton.setManaged(false);
                    String preferredVersion = LauncherSettings.getDefaultMinecraftVersion();
                    if (!preferredVersion.isBlank() && releases.contains(preferredVersion)) {
                        versionBox.getSelectionModel().select(preferredVersion);
                    } else {
                        versionBox.getSelectionModel().selectFirst();
                    }
                    statusLabel.setText("Minecraft releases loaded. Checking loader compatibility...");
                });
            } catch (Throwable ex) {
                ex.printStackTrace();
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != manifestRequestId.get()) return;
                    versionBox.getItems().clear();
                    versionBox.setDisable(true);
                    loaderBox.setDisable(true);
                    createButton.setDisable(true);
                    versionBox.setPromptText("Could not load versions");
                    statusLabel.setText("Could not load Minecraft releases. Check your connection and retry.");
                    retryVersionsButton.setVisible(true);
                    retryVersionsButton.setManaged(true);
                });
            }
        });
    }

    // =============================================================
    // LOADER AVAILABILITY
    // =============================================================

    private void checkLoaderAvailability(String minecraftVersion) {

        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            return;
        }

        int fabricId = fabricCheckId.incrementAndGet();
        int forgeId = forgeCheckId.incrementAndGet();
        fabricCheckRunning = true;
        forgeCheckRunning = true;

        UiExecutor.shared().dispatchToUi(() -> {
            loaderBox.getItems().setAll(
                    "Vanilla",
                    "Fabric",
                    "Forge"
            );
            loaderBox.getSelectionModel().select("Vanilla");
            loaderBox.setDisable(true);
            createButton.setDisable(true);
            statusLabel.setText("Checking available mod loaders for Minecraft " + minecraftVersion + "...");
        });

        UiExecutor.shared().execute(() -> {
            boolean fabricAvailable = false;
            boolean forgeAvailable = false;

            try {
                FabricInstaller.findLatestLoaderVersion(minecraftVersion);
                fabricAvailable = true;
            } catch (Throwable ignored) {
            }

            try {
                ForgeInstaller.findLatestLoaderVersion(minecraftVersion);
                forgeAvailable = true;
            } catch (Throwable ignored) {
            }

            final boolean fabric = fabricAvailable;
            final boolean forge = forgeAvailable;

            UiExecutor.shared().dispatchToUi(() -> {
                if (fabricId != fabricCheckId.get() || forgeId != forgeCheckId.get()) {
                    return;
                }

                fabricCheckRunning = false;
                forgeCheckRunning = false;
                loaderBox.getItems().setAll(
                        "Vanilla",
                        "Fabric",
                        "Forge"
                );

                // Keep unsupported loaders visible but prevent selecting them.
                loaderBox.getSelectionModel().select("Vanilla");

                String preferredLoader = LauncherSettings.getDefaultLoader();
                if (loaderBox.getItems().contains(preferredLoader)) {
                    loaderBox.getSelectionModel().select(preferredLoader);
                } else {
                    loaderBox.getSelectionModel().select("Vanilla");
                }

                if (fabric || forge) {
                    statusLabel.setText("Available loaders checked for Minecraft " + minecraftVersion + ".");
                } else {
                    statusLabel.setText("No mod loader is available for Minecraft " + minecraftVersion + ". Vanilla only.");
                }

                loaderBox.setDisable(false);

                if (!fabric) {
                    loaderBox.getItems().remove("Fabric");
                }

                if (!forge) {
                    loaderBox.getItems().remove("Forge");
                }

                createButton.setDisable(false);
            });
        });

    }

    // =============================================================
    // CREATE
    // =============================================================

    private void createInstance() {

        if (fabricCheckRunning || forgeCheckRunning) {

            statusLabel.setText(
                    "Still checking mod loader support..."
            );

            return;
        }

        String name =
                nameField
                        .getText()
                        .trim();

        String version =
                versionBox.getValue();

        String loader =
                loaderBox.getValue();

        // ---------------------------------------------------------
        // VALIDATION
        // ---------------------------------------------------------

        if (name.isBlank()) {

            statusLabel.setText(
                    "Enter an instance name."
            );

            nameField.requestFocus();

            return;
        }

        if (version == null
                || version.isBlank()) {

            statusLabel.setText(
                    "Select a Minecraft version."
            );

            return;
        }

        if (loader == null
                || loader.isBlank()) {

            statusLabel.setText(
                    "Select a loader."
            );

            return;
        }

        /*
         * Extra safety check: only install a loader that the
         * current version check actually exposed in the UI.
         */
        if ("Fabric".equals(loader)) {

            /* The loader was exposed only after the version check. */
            if (!loaderBox.getItems().contains("Fabric")) {

                loaderBox.getSelectionModel()
                        .select("Vanilla");

                statusLabel.setText(
                        "Fabric is not available for Minecraft "
                                + version
                                + "."
                );

                return;
            }
        }

        // ---------------------------------------------------------
        // LOCK UI
        // ---------------------------------------------------------

        createButton.setDisable(true);
        backButton.setDisable(true);
        nameField.setDisable(true);
        versionBox.setDisable(true);
        loaderBox.setDisable(true);

        createButton.setText(
                "INSTALLING..."
        );

        statusLabel.setText(
                "Installing Minecraft "
                        + version
                        + "..."
        );

        // ---------------------------------------------------------
        // INSTALL
        // ---------------------------------------------------------

        UiExecutor.shared().execute(() -> {

                    try {

                        if ("Fabric".equals(loader)) {

                            InstanceInstaller.installFabric(
                                    name,
                                    version
                            );

                        } else if ("Forge".equals(loader)) {

                            InstanceInstaller.installForge(
                                    name,
                                    version
                            );

                        } else {

                            InstanceInstaller.installVanilla(
                                    name,
                                    version
                            );
                        }

                        UiExecutor.shared().dispatchToUi(() -> {

                            statusLabel.setText(
                                    "Instance created successfully."
                            );

                            onCreated.run();
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        UiExecutor.shared().dispatchToUi(() -> {

                            createButton.setDisable(false);
                            backButton.setDisable(false);
                            nameField.setDisable(false);
                            versionBox.setDisable(false);
                            loaderBox.setDisable(false);

                            createButton.setText(
                                    "CREATE INSTANCE"
                            );

                            statusLabel.setText(
                                    ex.getMessage() != null
                                            ? ex.getMessage()
                                            : "Failed to create instance."
                            );
                        });
                    }

        });

    }
}

