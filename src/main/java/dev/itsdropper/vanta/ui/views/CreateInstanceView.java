package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.async.UiExecutor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import dev.itsdropper.vanta.launcher.instance.FabricInstaller;
import dev.itsdropper.vanta.launcher.instance.BabricInstaller;
import dev.itsdropper.vanta.launcher.instance.NilLoaderInstaller;
import dev.itsdropper.vanta.launcher.instance.LiteLoaderInstaller;
import dev.itsdropper.vanta.launcher.instance.ModLoaderInstaller;
import dev.itsdropper.vanta.launcher.instance.ForgeInstaller;
import dev.itsdropper.vanta.launcher.instance.NeoForgeInstaller;
import dev.itsdropper.vanta.launcher.instance.OrnitheInstaller;
import dev.itsdropper.vanta.launcher.instance.LegacyFabricInstaller;
import dev.itsdropper.vanta.launcher.instance.QuiltInstaller;
import dev.itsdropper.vanta.launcher.instance.InstanceInstaller;
import dev.itsdropper.vanta.launcher.instance.VantaLoaderInstaller;
import dev.itsdropper.vanta.launcher.instance.RiftInstaller;
import dev.itsdropper.vanta.ui.LauncherSettings;
import dev.itsdropper.vanta.telemetry.TelemetryService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
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
    private final Set<String> availableLoaders = new HashSet<>();

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
    private final AtomicInteger quiltCheckId = new AtomicInteger();
    private volatile boolean quiltCheckRunning;
    private final AtomicInteger neoForgeCheckId = new AtomicInteger();
    private volatile boolean neoForgeCheckRunning;
    private final AtomicInteger ornitheCheckId = new AtomicInteger();
    private volatile boolean ornitheCheckRunning;
    private final AtomicInteger legacyFabricCheckId = new AtomicInteger();
    private volatile boolean legacyFabricCheckRunning;
    private final AtomicInteger babricCheckId = new AtomicInteger();
    private volatile boolean babricCheckRunning;
    private final AtomicInteger liteLoaderCheckId = new AtomicInteger();
    private volatile boolean liteLoaderCheckRunning;
    private final AtomicInteger modLoaderCheckId = new AtomicInteger();
    private volatile boolean modLoaderCheckRunning;
    private final AtomicInteger riftCheckId = new AtomicInteger();
    private volatile boolean riftCheckRunning;
    private final AtomicInteger vantaLoaderCheckId = new AtomicInteger();
    private volatile boolean vantaLoaderCheckRunning;

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
                "Quilt",
                "Forge",
                "NeoForge",
                "Ornithe",
                "Legacy Fabric"
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
        loaderBox.setCellFactory(combo -> new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setDisable(false);
                } else {
                    boolean available = availableLoaders.contains(item);
                    String label = "ModLoader".equals(item) ? "Risugami's ModLoader" : item;
                    setText(available ? label : label + "  (not available for this version)");
                    setDisable(!available);
                }
            }
        });
        loaderBox.setButtonCell(new ListCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null
                        : ("ModLoader".equals(item) ? "Risugami's ModLoader" : item));
            }
        });
        availableLoaders.add("Vanilla");

        Label loaderHint = new Label(
                "Experimental independent loader: Vanta passes the isolated instance directory and official Minecraft mappings to the agent. "
                        + "Native mods and exact-target ASM transformations are available; in-game behavior, Minecraft lifecycle hooks, "
                        + "Mixin, and Fabric/Forge mod compatibility are not yet supported."
        );
        loaderHint.getStyleClass().add("create-panel-subtitle");
        loaderHint.setWrapText(true);
        loaderHint.setVisible(false);
        loaderHint.setManaged(false);
        loaderBox.valueProperty().addListener((observable, oldValue, newValue) -> {
            boolean preview = "VantaLoader".equals(newValue);
            loaderHint.setVisible(preview);
            loaderHint.setManaged(preview);
        });

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
                loaderBox,
                loaderHint
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
        versionBox.setPromptText("Loading Minecraft versions...");
        statusLabel.setText("Loading Minecraft version manifest, including historical versions...");

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

                java.util.List<String> availableVersions = new java.util.ArrayList<>();
                for (JsonNode version : versions) {
                    JsonNode idNode = version.get("id");
                    JsonNode typeNode = version.get("type");
                    if (idNode == null || !idNode.isTextual()
                            || typeNode == null || !typeNode.isTextual()) {
                        continue;
                    }

                    String type = typeNode.asText();
                    if ("release".equals(type)
                            || "old_beta".equals(type)
                            || "old_alpha".equals(type)) {
                        availableVersions.add(idNode.asText());
                    }
                }

                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != manifestRequestId.get()) return;
                    versionBox.getItems().setAll(availableVersions);
                    if (availableVersions.isEmpty()) {
                        versionBox.setDisable(true);
                        loaderBox.setDisable(true);
                        createButton.setDisable(true);
                        versionBox.setPromptText("No Minecraft versions available");
                        statusLabel.setText("No Minecraft versions were returned. Check your connection and retry.");
                        retryVersionsButton.setVisible(true);
                        retryVersionsButton.setManaged(true);
                        return;
                    }

                    versionBox.setDisable(false);
                    loaderBox.setDisable(false);
                    retryVersionsButton.setVisible(false);
                    retryVersionsButton.setManaged(false);
                    String preferredVersion = LauncherSettings.getDefaultMinecraftVersion();
                    if (!preferredVersion.isBlank() && availableVersions.contains(preferredVersion)) {
                        versionBox.getSelectionModel().select(preferredVersion);
                    } else {
                        versionBox.getSelectionModel().selectFirst();
                    }
                    statusLabel.setText("Minecraft versions loaded, including official Alpha and Beta history. Checking loader compatibility...");
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
                    statusLabel.setText("Could not load Minecraft versions. Check your connection and retry.");
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
        int quiltId = quiltCheckId.incrementAndGet();
        int neoForgeId = neoForgeCheckId.incrementAndGet();
        int ornitheId = ornitheCheckId.incrementAndGet();
        int legacyFabricId = legacyFabricCheckId.incrementAndGet();
        int babricId = babricCheckId.incrementAndGet();
        int liteLoaderId = liteLoaderCheckId.incrementAndGet();
        int modLoaderId = modLoaderCheckId.incrementAndGet();
        int riftId = riftCheckId.incrementAndGet();
        int vantaLoaderId = vantaLoaderCheckId.incrementAndGet();
        fabricCheckRunning = true;
        forgeCheckRunning = true;
        quiltCheckRunning = true;
        neoForgeCheckRunning = true;
        ornitheCheckRunning = true;
        legacyFabricCheckRunning = true;
        babricCheckRunning = true;
        liteLoaderCheckRunning = true;
        modLoaderCheckRunning = true;
        riftCheckRunning = true;
        vantaLoaderCheckRunning = true;

        UiExecutor.shared().dispatchToUi(() -> {
            loaderBox.getItems().setAll(
                    "Vanilla", "Fabric", "Quilt", "Forge", "NeoForge",
                    "Ornithe", "Legacy Fabric", "Babric", "NilLoader",
                    "LiteLoader", "ModLoader", "Rift", "VantaLoader"
            );
            loaderBox.getSelectionModel().select("Vanilla");
            loaderBox.setDisable(true);
            createButton.setDisable(true);
            statusLabel.setText("Checking available mod loaders for Minecraft " + minecraftVersion + "...");
        });

        UiExecutor.shared().execute(() -> {
            boolean fabricAvailable = false;
            boolean forgeAvailable = false;
            boolean quiltAvailable = false;
            boolean neoForgeAvailable = false;
            boolean ornitheAvailable = false;
            boolean legacyFabricAvailable = false;
            boolean babricAvailable = false;
            boolean nilLoaderAvailable = false;
            boolean liteLoaderAvailable = false;
            boolean modLoaderAvailable = false;
            boolean riftAvailable = false;
            boolean vantaLoaderAvailable = false;

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

            try {
                QuiltInstaller.findLatestLoaderVersion(minecraftVersion);
                quiltAvailable = true;
            } catch (Throwable ignored) {
            }

            try {
                NeoForgeInstaller.findLatestLoaderVersion(minecraftVersion);
                neoForgeAvailable = true;
            } catch (Throwable ignored) {
            }

            try {
                OrnitheInstaller.findLatestLoaderVersion(minecraftVersion);
                ornitheAvailable = true;
            } catch (Throwable ignored) {
            }

            try {
                LegacyFabricInstaller.findLatestLoaderVersion(minecraftVersion);
                legacyFabricAvailable = true;
            } catch (Throwable ignored) {
            }

            try {
                BabricInstaller.findLatestLoaderVersion(minecraftVersion);
                babricAvailable = true;
            } catch (Throwable ignored) {
            }

            try {
                NilLoaderInstaller.findLatestLoaderVersion(minecraftVersion);
                nilLoaderAvailable = true;
            } catch (Throwable ignored) {
            }

            try {
                LiteLoaderInstaller.findLatestLoaderVersion(minecraftVersion);
                liteLoaderAvailable = true;
            } catch (Throwable ignored) {
            }

            try {
                ModLoaderInstaller.findLatestLoaderVersion(minecraftVersion);
                modLoaderAvailable = true;
            } catch (Throwable ignored) {
            }

            try {
                RiftInstaller.findLatestLoaderVersion(minecraftVersion);
                riftAvailable = true;
            } catch (Throwable ignored) {
            }

            final boolean fabric = fabricAvailable;
            final boolean forge = forgeAvailable;
            final boolean quilt = quiltAvailable;
            final boolean neoForge = neoForgeAvailable;
            final boolean ornithe = ornitheAvailable;
            final boolean legacyFabric = legacyFabricAvailable;
            final boolean babric = babricAvailable;
            final boolean nilLoader = nilLoaderAvailable;
            final boolean liteLoader = liteLoaderAvailable;
            final boolean modLoader = modLoaderAvailable;
            final boolean rift = riftAvailable;
            try {
                if (VantaLoaderInstaller.supportsMinecraftVersion(minecraftVersion)) {
                    VantaLoaderInstaller.findLatestLoaderVersion();
                    vantaLoaderAvailable = true;
                }
            } catch (Throwable ignored) {
            }
            final boolean vantaLoader = vantaLoaderAvailable;

            UiExecutor.shared().dispatchToUi(() -> {
                if (fabricId != fabricCheckId.get() || forgeId != forgeCheckId.get()
                        || quiltId != quiltCheckId.get() || neoForgeId != neoForgeCheckId.get()
                        || ornitheId != ornitheCheckId.get()
                        || legacyFabricId != legacyFabricCheckId.get()
                        || babricId != babricCheckId.get()
                        || liteLoaderId != liteLoaderCheckId.get()
                        || modLoaderId != modLoaderCheckId.get()
                        || riftId != riftCheckId.get()
                        || vantaLoaderId != vantaLoaderCheckId.get()) {
                    return;
                }

                availableLoaders.clear();
                availableLoaders.add("Vanilla");
                if (fabric) availableLoaders.add("Fabric");
                if (forge) availableLoaders.add("Forge");
                if (quilt) availableLoaders.add("Quilt");
                if (neoForge) availableLoaders.add("NeoForge");
                if (ornithe) availableLoaders.add("Ornithe");
                if (legacyFabric) availableLoaders.add("Legacy Fabric");
                if (babric) availableLoaders.add("Babric");
                if (nilLoader) availableLoaders.add("NilLoader");
                if (liteLoader) availableLoaders.add("LiteLoader");
                if (modLoader) availableLoaders.add("ModLoader");
                if (rift) availableLoaders.add("Rift");
                if (vantaLoader) availableLoaders.add("VantaLoader");

                fabricCheckRunning = false;
                forgeCheckRunning = false;
                quiltCheckRunning = false;
                neoForgeCheckRunning = false;
                ornitheCheckRunning = false;
                legacyFabricCheckRunning = false;
                babricCheckRunning = false;
                liteLoaderCheckRunning = false;
                modLoaderCheckRunning = false;
                riftCheckRunning = false;
                vantaLoaderCheckRunning = false;
                loaderBox.getItems().setAll(
                        "Vanilla", "Fabric", "Quilt", "Forge", "NeoForge",
                        "Ornithe", "Legacy Fabric", "Babric", "NilLoader",
                        "LiteLoader", "ModLoader", "Rift", "VantaLoader"
                );

                // Keep unsupported loaders visible but prevent selecting them.
                loaderBox.getSelectionModel().select("Vanilla");

                String preferredLoader = LauncherSettings.getDefaultLoader();
                if (loaderBox.getItems().contains(preferredLoader)) {
                    loaderBox.getSelectionModel().select(preferredLoader);
                } else {
                    loaderBox.getSelectionModel().select("Vanilla");
                }

                if (fabric || forge || quilt || neoForge || ornithe || legacyFabric || babric || nilLoader || liteLoader || modLoader || rift || vantaLoader) {
                    statusLabel.setText("Available loaders checked for Minecraft " + minecraftVersion + ".");
                } else {
                    statusLabel.setText("No mod loader is available for Minecraft " + minecraftVersion + ". Vanilla only.");
                }

                loaderBox.setDisable(false);

                // Keep every implemented loader discoverable. Incompatible
                // entries remain visible but are disabled by the cell factory.
                if (loaderBox.getValue() == null
                        || !availableLoaders.contains(loaderBox.getValue())) {
                    loaderBox.getSelectionModel().select("Vanilla");
                }

                createButton.setDisable(false);
            });
        });

    }

    // =============================================================
    // CREATE
    // =============================================================

    private void createInstance() {

        if (fabricCheckRunning || forgeCheckRunning || quiltCheckRunning
                || neoForgeCheckRunning || ornitheCheckRunning || legacyFabricCheckRunning
                || babricCheckRunning || liteLoaderCheckRunning || modLoaderCheckRunning
                || riftCheckRunning || vantaLoaderCheckRunning) {

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
        if ("Babric".equals(loader) && !availableLoaders.contains("Babric")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText("Babric is currently available only for Minecraft b1.7.3.");
            return;
        }

        if ("LiteLoader".equals(loader) && !availableLoaders.contains("LiteLoader")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText("LiteLoader is not available for Minecraft " + version + ".");
            return;
        }

        if ("ModLoader".equals(loader) && !availableLoaders.contains("ModLoader")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText("No verified Risugami ModLoader archive is available for Minecraft " + version + ".");
            return;
        }

        if ("NilLoader".equals(loader) && !availableLoaders.contains("NilLoader")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText("NilLoader is not available for this Minecraft version.");
            return;
        }

        if ("Legacy Fabric".equals(loader) && !availableLoaders.contains("Legacy Fabric")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText("Legacy Fabric is not available for Minecraft " + version + ".");
            return;
        }

        if ("Ornithe".equals(loader) && !availableLoaders.contains("Ornithe")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText("Ornithe is not available for Minecraft " + version + ".");
            return;
        }

        if ("NeoForge".equals(loader) && !availableLoaders.contains("NeoForge")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText("NeoForge is not available for Minecraft " + version + ".");
            return;
        }

        if ("Quilt".equals(loader) && !availableLoaders.contains("Quilt")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText("Quilt is not available for Minecraft " + version + ".");
            return;
        }

        if ("VantaLoader".equals(loader)
                && (!availableLoaders.contains("VantaLoader")
                || !VantaLoaderInstaller.supportsMinecraftVersion(version))) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText("VantaLoader is not available for Minecraft " + version + ".");
            return;
        }

        if ("Fabric".equals(loader)) {

            /* The loader was exposed only after the version check. */
            if (!availableLoaders.contains("Fabric")) {

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

                        if ("VantaLoader".equals(loader)) {

                            InstanceInstaller.installVantaLoader(name, version);

                        } else if ("Fabric".equals(loader)) {

                            InstanceInstaller.installFabric(
                                    name,
                                    version
                            );

                        } else if ("Quilt".equals(loader)) {

                            InstanceInstaller.installQuilt(name, version);

                        } else if ("Ornithe".equals(loader)) {

                            InstanceInstaller.installOrnithe(name, version);

                        } else if ("Legacy Fabric".equals(loader)) {

                            InstanceInstaller.installLegacyFabric(name, version);

                        } else if ("Babric".equals(loader)) {

                            InstanceInstaller.installBabric(name, version);

                        } else if ("NilLoader".equals(loader)) {

                            InstanceInstaller.installNilLoader(name, version);

                        } else if ("LiteLoader".equals(loader)) {

                            InstanceInstaller.installLiteLoader(name, version);

                        } else if ("ModLoader".equals(loader)) {

                            InstanceInstaller.installModLoader(name, version);

                        } else if ("Rift".equals(loader)) {

                            InstanceInstaller.installRift(name, version);

                        } else if ("NeoForge".equals(loader)) {

                            InstanceInstaller.installNeoForge(name, version);

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

                        TelemetryService.track("feature_instance_created");

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

