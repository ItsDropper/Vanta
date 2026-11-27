package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.components.PageHeader;
import dev.itsdropper.vanta.ui.components.InstanceCreationProgressDialog;
import dev.itsdropper.vanta.launcher.instance.InstanceInstallationProgress;
import dev.itsdropper.vanta.ui.async.UiExecutor;

import dev.itsdropper.vanta.json.JsonNode;
import dev.itsdropper.vanta.json.ObjectMapper;

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

import dev.itsdropper.vanta.launcher.instance.InstanceInstaller;
import dev.itsdropper.vanta.launcher.instance.LoaderCompatibilityService;
import dev.itsdropper.vanta.launcher.instance.VantaLoaderInstaller;
import dev.itsdropper.vanta.ui.LauncherSettings;
import dev.itsdropper.vanta.telemetry.TelemetryService;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Future;
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
    private final Set<String> failedLoaders = new HashSet<>();

    private final Button createButton;
    private final Button backButton;

    private final Label statusLabel;
    private final Button retryVersionsButton;
    private final Button retryLoadersButton;

    private final Runnable onBack;
    private final Runnable onCreated;
    private final Runnable onBrowseModpacks;

    private final AtomicInteger manifestRequestId = new AtomicInteger();
    private final AtomicInteger loaderCheckId = new AtomicInteger();
    private volatile boolean loaderChecksRunning;
    private volatile boolean loaderCheckCompleted;
    private volatile Future<?> activeLoaderCheck;

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

        getStyleClass().addAll("page", "vanta-consistent-page", "create-instance-page");

        dev.itsdropper.vanta.ui.ResponsiveLayout.bindPagePadding(this);

        setSpacing(
                20
        );

        // =========================================================
        // HEADER
        // =========================================================

        PageHeader header = new PageHeader(
                Localization.text("createInstance.title"),
                Localization.text("createInstance.subtitle"),
                "page-title",
                "page-subtitle"
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

        panel.setMaxWidth(820);

        // =========================================================
        // PANEL TITLE
        // =========================================================

        Label panelTitle =
                new Label(Localization.text("createInstance.details"));

        panelTitle.getStyleClass().add(
                "create-panel-title"
        );

        Label panelSubtitle = new Label(
                Localization.text("createInstance.detailsHelp")
        );
        panelSubtitle.getStyleClass().add("create-panel-subtitle");

        // =========================================================
        // NAME
        // =========================================================

        Label nameLabel =
                createLabel(Localization.text("createInstance.name"));

        nameField =
                new TextField();

        nameField.setPromptText(
                Localization.text("createInstance.nameExample")
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
                createLabel(Localization.text("createInstance.minecraftVersion"));

        versionBox =
                new ComboBox<>();

        versionBox.setPromptText(
                Localization.text("createInstance.loadingVersions")
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
                createLabel(Localization.text("createInstance.modLoader"));

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
                    setTooltip(null);
                    return;
                }

                boolean available = availableLoaders.contains(item);
                boolean checkFailed = failedLoaders.contains(item);
                String label = "ModLoader".equals(item) ? "Risugami's ModLoader" : item;
                if (available) {
                    setText(label);
                    setTooltip(null);
                } else if (checkFailed) {
                    setText(label + Localization.text("createInstance.checkFailedSuffix"));
                    setTooltip(new javafx.scene.control.Tooltip(
                            Localization.text("createInstance.loaderChecksFailed")));
                } else {
                    setText(label + Localization.text("createInstance.unavailableSuffix"));
                    setTooltip(null);
                }
                setDisable(!available);
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
                Localization.text("createInstance.vantaLoaderDescription")
                        + Localization.text("createInstance.vantaLoaderLimit1")
                        + Localization.text("createInstance.vantaLoaderLimit2")
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

        VBox versionField = new VBox(7, versionLabel, versionBox);
        versionField.getStyleClass().add("create-instance-field-group");
        VBox loaderField = new VBox(7, loaderLabel, loaderBox, loaderHint);
        loaderField.getStyleClass().add("create-instance-field-group");
        HBox environmentFields = new HBox(16, versionField, loaderField);
        environmentFields.getStyleClass().add("create-instance-environment-fields");
        HBox.setHgrow(versionField, Priority.ALWAYS);
        HBox.setHgrow(loaderField, Priority.ALWAYS);
        versionField.setMaxWidth(Double.MAX_VALUE);
        loaderField.setMaxWidth(Double.MAX_VALUE);

        form.getChildren().addAll(
                nameLabel,
                nameField,
                environmentFields
        );

        // =========================================================
        // STATUS
        // =========================================================

        statusLabel =
                new Label(
                        Localization.text("createInstance.loadingMinecraftVersions")
                );

        statusLabel.getStyleClass().add(
                "create-status"
        );
        statusLabel.setWrapText(true);
        HBox.setHgrow(statusLabel, Priority.ALWAYS);

        retryVersionsButton = new Button(Localization.text("common.retry"));
        retryVersionsButton.getStyleClass().add("secondary-button");
        retryVersionsButton.setAccessibleText(Localization.text("createInstance.retryAccessible"));
        retryVersionsButton.setOnAction(event -> loadVersions());
        retryVersionsButton.setVisible(false);
        retryVersionsButton.setManaged(false);

        retryLoadersButton = new Button(Localization.text("common.retry"));
        retryLoadersButton.getStyleClass().add("secondary-button");
        retryLoadersButton.setAccessibleText(Localization.text("createInstance.retryLoaderChecks"));
        retryLoadersButton.setOnAction(event -> checkLoaderAvailability(versionBox.getValue()));
        retryLoadersButton.setVisible(false);
        retryLoadersButton.setManaged(false);

        statusLabel.setMinWidth(0);
        FlowPane statusRow = new FlowPane(10, 8, statusLabel, retryVersionsButton, retryLoadersButton);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.setMaxWidth(Double.MAX_VALUE);

        // =========================================================
        // ACTIONS
        // =========================================================

        backButton =
                new Button(
                        Localization.text("common.back")
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

        backButton.setOnAction(event -> {
            cancelLoaderCheck();
            onBack.run();
        });

        createButton =
                new Button(
                        Localization.text("createInstance.title")
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
                        Localization.text("common.browseModpacks")
                );

        browseModpacksButton.getStyleClass().add(
                "secondary-button"
        );

        browseModpacksButton.setOnAction(event -> {
            cancelLoaderCheck();
            onBrowseModpacks.run();
        });

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

        VBox preview = buildPreview();
        HBox.setHgrow(panel, javafx.scene.layout.Priority.ALWAYS);
        HBox body = new HBox(20, panel, preview);
        body.setFillHeight(false);
        body.setAlignment(Pos.TOP_LEFT);

        getChildren().addAll(
                header,
                body
        );

        loadVersions();
    }

    // =============================================================
    // LIVE PREVIEW
    // =============================================================

    private VBox buildPreview() {
        javafx.scene.layout.StackPane artHost = new javafx.scene.layout.StackPane();
        artHost.setMinHeight(170);
        artHost.setPrefHeight(170);
        artHost.setMaxHeight(170);
        Label name = new Label();
        name.getStyleClass().add("create-preview-name");
        name.setWrapText(true);
        Label meta = new Label();
        meta.getStyleClass().add("create-preview-meta");
        Runnable refresh = () -> {
            String text = nameField.getText() == null ? "" : nameField.getText().trim();
            String loader = loaderBox.getValue() == null ? "Vanilla" : loaderBox.getValue();
            dev.itsdropper.vanta.ui.kit.CoverArt art =
                    new dev.itsdropper.vanta.ui.kit.CoverArt(text.isEmpty() ? "New instance" : text, loader);
            art.setMinSize(0, 170);
            art.setPrefSize(300, 170);
            art.setMaxSize(Double.MAX_VALUE, 170);
            artHost.getChildren().setAll(art);
            name.setText(text.isEmpty() ? "New instance" : text);
            String version = versionBox.getValue();
            meta.setText(version == null ? loader : "Minecraft " + version + " with " + loader);
        };
        nameField.textProperty().addListener((o, a, b) -> refresh.run());
        versionBox.valueProperty().addListener((o, a, b) -> refresh.run());
        loaderBox.valueProperty().addListener((o, a, b) -> refresh.run());
        refresh.run();
        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.widthProperty().bind(artHost.widthProperty());
        clip.setHeight(170);
        clip.setArcWidth(24);
        clip.setArcHeight(24);
        artHost.setClip(clip);
        VBox copy = new VBox(4, name, meta);
        copy.setPadding(new Insets(14, 16, 16, 16));
        VBox card = new VBox(artHost, copy);
        card.getStyleClass().add("create-preview");
        card.setMinWidth(300);
        card.setPrefWidth(300);
        card.setMaxWidth(300);
        card.setMaxHeight(javafx.scene.layout.Region.USE_PREF_SIZE);
        card.setAlignment(Pos.TOP_LEFT);
        return card;
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
        versionBox.setPromptText(Localization.text("createInstance.loadingMinecraftVersions"));
        statusLabel.setText(Localization.text("createInstance.loadingManifest"));

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
                    throw new IllegalStateException(Localization.text("createInstance.invalidManifest"));
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
                        versionBox.setPromptText(Localization.text("createInstance.noVersions"));
                        statusLabel.setText(Localization.text("createInstance.emptyVersions"));
                        retryVersionsButton.setVisible(true);
                        retryVersionsButton.setManaged(true);
                        return;
                    }

                    versionBox.setDisable(false);
                    loaderBox.setDisable(false);
                    retryVersionsButton.setVisible(false);
                    retryVersionsButton.setManaged(false);
                    String preferredVersion = LauncherSettings.getDefaultMinecraftVersion();
                    statusLabel.setText(Localization.text("createInstance.versionsLoaded"));
                    if (!preferredVersion.isBlank() && availableVersions.contains(preferredVersion)) {
                        versionBox.getSelectionModel().select(preferredVersion);
                    } else {
                        versionBox.getSelectionModel().selectFirst();
                    }
                });
            } catch (Throwable ex) {
                ex.printStackTrace();
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != manifestRequestId.get()) return;
                    versionBox.getItems().clear();
                    versionBox.setDisable(true);
                    loaderBox.setDisable(true);
                    createButton.setDisable(true);
                    versionBox.setPromptText(Localization.text("createInstance.loadFailedTitle"));
                    statusLabel.setText(Localization.text("createInstance.loadFailed"));
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
        int requestId = loaderCheckId.incrementAndGet();
        Future<?> previousCheck = activeLoaderCheck;
        if (previousCheck != null) previousCheck.cancel(true);
        activeLoaderCheck = null;

        String previouslySelectedLoader = loaderCheckCompleted ? loaderBox.getValue() : null;
        availableLoaders.clear();
        failedLoaders.clear();
        availableLoaders.add("Vanilla");
        loaderBox.getItems().setAll(
                "Vanilla", "Fabric", "Quilt", "Forge", "NeoForge",
                "Ornithe", "Legacy Fabric", "Babric", "NilLoader",
                "LiteLoader", "ModLoader", "Rift", "Stein", "VantaLoader"
        );
        loaderBox.getSelectionModel().select("Vanilla");
        loaderBox.setDisable(true);
        createButton.setDisable(true);
        retryLoadersButton.setVisible(false);
        retryLoadersButton.setManaged(false);

        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            loaderChecksRunning = false;
            loaderBox.setDisable(false);
            createButton.setDisable(false);
            statusLabel.setText(Localization.text("createInstance.validationVersion"));
            return;
        }

        loaderChecksRunning = true;
        statusLabel.setText(
                Localization.text("createInstance.checkingLoadersPrefix") + minecraftVersion + "..."
        );

        try {
            activeLoaderCheck = UiExecutor.shared().execute(() ->
                    runLoaderCompatibilityCheck(requestId, minecraftVersion, previouslySelectedLoader));
        } catch (java.util.concurrent.RejectedExecutionException overloaded) {
            handleLoaderCheckFailure(requestId);
        }
    }

    private void runLoaderCompatibilityCheck(
            int requestId,
            String minecraftVersion,
            String previouslySelectedLoader
    ) {
        try {
            LoaderCompatibilityService.Report report =
                    LoaderCompatibilityService.checkMinecraftVersion(minecraftVersion);
            UiExecutor.shared().dispatchToUi(() ->
                    applyLoaderCompatibilityReport(
                            requestId, minecraftVersion, previouslySelectedLoader, report));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            UiExecutor.shared().dispatchToUi(() -> handleLoaderCheckFailure(requestId));
        } catch (RuntimeException failure) {
            UiExecutor.shared().dispatchToUi(() -> handleLoaderCheckFailure(requestId));
        }
    }

    private void applyLoaderCompatibilityReport(
            int requestId,
            String minecraftVersion,
            String previouslySelectedLoader,
            LoaderCompatibilityService.Report report
    ) {
        if (requestId != loaderCheckId.get()) return;

        activeLoaderCheck = null;
        availableLoaders.clear();
        availableLoaders.addAll(report.availableLoaders());
        failedLoaders.clear();
        failedLoaders.addAll(report.failedLoaders());
        loaderCheckCompleted = true;

        loaderBox.getItems().setAll(
                "Vanilla", "Fabric", "Quilt", "Forge", "NeoForge",
                "Ornithe", "Legacy Fabric", "Babric", "NilLoader",
                "LiteLoader", "ModLoader", "Rift", "Stein", "VantaLoader"
        );

        String preferredLoader = LauncherSettings.getDefaultLoader();
        String loaderToSelect = availableLoaders.contains(previouslySelectedLoader)
                ? previouslySelectedLoader
                : availableLoaders.contains(preferredLoader) ? preferredLoader : "Vanilla";
        loaderBox.getSelectionModel().select(loaderToSelect);

        loaderBox.setDisable(false);
        createButton.setDisable(false);
        loaderChecksRunning = false;

        boolean hasAnyModLoader = availableLoaders.stream()
                .anyMatch(loader -> !"Vanilla".equals(loader));
        if (report.hasFailures()) {
            statusLabel.setText(Localization.text("createInstance.loaderChecksFailed"));
            retryLoadersButton.setVisible(true);
            retryLoadersButton.setManaged(true);
        } else if (!hasAnyModLoader) {
            statusLabel.setText(
                    Localization.text("createInstance.noLoadersPrefix")
                            + minecraftVersion
                            + Localization.text("createInstance.vanillaOnlySuffix")
            );
        } else {
            statusLabel.setText(
                    Localization.text("createInstance.loadersCheckedPrefix")
                            + minecraftVersion + "."
            );
        }
        loaderBox.requestLayout();
    }

    private void cancelLoaderCheck() {
        loaderCheckId.incrementAndGet();
        Future<?> check = activeLoaderCheck;
        activeLoaderCheck = null;
        loaderChecksRunning = false;
        if (check != null) check.cancel(true);
    }

    private void handleLoaderCheckFailure(int requestId) {
        if (requestId != loaderCheckId.get()) return;

        activeLoaderCheck = null;
        loaderChecksRunning = false;
        loaderBox.setDisable(false);
        createButton.setDisable(false);
        failedLoaders.clear();
        failedLoaders.addAll(loaderBox.getItems().stream()
                .filter(loader -> !"Vanilla".equals(loader)).toList());
        loaderBox.requestLayout();
        statusLabel.setText(Localization.text("createInstance.loaderChecksFailed"));
        retryLoadersButton.setVisible(true);
        retryLoadersButton.setManaged(true);
    }

    // =============================================================
    // CREATE
    // =============================================================

    private void createInstance() {

        if (loaderChecksRunning) {

            statusLabel.setText(
                    Localization.text("createInstance.checkingLoaders")
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
                    Localization.text("createInstance.validationName")
            );

            nameField.requestFocus();

            return;
        }

        if (version == null
                || version.isBlank()) {

            statusLabel.setText(
                    Localization.text("createInstance.validationVersion")
            );

            return;
        }

        if (loader == null
                || loader.isBlank()) {

            statusLabel.setText(
                    Localization.text("createInstance.validationLoader")
            );

            return;
        }

        if (failedLoaders.contains(loader)) {
            statusLabel.setText(Localization.text("createInstance.loaderChecksFailed"));
            retryLoadersButton.setVisible(true);
            retryLoadersButton.setManaged(true);
            return;
        }

        /*
         * Extra safety check: only install a loader that the
         * current version check actually exposed in the UI.
         */
        if ("Babric".equals(loader) && !availableLoaders.contains("Babric")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText(Localization.text("createInstance.babricUnavailable"));
            return;
        }

        if ("LiteLoader".equals(loader) && !availableLoaders.contains("LiteLoader")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText(Localization.text("createInstance.liteLoaderUnavailablePrefix") + version + ".");
            return;
        }

        if ("ModLoader".equals(loader) && !availableLoaders.contains("ModLoader")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText(Localization.text("createInstance.risugamiUnavailablePrefix") + version + ".");
            return;
        }

        if ("NilLoader".equals(loader) && !availableLoaders.contains("NilLoader")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText(Localization.text("createInstance.nilLoaderUnavailable"));
            return;
        }

        if ("Legacy Fabric".equals(loader) && !availableLoaders.contains("Legacy Fabric")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText(Localization.text("createInstance.legacyFabricUnavailablePrefix") + version + ".");
            return;
        }

        if ("Ornithe".equals(loader) && !availableLoaders.contains("Ornithe")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText(Localization.text("createInstance.ornitheUnavailablePrefix") + version + ".");
            return;
        }

        if ("NeoForge".equals(loader) && !availableLoaders.contains("NeoForge")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText(Localization.text("createInstance.neoForgeUnavailablePrefix") + version + ".");
            return;
        }

        if ("Quilt".equals(loader) && !availableLoaders.contains("Quilt")) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText(Localization.text("createInstance.quiltUnavailablePrefix") + version + ".");
            return;
        }

        if ("VantaLoader".equals(loader)
                && (!availableLoaders.contains("VantaLoader")
                || !VantaLoaderInstaller.supportsMinecraftVersion(version))) {
            loaderBox.getSelectionModel().select("Vanilla");
            statusLabel.setText(Localization.text("createInstance.vantaLoaderUnavailablePrefix") + version + ".");
            return;
        }

        if ("Fabric".equals(loader)) {

            /* The loader was exposed only after the version check. */
            if (!availableLoaders.contains("Fabric")) {

                loaderBox.getSelectionModel()
                        .select("Vanilla");

                statusLabel.setText(
                        Localization.text("createInstance.fabricUnavailablePrefix")
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
                Localization.text("common.installing")
        );

        statusLabel.setText(
                Localization.text("createInstance.installingPrefix")
                        + version
                        + "..."
        );

        // ---------------------------------------------------------
        // INSTALL
        // ---------------------------------------------------------

        InstanceCreationProgressDialog progressDialog =
                new InstanceCreationProgressDialog(name, version, loader, onCreated);
        progressDialog.show();
        UiExecutor.shared().execute(() -> {
            try {
                InstanceInstallationProgress.withListener(update ->
                        UiExecutor.shared().dispatchToUi(() -> progressDialog.update(update)), () -> {
                    if ("VantaLoader".equals(loader)) InstanceInstaller.installVantaLoader(name, version);
                    else if ("Fabric".equals(loader)) InstanceInstaller.installFabric(name, version);
                    else if ("Quilt".equals(loader)) InstanceInstaller.installQuilt(name, version);
                    else if ("Ornithe".equals(loader)) InstanceInstaller.installOrnithe(name, version);
                    else if ("Legacy Fabric".equals(loader)) InstanceInstaller.installLegacyFabric(name, version);
                    else if ("Babric".equals(loader)) InstanceInstaller.installBabric(name, version);
                    else if ("NilLoader".equals(loader)) InstanceInstaller.installNilLoader(name, version);
                    else if ("LiteLoader".equals(loader)) InstanceInstaller.installLiteLoader(name, version);
                    else if ("ModLoader".equals(loader)) InstanceInstaller.installModLoader(name, version);
                    else if ("Rift".equals(loader)) InstanceInstaller.installRift(name, version);
                    else if ("Stein".equals(loader)) InstanceInstaller.installSteinLoader(name, version);
                    else if ("NeoForge".equals(loader)) InstanceInstaller.installNeoForge(name, version);
                    else if ("Forge".equals(loader)) InstanceInstaller.installForge(name, version);
                    else InstanceInstaller.installVanilla(name, version);
                });
                TelemetryService.track("feature_instance_created");
                UiExecutor.shared().dispatchToUi(() -> {
                    progressDialog.complete();
                    statusLabel.setText(Localization.text("createInstance.created"));
                });
            } catch (Throwable ex) {
                ex.printStackTrace();
                UiExecutor.shared().dispatchToUi(() -> {
                    progressDialog.fail(ex.getMessage());
                    createButton.setDisable(false);
                    backButton.setDisable(false);
                    nameField.setDisable(false);
                    versionBox.setDisable(false);
                    loaderBox.setDisable(false);
                    createButton.setText(Localization.text("common.createInstance"));
                    statusLabel.setText(ex.getMessage() != null ? ex.getMessage()
                            : Localization.text("createInstance.createFailed"));
                });
            }
        });

    }
}

