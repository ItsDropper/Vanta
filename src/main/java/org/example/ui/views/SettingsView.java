package org.example.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import org.example.launcher.MinecraftLocator;
import org.example.launcher.instance.InstanceManager;
import org.example.ui.LauncherSettings;
import org.example.ui.ThemeManager;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

public class SettingsView extends BorderPane {

    private final Consumer<String> onAccentChanged;
    private final VBox navigation = new VBox(4);
    private final VBox content = new VBox(22);

    private ColorPicker accentPicker;
    private TextField accentHexField;
    private Label accentValue;
    private Region accentPreview;
    private String accentColor;

    public SettingsView(Consumer<String> onAccentChanged) {
        this.onAccentChanged = onAccentChanged;
        this.accentColor = ThemeManager.loadAccent();

        getStyleClass().add("settings-shell");

        buildNavigation();

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("settings-scroll");

        setLeft(navigation);
        setCenter(scroll);
        BorderPane.setMargin(navigation, new Insets(0, 22, 0, 0));

        selectSection("Appearance");
        applyAccent(accentColor);
        onAccentChanged.accept(accentColor);
    }

    private void buildNavigation() {
        navigation.getStyleClass().add("settings-navigation");

        navigation.getChildren().add(group("PERSONALIZATION"));
        navigation.getChildren().add(sectionButton("Appearance", true));

        navigation.getChildren().add(group("SOCIAL"));
        navigation.getChildren().add(sectionButton("Discord", false));

        navigation.getChildren().add(group("LAUNCHER"));
        navigation.getChildren().addAll(
                sectionButton("General", false),
                sectionButton("Minecraft", false),
                sectionButton("Downloads", false)
        );

        navigation.getChildren().add(group("SYSTEM"));
        navigation.getChildren().addAll(
                sectionButton("Repair & Diagnostics", false),
                sectionButton("About Vanta", false)
        );
    }

    private Label group(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("settings-nav-group");
        return label;
    }

    private Button sectionButton(String text, boolean selected) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setAlignment(Pos.CENTER_LEFT);
        button.getStyleClass().add("settings-nav-button");

        if (selected) {
            button.getStyleClass().add("selected");
        }

