package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import org.example.launcher.modrinth.ModrinthContentType;
import org.example.launcher.modrinth.ModrinthProject;
import org.example.launcher.service.ModrinthContentService;
import org.example.launcher.service.ModrinthService;
import org.example.launcher.model.Instance;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.function.Consumer;

public class ContentBrowserView extends VBox {

    private final Instance instance;

    private ModrinthContentType contentType;

    private final Runnable onBack;
    private final Consumer<ModrinthProject> onProjectSelected;

    private final ModrinthContentService contentService;
    private final ModrinthService modrinthService;

    private final HttpClient httpClient;

    private final TextField searchField;
    private final VBox results;
    private final Label statusLabel;

    private final VBox popularResults;
    private final Label popularStatusLabel;
    private final Label popularTitle;
    private final ScrollPane popularScrollPane;
    private final ScrollPane searchScrollPane;

    private Button modsButton;
    private Button packsButton;
    private Button shadersButton;

    public ContentBrowserView(
            Instance instance,
            ModrinthContentType contentType,
            Runnable onBack
    ) {

        this(
                instance,
                contentType,
                onBack,
                project -> {
                }
        );
    }

    public ContentBrowserView(
            Instance instance,
            ModrinthContentType contentType,
            Runnable onBack,
            Consumer<ModrinthProject> onProjectSelected
    ) {

        this.instance =
                instance;

        this.contentType =
                contentType;

        this.onBack =
                onBack;

        this.onProjectSelected =
                onProjectSelected;

        this.contentService =
                new ModrinthContentService();

        this.modrinthService =
                new ModrinthService();

        this.httpClient =
                HttpClient.newBuilder()
                        .followRedirects(
                                HttpClient.Redirect.NORMAL
                        )
                        .build();

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

        Button backButton =
                new Button(
                        "← BACK"
                );

        backButton.getStyleClass().add(
                "secondary-button"
        );

        backButton.setOnAction(
                event ->
                        onBack.run()
        );

        Label title =
                new Label(
                        "Browse Content"
                );

        title.getStyleClass().add(
                "page-title"
        );

        Label subtitle =
                new Label(
                        instance.getName()
                                + " • Minecraft "
                                + instance.getMinecraftVersion()
                                + " • "
                                + instance.getDisplayLoader()
                );

        subtitle.getStyleClass().add(
                "page-subtitle"
        );

        VBox headerText =
                new VBox(
                        5,
                        title,
                        subtitle
                );

        // =========================================================
        // CONTENT TYPE NAVIGATION
        // =========================================================

        modsButton =
                new Button(
                        "MODS"
                );

        packsButton =
                new Button(
                        "PACKS"
                );

        shadersButton =
                new Button(
                        "SHADERS"
                );

        modsButton.setOnAction(
                event ->
                        switchContentType(
                                ModrinthContentType.MOD
                        )
        );

        packsButton.setOnAction(
                event ->
                        switchContentType(
                                ModrinthContentType.RESOURCE_PACK
                        )
        );

        shadersButton.setOnAction(
                event ->
                        switchContentType(
                                ModrinthContentType.SHADER
                        )
        );

        HBox typeNavigation =
                new HBox(
                        10,
                        modsButton,
                        packsButton,
                        shadersButton
                );

        typeNavigation.setAlignment(
                Pos.CENTER_LEFT
        );

        updateTypeButtons();

        VBox header =
                new VBox(
                        14,
                        backButton,
                        headerText,
                        typeNavigation
                );

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
        // POPULAR
        // =========================================================

        popularTitle =
                new Label(
                        "MOST DOWNLOADED"
                );

        popularTitle.getStyleClass().add(
                "settings-section-title"
        );

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
                new Label();

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

        searchScrollPane =
                new ScrollPane(
                        results
                );

        searchScrollPane.setFitToWidth(
                true
        );

        searchScrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        searchScrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        searchScrollPane.getStyleClass().add(
                "instances-scroll"
        );

        VBox.setVgrow(
                searchScrollPane,
                Priority.ALWAYS
        );

        // =========================================================
        // STATUS
        // =========================================================

        statusLabel =
                new Label();

        statusLabel.getStyleClass().add(
                "instances-status"
        );

        // =========================================================
        // BUILD
        // =========================================================

        getChildren().addAll(
                header,
                searchBar,
                popularTitle,
                popularScrollPane,
                popularStatusLabel,
                searchScrollPane,
                statusLabel
        );

        searchScrollPane.setVisible(
                false
        );

        searchScrollPane.setManaged(
                false
        );

        loadMostDownloaded();
    }

    // =============================================================
    // CHANGE CONTENT TYPE
    // =============================================================

    private void switchContentType(
            ModrinthContentType newType
    ) {

        if (contentType == newType) {
            return;
        }

        contentType =
                newType;

        searchField.clear();

        results
                .getChildren()
                .clear();

        showPopularSection();

        updateTypeButtons();

        loadMostDownloaded();
    }

    // =============================================================
    // TYPE BUTTONS
    // =============================================================

