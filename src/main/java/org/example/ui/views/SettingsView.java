package org.example.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
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
import javafx.application.Platform;

import org.example.launcher.MinecraftLocator;
import org.example.launcher.account.Account;
import org.example.launcher.instance.InstanceManager;
import org.example.launcher.service.AccountService;
import org.example.ui.DebugAccess;
import org.example.ui.components.NotificationManager;
import org.example.ui.LauncherSettings;
import org.example.ui.ThemeManager;

import java.awt.Desktop;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import java.util.function.Consumer;

public class SettingsView extends BorderPane {

    private final AccountService accountService;
    private final Consumer<String> onAccentChanged;
    private final Runnable onDebugOnboarding;
    private final VBox navigation = new VBox(4);
    private final VBox content = new VBox(22);

    private ColorPicker accentPicker;
    private TextField accentHexField;
    private Label accentValue;
    private Region accentPreview;
    private String accentColor;
    private String currentSection = "Appearance";
    private boolean debugAccess;

    public SettingsView(
            AccountService accountService,
            Consumer<String> onAccentChanged,
            Runnable onDebugOnboarding
    ) {
        this.accountService = accountService;
        this.onAccentChanged = onAccentChanged;
        this.onDebugOnboarding = onDebugOnboarding;

        accountService.addListener(account -> refreshDebugAccess());
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
        currentSection = section;
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

        org.example.ui.AnimationUtils.installInteractiveAnimations(content);

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

        VBox themeCard = card(
                "Complete theme",
                "Themes control the launcher-wide palette: shell, sidebar, cards, controls, text, borders, status colors and overlays."
        );

        ComboBox<String> themeBox = new ComboBox<>();
        themeBox.getItems().addAll(ThemeManager.getThemes().keySet());
        themeBox.setValue(ThemeManager.loadThemeName());
        themeBox.setMaxWidth(Double.MAX_VALUE);
        themeBox.setPromptText("Choose a theme");

        Label themePreview = new Label();
        themePreview.getStyleClass().add("theme-preview-label");

        themeBox.setOnAction(event -> {
            String selected = themeBox.getValue();
            if (selected == null) return;

            ThemeManager.saveThemeName(selected);
            accentColor = ThemeManager.getTheme(selected).accent();
            ThemeManager.saveAccent(accentColor);
            applyAccent(accentColor);
            onAccentChanged.accept(accentColor);

            if (accentPicker != null) {
                accentPicker.setValue(Color.web(accentColor));
            }
            if (accentValue != null) {
                accentValue.setText(accentColor);
            }
            updateAccentPreview(null);

            themePreview.setText(
                    selected + "  •  " + ThemeManager.getTheme(selected).accent()
            );
        });

        themePreview.setText(
                ThemeManager.loadThemeName() + "  •  " +
                        ThemeManager.getTheme(ThemeManager.loadThemeName()).accent()
        );

        HBox themeRow = new HBox(12, themeBox, themePreview);
        themeRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(themeBox, Priority.ALWAYS);

        themeCard.getChildren().add(themeRow);

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

        content.getChildren().addAll(themeCard, accentCard, presetsCard, reset);
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

        NotificationManager manager = NotificationManager.getGlobal();
        if (manager != null) {
            manager.success("Appearance updated", "Accent color changed to " + accentColor + ".");
        }

        updateAccentPreview(null);
    }

    private void buildDiscordPage() {
        pageHeader(
                "Discord",
                "Show what you are playing in Discord with a configurable Rich Presence."
        );

        VBox connection = card(
                "Rich Presence",
                "Vanta connects to the Discord desktop app locally using the official Vanta Discord application."
        );

        CheckBox enabled = new CheckBox("Enable Discord Rich Presence");
        enabled.setSelected(LauncherSettings.isDiscordPresenceEnabled());
        enabled.getStyleClass().add("settings-checkbox");
        enabled.setOnAction(event -> {
            LauncherSettings.setDiscordPresenceEnabled(enabled.isSelected());
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) {
                manager.success(
                        "Discord Rich Presence",
                        enabled.isSelected() ? "Rich Presence enabled." : "Rich Presence disabled."
                );
            }
        });

