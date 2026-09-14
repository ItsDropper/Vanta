package org.example.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.example.launcher.model.InstancePreset;

import java.util.List;
import java.util.function.Consumer;

public class PresetConfigurationView extends VBox {

    @FunctionalInterface
    public interface CreateHandler {
        void create(
                InstancePreset preset,
                String name,
                String minecraftVersion
        );
    }

    private final InstancePreset preset;

    private final TextField nameField;
    private final ComboBox<String> versionCombo;

    private final CreateHandler onCreate;
    private final Runnable onBack;

    public PresetConfigurationView(
            InstancePreset preset,
            CreateHandler onCreate,
            Runnable onBack
    ) {

        this.preset = preset;
        this.onCreate = onCreate;
        this.onBack = onBack;

        getStyleClass().add("page");

        setPadding(
                new Insets(28, 36, 28, 36)
        );

        setSpacing(
                20
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
                        "Choose the Minecraft version and configure the new instance."
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
        // MAIN PANEL
        // =========================================================

        VBox panel =
                new VBox();

        panel.getStyleClass().add(
                "create-instance-panel"
        );

        panel.setPadding(
                new Insets(24)
        );

        panel.setMaxWidth(
                760
        );

        VBox.setVgrow(
                panel,
                Priority.ALWAYS
        );

        // =========================================================
        // CONTENT
        // =========================================================

        VBox content =
                new VBox(20);

        // =========================================================
        // PRESET HEADER
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

        VBox presetHeader =
                new VBox(
                        6,
                        presetTitle,
                        description
                );

        // =========================================================
        // ENVIRONMENT
        // =========================================================

        VBox environmentSection =
                new VBox(8);

        Label environmentTitle =
                new Label(
                        "Environment"
                );

        environmentTitle.getStyleClass().add(
                "create-label"
        );

        HBox environment =
                new HBox(16);

        environment.getStyleClass().add(
                "preset-environment"
        );

        environment.setAlignment(
                Pos.CENTER_LEFT
        );

        // ---------------------------------------------------------
        // VERSION
        // ---------------------------------------------------------

        VBox versionBox =
                new VBox(5);

        Label versionLabel =
                new Label(
                        "Minecraft version"
                );

        versionLabel.getStyleClass().add(
                "create-label"
        );

        versionCombo =
                new ComboBox<>();

        versionCombo.getStyleClass().add(
                "create-combo"
        );

        List<String> versions =
                preset.getMinecraftVersions();

        if (versions != null) {

            versionCombo.getItems().addAll(
                    versions
            );
        }

        if (!versionCombo.getItems().isEmpty()) {

            versionCombo.getSelectionModel().selectFirst();

        } else {

            versionCombo.setDisable(
                    true
            );
        }

        versionCombo.setMaxWidth(
                Double.MAX_VALUE
        );

        versionCombo.setPrefHeight(
                44
        );

        versionBox.getChildren().addAll(
                versionLabel,
                versionCombo
        );

        HBox.setHgrow(
                versionBox,
                Priority.ALWAYS
        );

        // ---------------------------------------------------------
        // LOADER
        // ---------------------------------------------------------

        VBox loaderBox =
                new VBox(5);

        Label loaderLabel =
                new Label(
                        "Loader"
                );

        loaderLabel.getStyleClass().add(
                "create-label"
        );

        Label loader =
                new Label(
                        preset.getLoader()
                );

        loader.getStyleClass().add(
                "preset-environment-value"
        );

        loaderBox.getChildren().addAll(
                loaderLabel,
                loader
        );

        environment.getChildren().addAll(
                versionBox,
                loaderBox
        );

        environmentSection.getChildren().addAll(
                environmentTitle,
                environment
        );

        // =========================================================
        // MODS
        // =========================================================

        VBox modsSection =
                new VBox(8);

        Label modsTitle =
                new Label(
                        "Included mods"
                );

        modsTitle.getStyleClass().add(
                "create-label"
        );

        VBox mods =
                new VBox(4);

        List<String> presetMods =
                preset.getMods();

        if (presetMods == null || presetMods.isEmpty()) {

            Label none =
                    new Label(
                            "No mods included."
                    );

            none.getStyleClass().add(
                    "create-option-description"
            );

            mods.getChildren().add(
                    none
            );

        } else {

            for (String mod : presetMods) {

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

        ScrollPane modsScroll =
                new ScrollPane(
                        mods
                );

        modsScroll.getStyleClass().add(
                "preset-mods-scroll"
        );

        modsScroll.setFitToWidth(
                true
        );

        modsScroll.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        modsScroll.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        modsScroll.setPrefHeight(
                Math.min(
                        180,
                        Math.max(
                                60,
                                presetMods == null
                                        ? 60
                                        : presetMods.size() * 25 + 12
                        )
                )
        );

        modsScroll.setMaxHeight(
                180
        );

        modsScroll.setPannable(
                true
        );

        modsSection.getChildren().addAll(
                modsTitle,
                modsScroll
        );

        // =========================================================
        // INSTANCE NAME
        // =========================================================

        VBox nameSection =
                new VBox(8);

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

        nameSection.getChildren().addAll(
                nameLabel,
                nameField
        );

        // =========================================================
        // BUILD CONTENT
        // =========================================================

        content.getChildren().addAll(
                presetHeader,
                environmentSection,
                modsSection,
                nameSection
        );

        // =========================================================
        // CONTENT SCROLL
        // =========================================================

        ScrollPane contentScroll =
                new ScrollPane(
                        content
                );

        contentScroll.getStyleClass().add(
                "preset-config-scroll"
        );

        contentScroll.setFitToWidth(
                true
        );

        contentScroll.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        contentScroll.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        contentScroll.setPannable(
                true
        );

        VBox.setVgrow(
                contentScroll,
                Priority.ALWAYS
        );

        // =========================================================
        // ACTIONS
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
                event ->
                        onBack.run()
        );

        Button createButton =
                new Button(
                        "CREATE INSTANCE"
                );

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

                    String minecraftVersion =
                            versionCombo
                                    .getValue();

                    if (name.isBlank()) {

                        nameField.requestFocus();

                        return;
                    }

                    if (minecraftVersion == null
                            || minecraftVersion.isBlank()) {

                        versionCombo.requestFocus();

                        return;
                    }

                    onCreate.create(
                            preset,
                            name,
                            minecraftVersion
                    );
                });

        HBox actions =
                new HBox(
                        12,
                        backButton,
                        createButton
                );

        actions.getStyleClass().add(
                "preset-actions"
        );

        actions.setAlignment(
                Pos.CENTER_RIGHT
        );

        // =========================================================
        // PANEL BUILD
        // =========================================================

        panel.getChildren().addAll(
                contentScroll,
                actions
        );

        VBox.setVgrow(
                actions,
                Priority.NEVER
        );

        // =========================================================
        // PAGE BUILD
        // =========================================================

        HBox panelWrapper =
                new HBox(
                        panel
                );

        panelWrapper.setAlignment(
                Pos.CENTER
        );

        VBox.setVgrow(
                panelWrapper,
                Priority.ALWAYS
        );

        getChildren().addAll(
                header,
                panelWrapper
        );
    }
}

