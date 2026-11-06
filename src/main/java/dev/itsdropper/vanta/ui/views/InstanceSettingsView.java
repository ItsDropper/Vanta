package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.stage.FileChooser;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.Platform;
import dev.itsdropper.vanta.launcher.loader.LoaderStackConfiguration;
import dev.itsdropper.vanta.launcher.loader.LoaderStackManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import dev.itsdropper.vanta.launcher.instance.InstanceManager;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.model.InstanceSettings;
import dev.itsdropper.vanta.ui.components.IconView;
import dev.itsdropper.vanta.ui.components.PageHeader;

import java.io.File;

public class InstanceSettingsView extends VBox {

    private final Instance instance;
    private final Runnable onBack;
    private final Runnable onMods;

    private final Slider ramSlider;
    private final Label ramValueLabel;

    private final TextField widthField;
    private final TextField heightField;
    private final TextField javaPathField;

    private final TextArea javaArgumentsArea;
    private final TextArea gameArgumentsArea;

    private final CheckBox fullscreenCheck;

    private final Label statusLabel;
    private final TextArea loaderStackProfilesArea;
    private final TextArea loaderStackTweakersArea;
    private final List<LoaderStackConfiguration.Layer> loaderStackLayers = new ArrayList<>();
    private boolean loaderStackConfigured;
    private static final ObjectMapper LOADER_PROFILE_MAPPER = new ObjectMapper();

