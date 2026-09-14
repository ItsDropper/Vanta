package org.example.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.example.launcher.model.InstancePreset;

import java.util.function.BiConsumer;

public class PresetConfigurationView extends VBox {

    private final InstancePreset preset;

    private final TextField nameField;

    private final BiConsumer<InstancePreset, String> onCreate;
    private final Runnable onBack;

    public PresetConfigurationView(
            InstancePreset preset,
            BiConsumer<InstancePreset, String> onCreate,
            Runnable onBack
    ) {

        this.preset = preset;
        this.onCreate = onCreate;
        this.onBack = onBack;

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
                new Label("Configure preset");

        title.getStyleClass().add(
                "page-title"
        );

        Label subtitle =
                new Label(
                        "Review the preset before creating the instance."
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
        // PANEL
        // =========================================================

        VBox panel =
                new VBox(18);

        panel.getStyleClass().add(
                "create-instance-panel"
        );

        panel.setPadding(
                new Insets(28)
        );

        panel.setMaxWidth(
                680
        );

        // =========================================================
        // PRESET
        // =========================================================

        Label presetTitle =
                new Label(
                        preset.getName()
                );

        presetTitle.getStyleClass().add(
                "card-title"
        );

        Label description =
                new Label(
                        preset.getDescription()
                );

        description.getStyleClass().add(
                "create-option-description"
        );

        description.setWrapText(
                true
        );

        // =========================================================
        // DETAILS
        // =========================================================

        Label minecraftVersion =
                new Label(
                        "Minecraft: "
                                + preset.getMinecraftVersion()
                );

        minecraftVersion.getStyleClass().add(
                "create-label"
        );

        Label loader =
                new Label(
                        "Loader: "
                                + preset.getLoader()
                );

        loader.getStyleClass().add(
                "create-label"
        );

        // =========================================================
        // MODS
        // =========================================================

        Label modsTitle =
                new Label(
                        "Included mods"
                );

        modsTitle.getStyleClass().add(
                "create-label"
        );

        VBox mods =
                new VBox(5);

        if (preset.getMods().isEmpty()) {

            Label none =
                    new Label("No mods");

            none.getStyleClass().add(
                    "create-option-description"
            );

            mods.getChildren().add(
                    none
            );

        } else {

            for (String mod : preset.getMods()) {

                Label modLabel =
                        new Label(
                                "• " + mod
                        );

                modLabel.getStyleClass().add(
                        "create-option-description"
                );

                mods.getChildren().add(
                        modLabel
                );
            }
        }

        // =========================================================
        // NAME
        // =========================================================

        Label nameLabel =
                new Label(
                        "Instance name"
                );

        nameLabel.getStyleClass().add(
                "create-label"
        );

        nameField =
                new TextField();

        nameField.setText(
                preset.getName()
        );

        nameField.setPrefHeight(
                44
        );

        nameField.setPromptText(
                "e.g. Vanta PvP"
        );

        nameField.getStyleClass().add(
                "create-field"
        );

        // =========================================================
        // ACTIONS
        // =========================================================

        Button backButton =
                new Button("CANCEL");

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
                event ->
                        onBack.run()
        );

        Button createButton =
                new Button("CREATE INSTANCE");

        createButton.getStyleClass().add(
                "primary-button"
        );

        createButton.setPrefWidth(
                170
        );

        createButton.setPrefHeight(
                46
        );

        createButton.setOnAction(
                event -> {

                    String name =
                            nameField
                                    .getText()
                                    .trim();

                    if (name.isBlank()) {

                        nameField.requestFocus();

                        return;
                    }

                    onCreate.accept(
                            preset,
                            name
                    );
                });

        HBox actions =
                new HBox(
                        12,
                        backButton,
                        createButton
                );

        actions.setAlignment(
                Pos.CENTER_RIGHT
        );

        // =========================================================
        // BUILD
        // =========================================================

        panel.getChildren().addAll(
                presetTitle,
                description,
                minecraftVersion,
                loader,
                modsTitle,
                mods,
                nameLabel,
                nameField,
                actions
        );

        getChildren().addAll(
                header,
                panel
        );
    }
}