    private void updateTypeButtons() {

        modsButton.getStyleClass().remove(
                "primary-button"
        );

        modsButton.getStyleClass().remove(
                "secondary-button"
        );

        packsButton.getStyleClass().remove(
                "primary-button"
        );

        packsButton.getStyleClass().remove(
                "secondary-button"
        );

        shadersButton.getStyleClass().remove(
                "primary-button"
        );

        shadersButton.getStyleClass().remove(
                "secondary-button"
        );

        if (contentType ==
                ModrinthContentType.MOD) {

            modsButton.getStyleClass().add(
                    "primary-button"
            );

            packsButton.getStyleClass().add(
                    "secondary-button"
            );

            shadersButton.getStyleClass().add(
                    "secondary-button"
            );

        } else if (contentType ==
                ModrinthContentType.RESOURCE_PACK) {

            modsButton.getStyleClass().add(
                    "secondary-button"
            );

            packsButton.getStyleClass().add(
                    "primary-button"
            );

            shadersButton.getStyleClass().add(
                    "secondary-button"
            );

        } else {

            modsButton.getStyleClass().add(
                    "secondary-button"
            );

            packsButton.getStyleClass().add(
                    "secondary-button"
            );

            shadersButton.getStyleClass().add(
                    "primary-button"
            );
        }
    }

    // =============================================================
    // MOST DOWNLOADED
    // =============================================================

