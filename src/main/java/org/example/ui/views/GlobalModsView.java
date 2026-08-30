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

import org.example.launcher.modrinth.ModrinthClient;
import org.example.launcher.modrinth.ModrinthSearchHit;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import javax.imageio.ImageIO;

public class GlobalModsView extends VBox {

    private final ModrinthClient modrinthClient;
    private final HttpClient httpClient;

    private final TextField searchField;

    private final VBox popularResults;
    private final Label popularStatusLabel;
    private final Label popularTitle;
    private final ScrollPane popularScrollPane;

    private final VBox results;
    private final Label statusLabel;
    private final ScrollPane resultsScrollPane;

    private final java.util.function.Consumer<String> onModSelected;

    public GlobalModsView(
            java.util.function.Consumer<String> onModSelected
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
                        "Mods"
                );

        title.getStyleClass().add(
                "page-title"
        );

        Label subtitle =
                new Label(
                        "Browse mods on Modrinth."
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

        loadMostDownloadedMods();
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
                                        .getMostDownloadedMods(
                                                null,
                                                null
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
                                    projectId
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
                                                null,
                                                null
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
                                    projectId
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

