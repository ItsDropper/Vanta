package org.example.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import org.example.launcher.MinecraftLocator;
import org.example.launcher.instance.InstanceManager;
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
    private Label accentValue;
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

        // Apply the persisted theme immediately on startup.
        onAccentChanged.accept(accentColor);
    }

    private void buildNavigation() {
        navigation.getStyleClass().add("settings-navigation");

        navigation.getChildren().add(group("PERSONALIZATION"));
        navigation.getChildren().add(sectionButton("Appearance", true));

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
                "Make Vanta yours without changing how the launcher works."
        );

        VBox accentCard = card(
                "Accent color",
                "The accent is used across navigation, active controls, focus states, selections, progress bars and launcher actions."
        );

        accentPicker = new ColorPicker(Color.web(accentColor));
        accentPicker.getStyleClass().add("accent-picker");

        accentValue = new Label(accentColor);
        accentValue.getStyleClass().add("accent-value");

        accentPicker.valueProperty().addListener(
                (observable, oldColor, newColor) -> {
                    if (newColor != null) {
                        setAccent(toHex(newColor));
                    }
                }
        );

        HBox pickerRow = new HBox(12, accentPicker, accentValue);
        pickerRow.setAlignment(Pos.CENTER_LEFT);
        accentCard.getChildren().add(pickerRow);

        VBox presetsCard = card(
                "Presets",
                "Quickly switch between Vanta's built-in accent palettes."
        );

        HBox presets = new HBox(8);

        addPreset(presets, "Vanta Blue", "#5688ED");
        addPreset(presets, "Cyan", "#42C6E8");
        addPreset(presets, "Emerald", "#4FD18B");
        addPreset(presets, "Violet", "#8B7CFF");
        addPreset(presets, "Amber", "#F0B85B");
        addPreset(presets, "Rose", "#EF7187");

        presetsCard.getChildren().add(presets);

        Button reset = new Button("RESET APPEARANCE");
        reset.getStyleClass().add("secondary-button");
        reset.setOnAction(event -> setAccent(ThemeManager.DEFAULT_ACCENT));

        content.getChildren().addAll(
                accentCard,
                presetsCard,
                reset
        );
    }

    private void addPreset(HBox container, String name, String hex) {
        Button button = new Button(name);
        button.getStyleClass().add("accent-preset");
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
    }

    private void buildGeneralPage() {
        pageHeader(
                "General",
                "Launcher information and local storage."
        );

        Path vantaDirectory = MinecraftLocator.getVantaDirectory();

        VBox dataCard = card(
                "Vanta data",
                "All launcher-owned data is kept locally in the Vanta directory."
        );

        addInfoRow(dataCard, "Data directory", vantaDirectory.toString());
        addInfoRow(
                dataCard,
                "Settings",
                vantaDirectory.resolve("settings.properties").toString()
        );

        dataCard.getChildren().add(
                actionButton(
                        "OPEN VANTA FOLDER",
                        () -> openDirectory(vantaDirectory)
                )
        );

        VBox runtimeCard = card(
                "Runtime",
                "The Java runtime currently executing Vanta."
        );

        addInfoRow(
                runtimeCard,
                "Java",
                System.getProperty("java.version", "Unknown")
        );

        addInfoRow(
                runtimeCard,
                "Java home",
                System.getProperty("java.home", "Unknown")
        );

        content.getChildren().addAll(dataCard, runtimeCard);
    }

    private void buildMinecraftPage() {
        pageHeader(
                "Minecraft",
                "See where Vanta keeps your Minecraft environments."
        );

        Path instances = MinecraftLocator.getInstancesDirectory();
        Path libraries = MinecraftLocator.getLibrariesDirectory();

        VBox instanceCard = card(
                "Instances",
                "Each Minecraft environment is isolated in its own directory."
        );

        addInfoRow(
                instanceCard,
                "Instances directory",
                instances.toString()
        );

        instanceCard.getChildren().add(
                actionButton(
                        "OPEN INSTANCES",
                        () -> openDirectory(instances)
                )
        );

        VBox libraryCard = card(
                "Shared libraries",
                "Launcher-managed libraries are shared between instances."
        );

        addInfoRow(
                libraryCard,
                "Libraries directory",
                libraries.toString()
        );

        libraryCard.getChildren().add(
                actionButton(
                        "OPEN LIBRARIES",
                        () -> openDirectory(libraries)
                )
        );

        content.getChildren().addAll(instanceCard, libraryCard);
    }

    private void buildDownloadsPage() {
        pageHeader(
                "Downloads",
                "Vanta keeps shared files separate from individual instances."
        );

        VBox layout = card(
                "Storage layout",
                "This is the local layout used by the launcher."
        );

        addInfoRow(
                layout,
                "Instances",
                MinecraftLocator.getInstancesDirectory().toString()
        );

        addInfoRow(
                layout,
                "Libraries",
                MinecraftLocator.getLibrariesDirectory().toString()
        );

        addInfoRow(
                layout,
                "Launcher data",
                MinecraftLocator.getVantaDirectory().toString()
        );

        Label note = new Label(
                "Vanta uses shared libraries so separate instances do not need duplicate copies of the same launcher-managed files."
        );

        note.getStyleClass().add("settings-card-description");
        note.setWrapText(true);

        layout.getChildren().add(note);
        content.getChildren().add(layout);
    }

    private void buildDiagnosticsPage() {
        pageHeader(
                "Repair & Diagnostics",
                "Useful local checks without changing your Minecraft files."
        );

        VBox health = card(
                "Environment",
                "Basic launcher state available without running a repair."
        );

        int instanceCount = 0;

        try {
            instanceCount = InstanceManager.discoverInstances().size();
        } catch (Throwable ignored) {
        }

        addInfoRow(
                health,
                "Discovered instances",
                String.valueOf(instanceCount)
        );

        addInfoRow(
                health,
                "Vanta directory",
                MinecraftLocator.getVantaDirectory().toString()
        );

        health.getChildren().add(
                actionButton(
                        "OPEN DATA FOLDER",
                        () -> openDirectory(
                                MinecraftLocator.getVantaDirectory()
                        )
                )
        );

        VBox safety = card(
                "Safe diagnostics",
                "Opening a folder does not modify files. Full dependency repair remains available through the instance repair flow."
        );

        content.getChildren().addAll(health, safety);
    }

    private void buildAboutPage() {
        pageHeader(
                "About Vanta",
                "A lightweight Minecraft launcher built around isolated environments."
        );

        VBox about = card(
                "Vanta",
                "Modern launcher UI, isolated instances, Modrinth integration and automatic dependency handling."
        );

        addInfoRow(about, "Version", loadVersion());
        addInfoRow(about, "UI", "JavaFX");
        addInfoRow(about, "Content", "Modrinth");

        Label privacy = new Label(
                "Vanta does not need telemetry to manage your local instances. Launcher-owned settings are stored locally."
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

    private void addInfoRow(
            VBox parent,
            String label,
            String value
    ) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);

        Label key = new Label(label);
        key.getStyleClass().add("settings-info-label");

        Label valueLabel = new Label(value);
        valueLabel.getStyleClass().add("settings-info-value");
        valueLabel.setWrapText(true);

        javafx.scene.layout.Region spacer =
                new javafx.scene.layout.Region();

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

        try (InputStream stream =
                     getClass().getResourceAsStream("/version.properties")) {

            if (stream != null) {
                properties.load(stream);

                String version =
                        properties.getProperty("version");

                if (version != null
                        && !version.contains("${")) {
                    return version;
                }
            }
        } catch (IOException ignored) {
        }

        return "1.1.8";
    }

    private void buildPlaceholderPage(String section) {
        pageHeader(
                section,
                "This section is ready for Vanta's next settings modules."
        );
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
}
