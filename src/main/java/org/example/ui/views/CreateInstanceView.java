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

import org.example.launcher.instance.InstanceInstaller;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class CreateInstanceView extends VBox {

    private static final String VERSION_MANIFEST_URL =
            "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";

    private static final ObjectMapper MAPPER =
            new ObjectMapper();

    private static final HttpClient HTTP =
            HttpClient.newHttpClient();

    private final TextField nameField;
    private final ComboBox<String> versionBox;
    private final ComboBox<String> loaderBox;

    private final Button createButton;
    private final Button backButton;

    private final Label statusLabel;

    private final Runnable onBack;
    private final Runnable onCreated;

    public CreateInstanceView(
            Runnable onBack,
            Runnable onCreated
    ) {

        this.onBack = onBack;
        this.onCreated = onCreated;

        // =========================================================
        // PAGE
        // =========================================================

        getStyleClass().add("page");

        setPadding(
                new Insets(36)
        );

        setSpacing(
                24
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
        // INSTANCE DETAILS
        // =========================================================

        Label detailsTitle =
                new Label("INSTANCE DETAILS");

        detailsTitle.getStyleClass().add(
                "create-section-title"
        );

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

        // =========================================================
        // LOADER
        // =========================================================

        Label loaderLabel =
                createLabel("Mod loader");

        loaderBox =
                new ComboBox<>();

        loaderBox.getItems().addAll(
                "Vanilla",
                "Fabric"
        );

        loaderBox.getSelectionModel()
                .select("Fabric");

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

        form.getStyleClass().add(
                "create-panel"
        );

        form.setMaxWidth(
                680
        );

        form.getChildren().addAll(
                detailsTitle,

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
                        "CANCEL"
                );

        backButton.getStyleClass().add(
                "secondary-button"
        );

        backButton.setPrefHeight(
                46
        );

        backButton.setPrefWidth(
                110
        );

        backButton.setOnAction(
                event -> onBack.run()
        );

        createButton =
                new Button(
                        "CREATE INSTANCE"
                );

        createButton.getStyleClass().add(
                "primary-button"
        );

        createButton.setPrefHeight(
                46
        );

        createButton.setPrefWidth(
                170
        );

        createButton.setOnAction(
                event -> createInstance()
        );

        HBox actions =
                new HBox(
                        12,
                        backButton,
                        createButton
                );

        actions.setAlignment(
                Pos.CENTER_RIGHT
        );

        HBox.setHgrow(
                actions,
                Priority.ALWAYS
        );

        // =========================================================
        // ACTION PANEL
        // =========================================================

        VBox bottom =
                new VBox(
                        12,
                        statusLabel,
                        actions
                );

        bottom.setMaxWidth(
                680
        );

        // =========================================================
        // BUILD
        // =========================================================

        getChildren().addAll(
                header,
                form,
                bottom
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

                        for (JsonNode version :
                                manifest.get("versions")) {

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

                                statusLabel.setText(
                                        "No Minecraft versions found."
                                );

                                return;
                            }

                            versionBox
                                    .getSelectionModel()
                                    .selectFirst();

                            statusLabel.setText(
                                    "Ready to create instance."
                            );
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() -> {

                            versionBox.setDisable(
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
    // CREATE
    // =============================================================

    private void createInstance() {

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

        // ---------------------------------------------------------
        // LOCK UI
        // ---------------------------------------------------------

        createButton.setDisable(
                true
        );

        backButton.setDisable(
                true
        );

        nameField.setDisable(
                true
        );

        versionBox.setDisable(
                true
        );

        loaderBox.setDisable(
                true
        );

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

                            createButton.setDisable(
                                    false
                            );

                            backButton.setDisable(
                                    false
                            );

                            nameField.setDisable(
                                    false
                            );

                            versionBox.setDisable(
                                    false
                            );

                            loaderBox.setDisable(
                                    false
                            );

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