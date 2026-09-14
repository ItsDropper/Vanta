package org.example.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import org.example.launcher.instance.InstancePresets;
import org.example.launcher.model.InstancePreset;

import java.util.function.Consumer;

public class InstancePresetView extends VBox {

    private final Consumer<InstancePreset> onPresetSelected;
    private final Runnable onBack;

    public InstancePresetView(
            Consumer<InstancePreset> onPresetSelected,
            Runnable onBack
    ) {

        this.onPresetSelected = onPresetSelected;
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
                new Label("Choose a preset");

        title.getStyleClass().add(
                "page-title"
        );

        Label subtitle =
                new Label(
                        "Start with a predefined Minecraft configuration."
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
        // PRESETS
        // =========================================================

        VBox presets =
                new VBox(
                        12
                );

        presets.setMaxWidth(
                680
        );

        for (InstancePreset preset :
                InstancePresets.getBuiltInPresets()) {

            Button button =
                    createPresetButton(
                            preset
                    );

            button.setOnAction(
                    event ->
                            onPresetSelected.accept(
                                    preset
                            )
            );

            presets.getChildren().add(
                    button
            );
        }

        // =========================================================
        // BACK
        // =========================================================

        Button backButton =
                new Button("BACK");

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
                presets,
                actions
        );
    }

    private Button createPresetButton(
            InstancePreset preset
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
                92
        );

        Label title =
                new Label(
                        preset.getName()
                );

        title.getStyleClass().add(
                "create-option-title"
        );

        Label description =
                new Label(
                        preset.getDescription()
                );

        description.getStyleClass().add(
                "create-option-description"
        );

        Label details =
                new Label(
                        preset.getMinecraftVersion()
                                + " • "
                                + preset.getLoader()
                                + " • "
                                + preset.getMods().size()
                                + " mods"
                );

        details.getStyleClass().add(
                "create-option-description"
        );

        VBox text =
                new VBox(
                        5,
                        title,
                        description,
                        details
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