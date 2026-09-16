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

import org.example.launcher.modrinth.ModrinthContentType;
import org.example.launcher.modrinth.ModrinthProject;
import org.example.launcher.service.ModrinthContentService;

import java.util.List;

public class ModpackBrowserView extends VBox {

    private final String minecraftVersion;

    private final ModrinthContentService contentService;

    private final VBox resultsList;
    private final TextField searchField;
    private final Label statusLabel;
    private final Runnable onInstanceImported;

    public ModpackBrowserView(
            String minecraftVersion,
            Runnable onBack,
            Runnable onInstanceImported
    ) {

        this.minecraftVersion = minecraftVersion;
        this.contentService = new ModrinthContentService();
        this.onInstanceImported = onInstanceImported;

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

        Label title = new Label(
                "Browse Modpacks"
        );

        title.getStyleClass().add(
                "page-title"
        );

        Label subtitle = new Label(
                "Modrinth • Minecraft "
                        + minecraftVersion
        );

        subtitle.getStyleClass().add(
                "page-subtitle"
        );

        VBox headerText = new VBox(
                5,
                title,
                subtitle
        );

        Button backButton = new Button(
                "BACK"
        );

        backButton.getStyleClass().add(
                "secondary-button"
        );

        backButton.setOnAction(
                event -> onBack.run()
        );

        HBox header = new HBox(
                16,
                backButton,
                headerText
        );

        header.setAlignment(
                Pos.CENTER_LEFT
        );

        // =========================================================
        // SEARCH
        // =========================================================

        searchField = new TextField();

        searchField.setPromptText(
                "Search modpacks..."
        );

        HBox.setHgrow(
                searchField,
                Priority.ALWAYS
        );

        Button searchButton = new Button(
                "SEARCH"
        );

        searchButton.getStyleClass().add(
                "primary-button"
        );

        searchButton.setOnAction(
                event -> search()
        );

        searchField.setOnAction(
                event -> search()
        );

        HBox searchBar = new HBox(
                10,
                searchField,
                searchButton
        );

        searchBar.setAlignment(
                Pos.CENTER_LEFT
        );

        // =========================================================
        // RESULTS
        // =========================================================

        resultsList = new VBox(
                12
        );

        resultsList.getStyleClass().add(
                "instance-list"
        );

        ScrollPane scrollPane = new ScrollPane(
                resultsList
        );

        scrollPane.setFitToWidth(
                true
        );

        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        scrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        scrollPane.getStyleClass().add(
                "instances-scroll"
        );

        VBox.setVgrow(
                scrollPane,
                Priority.ALWAYS
        );

        // =========================================================
        // STATUS
        // =========================================================

        statusLabel = new Label(
                "Loading popular modpacks..."
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
                scrollPane,
                statusLabel
        );

        loadPopular();
    }

    // =============================================================
    // POPULAR
    // =============================================================