    public InstanceSettingsView(
            Instance instance,
            Runnable onBack,
            Runnable onMods
    ) {

        this.instance =
                instance;

        this.onBack =
                onBack;

        this.onMods =
                onMods;

        getStyleClass().add(
                "page"
        );

        dev.itsdropper.vanta.ui.ResponsiveLayout.bindPagePadding(this);

        setSpacing(
                24
        );

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        Button backButton =
                new Button(Localization.text("common.back"), IconView.create(IconView.Type.ARROW_LEFT, 16));

        backButton.getStyleClass().add(
                "secondary-button"
        );

        backButton.setOnAction(
                event -> onBack.run()
        );

        HBox navigation =
                new HBox(
                        10,
                        backButton
                );

        PageHeader headerText = new PageHeader(
                instance.getName(),
                "Configure how this instance launches.",
                "page-title",
                "page-subtitle"
        );

        VBox header =
                new VBox(
                        14,
                        navigation,
                        headerText
                );

        // ---------------------------------------------------------
        // MEMORY
        // ---------------------------------------------------------

        Label memoryTitle =
                createSectionTitle(
                        "MEMORY"
                );

        ramValueLabel =
                new Label(
                        "4096 MB"
                );

        ramValueLabel.getStyleClass().add(
                "instance-setting-value"
        );

        ramSlider =
                new Slider(
                        1024,
                        16384,
                        4096
                );

        ramSlider.setBlockIncrement(
                512
        );

        ramSlider.setMajorTickUnit(
                4096
        );

        ramSlider.setMinorTickCount(
                7
        );

        ramSlider.setSnapToTicks(
                true
        );

        ramSlider.setShowTickMarks(
                true
        );

        ramSlider.setShowTickLabels(
                true
        );

        ramSlider.setMaxWidth(
                Double.MAX_VALUE
        );

        ramSlider.getStyleClass().add(
                "ram-slider"
        );

        ramSlider.valueProperty().addListener(
                (observable, oldValue, newValue) -> {

                    int ram =
                            snapRam(
                                    newValue.doubleValue()
                            );

                    ramValueLabel.setText(
                            ram + " MB"
                    );
                }
        );

        HBox ramPresets = new HBox(6);
        for (int gigabytes : new int[]{2, 4, 6, 8, 10, 12, 14, 16}) {
            Button preset = new Button(gigabytes + " GB");
            preset.getStyleClass().add("accent-preset");
            preset.setOnAction(event -> ramSlider.setValue(gigabytes * 1024));
            ramPresets.getChildren().add(preset);
        }

        VBox ramBox =
                new VBox(
                        10,
                        ramValueLabel,
                        ramSlider,
                        new Label(Localization.text("instanceSettings.presets")),
                        ramPresets
                );

        ramBox.setMaxWidth(
                Double.MAX_VALUE
        );

        VBox memoryCard =
                createCard(
                        ramBox
                );

        // ---------------------------------------------------------
        // JAVA
        // ---------------------------------------------------------

        Label javaTitle =
                createSectionTitle(
                        "JAVA"
                );

        javaPathField =
                new TextField();

        javaPathField.setPromptText(
                Localization.text("common.automatic")
        );

        Button browseJavaButton = new Button(Localization.text("common.browse"));
        browseJavaButton.getStyleClass().add("secondary-button");
        browseJavaButton.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(Localization.text("instanceSettings.selectJava"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    Platform.operatingSystem() == Platform.OperatingSystem.WINDOWS
                            ? "Java executable (java.exe)" : "Java executable",
                    Platform.javaExecutableName()
            ));
            File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (selected != null) javaPathField.setText(selected.getAbsolutePath());
        });

        javaPathField.getStyleClass().add(
                "create-field"
        );

        javaArgumentsArea =
                new TextArea();

        javaArgumentsArea.setPromptText(
                Localization.text("instanceSettings.jvmArguments")
        );

        javaArgumentsArea.setWrapText(
                true
        );

        javaArgumentsArea.setPrefRowCount(
                3
        );

        javaArgumentsArea.getStyleClass().add(
                "create-field"
        );

        HBox javaPathRow = new HBox(8, javaPathField, browseJavaButton);
        HBox.setHgrow(javaPathField, Priority.ALWAYS);

        ComboBox<String> jvmPreset = new ComboBox<>();
        jvmPreset.getItems().addAll(
                "Choose JVM preset…",
                "Default (no extra arguments)",
                "G1GC balanced",
                "Low-pause G1GC",
                "Diagnostics (GC logging)"
        );
        jvmPreset.getSelectionModel().selectFirst();
        jvmPreset.setMaxWidth(Double.MAX_VALUE);
        jvmPreset.setOnAction(event -> {
            String selected = jvmPreset.getValue();
            if (selected == null) return;
            switch (selected) {
                case "Default (no extra arguments)" -> javaArgumentsArea.clear();
                case "G1GC balanced" -> javaArgumentsArea.setText("-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50");
                case "Low-memory / garbage collection" -> javaArgumentsArea.setText("-XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ParallelRefProcEnabled");
                case "Diagnostics (GC logging)" -> javaArgumentsArea.setText("-Xlog:gc*:file=gc.log:time,uptime,level,tags");
                default -> { }
            }
        });

        VBox javaCard =
                createCard(
                        createInput("Java executable", javaPathRow),
                        createInput("JVM argument preset", jvmPreset),
                        createInput("Java arguments", javaArgumentsArea)
                );

        // ---------------------------------------------------------
        // GAME
        // ---------------------------------------------------------

        Label gameTitle =
                createSectionTitle(
                        "GAME"
                );

        gameArgumentsArea =
                new TextArea();

        gameArgumentsArea.setPromptText(
                Localization.text("instanceSettings.minecraftArguments")
        );

        gameArgumentsArea.setWrapText(
                true
        );

        gameArgumentsArea.setPrefRowCount(
                3
        );

        gameArgumentsArea.getStyleClass().add(
                "create-field"
        );

        widthField =
                new TextField();

        heightField =
                new TextField();

        widthField.setPromptText(
                "1920"
        );

        heightField.setPromptText(
                "1080"
        );

        widthField.getStyleClass().add(
                "create-field"
        );

        heightField.getStyleClass().add(
                "create-field"
        );

        VBox widthInput =
                createInput(
                        "Width",
                        widthField
                );

        VBox heightInput =
                createInput(
                        "Height",
                        heightField
                );

        HBox.setHgrow(
                widthInput,
                Priority.ALWAYS
        );

        HBox.setHgrow(
                heightInput,
                Priority.ALWAYS
        );

        HBox resolution =
                new HBox(
                        10,
                        widthInput,
                        heightInput
                );

        resolution.setAlignment(
                Pos.CENTER_LEFT
        );

        fullscreenCheck =
                new CheckBox(
                        Localization.text("instanceSettings.fullscreen")
                );

        fullscreenCheck.getStyleClass().add(
                "settings-checkbox"
        );

        VBox gameCard =
                createCard(
                        createInput(
                                "Game arguments",
                                gameArgumentsArea
                        ),
                        resolution,
                        fullscreenCheck
                );

        // ---------------------------------------------------------
        // LOADER STACK
        // ---------------------------------------------------------

        Label loaderStackTitle = createSectionTitle("LOADER STACK");

        loaderStackProfilesArea = new TextArea();
        loaderStackProfilesArea.setEditable(false);
        loaderStackProfilesArea.setWrapText(true);
        loaderStackProfilesArea.setPrefRowCount(3);
        loaderStackProfilesArea.setPromptText(Localization.text("instanceSettings.noOverlays"));
        loaderStackProfilesArea.getStyleClass().add("create-field");

        loaderStackTweakersArea = new TextArea();
        loaderStackTweakersArea.setWrapText(true);
        loaderStackTweakersArea.setPrefRowCount(3);
        loaderStackTweakersArea.setPromptText(
                Localization.text("instanceSettings.tweakersHelp"));
        loaderStackTweakersArea.getStyleClass().add("create-field");

        Button addLoaderProfilesButton = new Button(Localization.text("instanceSettings.addProfile"));
        addLoaderProfilesButton.getStyleClass().add("secondary-button");
        addLoaderProfilesButton.setOnAction(event -> addLoaderProfiles());

        Button removeLastLoaderProfileButton = new Button(Localization.text("instanceSettings.removeLast"));
        removeLastLoaderProfileButton.getStyleClass().add("secondary-button");
        removeLastLoaderProfileButton.setOnAction(event -> {
            if (!loaderStackLayers.isEmpty()) {
                loaderStackLayers.remove(loaderStackLayers.size() - 1);
                refreshLoaderProfileList();
            }
        });

        VBox loaderStackCard = createCard(
                new Label(Localization.text("instanceSettings.overlayHelp")),
                loaderStackProfilesArea,
                new HBox(8, addLoaderProfilesButton, removeLastLoaderProfileButton),
                createInput("LaunchWrapper tweakers (one class per line)", loaderStackTweakersArea)
        );

        // ---------------------------------------------------------
        // DIRECTORY
        // ---------------------------------------------------------

        Label directoryTitle =
                createSectionTitle(
                        "DIRECTORY"
                );

        Label directory =
                new Label(
                        instance.getDirectory()
                                .toString()
                );

        directory.setWrapText(
                true
        );

        directory.getStyleClass().add(
                "instance-directory"
        );

        VBox directoryCard =
                createCard(
                        directory
                );

        // ---------------------------------------------------------
        // ACTIONS
        // ---------------------------------------------------------

        statusLabel =
                new Label();

        statusLabel.getStyleClass().add(
                "create-status"
        );

        Button cancelButton =
                new Button(
                        Localization.text("common.close"),
                        IconView.create(IconView.Type.CLOSE, 16)
                );

        cancelButton.getStyleClass().add(
                "secondary-button"
        );

        cancelButton.setOnAction(
                event -> onBack.run()
        );

        Button saveButton =
                new Button(
                        Localization.text("common.save"),
                        IconView.create(IconView.Type.SAVE, 16)
                );

        saveButton.getStyleClass().add(
                "secondary-button"
        );

        saveButton.setOnAction(
                event -> saveSettings()
        );

        Button saveAndCloseButton =
                new Button(
                        Localization.text("common.saveClose"),
                        IconView.create(IconView.Type.SAVE, 16)
                );

        saveAndCloseButton.getStyleClass().add(
                "primary-button"
        );

        saveAndCloseButton.setOnAction(
                event -> {
                    if (saveSettings()) {
                        onBack.run();
                    }
                }
        );

        // ---------------------------------------------------------
        // CONTENT
        // ---------------------------------------------------------

        VBox content =
                new VBox(
                        24,

                        header,

                        memoryTitle,
                        memoryCard,

                        javaTitle,
                        javaCard,

                        gameTitle,
                        gameCard,

                        loaderStackTitle,
                        loaderStackCard,

                        directoryTitle,
                        directoryCard
                );

        content.setPadding(
                new Insets(36)
        );

        content.setFillWidth(
                true
        );

        // ---------------------------------------------------------
        // SCROLL
        // ---------------------------------------------------------

        ScrollPane scrollPane =
                new ScrollPane(
                        content
                );

        scrollPane.setFitToWidth(
                true
        );

        scrollPane.setFitToHeight(
                false
        );

        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        scrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        scrollPane.getStyleClass().add(
                "settings-scroll"
        );

        VBox.setVgrow(
                scrollPane,
                Priority.ALWAYS
        );

        HBox footer =
                new HBox(
                        10,
                        statusLabel,
                        cancelButton,
                        saveButton,
                        saveAndCloseButton
                );

        footer.setAlignment(
                Pos.CENTER_RIGHT
        );

        footer.setPadding(
                new Insets(0, 36, 24, 36)
        );

        footer.getStyleClass().add(
                "instance-settings-footer"
        );

        HBox.setHgrow(
                statusLabel,
                Priority.ALWAYS
        );

        getChildren().addAll(
                scrollPane,
                footer
        );

        loadSettings();
    }

    // =============================================================
    // LOAD
    // =============================================================

    private void loadSettings() {

        try {

            InstanceSettings settings =
                    InstanceManager.loadSettings(
                            instance
                    );

            int ram =
                    settings.getRamMb();

            ramSlider.setValue(
                    clampRam(
                            ram
                    )
            );

            ramValueLabel.setText(
                    snapRam(
                            ramSlider.getValue()
                    ) + " MB"
            );

            widthField.setText(
                    String.valueOf(
                            settings.getWidth()
                    )
            );

            heightField.setText(
                    String.valueOf(
                            settings.getHeight()
                    )
            );

            javaPathField.setText(
                    settings.getJavaPath()
            );

            javaArgumentsArea.setText(
                    settings.getJavaArguments()
            );

            gameArgumentsArea.setText(
                    settings.getGameArguments()
            );

            fullscreenCheck.setSelected(
                    settings.isFullscreen()
            );

            loaderStackLayers.clear();
            loaderStackTweakersArea.clear();
            LoaderStackConfiguration stack = LoaderStackManager.load(instance);
            loaderStackConfigured = stack != null;
            if (stack != null) {
                for (LoaderStackConfiguration.Layer layer : stack.getOrderedLayers()) {
                    if (layer.type() == LoaderStackConfiguration.LayerType.PROFILE_OVERLAY) {
                        loaderStackLayers.add(layer);
                    } else if (layer.type() == LoaderStackConfiguration.LayerType.TWEAKER) {
                        loaderStackTweakersArea.appendText(
                                String.join(System.lineSeparator(), layer.launchTweakers())
                                        + System.lineSeparator());
                    }
                }
            }
            refreshLoaderProfileList();

            statusLabel.setText(
                    Localization.text("instanceSettings.loaded")
            );

        } catch (Exception e) {

            e.printStackTrace();

            statusLabel.setText(
                    Localization.text("instanceSettings.loadFailed")
            );
        }
    }

    // =============================================================
    // SAVE
    // =============================================================

    private boolean saveSettings() {

        try {

            int ram =
                    snapRam(
                            ramSlider.getValue()
                    );

            int width =
                    parsePositive(
                            widthField.getText(),
                            "Width"
                    );

            int height =
                    parsePositive(
                            heightField.getText(),
                            "Height"
                    );

            InstanceSettings settings =
                    new InstanceSettings();

            settings.setRamMb(
                    ram
            );

            settings.setWidth(
                    width
            );

            settings.setHeight(
                    height
            );

            settings.setJavaPath(
                    javaPathField.getText()
            );

            settings.setJavaArguments(
                    javaArgumentsArea.getText()
            );

            settings.setGameArguments(
                    gameArgumentsArea.getText()
            );

            settings.setFullscreen(
                    fullscreenCheck.isSelected()
            );

            InstanceManager.saveSettings(
                    instance,
                    settings
            );

            List<LoaderStackConfiguration.Layer> configuredLayers = new ArrayList<>(loaderStackLayers);
            List<String> tweakers = loaderStackTweakersArea.getText().lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .toList();
            if (!tweakers.isEmpty()) {
                configuredLayers.add(new LoaderStackConfiguration.Layer(
                        "manual-tweakers", "custom", LoaderStackConfiguration.LayerType.TWEAKER,
                        configuredLayers.size() * 10 + 10, tweakers));
            }
            if (configuredLayers.isEmpty()) {
                if (loaderStackConfigured) {
                    LoaderStackManager.clear(instance);
                    loaderStackConfigured = false;
                }
            } else {
                LoaderStackManager.apply(instance, new LoaderStackConfiguration(configuredLayers));
                loaderStackConfigured = true;
            }

            statusLabel.setText(
                    Localization.text("instanceSettings.saved")
            );

            return true;

        } catch (NumberFormatException e) {

            statusLabel.setText(
                    e.getMessage()
            );

            return false;

        } catch (Exception e) {

            e.printStackTrace();

            statusLabel.setText(
                    Localization.text("instanceSettings.saveFailed")
            );

            return false;
        }
    }

    private void addLoaderProfiles() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Localization.text("instanceSettings.selectProfileFiles"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Launcher profile JSON", "*.json"));
        List<File> selected = chooser.showOpenMultipleDialog(
                getScene() == null ? null : getScene().getWindow());
        if (selected == null || selected.isEmpty()) {
            return;
        }

        try {
            Path profileDirectory = instance.getDirectory().resolve("loader-profiles");
            Files.createDirectories(profileDirectory);
            for (File source : selected) {
                JsonNode profile = LOADER_PROFILE_MAPPER.readTree(source);
                if (profile == null || !profile.isObject()
                        || profile.path("libraries").isMissingNode()
                        || !profile.path("libraries").isArray()) {
                    throw new IOException("Invalid launcher profile: " + source.getName());
                }

                String id = "profile-" + UUID.randomUUID().toString().substring(0, 8);
                String relativePath = "loader-profiles/" + id + ".json";
                Path destination = profileDirectory.resolve(id + ".json");
                Files.copy(source.toPath(), destination, StandardCopyOption.REPLACE_EXISTING);

                loaderStackLayers.add(new LoaderStackConfiguration.Layer(
                        id, "imported", LoaderStackConfiguration.LayerType.PROFILE_OVERLAY,
                        loaderStackLayers.size() * 10, List.of(), relativePath));
            }
            refreshLoaderProfileList();
            statusLabel.setText(Localization.text("instanceSettings.addedPrefix") + selected.size() + " profile overlay(s). Save to apply.");
        } catch (Exception e) {
            statusLabel.setText(Localization.text("instanceSettings.addProfileFailedPrefix") + e.getMessage());
        }
    }

    private void refreshLoaderProfileList() {
        if (loaderStackProfilesArea == null) {
            return;
        }
        loaderStackProfilesArea.setText(loaderStackLayers.stream()
                .map(layer -> layer.id() + "  ·  " + layer.profileFile())
                .reduce((left, right) -> left + System.lineSeparator() + right)
                .orElse(""));
    }

    // =============================================================
    // RAM
    // =============================================================

    private int snapRam(
            double value
    ) {

        int ram =
                (int) Math.round(
                        value / 512.0
                ) * 512;

        return clampRam(
                ram
        );
    }

    private int clampRam(
            int ram
    ) {

        return Math.max(
                1024,
                Math.min(
                        16384,
                        ram
                )
        );
    }

    // =============================================================
    // VALIDATION
    // =============================================================

    private int parsePositive(
            String value,
            String name
    ) {

        try {

            int number =
                    Integer.parseInt(
                            value.trim()
                    );

            if (number <= 0) {

                throw new NumberFormatException(
                        name + " must be greater than 0."
                );
            }

            return number;

        } catch (NumberFormatException e) {

            if (e.getMessage() != null
                    && !e.getMessage().startsWith(
                    "For input string"
            )) {

                throw e;
            }

            throw new NumberFormatException(
                    name + " must be a valid number."
            );
        }
    }

    // =============================================================
    // HELPERS
    // =============================================================

    private Label createSectionTitle(
            String text
    ) {

        Label label =
                new Label(
                        text
                );

        label.getStyleClass().add(
                "settings-section-title"
        );

        return label;
    }

    private VBox createCard(
            javafx.scene.Node... nodes
    ) {

        VBox card =
                new VBox(
                        16,
                        nodes
                );

        card.setPadding(
                new Insets(22)
        );

        card.getStyleClass().add(
                "instance-settings-card"
        );

        return card;
    }

    private VBox createInput(
            String labelText,
            javafx.scene.Node input
    ) {

        Label label =
                new Label(
                        labelText
                );

        label.getStyleClass().add(
                "instance-setting-label"
        );

        VBox box =
                new VBox(
                        6,
                        label,
                        input
                );

        VBox.setVgrow(
                input,
                Priority.NEVER
        );

        return box;
    }
}