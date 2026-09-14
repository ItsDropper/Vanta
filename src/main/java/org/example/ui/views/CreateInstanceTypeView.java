package org.example.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public class CreateInstanceTypeView extends VBox {

    private final Runnable onBack;
    private final Runnable onCustom;
    private final Runnable onImport;
    private final Runnable onPreset;
    private final Runnable onModrinth;

    public CreateInstanceTypeView(
            Runnable onBack,
            Runnable onCustom,
            Runnable onImport,
            Runnable onPreset,
            Runnable onModrinth
    ) {

        this.onBack = onBack;
        this.onCustom = onCustom;
        this.onImport = onImport;
        this.onPreset = onPreset;
        this.onModrinth = onModrinth;

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
                        "Choose how you want to create your Minecraft instance."
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
        // OPTIONS
        // =========================================================

        VBox options =
                new VBox(
                        12
                );

        options.setMaxWidth(
                680
        );

        // ---------------------------------------------------------
        // CUSTOM
        // ---------------------------------------------------------

        Button customButton =
                createOption(
                        "CUSTOM",
                        "Create a new instance manually.",
                        false
                );

        customButton.setOnAction(
                event -> onCustom.run()
        );

        // ---------------------------------------------------------
        // IMPORT
        // ---------------------------------------------------------

        Button importButton =
                createOption(
                        "IMPORT AN INSTANCE",
                        "Import an existing Vanta instance or .mrpack.",
                        false
                );

        importButton.setOnAction(
                event -> onImport.run()
        );

        // ---------------------------------------------------------
        // PRESET
        // ---------------------------------------------------------

        Button presetButton =
                createOption(
                        "CHOOSE A PRESET",
                        "Start with a predefined Vanta instance configuration.",
                        false
                );

        presetButton.setOnAction(
                event -> onPreset.run()
        );

        // ---------------------------------------------------------
        // MODRINTH
        // ---------------------------------------------------------

        Button modrinthButton =
                createOption(
                        "BROWSE MODRINTH",
                        "Find and install a modpack from Modrinth.",
                        true
                );

        modrinthButton.setOnAction(
                event -> onModrinth.run()
        );

        options.getChildren().addAll(
                customButton,
                importButton,
                presetButton,
                modrinthButton
        );

        // =========================================================
        // BACK
        // =========================================================

        Button backButton =
                new Button(
                        "CANCEL"
                );

        backButton.getStyleClass().add(
                "secondary-button"
        );

        backButton.setPrefWidth(
                110
        );

        backButton.setPrefHeight(
                46
        );

        backButton.setOnAction(
                event -> onBack.run()
        );

        HBox actions =
                new HBox(
                        backButton
                );

        actions.setAlignment(
                Pos.CENTER_RIGHT
        );

        // =========================================================
        // BUILD
        // =========================================================

        getChildren().addAll(
                header,
                options,
                actions
        );
    }

    // =============================================================
    // OPTION BUTTON
    // =============================================================

    private Button createOption(
            String title,
            String description,
            boolean disabled
    ) {

        Button button =
                new Button();

        button.getStyleClass().add(
                "create-option"
        );

        button.setMaxWidth(
                Double.MAX_VALUE
        );

        button.setPrefHeight(
                82
        );

        button.setDisable(
                disabled
        );

        Label titleLabel =
                new Label(
                        title
                                + (disabled
                                ? "  •  COMING SOON"
                                : "")
                );

        titleLabel.getStyleClass().add(
                "create-option-title"
        );

        Label descriptionLabel =
                new Label(
                        description
                );

        descriptionLabel.getStyleClass().add(
                "create-option-description"
        );

        VBox text =
                new VBox(
                        5,
                        titleLabel,
                        descriptionLabel
                );

        text.setAlignment(
                Pos.CENTER_LEFT
        );

        button.setGraphic(
                text
        );

        return button;
    }
}