    private void loadMostDownloaded() {

        popularStatusLabel.setText(
                "Loading popular "
                        + contentType.getDisplayName()
                        .toLowerCase()
                        + "s..."
        );

        Thread thread =
                new Thread(() -> {

                    try {

                        List<ModrinthProject> projects =
                                contentService.getMostDownloaded(
                                        contentType,
                                        null,
                                        instance.getMinecraftVersion()
                                );

                        Platform.runLater(() ->
                                showMostDownloaded(
                                        projects
                                )
                        );

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() ->
                                popularStatusLabel.setText(
                                        "Could not load popular "
                                                + contentType
                                                .getDisplayName()
                                                .toLowerCase()
                                                + "s."
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
    // SHOW POPULAR
    // =============================================================

    private void showMostDownloaded(
            List<ModrinthProject> projects
    ) {

        popularResults
                .getChildren()
                .clear();

        if (projects == null
                || projects.isEmpty()) {

            popularStatusLabel.setText(
                    "No popular "
                            + contentType
                            .getDisplayName()
                            .toLowerCase()
                            + "s found."
            );

            return;
        }

        for (
                ModrinthProject project
                : projects
        ) {

            popularResults
                    .getChildren()
                    .add(
                            createProjectCard(
                                    project,
                                    true
                            )
                    );
        }

        popularStatusLabel.setText(
                projects.size()
                        + " popular "
                        + contentType
                        .getDisplayName()
                        .toLowerCase()
                        + "s."
        );
    }

    // =============================================================
    // SEARCH
    // =============================================================

    private void search() {

        String query =
                searchField
                        .getText()
                        .trim();

        if (query.isBlank()) {

            showPopularSection();

            results
                    .getChildren()
                    .clear();

            statusLabel.setText(
                    "Search Modrinth for compatible "
                            + contentType
                            .getDisplayName()
                            .toLowerCase()
                            + "s."
            );

            return;
        }

        hidePopularSection();

        statusLabel.setText(
                "Searching Modrinth..."
        );

        results
                .getChildren()
                .clear();

        Thread thread =
                new Thread(() -> {

                    try {

                        List<ModrinthProject> projects =
                                contentService.search(
                                        query,
                                        contentType,
                                        null,
                                        instance.getMinecraftVersion()
                                );

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
    // RESULTS
    // =============================================================

    private void showResults(
            List<ModrinthProject> projects
    ) {

        results
                .getChildren()
                .clear();

        if (projects == null
                || projects.isEmpty()) {

            statusLabel.setText(
                    "No compatible "
                            + contentType
                            .getDisplayName()
                            .toLowerCase()
                            + "s found."
            );

            return;
        }

        for (
                ModrinthProject project
                : projects
        ) {

            results
                    .getChildren()
                    .add(
                            createProjectCard(
                                    project,
                                    false
                            )
                    );
        }

        statusLabel.setText(
                projects.size()
                        + " compatible "
                        + contentType
                        .getDisplayName()
                        .toLowerCase()
                        + (
                        projects.size() == 1
                                ? " found."
                                : "s found."
                )
        );
    }

    // =============================================================
    // PROJECT CARD
    // =============================================================

    private VBox createProjectCard(
            ModrinthProject project,
            boolean popular
    ) {

        ImageView icon =
                createIcon(
                        popular
                                ? 48
                                : 52
                );

        loadIcon(
                project,
                icon
        );

        VBox iconBox =
                createIconBox(
                        icon,
                        popular
                                ? 48
                                : 52
                );

        Label title =
                new Label(
                        safe(
                                project.getTitle(),
                                "Unknown project"
                        )
                );

        title.getStyleClass().add(
                "instance-name"
        );

        title.setWrapText(
                true
        );

        Label description =
                new Label(
                        safe(
                                project.getDescription(),
                                "No description available."
                        )
                );

        description.setWrapText(
                true
        );

        description.setMaxWidth(
                popular
                        ? 210
                        : Double.MAX_VALUE
        );

        if (popular) {
            description.setMaxHeight(42);
        } else {
            description.setMinHeight(36);
        }

        description.getStyleClass().add(
                "instance-version"
        );

        Label metadata =
                new Label(
                        "Modrinth • "
                                + formatDownloads(
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

        information.setFillWidth(
                true
        );

        HBox.setHgrow(
                information,
                Priority.ALWAYS
        );

        HBox row;

        if (popular) {

            row =
                    new HBox(
                            12,
                            iconBox,
                            information
                    );

        } else {

            Button installButton =
                    new Button(
                            "INSTALL"
                    );

            installButton.getStyleClass().add(
                    "instance-play-button"
            );

            installButton.setPrefWidth(
                    110
            );

            installButton.setMinWidth(
                    110
            );

            installButton.setOnAction(
                    event -> {

                        event.consume();

                        install(
                                project,
                                installButton
                        );
                    }
            );

            row =
                    new HBox(
                            16,
                            iconBox,
                            information,
                            installButton
                    );
        }

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
                        14
                )
        );

        card.getStyleClass().add(
                "instance-card"
        );

        card.setCursor(
                Cursor.HAND
        );

        card.setOnMouseClicked(
                event -> {

                    if (event.getButton()
                            == MouseButton.PRIMARY) {

                        onProjectSelected.accept(
                                project
                        );
                    }
                }
        );

        return card;
    }

    // =============================================================
    // ICON
    // =============================================================

    private ImageView createIcon(
            double size
    ) {

        ImageView icon =
                new ImageView();

        icon.setFitWidth(
                size
        );

        icon.setFitHeight(
                size
        );

        icon.setPreserveRatio(
                true
        );

        icon.setSmooth(
                true
        );

        Rectangle clip =
                new Rectangle(
                        size,
                        size
                );

        clip.setArcWidth(
                size * 0.25
        );

        clip.setArcHeight(
                size * 0.25
        );

        icon.setClip(
                clip
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
    // LOAD ICON
    // =============================================================

    private void loadIcon(
            ModrinthProject project,
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

                        if (image.isError()) {
                            return;
                        }

                        Platform.runLater(() ->
                                imageView.setImage(
                                        image
                                )
                        );

                    } catch (Throwable ex) {

                        System.err.println(
                                "Failed to load icon for "
                                        + project.getTitle()
                        );

                        ex.printStackTrace();
                    }
                });

        thread.setDaemon(
                true
        );

        thread.start();
    }

    // =============================================================
    // INSTALL
    // =============================================================

    private void install(
            ModrinthProject project,
            Button button
    ) {

        button.setDisable(
                true
        );

        button.setText(
                "INSTALLING..."
        );

        statusLabel.setText(
                "Installing "
                        + project.getTitle()
                        + "..."
        );

        Thread thread =
                new Thread(() -> {

                    try {

                        if (contentType ==
                                ModrinthContentType.MOD) {

                            modrinthService.installMod(
                                    instance,
                                    project
                            );

                        } else if (contentType ==
                                ModrinthContentType.RESOURCE_PACK) {

                            contentService.installResourcePack(
                                    instance,
                                    project
                            );

                        } else if (contentType ==
                                ModrinthContentType.SHADER) {

                            contentService.installShader(
                                    instance,
                                    project
                            );

                        } else {

                            throw new IllegalStateException(
                                    "Unsupported content type: "
                                            + contentType
                            );
                        }

                        Platform.runLater(() -> {

                            button.setText(
                                    "INSTALLED"
                            );

                            statusLabel.setText(
                                    project.getTitle()
                                            + " installed successfully."
                            );
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() -> {

                            button.setDisable(
                                    false
                            );

                            button.setText(
                                    "INSTALL"
                            );

                            statusLabel.setText(
                                    ex.getMessage() != null
                                            ? ex.getMessage()
                                            : "Failed to install "
                                              + project.getTitle()
                                              + "."
                            );
                        });
                    }
                });

        thread.setDaemon(
                true
        );

        thread.start();
    }

    // =============================================================
    // POPULAR / SEARCH
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

        searchScrollPane.setVisible(
                false
        );

        searchScrollPane.setManaged(
                false
        );

        if (popularResults.getChildren().isEmpty()) {
            loadMostDownloaded();
        }
    }

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

        popularResults
                .getChildren()
                .clear();

        searchScrollPane.setVisible(
                true
        );

        searchScrollPane.setManaged(
                true
        );
    }

    // =============================================================
    // HELPERS
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

    private String formatDownloads(
            int downloads
    ) {

        if (downloads >= 1_000_000) {

            return String.format(
                    "%.1fM",
                    downloads / 1_000_000.0
            );
        }

        if (downloads >= 1_000) {

            return String.format(
                    "%.1fK",
                    downloads / 1_000.0
            );
        }

        return String.valueOf(
                downloads
        );
    }

    public ModrinthContentType getContentType() {
        return contentType;
    }
}