    private void loadPopular() {

        statusLabel.setText(
                "Loading popular modpacks..."
        );

        resultsList
                .getChildren()
                .clear();

        Thread thread = new Thread(() -> {

            try {

                List<ModrinthProject> projects =
                        contentService.getMostDownloaded(
                                ModrinthContentType.MODPACK,
                                null,
                                minecraftVersion
                        );

                Platform.runLater(() ->
                        showResults(projects)
                );

            } catch (Exception e) {

                e.printStackTrace();

                Platform.runLater(() ->
                        statusLabel.setText(
                                "Failed to load modpacks."
                        )
                );
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    // =============================================================
    // SEARCH
    // =============================================================

    private void search() {

        String query =
                searchField.getText()
                        .trim();

        if (query.isEmpty()) {

            loadPopular();
            return;
        }

        statusLabel.setText(
                "Searching..."
        );

        resultsList
                .getChildren()
                .clear();

        Thread thread = new Thread(() -> {

            try {

                List<ModrinthProject> projects =
                        contentService.search(
                                query,
                                ModrinthContentType.MODPACK,
                                null,
                                minecraftVersion
                        );

                Platform.runLater(() ->
                        showResults(projects)
                );

            } catch (Exception e) {

                e.printStackTrace();

                Platform.runLater(() ->
                        statusLabel.setText(
                                "Search failed."
                        )
                );
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    // =============================================================
    // SHOW RESULTS
    // =============================================================

    private void showResults(
            List<ModrinthProject> projects
    ) {

        resultsList
                .getChildren()
                .clear();

        if (projects == null || projects.isEmpty()) {

            Label empty = new Label(
                    "No modpacks found."
            );

            empty.getStyleClass().add(
                    "instances-status"
            );

            resultsList
                    .getChildren()
                    .add(empty);

            statusLabel.setText(
                    "No results."
            );

            return;
        }

        for (ModrinthProject project : projects) {

            resultsList
                    .getChildren()
                    .add(
                            createProjectCard(
                                    project
                            )
                    );
        }

        statusLabel.setText(
                projects.size()
                        + " modpack"
                        + (projects.size() == 1 ? "" : "s")
                        + " found."
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

        ImageView icon = new ImageView();

        icon.setFitWidth(60);
        icon.setFitHeight(60);
        icon.setPreserveRatio(true);
        icon.setSmooth(true);

        Rectangle clip = new Rectangle(
                60,
                60
        );

        clip.setArcWidth(14);
        clip.setArcHeight(14);

        icon.setClip(clip);

        VBox iconBox = new VBox(
                icon
        );

        iconBox.setAlignment(
                Pos.CENTER
        );

        iconBox.setMinSize(
                60,
                60
        );

        iconBox.setPrefSize(
                60,
                60
        );

        iconBox.setMaxSize(
                60,
                60
        );

        iconBox.getStyleClass().add(
                "mod-icon-box"
        );

        loadIcon(
                project.getIconUrl(),
                icon
        );

        // =========================================================
        // INFORMATION
        // =========================================================

        Label title = new Label(
                project.getTitle()
        );

        title.getStyleClass().add(
                "instance-name"
        );

        title.setWrapText(true);

        String description =
                project.getDescription();

        if (description == null) {
            description = "";
        }

        if (description.length() > 220) {

            description =
                    description.substring(
                            0,
                            217
                    )
                            + "...";
        }

        Label descriptionLabel =
                new Label(
                        description
                );

        descriptionLabel.getStyleClass().add(
                "page-subtitle"
        );

        descriptionLabel.setWrapText(true);

        Label metadata = new Label(
                formatDownloads(
                        project.getDownloads()
                )
                        + " downloads"
        );

        metadata.getStyleClass().add(
                "instance-loader"
        );

        VBox information = new VBox(
                5,
                title,
                descriptionLabel,
                metadata
        );

        HBox.setHgrow(
                information,
                Priority.ALWAYS
        );

        // =========================================================
        // IMPORT
        // =========================================================

        Button importButton = new Button(
                "IMPORT"
        );

        importButton.getStyleClass().add(
                "primary-button"
        );

        importButton.setOnAction(
                event ->
                        importModpack(
                                project,
                                importButton,
                                onInstanceImported
                        )
        );

        // =========================================================
        // ROW
        // =========================================================

        HBox row = new HBox(
                16,
                iconBox,
                information,
                importButton
        );

        row.setAlignment(
                Pos.CENTER_LEFT
        );

        // =========================================================
        // CARD
        // =========================================================

        VBox card = new VBox(
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

        return card;
    }

    // =============================================================
    // IMPORT
    // =============================================================

    private void importModpack(
            ModrinthProject project,
            Button button,
            Runnable onInstanceImported
    ) {

        button.setDisable(true);
        button.setText("IMPORTING...");

        statusLabel.setText(
                "Downloading "
                        + project.getTitle()
                        + "..."
        );

        Thread thread = new Thread(() -> {

            try {

                contentService.installModpack(
                        project
                );

                Platform.runLater(() -> {

                    button.setText(
                            "IMPORTED"
                    );

                    statusLabel.setText(
                            project.getTitle()
                                    + " imported successfully."
                    );

                    if (onInstanceImported != null) {
                        onInstanceImported.run();
                    }
                });

            } catch (Exception e) {

                e.printStackTrace();

                Platform.runLater(() -> {

                    button.setDisable(false);
                    button.setText("IMPORT");

                    statusLabel.setText(
                            "Failed to import "
                                    + project.getTitle()
                                    + "."
                    );
                });
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    // =============================================================
    // ICON
    // =============================================================

    private void loadIcon(
            String url,
            ImageView imageView
    ) {

        if (url == null || url.isBlank()) {
            return;
        }

        Thread thread = new Thread(() -> {

            try {

                Image image = new Image(
                        url,
                        60,
                        60,
                        true,
                        true,
                        true
                );

                Platform.runLater(() -> {

                    if (!image.isError()) {

                        imageView.setImage(
                                image
                        );
                    }
                });

            } catch (Exception ignored) {
            }
        });

        thread.setDaemon(true);
        thread.start();
    }

    // =============================================================
    // DOWNLOAD FORMAT
    // =============================================================

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
}

