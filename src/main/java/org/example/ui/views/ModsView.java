package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.example.launcher.model.Instance;
import org.example.launcher.modrinth.ModrinthProject;
import org.example.launcher.service.ModrinthService;

import java.util.List;

public class ModsView extends VBox {

    private final Instance instance;
    private final ModrinthService modrinthService;

    private final TextField searchField;
    private final VBox results;
    private final Label statusLabel;

    public ModsView(
            Instance instance
    ) {

        this.instance =
                instance;

        this.modrinthService =
                new ModrinthService();

        getStyleClass().add(
                "page"
        );

        setPadding(
                new Insets(36)
        );

        setSpacing(
                20
        );

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        Label title =
                new Label(
                        "Mods"
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

        VBox header =
                new VBox(
                        5,
                        title,
                        subtitle
                );

        // ---------------------------------------------------------
        // SEARCH
        // ---------------------------------------------------------

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
                event -> search()
        );

        searchField.setOnAction(
                event -> search()
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

        // ---------------------------------------------------------
        // RESULTS
        // ---------------------------------------------------------

        results =
                new VBox(
                        12
                );

        results.getStyleClass().add(
                "instance-list"
        );

        ScrollPane scrollPane =
                new ScrollPane(
                        results
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

        // ---------------------------------------------------------
        // STATUS
        // ---------------------------------------------------------

        statusLabel =
                new Label(
                        "Search Modrinth for compatible mods."
                );

        statusLabel.getStyleClass().add(
                "instances-status"
        );

        // ---------------------------------------------------------
        // BUILD
        // ---------------------------------------------------------

        getChildren().addAll(
                header,
                searchBar,
                scrollPane,
                statusLabel
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

            statusLabel.setText(
                    "Enter a mod name to search."
            );

            return;
        }

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
                                showResults(projects)
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

        thread.setDaemon(true);
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
                    "No compatible mods found."
            );

            return;
        }

        for (ModrinthProject project
                : projects) {

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

        Label title =
                new Label(
                        project.getTitle()
                );

        title.getStyleClass().add(
                "instance-name"
        );

        Label description =
                new Label(
                        project.getDescription()
                                != null
                                ? project.getDescription()
                                : "No description."
                );

        description.setWrapText(
                true
        );

        description.getStyleClass().add(
                "instance-version"
        );

        Label metadata =
                new Label(
                        "Modrinth • "
                                + instance.getMinecraftVersion()
                                + " • "
                                + instance.getDisplayLoader()
                );

        metadata.getStyleClass().add(
                "instance-loader"
        );

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
                event ->
                        install(
                                project,
                                installButton
                        )
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
                        18,
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

        card.setPadding(
                new Insets(
                        18,
                        20,
                        18,
                        20
                )
        );

        card.getStyleClass().add(
                "instance-card"
        );

        return card;
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

        thread.setDaemon(true);
        thread.start();
    }
}