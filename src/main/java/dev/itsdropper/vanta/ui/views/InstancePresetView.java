package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.components.PageHeader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.Priority;

import dev.itsdropper.vanta.launcher.instance.InstancePresets;
import dev.itsdropper.vanta.launcher.model.InstancePreset;

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

        getStyleClass().addAll("page", "vanta-consistent-page", "instance-preset-page");

        dev.itsdropper.vanta.ui.ResponsiveLayout.bindPagePadding(this);

        setSpacing(
                24
        );

        // =========================================================
        // HEADER
        // =========================================================

        PageHeader header = new PageHeader(
                Localization.text("preset.title"),
                Localization.text("preset.description"),
                "page-title",
                "page-subtitle"
        );

        // =========================================================
        // PRESETS
        // =========================================================

        VBox presets =
                new VBox(
                        12
                );

        presets.setMaxWidth(920);
        presets.getStyleClass().add("preset-choice-list");

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

        ScrollPane presetScroll =
                new ScrollPane(
                        presets
                );

        presetScroll.setFitToWidth(true);
        presetScroll.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );
        presetScroll.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );
        presetScroll.setPannable(true);
        presetScroll.getStyleClass().add(
                "preset-list-scroll"
        );

        VBox.setVgrow(
                presetScroll,
                Priority.ALWAYS
        );

        // =========================================================
        // BACK
        // =========================================================

        Button backButton =
                new Button(Localization.text("common.back"));

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
                presetScroll,
                actions
        );
    }

    private Button createPresetButton(
            InstancePreset preset
    ) {

        Button button =
                new Button();

        button.getStyleClass().addAll("create-option", "preset-choice-card");

        button.setMaxWidth(
                Double.MAX_VALUE
        );

        button.setPrefHeight(132);

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

        java.util.List<String> versions = preset.getMinecraftVersions();
        java.util.List<String> mods = preset.getMods();
        String versionSummary = versions == null || versions.isEmpty()
                ? "—" : versions.size() == 1 ? versions.get(0) : versions.get(0) + " + " + (versions.size() - 1);
        Label details = new Label(versionSummary + " • " + preset.getLoader() + " • "
                + (mods == null ? 0 : mods.size()) + " mods");

        details.getStyleClass().add("preset-choice-meta");

        Label symbol = new Label("✦");
        symbol.getStyleClass().add("preset-choice-icon");
        Label arrow = new Label("→");
        arrow.getStyleClass().add("preset-choice-arrow");
        VBox text = new VBox(7, title, description, details);
        HBox card = new HBox(18, symbol, text, arrow);
        card.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(text, Priority.ALWAYS);
        card.getStyleClass().add("preset-choice-content");

        text.setAlignment(
                Pos.CENTER_LEFT
        );

        button.setGraphic(card);

        return button;
    }
}