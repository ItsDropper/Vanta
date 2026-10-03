package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Dialog;
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
            showPopularSection();
            loadMostDownloadedMods();
        });

        loaderBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (contentTypeBox.getValue() == ModrinthContentType.MOD) {
                        showPopularSection();
                loadMostDownloadedMods();
            }
        });

        versionBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            showPopularSection();
            loadMostDownloadedMods();
        });

        Thread versionThread = new Thread(() -> {
            try {
                List<String> versions =
                        MinecraftVersionResolver.getReleaseVersions();

                Platform.runLater(() -> {
                    versionBox.getItems().setAll("All versions");
                    versionBox.getItems().addAll(versions);
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

        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Install " + project.getTitle());
        dialog.setHeaderText("Choose the compatible instances to install this into.");

        VBox choices = new VBox(10);
        choices.setPadding(new Insets(8));
        List<CheckBox> boxes = new ArrayList<>();

        for (Instance instance : compatible) {
            CheckBox box = new CheckBox(
                    instance.getName() + " • Minecraft " + instance.getMinecraftVersion()
                            + " • " + instance.getDisplayLoader()
            );
            boxes.add(box);
            choices.getChildren().add(box);
        }

        ScrollPane scroll = new ScrollPane(choices);
        scroll.setFitToWidth(true);
        scroll.setPrefViewportHeight(Math.min(420, 80 + compatible.size() * 44.0));
        dialog.getDialogPane().setContent(scroll);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, ButtonType.OK);

        Button ok = (Button) dialog.getDialogPane().lookupButton(ButtonType.OK);
        ok.setDisable(true);
        for (CheckBox box : boxes) {
            box.selectedProperty().addListener((obs, oldValue, selected) ->
                    ok.setDisable(boxes.stream().noneMatch(CheckBox::isSelected))
            );
        }

        dialog.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) return;
            List<Instance> selected = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).isSelected()) selected.add(compatible.get(i));
            }
            installToInstances(project, selected, sourceButton);
        });
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
                "instance-version"
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

        Button installButton =
                new Button("INSTALL");
        installButton.getStyleClass().add("primary-button");
        installButton.setOnAction(event ->
                loadProjectForInstall(project.getProjectId(), installButton)
        );

        HBox row =
                new HBox(
                        16,
                        createIconBox(
                                icon,
                                52
                        ),
                        information,
                        installButton
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
                "instance-version"
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

        HBox row =
                new HBox(
                        16,
                        createIconBox(
                                icon,
                                52
                        ),
                        information
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

