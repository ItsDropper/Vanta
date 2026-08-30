package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import org.example.launcher.model.Instance;
import org.example.launcher.modrinth.ModrinthProject;
import org.example.launcher.service.ModrinthService;

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

public class BrowseModsView extends VBox {

    private final Instance instance;
    private final Runnable onBack;

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

    private final java.util.function.Consumer<ModrinthProject> onModSelected;

    public BrowseModsView(
            Instance instance,
            Runnable onBack,
            java.util.function.Consumer<ModrinthProject> onModSelected
    ) {

        this.instance =
                instance;

        this.onBack =
                onBack;

        this.onModSelected =
                onModSelected;

        this.modrinthService =
                new ModrinthService();

        this.httpClient =
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
                        "Browse Mods"
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

        VBox header =
                new VBox(
                        14,
                        backButton,
                        headerText
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
                new Label(
                        "Search Modrinth for compatible mods."
                );

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

        // Search results are hidden initially.
        searchScrollPane.setVisible(
                false
        );

        searchScrollPane.setManaged(
                false
        );

        // =========================================================
        // LOAD POPULAR MODS
        // =========================================================

        loadMostDownloadedMods();
    }

    // =============================================================
    // MOST DOWNLOADED
    // =============================================================

    private void loadMostDownloadedMods() {

        popularStatusLabel.setText(
                "Loading popular mods..."
        );

        Thread thread =
                new Thread(() -> {

                    try {

                        List<ModrinthProject> projects =
                                modrinthService
                                        .getMostDownloadedMods(
                                                instance
                                        );

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
            List<ModrinthProject> projects
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
                ModrinthProject project
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
            ModrinthProject project
    ) {

        // =========================================================
        // ICON
        // =========================================================

        ImageView icon =
                new ImageView();

        icon.setFitWidth(
                48
        );

        icon.setFitHeight(
                48
        );

        icon.setPreserveRatio(
                true
        );

        icon.setSmooth(
                true
        );

        Rectangle clip =
                new Rectangle(
                        48,
                        48
                );

        clip.setArcWidth(
                12
        );

        clip.setArcHeight(
                12
        );

        icon.setClip(
                clip
        );

        VBox iconBox =
                new VBox(
                        icon
                );

        iconBox.setAlignment(
                Pos.CENTER
        );

        iconBox.setMinSize(
                48,
                48
        );

        iconBox.setPrefSize(
                48,
                48
        );

        iconBox.setMaxSize(
                48,
                48
        );

        iconBox.getStyleClass().add(
                "mod-icon-box"
        );

        loadIcon(
                project,
                icon
        );

        // =========================================================
        // TITLE
        // =========================================================

        Label title =
                new Label(
                        project.getTitle()
                );

        title.getStyleClass().add(
                "instance-name"
        );

        // =========================================================
        // DESCRIPTION
        // =========================================================

        Label description =
                new Label(
                        project.getDescription() != null
                                ? project.getDescription()
                                : "No description."
                );

        description.setWrapText(
                true
        );

        description.setMaxWidth(
                210
        );

        description.setMaxHeight(
                42
        );

        description.getStyleClass().add(
                "instance-version"
        );

        // =========================================================
        // DOWNLOADS
        // =========================================================

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

        // =========================================================
        // INFORMATION
        // =========================================================

        VBox information =
                new VBox(
                        5,
                        title,
                        description,
                        downloads
                );

        // =========================================================
        // ROW
        // =========================================================

        HBox row =
                new HBox(
                        12,
                        iconBox,
                        information
                );

        row.setAlignment(
                Pos.TOP_LEFT
        );

        // =========================================================
        // CARD
        // =========================================================

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
                javafx.scene.Cursor.HAND
        );

        card.setOnMouseClicked(
                event -> {

                    if (event.getButton() ==
                            javafx.scene.input.MouseButton.PRIMARY) {

                        onModSelected.accept(
                                project
                        );
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
        // EMPTY SEARCH = MOST DOWNLOADED
        // =========================================================

        if (query.isBlank()) {

            showPopularSection();

            results
                    .getChildren()
                    .clear();

            statusLabel.setText(
                    "Search Modrinth for compatible mods."
            );

            return;
        }

        // =========================================================
        // SEARCH ACTIVE
        // =========================================================

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
                                modrinthService.searchMods(
                                        instance,
                                        query
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
    // SHOW POPULAR
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

        // If the popular results were unloaded,
        // load them again when returning to the empty search.
        if (popularResults.getChildren().isEmpty()) {

            loadMostDownloadedMods();
        }
    }

    // =============================================================
    // HIDE POPULAR
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

        // Actually unload the popular cards.
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
                    "No compatible mods found."
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
                                    project
                            )
                    );
        }

        statusLabel.setText(
                projects.size()
                        + " compatible projects found."
        );
    }

    // =============================================================
    // PROJECT CARD
    // =============================================================

    private VBox createProjectCard(
            ModrinthProject project
    ) {

        // =========================================================
        // ICON
        // =========================================================

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

        VBox iconBox =
                new VBox(
                        icon
                );

        iconBox.setAlignment(
                Pos.CENTER
        );

        iconBox.setMinSize(
                52,
                52
        );

        iconBox.setPrefSize(
                52,
                52
        );

        iconBox.setMaxSize(
                52,
                52
        );

        iconBox.getStyleClass().add(
                "mod-icon-box"
        );

        loadIcon(
                project,
                icon
        );

        // =========================================================
        // TITLE
        // =========================================================

        Label title =
                new Label(
                        project.getTitle()
                );

        title.getStyleClass().add(
                "instance-name"
        );

        // =========================================================
        // DESCRIPTION
        // =========================================================

        Label description =
                new Label(
                        project.getDescription() != null
                                ? project.getDescription()
                                : "No description."
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

        // =========================================================
        // METADATA
        // =========================================================

        Label metadata =
                new Label(
                        "Modrinth • "
                                + instance.getMinecraftVersion()
                                + " • "
                                + instance.getDisplayLoader()
                                + " • "
                                + formatNumber(
                                project.getDownloads()
                        )
                                + " downloads"
                );

        metadata.getStyleClass().add(
                "instance-loader"
        );

        // =========================================================
        // INSTALL BUTTON
        // =========================================================

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

        installButton.setOnAction(
                event -> {

                    event.consume();

                    install(
                            project,
                            installButton
                    );
                }
        );

        // =========================================================
        // INFORMATION
        // =========================================================

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

        // =========================================================
        // ROW
        // =========================================================

        HBox row =
                new HBox(
                        16,
                        iconBox,
                        information,
                        installButton
                );

        row.setAlignment(
                Pos.CENTER_LEFT
        );

        // =========================================================
        // CARD
        // =========================================================

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

                        onModSelected.accept(
                                project
                        );
                    }
                }
        );

        return card;
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

                        modrinthService.installMod(
                                instance,
                                project
                        );

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
                                            : "Failed to install mod."
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

