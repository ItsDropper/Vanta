package org.example.ui.views;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.example.launcher.instance.FabricInstaller;
import org.example.launcher.instance.ForgeInstaller;
import org.example.launcher.instance.InstanceInstaller;
import org.example.ui.LauncherSettings;

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

    private final Runnable onBack;
    private final Runnable onCreated;
    private final Runnable onBrowseModpacks;

    /*
     * Every version change gets a new check ID.
     *
     * If the user changes versions quickly, an older Fabric
     * check is ignored when it finishes.
     */
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

        HBox actions =
                new HBox(
                        12,
                        backButton,
                        browseModpacksButton,
                        createButton
                );

        actions.setAlignment(
                Pos.CENTER_RIGHT
        );

        // =========================================================
        // PANEL BUILD
        // =========================================================

        panel.getChildren().addAll(
                panelTitle,
                panelSubtitle,
                form,
                statusLabel,
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

        Thread thread =
                new Thread(() -> {

                    try {

                        HttpRequest request =
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        VERSION_MANIFEST_URL
                                                )
                                        )
                                        .timeout(Duration.ofSeconds(15))
                                        .GET()
                                        .build();

                        HttpResponse<String> response =
                                HTTP.send(
                                        request,
                                        HttpResponse.BodyHandlers.ofString()
                                );

                        if (response.statusCode() < 200
                                || response.statusCode() >= 300) {

                            throw new IllegalStateException(
                                    "Failed to load Minecraft versions."
                            );
                        }

                        JsonNode manifest =
                                MAPPER.readTree(
                                        response.body()
                                );

                        JsonNode versions =
                                manifest.get("versions");

                        if (versions == null
                                || !versions.isArray()) {

                            throw new IllegalStateException(
                                    "Invalid Minecraft version manifest."
                            );
                        }

                        for (JsonNode version : versions) {

                            String id =
                                    version
                                            .get("id")
                                            .asText();

                            String type =
                                    version
                                            .get("type")
                                            .asText();

                            if (!"release".equals(type)) {
                                continue;
                            }

                            Platform.runLater(() ->
                                    versionBox
                                            .getItems()
                                            .add(id)
                            );
                        }

                        Platform.runLater(() -> {

                            if (versionBox
                                    .getItems()
                                    .isEmpty()) {

                                versionBox.setDisable(
                                        true
                                );

                                loaderBox.setDisable(
                                        true
                                );

                                createButton.setDisable(
                                        true
                                );

                                statusLabel.setText(
                                        "No Minecraft versions found."
                                );

                                return;
                            }

                            String preferredVersion =
                                    LauncherSettings.getDefaultMinecraftVersion();

                            if (!preferredVersion.isBlank()
                                    && versionBox.getItems().contains(preferredVersion)) {
                                versionBox.getSelectionModel().select(preferredVersion);
                            } else {
                                versionBox.getSelectionModel().selectFirst();
                            }

                            statusLabel.setText(
                                    "Checking mod loader support..."
                            );
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() -> {

                            versionBox.setDisable(
                                    true
                            );

                            loaderBox.setDisable(
                                    true
                            );

                            createButton.setDisable(
                                    true
                            );

                            statusLabel.setText(
                                    "Failed to load Minecraft versions."
                            );
                        });
                    }

                });

        thread.setDaemon(true);
        thread.start();
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

        Platform.runLater(() -> {
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

        Thread thread = new Thread(() -> {
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

            Platform.runLater(() -> {
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

        thread.setDaemon(true);
        thread.start();
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

        Thread thread =
                new Thread(() -> {

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

                        Platform.runLater(() -> {

                            statusLabel.setText(
                                    "Instance created successfully."
                            );

                            onCreated.run();
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() -> {

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

        thread.setDaemon(true);
        thread.start();
    }
}