        connection.getChildren().add(
                enabled
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

        CheckBox showVersion = new CheckBox("Show Minecraft version");
        showVersion.setSelected(LauncherSettings.isDiscordShowVersionEnabled());
        showVersion.getStyleClass().add("settings-checkbox");
        showVersion.setOnAction(event ->
                LauncherSettings.setDiscordShowVersionEnabled(showVersion.isSelected())
        );

        CheckBox showLoader = new CheckBox("Show mod loader");
        showLoader.setSelected(LauncherSettings.isDiscordShowLoaderEnabled());
        showLoader.getStyleClass().add("settings-checkbox");
        showLoader.setOnAction(event ->
                LauncherSettings.setDiscordShowLoaderEnabled(showLoader.isSelected())
        );

        activity.getChildren().addAll(
                showInstance,
                showPlaytime,
                showVersion,
                showLoader
        );

        VBox note = card(
                "Setup",
                "Discord Rich Presence uses Vanta's official Discord application automatically. Vanta does not send your Discord token or account credentials."
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

        CheckBox hideLauncher = new CheckBox("Hide Vanta when Minecraft starts");
        hideLauncher.setSelected(LauncherSettings.isHideLauncherOnLaunchEnabled());
        hideLauncher.getStyleClass().add("settings-checkbox");
        hideLauncher.setTooltip(new javafx.scene.control.Tooltip(
                "Hide the launcher window after Minecraft successfully starts. Vanta can be shown again from the taskbar."
        ));
        hideLauncher.setOnAction(event ->
                LauncherSettings.setHideLauncherOnLaunchEnabled(hideLauncher.isSelected())
        );

        CheckBox autoOpenBrowser = new CheckBox("Automatically open web links in your browser");
        autoOpenBrowser.setSelected(LauncherSettings.isAutoOpenBrowserEnabled());
        autoOpenBrowser.getStyleClass().add("settings-checkbox");
        autoOpenBrowser.setTooltip(new javafx.scene.control.Tooltip(
                "Automatically open supported authentication links in your default browser."
        ));
        autoOpenBrowser.setOnAction(event ->
                LauncherSettings.setAutoOpenBrowserEnabled(autoOpenBrowser.isSelected())
        );

        behavior.getChildren().addAll(
                animations,
                updates,
                confirmations,
                hideLauncher,
                autoOpenBrowser
        );

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

        VBox workspace = card("Workspace", "Control how the main Vanta pages behave and how much information they show.");
        CheckBox playtime = new CheckBox("Show playtime on instance cards");
        playtime.setSelected(LauncherSettings.isShowPlaytimeEnabled());
        playtime.getStyleClass().add("settings-checkbox");
        playtime.setOnAction(e -> LauncherSettings.setShowPlaytimeEnabled(playtime.isSelected()));
        CheckBox compact = new CheckBox("Use compact instance cards");
        compact.setSelected(LauncherSettings.isCompactInstancesEnabled());
        compact.getStyleClass().add("settings-checkbox");
        compact.setOnAction(e -> LauncherSettings.setCompactInstancesEnabled(compact.isSelected()));
        CheckBox notifications = new CheckBox("Show launcher notifications");
        notifications.setSelected(LauncherSettings.isNotificationsEnabled());
        notifications.getStyleClass().add("settings-checkbox");
        notifications.setOnAction(e -> LauncherSettings.setNotificationsEnabled(notifications.isSelected()));
        CheckBox remember = new CheckBox("Remember the last Settings section");
        remember.setSelected(LauncherSettings.isRememberLastSettingsPageEnabled());
        remember.getStyleClass().add("settings-checkbox");
        remember.setOnAction(e -> LauncherSettings.setRememberLastSettingsPageEnabled(remember.isSelected()));
        CheckBox stateScan = new CheckBox("Scan State automatically when opened");
        stateScan.setSelected(LauncherSettings.isStateScanOnOpenEnabled());
        stateScan.getStyleClass().add("settings-checkbox");
        stateScan.setOnAction(e -> LauncherSettings.setStateScanOnOpenEnabled(stateScan.isSelected()));
        workspace.getChildren().addAll(playtime, compact, notifications, remember, stateScan);

        content.getChildren().addAll(behavior, workspace, dataCard, runtimeCard);
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

        TextField javaPath = new TextField(LauncherSettings.getDefaultJavaPath());
        javaPath.getStyleClass().add("create-field");
        javaPath.setPromptText("Auto-detect compatible Java");

        TextField javaArguments = new TextField(LauncherSettings.getDefaultJavaArguments());
        javaArguments.getStyleClass().add("create-field");
        javaArguments.setPromptText("Optional JVM arguments");

        CheckBox fullscreen = new CheckBox("Start new instances in fullscreen");
        fullscreen.setSelected(LauncherSettings.isDefaultFullscreen());
        fullscreen.getStyleClass().add("settings-checkbox");

        TextField defaultVersion = new TextField(LauncherSettings.getDefaultMinecraftVersion());
        defaultVersion.getStyleClass().add("create-field");
        defaultVersion.setPromptText("Leave blank to use the newest release");

        ComboBox<String> defaultLoader = new ComboBox<>();
        defaultLoader.getItems().addAll("Fabric", "Forge", "Vanilla");
        defaultLoader.getSelectionModel().select(LauncherSettings.getDefaultLoader());
        defaultLoader.setMaxWidth(Double.MAX_VALUE);
        defaultLoader.getStyleClass().add("create-combo");


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
                LauncherSettings.setDefaultMinecraftVersion(defaultVersion.getText());
                LauncherSettings.setDefaultLoader(defaultLoader.getValue());
                LauncherSettings.setDefaultJavaPath(javaPath.getText());
                LauncherSettings.setDefaultJavaArguments(javaArguments.getText());

                NotificationManager manager = NotificationManager.getGlobal();
                if (manager != null) {
                    manager.success(
                            "Minecraft defaults saved",
                            "New instances will use the updated defaults."
                    );
                }
            } catch (NumberFormatException ignored) {
                width.setText(String.valueOf(LauncherSettings.getDefaultWidth()));
                height.setText(String.valueOf(LauncherSettings.getDefaultHeight()));

                NotificationManager manager = NotificationManager.getGlobal();
                if (manager != null) {
                    manager.error(
                            "Invalid resolution",
                            "Use a resolution of at least 640×480."
                    );
                }
            }
        });

        defaults.getChildren().addAll(
                new Label("Memory"),
                ramValue,
                ram,
                createInput("Resolution width", width),
                createInput("Resolution height", height),
                createInput("Java executable", javaPath),
                createInput("JVM arguments", javaArguments),
                createInput("Default Minecraft version", defaultVersion),
                createInput("Default mod loader", defaultLoader),
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

        HBox presets = new HBox(8);
        for (int workerCount : new int[]{2, 4, 8, 12, 16}) {
            Button preset = new Button(workerCount + "×");
            preset.getStyleClass().add("accent-preset");
            preset.setOnAction(event -> {
                slider.setValue(workerCount);
                update.run();
            });
            presets.getChildren().add(preset);
        }

        Button save = new Button("SAVE DOWNLOAD SETTINGS");
        save.getStyleClass().add("primary-button");
        save.setOnAction(event -> {
            LauncherSettings.setDownloadThreads((int) Math.round(slider.getValue()));
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) {
                manager.success(
                        "Download settings saved",
                        "Vanta will use " + (int) Math.round(slider.getValue()) + " download workers."
                );
            }
        });

        Label presetLabel = new Label("Quick presets");
        presetLabel.getStyleClass().add("settings-card-description");
        workers.getChildren().addAll(value, slider, presetLabel, presets, save);

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

        VBox reliability = card("Download reliability", "Control verification and retry behavior for network downloads.");
        CheckBox verify = new CheckBox("Verify downloaded files before installation");
        verify.setSelected(LauncherSettings.isVerifyDownloadsEnabled());
        verify.getStyleClass().add("settings-checkbox");
        verify.setOnAction(e -> LauncherSettings.setVerifyDownloadsEnabled(verify.isSelected()));
        Slider retries = new Slider(1, 8, LauncherSettings.getDownloadRetries());
        retries.setMajorTickUnit(1); retries.setMinorTickCount(0); retries.setSnapToTicks(true); retries.setShowTickLabels(true); retries.setShowTickMarks(true); retries.setMaxWidth(Double.MAX_VALUE);
        Label retryValue = new Label(LauncherSettings.getDownloadRetries() + " retries"); retryValue.getStyleClass().add("instance-setting-value");
        retries.valueProperty().addListener((o,a,b) -> retryValue.setText((int)Math.round(b.doubleValue()) + " retries"));
        Button saveReliability = new Button("SAVE DOWNLOAD RELIABILITY"); saveReliability.getStyleClass().add("primary-button");
        saveReliability.setOnAction(e -> LauncherSettings.setDownloadRetries((int)Math.round(retries.getValue())));
        reliability.getChildren().addAll(verify, retryValue, retries, saveReliability);
        content.getChildren().addAll(workers, reliability, layout);
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

        CheckBox instanceDeletion = new CheckBox("Confirm instance deletion");
        instanceDeletion.setSelected(LauncherSettings.isConfirmInstanceDeletionEnabled());
        instanceDeletion.getStyleClass().add("settings-checkbox");
        instanceDeletion.setOnAction(e -> LauncherSettings.setConfirmInstanceDeletionEnabled(instanceDeletion.isSelected()));

        health.getChildren().addAll(
                confirmations,
                instanceDeletion,
                actionButton(
                        "OPEN DATA FOLDER",
                        () -> openDirectory(MinecraftLocator.getVantaDirectory())
                )
        );

        VBox scanPerformance = card(
                "State scanner performance",
                "Control how many background workers Vanta can use while scanning instances, libraries and assets. Higher values can finish I/O-heavy scans faster, but use more system resources."
        );

        Slider scanWorkers = new Slider(1, 32, LauncherSettings.getStateScanWorkers());
        scanWorkers.setMajorTickUnit(4);
        scanWorkers.setMinorTickCount(3);
        scanWorkers.setSnapToTicks(true);
        scanWorkers.setShowTickLabels(true);
        scanWorkers.setShowTickMarks(true);
        scanWorkers.setMaxWidth(Double.MAX_VALUE);

        Label scanWorkerValue = new Label();
        scanWorkerValue.getStyleClass().add("instance-setting-value");

        Label presetLabel = new Label("Quick presets");
        presetLabel.getStyleClass().add("settings-card-description");

        Runnable updateScanWorkerValue = () ->
                scanWorkerValue.setText((int) Math.round(scanWorkers.getValue()) + " scan workers");

        scanWorkers.valueProperty().addListener((o, oldValue, newValue) -> updateScanWorkerValue.run());
        updateScanWorkerValue.run();

        HBox scanPresets = new HBox(8);
        for (int workerCount : new int[]{2, 4, 8, 12, 16, 24}) {
            Button preset = new Button(workerCount + "×");
            preset.getStyleClass().add("accent-preset");
            preset.setOnAction(event -> scanWorkers.setValue(workerCount));
            scanPresets.getChildren().add(preset);
        }

        Button saveScanWorkers = new Button("SAVE SCAN WORKERS");
        saveScanWorkers.getStyleClass().add("primary-button");
        saveScanWorkers.setOnAction(event -> {
            int workers = (int) Math.round(scanWorkers.getValue());
            LauncherSettings.setStateScanWorkers(workers);

            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) {
                manager.success(
                        "State scanner settings saved",
                        "Vanta will use " + workers + " workers on the next State scan."
                );
            }
        });

        scanPerformance.getChildren().addAll(
                scanWorkerValue,
                scanWorkers,
                presetLabel,
                scanPresets,
                saveScanWorkers
        );
        CheckBox autoRepair = new CheckBox("Automatically repair broken State results");
        autoRepair.setSelected(LauncherSettings.isStateAutoRepairEnabled());
        autoRepair.getStyleClass().add("settings-checkbox");
        autoRepair.setOnAction(event -> LauncherSettings.setStateAutoRepairEnabled(autoRepair.isSelected()));

        scanPerformance.getChildren().add(autoRepair);

        VBox safety = card(
                "What diagnostics change",
                "The launcher does not silently rewrite files from this page. Instance dependency repair remains available from the instance repair flow."
        );

        if (debugAccess) {
            VBox debug = card(
                    "Developer Debug",
                    "Owner-only launcher diagnostics. These controls are hidden from all other Minecraft accounts."
            );

            addInfoRow(debug, "Launcher PID", String.valueOf(ProcessHandle.current().pid()));
            addInfoRow(debug, "Java", System.getProperty("java.version", "Unknown"));
            addInfoRow(debug, "Java VM", System.getProperty("java.vm.name", "Unknown"));
            addInfoRow(debug, "OS", System.getProperty("os.name", "Unknown") + " " + System.getProperty("os.version", ""));
            addInfoRow(debug, "Architecture", System.getProperty("os.arch", "Unknown"));
            addInfoRow(debug, "Processors", String.valueOf(Runtime.getRuntime().availableProcessors()));
            addInfoRow(debug, "JVM max memory", formatBytes(Runtime.getRuntime().maxMemory()));
            addInfoRow(debug, "JVM allocated", formatBytes(Runtime.getRuntime().totalMemory()));
            addInfoRow(debug, "Working directory", System.getProperty("user.dir", "Unknown"));
            Account debugAccount = accountService.getCurrentAccount();
            addInfoRow(debug, "Account UUID", debugAccount == null ? "Not signed in" : debugAccount.getUuid());
            addInfoRow(debug, "Vanta data", MinecraftLocator.getVantaDirectory().toString());
            addInfoRow(debug, "Settings file", MinecraftLocator.getVantaDirectory().resolve("settings.properties").toString());

            HBox debugActions = new HBox(8);
            debugActions.getChildren().addAll(
                    actionButton("OPEN DATA", () -> openDirectory(MinecraftLocator.getVantaDirectory())),
                    actionButton("OPEN SETTINGS", () -> openFile(MinecraftLocator.getVantaDirectory().resolve("settings.properties"))),
                    actionButton("REFRESH", () -> selectSection("Repair & Diagnostics"))
            );

            Button testOnboarding = new Button("TEST ONBOARDING");
            testOnboarding.getStyleClass().add("debug-button");
            testOnboarding.setOnAction(event -> {
                LauncherSettings.resetOnboarding();
                NotificationManager manager = NotificationManager.getGlobal();
                if (manager != null) {
                    manager.success(
                            "Onboarding debug",
                            "Opening the first-run onboarding flow."
                    );
                }
                onDebugOnboarding.run();
            });

            VBox tests = card(
                    "Live launcher tests",
                    "These tests exercise real Vanta dependencies and local paths. They do not modify Minecraft instances."
            );

            Button testModrinth = new Button("TEST MODRINTH API");
            testModrinth.getStyleClass().add("debug-button");
            testModrinth.setOnAction(event -> runDebugTest(
                    "Modrinth API",
                    "https://api.modrinth.com/v2/project/fabric-api"
            ));

            Button testMinecraft = new Button("TEST MINECRAFT API");
            testMinecraft.getStyleClass().add("debug-button");
            testMinecraft.setOnAction(event -> runDebugTest(
                    "Minecraft version API",
                    "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
            ));

            Button testStorage = new Button("TEST STORAGE");
            testStorage.getStyleClass().add("debug-button");
            testStorage.setOnAction(event -> runStorageTest());

            Button testJava = new Button("TEST JAVA RUNTIME");
            testJava.getStyleClass().add("debug-button");
            testJava.setOnAction(event -> runJavaRuntimeTest());

            HBox testButtons = new HBox(8, testModrinth, testMinecraft, testStorage, testJava);
            tests.getChildren().add(testButtons);

            debug.getChildren().addAll(debugActions, testOnboarding, tests);
            content.getChildren().add(debug);
        }

        content.getChildren().add(safety);
    }

    private void runDebugTest(String name, String url) {
        Thread thread = new Thread(() -> {
            try {
                HttpClient client = HttpClient.newBuilder()
                        .connectTimeout(java.time.Duration.ofSeconds(8))
                        .build();

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(java.time.Duration.ofSeconds(12))
                        .GET()
                        .build();

                HttpResponse<Void> response =
                        client.send(request, HttpResponse.BodyHandlers.discarding());

                Platform.runLater(() -> {
                    NotificationManager manager = NotificationManager.getGlobal();
                    if (manager != null) {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            manager.success(name + " test passed", "HTTP " + response.statusCode());
                        } else {
                            manager.error(name + " test failed", "HTTP " + response.statusCode());
                        }
                    }
                });
            } catch (Throwable ex) {
                Platform.runLater(() -> {
                    NotificationManager manager = NotificationManager.getGlobal();
                    if (manager != null) {
                        manager.error(name + " test failed",
                                ex.getClass().getSimpleName() + ": " +
                                        (ex.getMessage() == null ? "No details." : ex.getMessage()));
                    }
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void runStorageTest() {
        Thread thread = new Thread(() -> {
            Path testFile = MinecraftLocator.getVantaDirectory().resolve(".vanta-debug-test");
            try {
                Files.createDirectories(testFile.getParent());
                byte[] payload = "VANTA_DEBUG_TEST".getBytes(StandardCharsets.UTF_8);
                Files.write(testFile, payload);
                byte[] read = Files.readAllBytes(testFile);
                Files.deleteIfExists(testFile);

                if (!java.util.Arrays.equals(payload, read)) {
                    throw new IOException("Read-back data did not match.");
                }

                Platform.runLater(() -> {
                    NotificationManager manager = NotificationManager.getGlobal();
                    if (manager != null) {
                        manager.success("Storage test passed", "Write, read and delete completed successfully.");
                    }
                });
            } catch (Throwable ex) {
                try {
                    Files.deleteIfExists(testFile);
                } catch (IOException ignored) {
                }

                Platform.runLater(() -> {
                    NotificationManager manager = NotificationManager.getGlobal();
                    if (manager != null) {
                        manager.error("Storage test failed",
                                ex.getClass().getSimpleName() + ": " +
                                        (ex.getMessage() == null ? "No details." : ex.getMessage()));
                    }
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    private void runJavaRuntimeTest() {
        Thread thread = new Thread(() -> {
            try {
                Path javaBinary = Path.of(
                        System.getProperty("java.home"),
                        "bin",
                        System.getProperty("os.name", "").toLowerCase().contains("win")
                                ? "java.exe"
                                : "java"
                );

                if (!Files.isRegularFile(javaBinary)) {
                    throw new IOException("Java executable was not found at " + javaBinary);
                }

                Process process = new ProcessBuilder(
                        javaBinary.toString(),
                        "-version"
                ).redirectErrorStream(true).start();

                boolean finished = process.waitFor(8, java.util.concurrent.TimeUnit.SECONDS);
                if (!finished) {
                    process.destroyForcibly();
                    throw new IOException("java -version timed out.");
                }

                if (process.exitValue() != 0) {
                    throw new IOException("java -version exited with code " + process.exitValue());
                }

                Platform.runLater(() -> {
                    NotificationManager manager = NotificationManager.getGlobal();
                    if (manager != null) {
                        manager.success("Java runtime test passed",
                                javaBinary + " executed successfully.");
                    }
                });
            } catch (Throwable ex) {
                Platform.runLater(() -> {
                    NotificationManager manager = NotificationManager.getGlobal();
                    if (manager != null) {
                        manager.error("Java runtime test failed",
                                ex.getClass().getSimpleName() + ": " +
                                        (ex.getMessage() == null ? "No details." : ex.getMessage()));
                    }
                });
            }
        });
        thread.setDaemon(true);
        thread.start();
    }

    public void refreshDebugAccess() {
        Account account = accountService.getCurrentAccount();
        boolean next = DebugAccess.isOwner(account);

        if (next != debugAccess) {
            debugAccess = next;

            if ("Repair & Diagnostics".equals(currentSection)) {
                selectSection("Repair & Diagnostics");
            }
        }
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
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) {
                manager.error("Could not open folder", directory.toString());
            }
        }
    }

    private void openFile(Path file) {
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(file.getParent());
                Files.createFile(file);
            }

            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().open(file.toFile());
            }
        } catch (Exception ignored) {
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) {
                manager.error("Could not open file", file.toString());
            }
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 1024L * 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L * 1024L) {
            return String.format("%.0f MB", bytes / 1024.0 / 1024.0);
        }
        return String.format("%.1f GB", bytes / 1024.0 / 1024.0 / 1024.0);
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
