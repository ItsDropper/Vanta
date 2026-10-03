package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Separator;
import javafx.stage.Popup;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import org.example.launcher.modrinth.ModrinthClient;
import org.example.launcher.modrinth.ModrinthSearchHit;
import org.example.launcher.instance.InstanceManager;
import org.example.launcher.instance.MinecraftVersionResolver;
import org.example.launcher.model.Instance;
import org.example.launcher.modrinth.ModrinthContentType;
import org.example.launcher.modrinth.ModrinthProject;
import org.example.launcher.service.ModrinthContentService;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import javax.imageio.ImageIO;

public class GlobalModsView extends VBox {

    private final ModrinthClient modrinthClient;
    private final HttpClient httpClient;

    private final TextField searchField;
    private final ComboBox<String> loaderBox;
    private final ComboBox<String> versionBox;
    private final ComboBox<ModrinthContentType> contentTypeBox;

    private final VBox popularResults;
    private final Label popularStatusLabel;
    private final Label popularTitle;
    private final ScrollPane popularScrollPane;

    private final VBox results;
    private final Label statusLabel;
    private final ScrollPane resultsScrollPane;

    private final BiConsumer<String, ModrinthContentType> onModSelected;

    public GlobalModsView(
            BiConsumer<String, ModrinthContentType> onModSelected
    ) {

        this.onModSelected =
                onModSelected;

        modrinthClient =
                new ModrinthClient();

        httpClient =
                HttpClient.newHttpClient();

        getStyleClass().add(
                "page"
        );

        setPadding(
                new Insets(36)
        );

        setSpacing(
                20
        );

        // =========================================================
        // HEADER
        // =========================================================

        Label title =
                new Label(
                        "Modrinth"
                );

        title.getStyleClass().add(
                "page-title"
        );

        Label subtitle =
                new Label(
                        "Browse mods, resource packs, and shaders on Modrinth."
                );

        subtitle.getStyleClass().add(
                "page-subtitle"
        );

        VBox header =
                new VBox(
                        5,
                        title,
                        subtitle
                );

        // =========================================================
        // GLOBAL CONTENT FILTERS
        // =========================================================

        contentTypeBox = new ComboBox<>();
        contentTypeBox.getItems().addAll(
                ModrinthContentType.MOD,
                ModrinthContentType.RESOURCE_PACK,
                ModrinthContentType.SHADER
        );
        contentTypeBox.setValue(ModrinthContentType.MOD);
        contentTypeBox.setPrefWidth(170);
        contentTypeBox.setPrefHeight(42);
        contentTypeBox.getStyleClass().add("create-combo");

        loaderBox = new ComboBox<>();
        loaderBox.getItems().addAll("Fabric", "Forge");
        loaderBox.setValue("Fabric");
        loaderBox.setPrefWidth(140);
        loaderBox.setPrefHeight(42);
        loaderBox.getStyleClass().add("create-combo");

        versionBox = new ComboBox<>();
        versionBox.getItems().add("All versions");
        versionBox.setValue("All versions");
        versionBox.setPrefWidth(170);
        versionBox.setPrefHeight(42);
        versionBox.getStyleClass().add("create-combo");

        HBox filters = new HBox(
                10,
                contentTypeBox,
                loaderBox,
                versionBox
        );
        filters.setAlignment(Pos.CENTER_LEFT);

        contentTypeBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            loaderBox.setDisable(newValue != ModrinthContentType.MOD);
            refreshForCurrentFilters();
        });

        loaderBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (contentTypeBox.getValue() == ModrinthContentType.MOD) {
                refreshForCurrentFilters();
            }
        });

        versionBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            refreshForCurrentFilters();
        });

        Thread versionThread = new Thread(() -> {
            try {
                List<String> versions =
                        MinecraftVersionResolver.getReleaseVersions();

                Platform.runLater(() -> {
                    versionBox.getItems().setAll("All versions");
                    versionBox.getItems().addAll(versions);
                    versionBox.getSelectionModel().select("All versions");
                });
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        });
        versionThread.setDaemon(true);

        // =========================================================
        // SEARCH
        // =========================================================

        searchField =
                new TextField();

        searchField.setPromptText(
                "Search Modrinth..."
        );

        searchField.getStyleClass().add(
                "create-field"
        );

        HBox.setHgrow(
                searchField,
                Priority.ALWAYS
        );

        Button searchButton =
                new Button(
                        "SEARCH"
                );

        searchButton.getStyleClass().add(
                "primary-button"
        );

        searchButton.setOnAction(
                event ->
                        search()
        );

        searchField.setOnAction(
                event ->
                        search()
        );

        HBox searchBar =
                new HBox(
                        10,
                        searchField,
                        searchButton
                );

        searchBar.setAlignment(
                Pos.CENTER_LEFT
        );

        // =========================================================
        // MOST DOWNLOADED TITLE
        // =========================================================

        popularTitle =
                new Label(
                        "MOST DOWNLOADED"
                );

        popularTitle.getStyleClass().add(
                "settings-section-title"
        );

        // =========================================================
        // MOST DOWNLOADED RESULTS
        // =========================================================

        popularResults =
                new VBox(
                        12
                );

        popularResults.setAlignment(
                Pos.TOP_LEFT
        );

        popularScrollPane =
                new ScrollPane(
                        popularResults
                );

        popularScrollPane.setFitToWidth(
                true
        );

        popularScrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        popularScrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        popularScrollPane.setPrefHeight(
                320
        );

        popularScrollPane.setMaxHeight(
                320
        );

        popularScrollPane.getStyleClass().add(
                "instances-scroll"
        );

        popularStatusLabel =
                new Label(
                        "Loading popular mods..."
                );

        popularStatusLabel.getStyleClass().add(
                "instances-status"
        );

        // =========================================================
        // SEARCH RESULTS
        // =========================================================

        results =
                new VBox(
                        12
                );

        results.getStyleClass().add(
                "instance-list"
        );

        resultsScrollPane =
                new ScrollPane(
                        results
                );

        resultsScrollPane.setFitToWidth(
                true
        );

        resultsScrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        resultsScrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        resultsScrollPane.getStyleClass().add(
                "instances-scroll"
        );

        VBox.setVgrow(
                resultsScrollPane,
                Priority.ALWAYS
        );

        // =========================================================
        // STATUS
        // =========================================================

        statusLabel =
                new Label(
                        "Search Modrinth for mods."
                );

        statusLabel.getStyleClass().add(
                "instances-status"
        );

        // =========================================================
        // BUILD
        // =========================================================

        getChildren().addAll(
                header,
                filters,
                searchBar,
                popularTitle,
                popularScrollPane,
                popularStatusLabel,
                resultsScrollPane,
                statusLabel
        );

        // =========================================================
        // INITIAL STATE
        // =========================================================

        resultsScrollPane.setVisible(
                false
        );

        resultsScrollPane.setManaged(
                false
        );

        // =========================================================
        // LOAD POPULAR MODS
        // =========================================================

        versionThread.start();
        loadMostDownloadedMods();
    }

    private void refreshForCurrentFilters() {
        String query = searchField.getText();

        if (query != null && !query.trim().isBlank()) {
            search();
        } else {
            showPopularSection();
            loadMostDownloadedMods();
        }
    }

    private String selectedVersion() {
        String value = versionBox.getValue();
        return value == null || "All versions".equals(value) ? null : value;
    }

    private String selectedLoader() {
        return contentTypeBox.getValue() == ModrinthContentType.MOD
                ? loaderBox.getValue()
                : null;
    }

    private List<Instance> compatibleInstances(
            ModrinthProject project,
            List<org.example.launcher.modrinth.ModrinthVersion> versions
    ) {
        List<Instance> compatible = new ArrayList<>();

        for (Instance instance : InstanceManager.discoverInstances()) {
            if (instance == null
                    || instance.getMinecraftVersion() == null
                    || instance.getMinecraftVersion().isBlank()
                    || instance.getLoader() == null) {
                continue;
            }

            String minecraftVersion = instance.getMinecraftVersion();
            String loader = instance.getLoader().trim().toLowerCase(java.util.Locale.ROOT);

            if (contentTypeBox.getValue() == ModrinthContentType.MOD
                    && !loader.equals("fabric")
                    && !loader.equals("forge")) {
                continue;
            }

            if (contentTypeBox.getValue() == ModrinthContentType.MOD) {
                String selected = selectedLoader();
                if (!selected.isBlank()
                        && !selected.equalsIgnoreCase("all")
                        && !loader.equalsIgnoreCase(selected)) {
                    continue;
                }
            }

            boolean compatibleVersion = false;

            if (versions != null) {
                for (org.example.launcher.modrinth.ModrinthVersion version : versions) {
                    if (version.getGameVersions() == null
                            || !version.getGameVersions().contains(minecraftVersion)) {
                        continue;
                    }

                    if (contentTypeBox.getValue() == ModrinthContentType.MOD) {
                        if (version.getLoaders() == null
                                || version.getLoaders().stream()
                                .noneMatch(value -> loader.equalsIgnoreCase(value))) {
                            continue;
                        }
                    }

                    compatibleVersion = true;
                    break;
                }
            }

            if (compatibleVersion) {
                compatible.add(instance);
            }
        }

        return compatible;
    }

    private void loadProjectForInstall(String projectId, Button sourceButton) {
        Thread thread = new Thread(() -> {
            try {
                ModrinthProject project = modrinthClient.getProject(projectId);
                List<org.example.launcher.modrinth.ModrinthVersion> versions =
                        modrinthClient.getVersions(projectId);
                Platform.runLater(() -> chooseInstances(project, versions, sourceButton));
            } catch (Throwable ex) {
                ex.printStackTrace();
                Platform.runLater(() -> statusLabel.setText("Could not load project compatibility information."));
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void chooseInstances(
            ModrinthProject project,
            List<org.example.launcher.modrinth.ModrinthVersion> versions,
            Button sourceButton
    ) {
        List<Instance> compatible = compatibleInstances(project, versions);
        if (compatible.isEmpty()) {
            statusLabel.setText(
                    "No compatible instances found for " + project.getTitle()
                            + ". Check that you have a matching Minecraft version and loader."
            );
            return;
        }

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);

        VBox root = new VBox(14);
        root.getStyleClass().add("modrinth-popup");
        root.setPrefWidth(560);

        Label title = new Label("INSTALL " + project.getTitle());
        title.getStyleClass().add("modrinth-popup-title");
        Label subtitle = new Label("Choose compatible instances.");
        subtitle.getStyleClass().add("modrinth-popup-subtitle");

        VBox choices = new VBox(8);
        List<CheckBox> boxes = new ArrayList<>();
        for (Instance instance : compatible) {
            CheckBox box = new CheckBox(
                    instance.getName() + "   •   Minecraft " + instance.getMinecraftVersion()
                            + "   •   " + instance.getDisplayLoader()
            );
            box.getStyleClass().add("dialog-instance-checkbox");
            boxes.add(box);
            choices.getChildren().add(box);
        }

        ScrollPane scroll = new ScrollPane(choices);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(Math.min(420, 90 + compatible.size() * 48.0));
        scroll.getStyleClass().add("modrinth-popup-scroll");

        Button cancel = new Button("CANCEL");
        cancel.getStyleClass().add("modrinth-popup-secondary");
        Button install = new Button("INSTALL");
        install.getStyleClass().add("modrinth-popup-primary");
        install.setDisable(true);

        for (CheckBox box : boxes) {
            box.selectedProperty().addListener((obs, oldValue, selected) ->
                    install.setDisable(boxes.stream().noneMatch(CheckBox::isSelected))
            );
        }

        HBox actions = new HBox(8, cancel, install);
        actions.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().addAll(title, subtitle, scroll, actions);

        cancel.setOnAction(event -> popup.hide());
        install.setOnAction(event -> {
            List<Instance> selected = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).isSelected()) selected.add(compatible.get(i));
            }
            popup.hide();
            installToInstances(project, selected, sourceButton);
        });

        popup.getContent().add(root);
        popup.show(sourceButton,
                sourceButton.localToScreen(sourceButton.getBoundsInLocal()).getMinX() - 560 + sourceButton.getWidth(),
                sourceButton.localToScreen(sourceButton.getBoundsInLocal()).getMaxY() + 8);
    }

    private void installToInstances(ModrinthProject project, List<Instance> instances, Button sourceButton) {
        sourceButton.setDisable(true);
        sourceButton.setText("INSTALLING...");
        Thread thread = new Thread(() -> {
            int success = 0;
            int failed = 0;
            for (Instance instance : instances) {
                try {
                    ModrinthContentType type = contentTypeBox.getValue();
                    if (type == ModrinthContentType.MOD) {
                        new org.example.launcher.service.ModrinthService().installMod(instance, project);
                    } else if (type == ModrinthContentType.RESOURCE_PACK) {
                        new ModrinthContentService().installResourcePack(instance, project);
                    } else if (type == ModrinthContentType.SHADER) {
                        new ModrinthContentService().installShader(instance, project);
                    }
                    success++;
                } catch (Throwable ex) {
                    failed++;
                    ex.printStackTrace();
                }
            }
            int installed = success;
            int failures = failed;
            Platform.runLater(() -> {
                sourceButton.setDisable(false);
                sourceButton.setText("INSTALL");
                statusLabel.setText(project.getTitle() + " installed into " + installed
                        + " instance" + (installed == 1 ? "" : "s")
                        + (failures == 0 ? "." : "; " + failures + " failed."));
            });
        });
        thread.setDaemon(true);
        thread.start();
    }

    // =============================================================
    // MOST DOWNLOADED
    // =============================================================

    private void loadMostDownloadedMods() {

        Thread thread =
                new Thread(() -> {

                    try {

                        List<ModrinthSearchHit> projects =
                                modrinthClient
                                        .getMostDownloaded(
                                                contentTypeBox.getValue(),
                                                selectedLoader(),
                                                selectedVersion()
                                        )
                                        .getHits();

                        Platform.runLater(() ->
                                showMostDownloadedMods(
                                        projects
                                )
                        );

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() ->
                                popularStatusLabel.setText(
                                        "Could not load popular mods."
                                )
                        );
                    }
                });

        thread.setDaemon(
                true
        );

        thread.start();
    }

    // =============================================================
    // SHOW MOST DOWNLOADED
    // =============================================================

    private void showMostDownloadedMods(
            List<ModrinthSearchHit> projects
    ) {

        popularResults
                .getChildren()
                .clear();

        if (projects == null
                || projects.isEmpty()) {

            popularStatusLabel.setText(
                    "No popular mods found."
            );

            return;
        }

        for (
                ModrinthSearchHit project
                : projects
        ) {

            popularResults
                    .getChildren()
                    .add(
                            createPopularCard(
                                    project
                            )
                    );
        }

        popularStatusLabel.setText(
                projects.size()
                        + " popular mods."
        );
    }

    // =============================================================
    // POPULAR CARD
    // =============================================================

    private VBox createPopularCard(
            ModrinthSearchHit project
    ) {

        ImageView icon =
                createIcon(
                        project
                );

        Label title =
                new Label(
                        safe(
                                project.getTitle(),
                                "Unknown Mod"
                        )
                );

        title.getStyleClass().add(
                "instance-name"
        );

        Label description =
                new Label(
                        safe(
                                project.getDescription(),
                                "No description."
                        )
                );

        description.setWrapText(
                true
        );

        description.setMaxWidth(
                650
        );

        description.getStyleClass().add(
                "global-mod-description"
        );

        Label downloads =
                new Label(
                        formatNumber(
                                project.getDownloads()
                        )
                                + " downloads"
                );

        downloads.getStyleClass().add(
                "instance-loader"
        );

        VBox information =
                new VBox(
                        5,
                        title,
                        description,
                        downloads
                );

        HBox.setHgrow(
                information,
                Priority.ALWAYS
        );

        Button versionsButton = createVersionsButton(project.getProjectId());

        Button installButton =
                new Button("INSTALL");
        installButton.getStyleClass().add("primary-button");
        installButton.setOnAction(event ->
                loadProjectForInstall(project.getProjectId(), installButton)
        );

        HBox actions = new HBox(
                8,
                versionsButton,
                installButton
        );
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox row =
                new HBox(
                        16,
                        createIconBox(
                                icon,
                                52
                        ),
                        information,
                        actions
                );

        row.setAlignment(
                Pos.CENTER_LEFT
        );

        VBox card =
                new VBox(
                        row
                );

        card.setMaxWidth(
                Double.MAX_VALUE
        );

        card.setPadding(
                new Insets(
                        16,
                        18,
                        16,
                        18
                )
        );

        card.getStyleClass().add(
                "instance-card"
        );

        card.setCursor(
                javafx.scene.Cursor.HAND
        );

        card.setOnMouseClicked(
                event -> {

                    if (event.getButton() ==
                            javafx.scene.input.MouseButton.PRIMARY) {

                        String projectId =
                                project.getProjectId();

                        if (projectId != null
                                && !projectId.isBlank()) {

                            onModSelected.accept(
                                    projectId,
                                    contentTypeBox.getValue()
                            );
                        }
                    }
                }
        );

        return card;
    }

    // =============================================================
    // SEARCH
    // =============================================================

    private void search() {

        String query =
                searchField
                        .getText()
                        .trim();

        // =========================================================
        // EMPTY = MOST DOWNLOADED
        // =========================================================

        if (query.isBlank()) {

            results
                    .getChildren()
                    .clear();

            showPopularSection();

            statusLabel.setText(
                    "Search Modrinth for mods."
            );

            return;
        }

        // =========================================================
        // SEARCH = HIDE POPULAR
        // =========================================================

        hidePopularSection();

        results
                .getChildren()
                .clear();

        statusLabel.setText(
                "Searching Modrinth..."
        );

        Thread thread =
                new Thread(() -> {

                    try {

                        List<ModrinthSearchHit> projects =
                                modrinthClient
                                        .search(
                                                query,
                                                contentTypeBox.getValue(),
                                                selectedLoader(),
                                                selectedVersion()
                                        )
                                        .getHits();

                        Platform.runLater(() ->
                                showResults(
                                        projects
                                )
                        );

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() ->
                                statusLabel.setText(
                                        ex.getMessage() != null
                                                ? ex.getMessage()
                                                : "Failed to search Modrinth."
                                )
                        );
                    }
                });

        thread.setDaemon(
                true
        );

        thread.start();
    }

    // =============================================================
    // SHOW POPULAR SECTION
    // =============================================================

    private void showPopularSection() {

        popularTitle.setVisible(
                true
        );

        popularTitle.setManaged(
                true
        );

        popularScrollPane.setVisible(
                true
        );

        popularScrollPane.setManaged(
                true
        );

        popularStatusLabel.setVisible(
                true
        );

        popularStatusLabel.setManaged(
                true
        );

        resultsScrollPane.setVisible(
                false
        );

        resultsScrollPane.setManaged(
                false
        );
    }

    // =============================================================
    // HIDE POPULAR SECTION
    // =============================================================

    private void hidePopularSection() {

        popularTitle.setVisible(
                false
        );

        popularTitle.setManaged(
                false
        );

        popularScrollPane.setVisible(
                false
        );

        popularScrollPane.setManaged(
                false
        );

        popularStatusLabel.setVisible(
                false
        );

        popularStatusLabel.setManaged(
                false
        );

        resultsScrollPane.setVisible(
                true
        );

        resultsScrollPane.setManaged(
                true
        );
    }

    // =============================================================
    // RESULTS
    // =============================================================

    private void showResults(
            List<ModrinthSearchHit> projects
    ) {

        results
                .getChildren()
                .clear();

        if (projects == null
                || projects.isEmpty()) {

            statusLabel.setText(
                    "No mods found."
            );

            return;
        }

        for (
                ModrinthSearchHit project
                : projects
        ) {

            results
                    .getChildren()
                    .add(
                            createProjectCard(
                                    project
                            )
                    );
        }

        statusLabel.setText(
                projects.size()
                        + " projects found."
        );
    }

    // =============================================================
    // PROJECT CARD
    // =============================================================

    private Button createVersionsButton(String projectId) {
        Button button = new Button("⋮");
        button.getStyleClass().add("modrinth-versions-button");
        button.setAccessibleText("Versions");
        button.setOnAction(event -> showVersionsPopup(projectId, button));
        return button;
    }

    private void showVersionsPopup(String projectId, Button anchor) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);

        VBox root = new VBox(10);
        root.getStyleClass().add("modrinth-popup");
        root.setPrefWidth(520);

        Label title = new Label("VERSIONS");
        title.getStyleClass().add("modrinth-popup-title");
        Label subtitle = new Label("Loading project versions...");
        subtitle.getStyleClass().add("modrinth-popup-subtitle");

        VBox list = new VBox(7);
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(420);
        scroll.getStyleClass().add("modrinth-popup-scroll");

        root.getChildren().addAll(title, subtitle, new Separator(), scroll);
        popup.getContent().add(root);

        Thread thread = new Thread(() -> {
            try {
                List<org.example.launcher.modrinth.ModrinthVersion> versions =
                        modrinthClient.getVersions(projectId);
                Platform.runLater(() -> {
                    list.getChildren().clear();
                    subtitle.setText((versions == null ? 0 : versions.size()) + " versions");
                    if (versions == null || versions.isEmpty()) {
                        list.getChildren().add(new Label("No versions found."));
                        return;
                    }
                    for (org.example.launcher.modrinth.ModrinthVersion version : versions) {
                        list.getChildren().add(createVersionRow(projectId, version, popup));
                    }
                });
            } catch (Throwable ex) {
                Platform.runLater(() -> {
                    list.getChildren().clear();
                    subtitle.setText("Could not load versions");
                });
            }
        });
        thread.setDaemon(true);
        thread.start();

        popup.show(anchor, anchor.localToScreen(anchor.getBoundsInLocal()).getMaxX() - 520,
                anchor.localToScreen(anchor.getBoundsInLocal()).getMaxY() + 6);
    }

    private HBox createVersionRow(
            String projectId,
            org.example.launcher.modrinth.ModrinthVersion version,
            Popup versionsPopup
    ) {
        Label name = new Label(safe(version.getVersionNumber(), safe(version.getName(), "Unknown")));
        name.getStyleClass().add("modrinth-version-name");
        Label type = new Label(safe(version.getVersionType(), "release").toUpperCase());
        type.getStyleClass().add("modrinth-version-type");
        String games = version.getGameVersions() == null ? "" : String.join(", ", version.getGameVersions());
        Label meta = new Label(games + (version.getLoaders() == null ? "" : "  •  " + String.join(", ", version.getLoaders())));
        meta.getStyleClass().add("modrinth-version-meta");
        VBox info = new VBox(4, name, meta);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label downloads = new Label(formatNumber(version.getDownloads()));
        downloads.getStyleClass().add("modrinth-version-downloads");

        HBox row = new HBox(10, info, type, downloads);

        if (contentTypeBox.getValue() == ModrinthContentType.MOD) {
            Button installButton = new Button("INSTALL");
            installButton.getStyleClass().add("modrinth-version-install");
            installButton.setOnAction(event -> {
                versionsPopup.hide();
                chooseInstancesForVersion(projectId, version);
            });
            row.getChildren().add(installButton);
        }
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("modrinth-version-row");
        return row;
    }

    private void chooseInstancesForVersion(
            String projectId,
            org.example.launcher.modrinth.ModrinthVersion version
    ) {
        List<Instance> compatible = new ArrayList<>();

        for (Instance instance : InstanceManager.discoverInstances()) {
            if (instance == null || instance.getMinecraftVersion() == null
                    || instance.getMinecraftVersion().isBlank()) continue;

            if (contentTypeBox.getValue() != ModrinthContentType.MOD) continue;

            String instanceLoader = instance.getLoader() == null
                    ? ""
                    : instance.getLoader().trim();

            if (!"fabric".equalsIgnoreCase(instanceLoader)
                    && !"forge".equalsIgnoreCase(instanceLoader)) continue;

            boolean gameMatch = version.getGameVersions() != null
                    && version.getGameVersions().contains(instance.getMinecraftVersion());
            boolean loaderMatch = version.getLoaders() != null
                    && version.getLoaders().stream().anyMatch(v -> instanceLoader.equalsIgnoreCase(v));

            if (gameMatch && loaderMatch) compatible.add(instance);
        }

        if (compatible.isEmpty()) {
            statusLabel.setText("No compatible instances found for this mod version.");
            return;
        }

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);

        VBox root = new VBox(14);
        root.getStyleClass().add("modrinth-popup");
        root.setPrefWidth(560);

        Label title = new Label("INSTALL " + safe(version.getVersionNumber(), "VERSION"));
        title.getStyleClass().add("modrinth-popup-title");
        Label subtitle = new Label("Choose compatible instances for this exact version.");
        subtitle.getStyleClass().add("modrinth-popup-subtitle");

        VBox choices = new VBox(8);
        List<CheckBox> boxes = new ArrayList<>();
        for (Instance instance : compatible) {
            CheckBox box = new CheckBox(instance.getName() + "   •   Minecraft "
                    + instance.getMinecraftVersion() + "   •   " + instance.getDisplayLoader());
            box.getStyleClass().add("dialog-instance-checkbox");
            boxes.add(box);
            choices.getChildren().add(box);
        }

        ScrollPane scroll = new ScrollPane(choices);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(Math.min(420, 90 + compatible.size() * 48.0));
        scroll.getStyleClass().add("modrinth-popup-scroll");

        Button cancel = new Button("CANCEL");
        cancel.getStyleClass().add("modrinth-popup-secondary");
        Button install = new Button("INSTALL");
        install.getStyleClass().add("modrinth-popup-primary");
        install.setDisable(true);

        for (CheckBox box : boxes) {
            box.selectedProperty().addListener((obs, oldValue, selected) ->
                    install.setDisable(boxes.stream().noneMatch(CheckBox::isSelected)));
        }

        HBox actions = new HBox(8, cancel, install);
        actions.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().addAll(title, subtitle, scroll, actions);
        popup.getContent().add(root);

        cancel.setOnAction(event -> popup.hide());
        install.setOnAction(event -> {
            List<Instance> selected = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).isSelected()) selected.add(compatible.get(i));
            }
            popup.hide();
            installExactVersion(projectId, version, selected);
        });

        if (getScene() != null) {
            javafx.geometry.Bounds bounds = localToScreen(getBoundsInLocal());
            if (bounds != null) {
                popup.show(this,
                        Math.max(bounds.getMinX() + 80, bounds.getCenterX() - 280),
                        Math.max(bounds.getMinY() + 80, bounds.getCenterY() - 220));
            }
        }
    }

    private void installExactVersion(
            String projectId,
            org.example.launcher.modrinth.ModrinthVersion version,
            List<Instance> instances
    ) {
        Thread thread = new Thread(() -> {
            int success = 0;
            int failed = 0;
            for (Instance instance : instances) {
                try {
                    new org.example.launcher.service.ModrinthService()
                            .installModVersion(instance, projectId, version);
                    success++;
                } catch (Throwable ex) {
                    failed++;
                    ex.printStackTrace();
                }
            }
            int installed = success;
            int failures = failed;
            Platform.runLater(() -> statusLabel.setText(
                    safe(version.getVersionNumber(), "Version") + " installed into " + installed
                            + " instance" + (installed == 1 ? "" : "s")
                            + (failures == 0 ? "." : "; " + failures + " failed.")
            ));
        });
        thread.setDaemon(true);
        thread.start();
    }

    private VBox createProjectCard(
            ModrinthSearchHit project
    ) {

        ImageView icon =
                createIcon(
                        project
                );

        Label title =
                new Label(
                        safe(
                                project.getTitle(),
                                "Unknown Mod"
                        )
                );

        title.getStyleClass().add(
                "instance-name"
        );

        Label description =
                new Label(
                        safe(
                                project.getDescription(),
                                "No description."
                        )
                );

        description.setWrapText(
                true
        );

        description.setMaxWidth(
                650
        );

        description.getStyleClass().add(
                "global-mod-description"
        );

        Label metadata =
                new Label(
                        "Modrinth • "
                                + formatNumber(
                                project.getDownloads()
                        )
                                + " downloads"
                );

        metadata.getStyleClass().add(
                "instance-loader"
        );

        VBox information =
                new VBox(
                        6,
                        title,
                        description,
                        metadata
                );

        HBox.setHgrow(
                information,
                Priority.ALWAYS
        );

        Button versionsButton = createVersionsButton(project.getProjectId());

        Button installButton =
                new Button("INSTALL");
        installButton.getStyleClass().add("primary-button");
        installButton.setOnAction(event ->
                loadProjectForInstall(project.getProjectId(), installButton)
        );

        HBox actions = new HBox(
                8,
                versionsButton,
                installButton
        );
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox row =
                new HBox(
                        16,
                        createIconBox(
                                icon,
                                52
                        ),
                        information,
                        actions
                );

        row.setAlignment(
                Pos.CENTER_LEFT
        );

        VBox card =
                new VBox(
                        row
                );

        card.setPadding(
                new Insets(
                        16,
                        18,
                        16,
                        18
                )
        );

        card.getStyleClass().add(
                "instance-card"
        );

        card.setCursor(
                javafx.scene.Cursor.HAND
        );

        card.setOnMouseClicked(
                event -> {

                    if (event.getButton() ==
                            javafx.scene.input.MouseButton.PRIMARY) {

                        String projectId =
                                project.getProjectId();

                        if (projectId != null
                                && !projectId.isBlank()) {

                            onModSelected.accept(
                                    projectId,
                                    contentTypeBox.getValue()
                            );
                        }
                    }
                }
        );

        return card;
    }

    // =============================================================
    // ICON
    // =============================================================

    private ImageView createIcon(
            ModrinthSearchHit project
    ) {

        ImageView icon =
                new ImageView();

        icon.setFitWidth(
                52
        );

        icon.setFitHeight(
                52
        );

        icon.setPreserveRatio(
                true
        );

        icon.setSmooth(
                true
        );

        Rectangle clip =
                new Rectangle(
                        52,
                        52
                );

        clip.setArcWidth(
                14
        );

        clip.setArcHeight(
                14
        );

        icon.setClip(
                clip
        );

        loadIcon(
                project,
                icon
        );

        return icon;
    }

    private VBox createIconBox(
            ImageView icon,
            double size
    ) {

        VBox iconBox =
                new VBox(
                        icon
                );

        iconBox.setAlignment(
                Pos.CENTER
        );

        iconBox.setMinSize(
                size,
                size
        );

        iconBox.setPrefSize(
                size,
                size
        );

        iconBox.setMaxSize(
                size,
                size
        );

        iconBox.getStyleClass().add(
                "mod-icon-box"
        );

        return iconBox;
    }

    // =============================================================
    // LOAD MODRINTH ICON
    // =============================================================

    private void loadIcon(
            ModrinthSearchHit project,
            ImageView imageView
    ) {

        String iconUrl =
                project.getIconUrl();

        if (iconUrl == null
                || iconUrl.isBlank()) {

            return;
        }

        Thread thread =
                new Thread(() -> {

                    try {

                        HttpRequest request =
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        iconUrl
                                                )
                                        )
                                        .GET()
                                        .header(
                                                "User-Agent",
                                                "VantaLauncher/1.0"
                                        )
                                        .build();

                        HttpResponse<InputStream> response =
                                httpClient.send(
                                        request,
                                        HttpResponse.BodyHandlers
                                                .ofInputStream()
                                );

                        if (response.statusCode() < 200
                                || response.statusCode() >= 300) {

                            response.body().close();

                            return;
                        }

                        BufferedImage bufferedImage;

                        try (InputStream input =
                                     response.body()) {

                            bufferedImage =
                                    ImageIO.read(
                                            input
                                    );
                        }

                        if (bufferedImage == null) {

                            return;
                        }

                        ByteArrayOutputStream output =
                                new ByteArrayOutputStream();

                        ImageIO.write(
                                bufferedImage,
                                "png",
                                output
                        );

                        Image image =
                                new Image(
                                        new ByteArrayInputStream(
                                                output.toByteArray()
                                        )
                                );

                        Platform.runLater(() -> {

                            if (!image.isError()) {

                                imageView.setImage(
                                        image
                                );
                            }
                        });

                    } catch (Throwable ex) {


                        ex.printStackTrace();
                    }
                });

        thread.setDaemon(
                true
        );

        thread.start();
    }

    // =============================================================
    // SAFE
    // =============================================================

    private String safe(
            String value,
            String fallback
    ) {

        if (value == null
                || value.isBlank()) {

            return fallback;
        }

        return value;
    }

    // =============================================================
    // FORMAT NUMBER
    // =============================================================

    private String formatNumber(
            int number
    ) {

        if (number >= 1_000_000) {

            return String.format(
                    "%.1fM",
                    number / 1_000_000.0
            );
        }

        if (number >= 1_000) {

            return String.format(
                    "%.1fK",
                    number / 1_000.0
            );
        }

        return String.valueOf(
                number
        );
    }
}

