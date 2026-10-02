package org.example.ui.views;

import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;\nimport java.util.function.Consumer;
import javafx.scene.paint.Color;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public class SettingsView extends BorderPane {\n\n    private final Consumer<String> onAccentChanged;

    private static final String DEFAULT_ACCENT = "#5688ED";
    private static final Path SETTINGS_FILE = Path.of(
            System.getProperty("user.home"),
            ".vanta",
            "settings.properties"
    );

    private final VBox navigation = new VBox(4);
    private final VBox content = new VBox(22);

    private ColorPicker accentPicker;
    private String accentColor;

    public SettingsView(Consumer<String> onAccentChanged) {\n        this.onAccentChanged = onAccentChanged;
        getStyleClass().add("settings-shell");

        loadSettings();
        buildNavigation();

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("settings-scroll");

        setLeft(navigation);
        setCenter(scroll);
        BorderPane.setMargin(navigation, new Insets(0, 22, 0, 0));

        selectSection("Appearance");\n        applyAccent(accentColor);
    }

    private void loadSettings() {
        accentColor = DEFAULT_ACCENT;

        if (!Files.exists(SETTINGS_FILE)) {
            return;
        }

        Properties properties = new Properties();

        try (var reader = Files.newBufferedReader(SETTINGS_FILE)) {
            properties.load(reader);

            String saved = properties.getProperty("accentColor");
            if (saved != null && saved.matches("#[0-9a-fA-F]{6}")) {
                accentColor = saved.toUpperCase();
            }
        } catch (IOException ignored) {
        }
    }

    private void saveSettings() {
        Properties properties = new Properties();
        properties.setProperty("accentColor", accentColor);

        try {
            Files.createDirectories(SETTINGS_FILE.getParent());

            try (var writer = Files.newBufferedWriter(SETTINGS_FILE)) {
                properties.store(writer, "Vanta settings");
            }
        } catch (IOException ignored) {
        }
    }

    private void buildNavigation() {
        navigation.getStyleClass().add("settings-navigation");

        Label appearanceGroup = group("PERSONALIZATION");
        navigation.getChildren().add(appearanceGroup);

        navigation.getChildren().add(
                sectionButton("Appearance", true)
        );

        Label launcherGroup = group("LAUNCHER");
        navigation.getChildren().add(launcherGroup);

        navigation.getChildren().addAll(
                sectionButton("General", false),
                sectionButton("Minecraft", false),
                sectionButton("Downloads", false)
        );

        Label supportGroup = group("SYSTEM");
        navigation.getChildren().add(supportGroup);

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

    private javafx.scene.control.Button sectionButton(
            String text,
            boolean selected
    ) {
        var button = new javafx.scene.control.Button(text);
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

        if (section.equals("Appearance")) {
            buildAppearancePage();
        } else {
            buildPlaceholderPage(section);
        }

        for (var node : navigation.getChildren()) {
            if (node instanceof javafx.scene.control.Button button) {
                button.getStyleClass().remove("selected");
                if (button.getText().equals(section)) {
                    button.getStyleClass().add("selected");
                }
            }
        }
    }

    private void buildAppearancePage() {
        Label title = new Label("Appearance");
        title.getStyleClass().add("settings-page-title");

        Label subtitle = new Label(
                "Customize how Vanta looks and feels."
        );
        subtitle.getStyleClass().add("settings-page-subtitle");

        VBox card = new VBox(14);
        card.getStyleClass().add("settings-card");

        Label cardTitle = new Label("Accent color");
        cardTitle.getStyleClass().add("settings-card-title");

        Label description = new Label(
                "Choose the color used for active navigation, buttons, focus states and progress."
        );
        description.getStyleClass().add("settings-card-description");
        description.setWrapText(true);

        accentPicker = new ColorPicker(Color.web(accentColor));
        accentPicker.getStyleClass().add("accent-picker");

        Label value = new Label(accentColor);
        value.getStyleClass().add("accent-value");

        ChangeListener<Color> listener = (observable, oldColor, newColor) -> {
            if (newColor == null) {
                return;
            }

            accentColor = toHex(newColor);
            value.setText(accentColor);
            applyAccent(accentColor);
            saveSettings();\n            onAccentChanged.accept(accentColor);
        };

        accentPicker.valueProperty().addListener(listener);

        HBox row = new HBox(16, accentPicker, value);
        row.setAlignment(Pos.CENTER_LEFT);

        card.getChildren().addAll(
                cardTitle,
                description,
                row
        );

        content.getChildren().addAll(
                title,
                subtitle,
                card
        );
    }

    private void buildPlaceholderPage(String section) {
        Label title = new Label(section);
        title.getStyleClass().add("settings-page-title");

        Label subtitle = new Label(
                "This section is ready for Vanta's next settings modules."
        );
        subtitle.getStyleClass().add("settings-page-subtitle");

        content.getChildren().addAll(title, subtitle);
    }

    private void applyAccent(String hex) {
        String rgb = rgb(hex);

        getSceneStylesheets();
        getStyleClass().removeIf(style -> style.startsWith("accent-"));

        setStyle(
                "-vanta-accent: " + hex + ";" +
                "-vanta-accent-rgb: " + rgb + ";"
        );
    }

    private static String toHex(Color color) {
        return String.format(
                "#%02X%02X%02X",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255)
        );
    }

    private static String rgb(String hex) {
        int r = Integer.parseInt(hex.substring(1, 3), 16);
        int g = Integer.parseInt(hex.substring(3, 5), 16);
        int b = Integer.parseInt(hex.substring(5, 7), 16);
        return r + "," + g + "," + b;
    }
}