        button.setOnAction(event -> selectSection(text));
        return button;
    }

    private void selectSection(String section) {
        content.getChildren().clear();

        switch (section) {
            case "Appearance" -> buildAppearancePage();
            case "Discord" -> buildDiscordPage();
            case "General" -> buildGeneralPage();
            case "Minecraft" -> buildMinecraftPage();
            case "Downloads" -> buildDownloadsPage();
            case "Repair & Diagnostics" -> buildDiagnosticsPage();
            case "About Vanta" -> buildAboutPage();
            default -> buildPlaceholderPage(section);
        }

        for (var node : navigation.getChildren()) {
            if (node instanceof Button button) {
                button.getStyleClass().remove("selected");
                if (button.getText().equals(section)) {
                    button.getStyleClass().add("selected");
                }
            }
        }
    }

    private void buildAppearancePage() {
        pageHeader(
                "Appearance",
                "Control Vanta's visual language. Changes apply immediately and persist across launches."
        );

        VBox accentCard = card(
                "Accent color",
                "Used for active navigation, controls, focus states, selections and progress indicators."
        );

        accentPreview = new Region();
        accentPreview.getStyleClass().add("accent-preview");

        Label previewText = new Label("LIVE PREVIEW");
        previewText.getStyleClass().add("accent-preview-label");

        StackPaneLike preview = new StackPaneLike(accentPreview, previewText);
        preview.setMinHeight(72);
        preview.setPrefHeight(72);
        preview.setMaxWidth(Double.MAX_VALUE);

        accentPicker = new ColorPicker(Color.web(accentColor));
        accentPicker.getStyleClass().add("accent-picker");
        accentPicker.setTooltip(new javafx.scene.control.Tooltip("Choose an accent color"));

        accentHexField = new TextField(accentColor);
        accentHexField.getStyleClass().add("accent-hex-field");
        accentHexField.setPromptText("#5688ED");
        accentHexField.setPrefWidth(110);

        Button applyHex = new Button("APPLY");
        applyHex.getStyleClass().add("primary-button");
        applyHex.setOnAction(event -> applyHexField());

        accentValue = new Label(accentColor);
        accentValue.getStyleClass().add("accent-value");

        accentPicker.valueProperty().addListener(
                (observable, oldColor, newColor) -> {
                    if (newColor != null) {
                        setAccent(toHex(newColor));
                    }
                }
        );

        HBox pickerRow = new HBox(
                10,
                new Label("Picker"),
                accentPicker,
                new Label("HEX"),
                accentHexField,
                applyHex
        );
        pickerRow.setAlignment(Pos.CENTER_LEFT);

        Label rgb = new Label();
        rgb.getStyleClass().add("settings-card-description");

        accentCard.getChildren().addAll(
                preview,
                pickerRow,
                accentValue,
                rgb
        );

        updateAccentPreview(rgb);

        VBox presetsCard = card(
                "Presets",
                "Use a preset as a starting point, then fine-tune it with the picker or HEX field."
        );

        HBox presets = new HBox(8);
        addPreset(presets, "Vanta Blue", "#5688ED");
        addPreset(presets, "Cyan", "#42C6E8");
        addPreset(presets, "Emerald", "#4FD18B");
        addPreset(presets, "Violet", "#8B7CFF");
        addPreset(presets, "Amber", "#F0B85B");
        addPreset(presets, "Rose", "#EF7187");
        presetsCard.getChildren().add(presets);

        Button reset = new Button("RESET TO VANTA BLUE");
        reset.getStyleClass().add("secondary-button");
        reset.setOnAction(event -> setAccent(ThemeManager.DEFAULT_ACCENT));

        content.getChildren().addAll(accentCard, presetsCard, reset);
    }

    private void applyHexField() {
        String value = accentHexField.getText().trim();
        if (ThemeManager.isValidAccent(value)) {
            setAccent(value);
        } else {
            accentHexField.setText(accentColor);
            accentHexField.selectAll();
        }
    }

    private void updateAccentPreview(Label rgbLabel) {
        if (accentPreview != null) {
            accentPreview.setStyle(
                    "-fx-background-color: " + accentColor + ";"
                            + "-fx-background-radius: 12px;"
                            + "-fx-border-color: rgba(255,255,255,0.18);"
                            + "-fx-border-radius: 12px;"
            );
        }

        if (accentHexField != null && !accentHexField.getText().equals(accentColor)) {
            accentHexField.setText(accentColor);
        }

        if (rgbLabel != null) {
            Color color = Color.web(accentColor);
            rgbLabel.setText(
                    "RGB "
                            + Math.round(color.getRed() * 255)
                            + ", "
                            + Math.round(color.getGreen() * 255)
                            + ", "
                            + Math.round(color.getBlue() * 255)
            );
        }
    }

    private void addPreset(HBox container, String name, String hex) {
        Button button = new Button(name);
        button.getStyleClass().add("accent-preset");
        button.setStyle("-vanta-preset-color: " + hex + ";");
        button.setOnAction(event -> setAccent(hex));
        container.getChildren().add(button);
    }

    private void setAccent(String hex) {
        if (!ThemeManager.isValidAccent(hex)) {
            return;
        }

        accentColor = hex.toUpperCase();

        if (accentPicker != null
                && !accentPicker.getValue().equals(Color.web(accentColor))) {
            accentPicker.setValue(Color.web(accentColor));
        }

        if (accentValue != null) {
            accentValue.setText(accentColor);
        }

        applyAccent(accentColor);
        ThemeManager.saveAccent(accentColor);
        onAccentChanged.accept(accentColor);

        updateAccentPreview(null);
    }

    private void buildDiscordPage() {
        pageHeader(
                "Discord",
                "Show what you are playing in Discord with a configurable Rich Presence."
        );

        VBox connection = card(
                "Rich Presence",
                "Vanta connects to the Discord desktop app locally. You provide the Discord application Client ID."
        );

        CheckBox enabled = new CheckBox("Enable Discord Rich Presence");
        enabled.setSelected(LauncherSettings.isDiscordPresenceEnabled());
        enabled.getStyleClass().add("settings-checkbox");
        enabled.setOnAction(event ->
                LauncherSettings.setDiscordPresenceEnabled(enabled.isSelected())
        );

        TextField clientId = new TextField(
                LauncherSettings.getDiscordClientId()
        );
        clientId.setPromptText("Discord application Client ID");
        clientId.getStyleClass().add("create-field");
        clientId.setMaxWidth(Double.MAX_VALUE);

        Button saveClient = new Button("SAVE CLIENT ID");
        saveClient.getStyleClass().add("primary-button");
        saveClient.setOnAction(event ->
                LauncherSettings.setDiscordClientId(clientId.getText())
        );

        connection.getChildren().addAll(
                enabled,
                createInput("Client ID", clientId),
                saveClient
        );

        VBox activity = card(
                "Activity details",
                "Choose what Vanta puts into the two text lines of the Discord activity."
        );

        CheckBox showInstance = new CheckBox("Show instance name");
        showInstance.setSelected(LauncherSettings.isDiscordShowInstanceEnabled());
        showInstance.getStyleClass().add("settings-checkbox");
        showInstance.setOnAction(event ->
                LauncherSettings.setDiscordShowInstanceEnabled(showInstance.isSelected())
        );

        CheckBox showPlaytime = new CheckBox("Show total instance playtime");
        showPlaytime.setSelected(LauncherSettings.isDiscordShowPlaytimeEnabled());
        showPlaytime.getStyleClass().add("settings-checkbox");
        showPlaytime.setOnAction(event ->
                LauncherSettings.setDiscordShowPlaytimeEnabled(showPlaytime.isSelected())
        );

        activity.getChildren().addAll(showInstance, showPlaytime);

        VBox note = card(
                "Setup",
                "Create a Discord application, copy its Application ID into Client ID, then enable Rich Presence. Vanta does not send your Discord token or account credentials."
        );

        content.getChildren().addAll(connection, activity, note);
    }

    private void buildGeneralPage() {
        pageHeader(
                "General",
                "Launcher behavior and interaction preferences."
        );

        VBox behavior = card(
                "Launcher behavior",
                "These options affect the launcher itself rather than individual Minecraft instances."
        );

        CheckBox animations = new CheckBox("Use interface animations");
        animations.setSelected(LauncherSettings.isAnimationsEnabled());
        animations.getStyleClass().add("settings-checkbox");
        animations.setOnAction(event ->
                LauncherSettings.setAnimationsEnabled(animations.isSelected())
        );

        CheckBox updates = new CheckBox("Check for launcher updates on startup");
        updates.setSelected(LauncherSettings.isUpdateChecksEnabled());
        updates.getStyleClass().add("settings-checkbox");
        updates.setOnAction(event ->
                LauncherSettings.setUpdateChecksEnabled(updates.isSelected())
        );

        CheckBox confirmations = new CheckBox("Confirm destructive content actions");
        confirmations.setSelected(LauncherSettings.isConfirmRemovalsEnabled());
        confirmations.getStyleClass().add("settings-checkbox");
        confirmations.setOnAction(event ->
                LauncherSettings.setConfirmRemovalsEnabled(confirmations.isSelected())
        );

        behavior.getChildren().addAll(animations, updates, confirmations);

        VBox dataCard = card(
                "Vanta data",
                "Launcher-owned configuration is kept locally in the Vanta directory."
        );

        Path vantaDirectory = MinecraftLocator.getVantaDirectory();
        addInfoRow(dataCard, "Data directory", vantaDirectory.toString());
        addInfoRow(dataCard, "Settings", vantaDirectory.resolve("settings.properties").toString());

        dataCard.getChildren().add(
                actionButton("OPEN VANTA FOLDER", () -> openDirectory(vantaDirectory))
        );

        VBox runtimeCard = card(
                "Runtime",
                "The Java runtime currently executing Vanta."
        );

        addInfoRow(runtimeCard, "Java", System.getProperty("java.version", "Unknown"));
        addInfoRow(runtimeCard, "Java home", System.getProperty("java.home", "Unknown"));

        content.getChildren().addAll(behavior, dataCard, runtimeCard);
    }

    private void buildMinecraftPage() {
        pageHeader(
                "Minecraft",
                "Set defaults used when Vanta creates a new instance. Existing instances keep their own settings."
        );

        VBox defaults = card(
                "New instance defaults",
                "These values are copied into settings.json when a new instance is created."
        );

        Slider ram = new Slider(1024, 16384, LauncherSettings.getDefaultRamMb());
        ram.setBlockIncrement(512);
        ram.setMajorTickUnit(4096);
        ram.setMinorTickCount(7);
        ram.setSnapToTicks(true);
        ram.setShowTickMarks(true);
        ram.setMaxWidth(Double.MAX_VALUE);

        Label ramValue = new Label();
        ramValue.getStyleClass().add("instance-setting-value");
        Runnable updateRam = () ->
                ramValue.setText(((int) Math.round(ram.getValue() / 512.0) * 512) + " MB");
        ram.valueProperty().addListener((o, oldValue, newValue) -> updateRam.run());
        updateRam.run();

        TextField width = new TextField(String.valueOf(LauncherSettings.getDefaultWidth()));
        width.getStyleClass().add("create-field");

        TextField height = new TextField(String.valueOf(LauncherSettings.getDefaultHeight()));
        height.getStyleClass().add("create-field");

        CheckBox fullscreen = new CheckBox("Start new instances in fullscreen");
        fullscreen.setSelected(LauncherSettings.isDefaultFullscreen());
        fullscreen.getStyleClass().add("settings-checkbox");

        Button save = new Button("SAVE MINECRAFT DEFAULTS");
        save.getStyleClass().add("primary-button");
        save.setOnAction(event -> {
            try {
                int ramMb = (int) Math.round(ram.getValue() / 512.0) * 512;
                int w = Integer.parseInt(width.getText().trim());
                int h = Integer.parseInt(height.getText().trim());

                if (w < 640 || h < 480) {
                    throw new NumberFormatException();
                }

                LauncherSettings.setDefaultRamMb(ramMb);
                LauncherSettings.setDefaultWidth(w);
                LauncherSettings.setDefaultHeight(h);
                LauncherSettings.setDefaultFullscreen(fullscreen.isSelected());
            } catch (NumberFormatException ignored) {
                width.setText(String.valueOf(LauncherSettings.getDefaultWidth()));
                height.setText(String.valueOf(LauncherSettings.getDefaultHeight()));
            }
        });

        defaults.getChildren().addAll(
                new Label("Memory"),
                ramValue,
                ram,
                createInput("Resolution width", width),
                createInput("Resolution height", height),
                fullscreen,
                save
        );

        VBox paths = card(
                "Minecraft storage",
                "Shared launcher data is separate from each instance."
        );

        Path instances = MinecraftLocator.getInstancesDirectory();
        Path libraries = MinecraftLocator.getLibrariesDirectory();

        addInfoRow(paths, "Instances", instances.toString());
        addInfoRow(paths, "Libraries", libraries.toString());

        paths.getChildren().addAll(
                actionButton("OPEN INSTANCES", () -> openDirectory(instances)),
                actionButton("OPEN LIBRARIES", () -> openDirectory(libraries))
        );

        content.getChildren().addAll(defaults, paths);
    }

    private void buildDownloadsPage() {
        pageHeader(
                "Downloads",
                "Tune how aggressively Vanta downloads Minecraft assets."
        );

        VBox workers = card(
                "Download workers",
                "More workers can improve throughput on fast connections; fewer workers reduce concurrent network and disk activity."
        );

        Slider slider = new Slider(1, 16, LauncherSettings.getDownloadThreads());
        slider.setMajorTickUnit(1);
        slider.setMinorTickCount(0);
        slider.setSnapToTicks(true);
        slider.setShowTickLabels(true);
        slider.setShowTickMarks(true);
        slider.setMaxWidth(Double.MAX_VALUE);

        Label value = new Label();
        value.getStyleClass().add("instance-setting-value");
        Runnable update = () -> value.setText(
                String.valueOf((int) Math.round(slider.getValue())) + " workers"
        );
        slider.valueProperty().addListener((o, oldValue, newValue) -> update.run());
        update.run();

        Button save = new Button("SAVE DOWNLOAD SETTINGS");
        save.getStyleClass().add("primary-button");
        save.setOnAction(event ->
                LauncherSettings.setDownloadThreads((int) Math.round(slider.getValue()))
        );

        workers.getChildren().addAll(value, slider, save);

        VBox layout = card(
                "Storage",
                "These locations are managed by Vanta and can be opened directly."
        );

        addInfoRow(layout, "Instances", MinecraftLocator.getInstancesDirectory().toString());
        addInfoRow(layout, "Libraries", MinecraftLocator.getLibrariesDirectory().toString());
        addInfoRow(layout, "Launcher data", MinecraftLocator.getVantaDirectory().toString());

        layout.getChildren().add(
                new Label(
                        "Vanta verifies downloaded Minecraft assets with SHA-1 before installing them."
                )
        );
        layout.getChildren().get(layout.getChildren().size() - 1)
                .getStyleClass().add("settings-card-description");

        content.getChildren().addAll(workers, layout);
    }

    private void buildDiagnosticsPage() {
        pageHeader(
                "Repair & Diagnostics",
                "Inspect launcher state and control how destructive actions behave."
        );

        VBox health = card(
                "Environment",
                "Basic local state available without modifying an instance."
        );

        int instanceCount = 0;
        try {
            instanceCount = InstanceManager.discoverInstances().size();
        } catch (Throwable ignored) {
        }

        addInfoRow(health, "Discovered instances", String.valueOf(instanceCount));
        addInfoRow(health, "Vanta directory", MinecraftLocator.getVantaDirectory().toString());

        CheckBox confirmations = new CheckBox("Confirm mod removals");
        confirmations.setSelected(LauncherSettings.isConfirmRemovalsEnabled());
        confirmations.getStyleClass().add("settings-checkbox");
        confirmations.setOnAction(event ->
                LauncherSettings.setConfirmRemovalsEnabled(confirmations.isSelected())
        );

        Button resetIntro = new Button("RESET INTRODUCTION");
        resetIntro.getStyleClass().add("secondary-button");
        resetIntro.setOnAction(event -> LauncherSettings.resetOnboarding());

        health.getChildren().addAll(
                confirmations,
                resetIntro,
                actionButton(
                        "OPEN DATA FOLDER",
                        () -> openDirectory(MinecraftLocator.getVantaDirectory())
                )
        );

        VBox safety = card(
                "What diagnostics change",
                "The launcher does not silently rewrite files from this page. Instance dependency repair remains available from the instance repair flow."
        );

        content.getChildren().addAll(health, safety);
    }

    private void buildAboutPage() {
        pageHeader(
                "About Vanta",
                "Launcher information and local configuration status."
        );

        VBox about = card(
                "Vanta",
                "Modern launcher UI, isolated instances, Modrinth integration and automatic dependency handling."
        );

        addInfoRow(about, "Version", loadVersion());
        addInfoRow(about, "UI", "JavaFX");
        addInfoRow(about, "Content", "Modrinth");
        addInfoRow(about, "Telemetry", "Not required");

        Label privacy = new Label(
                "Vanta keeps launcher settings local. Minecraft authentication is handled through the existing Microsoft authentication flow."
        );
        privacy.getStyleClass().add("settings-card-description");
        privacy.setWrapText(true);

        about.getChildren().add(privacy);
        content.getChildren().add(about);
    }

    private void pageHeader(String title, String subtitle) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("settings-page-title");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("settings-page-subtitle");
        subtitleLabel.setWrapText(true);

        content.getChildren().addAll(titleLabel, subtitleLabel);
    }

    private VBox card(String title, String description) {
        VBox card = new VBox(14);
        card.getStyleClass().add("settings-card");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("settings-card-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("settings-card-description");
        descriptionLabel.setWrapText(true);

        card.getChildren().addAll(titleLabel, descriptionLabel);
        return card;
    }

    private void addInfoRow(VBox parent, String label, String value) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);

        Label key = new Label(label);
        key.getStyleClass().add("settings-info-label");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("settings-info-value");
        valueLabel.setWrapText(true);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        row.getChildren().addAll(key, spacer, valueLabel);
        parent.getChildren().add(row);
    }

    private Button actionButton(String text, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().add("secondary-button");
        button.setOnAction(event -> action.run());
        return button;
    }

    private VBox createInput(String labelText, javafx.scene.Node input) {
        Label label = new Label(labelText);
        label.getStyleClass().add("instance-setting-label");
        return new VBox(6, label, input);
    }

    private void openDirectory(Path directory) {
        try {
            Files.createDirectories(directory);
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(directory.toFile());
            }
        } catch (Exception ignored) {
        }
    }

    private String loadVersion() {
        Properties properties = new Properties();

        try (InputStream stream = getClass().getResourceAsStream("/version.properties")) {
            if (stream != null) {
                properties.load(stream);
                String version = properties.getProperty("version");
                if (version != null && !version.contains("${")) {
                    return version;
                }
            }
        } catch (IOException ignored) {
        }

        return "1.1.8";
    }

    private void buildPlaceholderPage(String section) {
        pageHeader(section, "This section is ready for Vanta's next settings modules.");
    }

    private void applyAccent(String hex) {
        ThemeManager.apply(this, hex);
    }

    private static String toHex(Color color) {
        return String.format(
                "#%02X%02X%02X",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255)
        );
    }

    private static final class StackPaneLike extends javafx.scene.layout.StackPane {
        private StackPaneLike(javafx.scene.Node... nodes) {
            getChildren().addAll(nodes);
        }
    }
}
