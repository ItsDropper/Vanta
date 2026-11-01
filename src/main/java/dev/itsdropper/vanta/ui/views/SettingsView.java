package dev.itsdropper.vanta.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Slider;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.util.StringConverter;

import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.account.Account;
import dev.itsdropper.vanta.launcher.instance.InstanceManager;
import dev.itsdropper.vanta.launcher.service.AccountService;
import dev.itsdropper.vanta.launcher.update.UpdateInfo;
import dev.itsdropper.vanta.launcher.update.UpdateService;
import dev.itsdropper.vanta.ui.DebugAccess;
import dev.itsdropper.vanta.ui.components.NotificationManager;
import dev.itsdropper.vanta.ui.components.PageHeader;
import dev.itsdropper.vanta.ui.LauncherSettings;
import dev.itsdropper.vanta.ui.ThemeManager;
import dev.itsdropper.vanta.ui.async.UiExecutor;

import java.awt.Desktop;
import java.io.IOException;
import java.io.File;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.function.Consumer;

public class SettingsView extends BorderPane implements dev.itsdropper.vanta.ui.LauncherViewLifecycle {

    private final AccountService accountService;
    private final Consumer<String> onAccentChanged;
    private final Runnable onDebugOnboarding;
    private final Consumer<UpdateInfo> onVantaUpdate;
    private final VBox navigation = new VBox(4);
    private final VBox content = new VBox(22);
    private final ComboBox<String> compactNavigation = new ComboBox<>();
    private final HBox compactNavigationBar = new HBox(12);
    private final AccountService.AccountListener accountListener = account -> refreshDebugAccess();
    private boolean accountListenerRegistered;
    private boolean updatingNavigation;

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
            Runnable onDebugOnboarding,
            Consumer<UpdateInfo> onVantaUpdate
    ) {
        this.accountService = accountService;
        this.onAccentChanged = onAccentChanged;
        this.onDebugOnboarding = onDebugOnboarding;
        this.onVantaUpdate = onVantaUpdate;

        this.accentColor = ThemeManager.loadAccent();

        getStyleClass().add("settings-shell");

        buildNavigation();

        content.setFillWidth(true);
        content.setPadding(new Insets(0, 4, 24, 4));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setFitToHeight(false);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPannable(true);
        scroll.setFocusTraversable(false);
        scroll.getStyleClass().add("settings-scroll");

        setLeft(navigation);
        setCenter(scroll);
        BorderPane.setMargin(navigation, new Insets(0, 22, 0, 0));
        buildCompactNavigation();
        widthProperty().addListener((observable, oldWidth, newWidth) ->
                updateNavigationLayout(newWidth.doubleValue())
        );

        String rememberedSection = LauncherSettings.isRememberLastSettingsPageEnabled()
                ? LauncherSettings.getLastSettingsPage()
                : "Appearance";
        if (!java.util.Set.of(
                "Appearance", "Discord", "General", "Minecraft", "Downloads",
                "Storage & Cache", "State", "Repair & Diagnostics", "About Vanta"
        ).contains(rememberedSection)) {
            rememberedSection = "Appearance";
        }
        selectSection(rememberedSection);
        refreshDebugAccess();
        applyAccent(accentColor);
        onAccentChanged.accept(accentColor);
    }

    private void buildCompactNavigation() {
        Label label = new Label("SETTINGS");
        label.getStyleClass().add("settings-compact-label");
        compactNavigation.getItems().setAll(
                "Appearance", "Discord", "General", "Minecraft", "Downloads",
                "Storage & Cache", "State", "Repair & Diagnostics", "About Vanta"
        );
        compactNavigation.setMaxWidth(Double.MAX_VALUE);
        compactNavigation.setAccessibleText("Settings section");
        compactNavigation.setOnAction(event -> {
            if (!updatingNavigation && compactNavigation.getValue() != null) {
                selectSection(compactNavigation.getValue());
            }
        });
        HBox.setHgrow(compactNavigation, Priority.ALWAYS);
        compactNavigationBar.getStyleClass().add("settings-compact-navigation");
        compactNavigationBar.setAlignment(Pos.CENTER_LEFT);
        compactNavigationBar.setPadding(new Insets(0, 0, 14, 0));
        compactNavigationBar.getChildren().setAll(label, compactNavigation);
    }

    private void updateNavigationLayout(double width) {
        if (width <= 1) return;
        boolean compact = width < 760;
        if (compact) {
            if (getLeft() != null) setLeft(null);
            if (getTop() != compactNavigationBar) setTop(compactNavigationBar);
            setMargin(compactNavigationBar, new Insets(0, 0, 16, 0));
        } else {
            if (getTop() != null) setTop(null);
            if (getLeft() != navigation) setLeft(navigation);
            BorderPane.setMargin(navigation, new Insets(0, 22, 0, 0));
        }
    }

    @Override
    public void onShown() {
        if (!accountListenerRegistered) {
            accountService.addListener(accountListener);
            accountListenerRegistered = true;
        }
        refreshDebugAccess();
    }

    @Override
    public void onHidden() {
        if (accountListenerRegistered) {
            accountService.removeListener(accountListener);
            accountListenerRegistered = false;
        }
    }

    private void buildNavigation() {
        navigation.getStyleClass().add("settings-navigation");

        TextField sectionSearch = new TextField();
        sectionSearch.setPromptText("Find a settings page…");
        sectionSearch.setAccessibleText("Search settings pages");
        sectionSearch.setTooltip(new javafx.scene.control.Tooltip(
                "Filter settings pages by name. Clear the field to show every page."
        ));
        sectionSearch.getStyleClass().add("settings-search-field");
        sectionSearch.textProperty().addListener((observable, oldValue, newValue) -> {
            String query = newValue == null ? "" : newValue.trim().toLowerCase(java.util.Locale.ROOT);
            for (var node : navigation.getChildren()) {
                if (node instanceof Button button) {
                    boolean visible = query.isEmpty()
                            || button.getText().toLowerCase(java.util.Locale.ROOT).contains(query);
                    button.setVisible(visible);
                    button.setManaged(visible);
                }
            }
            // Keep group headings only when at least one matching page exists beneath them.
            String currentGroup = null;
            java.util.Map<String, Boolean> groupMatches = new java.util.LinkedHashMap<>();
            for (var node : navigation.getChildren()) {
                if (node instanceof Label label && label.getStyleClass().contains("settings-nav-group")) {
                    currentGroup = label.getText();
                    groupMatches.putIfAbsent(currentGroup, false);
                } else if (node instanceof Button button && button.isVisible() && currentGroup != null) {
                    groupMatches.put(currentGroup, true);
                }
            }
            for (var node : navigation.getChildren()) {
                if (node instanceof Label label && label.getStyleClass().contains("settings-nav-group")) {
                    boolean visible = query.isEmpty() || groupMatches.getOrDefault(label.getText(), false);
                    label.setVisible(visible);
                    label.setManaged(visible);
                }
            }
        });
        navigation.getChildren().add(sectionSearch);

        navigation.getChildren().add(group("PERSONALIZATION"));
        navigation.getChildren().add(sectionButton("Appearance", true));

        navigation.getChildren().add(group("SOCIAL"));
        navigation.getChildren().add(sectionButton("Discord", false));

        navigation.getChildren().add(group("LAUNCHER"));
        navigation.getChildren().addAll(
                sectionButton("General", false),
                sectionButton("Minecraft", false),
                sectionButton("Downloads", false),
                sectionButton("Storage & Cache", false)
        );

        navigation.getChildren().add(group("SYSTEM"));
        navigation.getChildren().addAll(
                sectionButton("State", false),
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
        updatingNavigation = true;
        compactNavigation.setValue(section);
        updatingNavigation = false;
        if (LauncherSettings.isRememberLastSettingsPageEnabled()) {
            LauncherSettings.setLastSettingsPage(section);
        }
        content.getChildren().clear();

        switch (section) {
            case "Appearance" -> buildAppearancePage();
            case "Discord" -> buildDiscordPage();
            case "General" -> buildGeneralPage();
            case "Minecraft" -> buildMinecraftPage();
            case "Downloads" -> buildDownloadsPage();
            case "Storage & Cache" -> buildStoragePage();
            case "State" -> buildStatePage();
            case "Repair & Diagnostics" -> buildDiagnosticsPage();
            case "About Vanta" -> buildAboutPage();
            default -> buildPlaceholderPage(section);
        }

        dev.itsdropper.vanta.ui.AnimationUtils.installInteractiveAnimations(content);

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
        themeBox.setPrefWidth(260);
        themeBox.setAccessibleText("Launcher theme");

        Label themePreview = new Label();
        themePreview.setWrapText(true);
        themePreview.setMaxWidth(260);
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

        FlowPane themeRow = new FlowPane(12, 8, themeBox, themePreview);
        themeRow.setAlignment(Pos.CENTER_LEFT);

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
        accentPicker.setAccessibleText("Choose launcher accent color");
        accentPicker.setTooltip(new javafx.scene.control.Tooltip("Choose an accent color"));

        accentHexField = new TextField(accentColor);
        accentHexField.getStyleClass().add("accent-hex-field");
        accentHexField.setPromptText("#5688ED");
        accentHexField.setPrefWidth(110);
        accentHexField.setAccessibleText("Accent color hexadecimal value");

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

        FlowPane pickerRow = new FlowPane(
                10, 8,
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

        FlowPane presets = new FlowPane(8, 8);
        presets.setPrefWrapLength(560);
        addPreset(presets, "Vanta Blue", "#5688ED");
        addPreset(presets, "Cyan", "#42C6E8");
        addPreset(presets, "Emerald", "#4FD18B");
        addPreset(presets, "Violet", "#8B7CFF");
        addPreset(presets, "Amber", "#F0B85B");
        addPreset(presets, "Rose", "#EF7187");
        addPreset(presets, "Arctic", "#7DD3FC");
        addPreset(presets, "Lime", "#A3E635");
        addPreset(presets, "Coral", "#FB7185");
        addPreset(presets, "Lavender", "#C4A7FF");
        addPreset(presets, "Ice", "#A5F3FC");
        addPreset(presets, "Crimson", "#FF647C");
        addPreset(presets, "Silver", "#E5E7EB");
        presetsCard.getChildren().add(presets);

        Button reset = new Button("RESET TO VANTA BLUE");
        reset.getStyleClass().add("secondary-button");
        reset.setOnAction(event -> setAccent(ThemeManager.DEFAULT_ACCENT));

        VBox customColors = card(
                "Custom interface palette",
                "Override the theme's core colors independently. These overrides are saved locally and apply across the launcher."
        );

        ColorPicker backgroundColor = customColorPicker("customBackground", ThemeManager.loadTheme().background());
        ColorPicker surfaceColor = customColorPicker("customSurface", ThemeManager.loadTheme().surface());
        ColorPicker raisedColor = customColorPicker("customRaisedSurface", ThemeManager.loadTheme().surfaceRaised());
        ColorPicker hoverColor = customColorPicker("customHoverSurface", ThemeManager.loadTheme().surfaceHover());
        ColorPicker borderColor = customColorPicker("customBorder", ThemeManager.loadTheme().border());
        ColorPicker textColor = customColorPicker("customText", ThemeManager.loadTheme().text());
        ColorPicker mutedColor = customColorPicker("customMuted", ThemeManager.loadTheme().muted());
        ColorPicker dangerColor = customColorPicker("customDanger", ThemeManager.loadTheme().danger());
        ColorPicker successColor = customColorPicker("customSuccess", ThemeManager.loadTheme().success());

        customColors.getChildren().addAll(
                colorSettingRow("Window background", "Main application shell and page canvas", backgroundColor),
                colorSettingRow("Card surfaces", "Panels and standard cards", surfaceColor),
                colorSettingRow("Raised surfaces", "Elevated cards, menus and focused surfaces", raisedColor),
                colorSettingRow("Hover surfaces", "Interactive hover states and highlighted panels", hoverColor),
                colorSettingRow("Borders", "Separators and control outlines", borderColor),
                colorSettingRow("Primary text", "Headings and high-priority text", textColor),
                colorSettingRow("Muted text", "Descriptions and secondary labels", mutedColor),
                colorSettingRow("Error color", "Warnings and destructive-action states", dangerColor),
                colorSettingRow("Success color", "Successful actions and completion states", successColor)
        );

        Button resetPalette = new Button("RESET CUSTOM PALETTE");
        resetPalette.getStyleClass().add("secondary-button");
        resetPalette.setOnAction(event -> {
            ThemeManager.clearCustomColors();
            ThemeManager.apply(this, accentColor);
            onAccentChanged.accept(accentColor);
            backgroundColor.setValue(Color.web(ThemeManager.loadTheme().background()));
            surfaceColor.setValue(Color.web(ThemeManager.loadTheme().surface()));
            raisedColor.setValue(Color.web(ThemeManager.loadTheme().surfaceRaised()));
            hoverColor.setValue(Color.web(ThemeManager.loadTheme().surfaceHover()));
            borderColor.setValue(Color.web(ThemeManager.loadTheme().border()));
            textColor.setValue(Color.web(ThemeManager.loadTheme().text()));
            mutedColor.setValue(Color.web(ThemeManager.loadTheme().muted()));
            dangerColor.setValue(Color.web(ThemeManager.loadTheme().danger()));
            successColor.setValue(Color.web(ThemeManager.loadTheme().success()));
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) manager.success("Palette reset", "Theme colors restored to their defaults.");
        });
        customColors.getChildren().add(resetPalette);


        VBox presentationCard = card(
                "Overengineered UI",
                "Turn on the deluxe presentation layer across Vanta: richer surfaces, stronger depth, sharper active states and more expressive controls. This is purely visual."
        );
        CheckBox overengineeredToggle = new CheckBox("Overengineered UI");
        overengineeredToggle.setSelected(LauncherSettings.isOverengineeredUiEnabled());
        overengineeredToggle.setAccessibleText("Enable or disable the overengineered user interface");
        Label presentationState = new Label(
                overengineeredToggle.isSelected() ? "ON  ·  DELUXE PRESENTATION" : "OFF  ·  STANDARD PRESENTATION"
        );
        presentationState.getStyleClass().add("overengineered-state");
        overengineeredToggle.selectedProperty().addListener((observable, wasEnabled, enabled) -> {
            LauncherSettings.setOverengineeredUiEnabled(enabled);
            presentationState.setText(enabled
                    ? "ON  ·  DELUXE PRESENTATION"
                    : "OFF  ·  STANDARD PRESENTATION");
            applyAccent(accentColor);
            onAccentChanged.accept(accentColor);
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) {
                manager.success(
                        enabled ? "Deluxe presentation enabled" : "Standard presentation restored",
                        enabled ? "The overengineered visual layer is now active." : "Vanta is using its standard appearance."
                );
            }
        });
        FlowPane presentationRow = new FlowPane(14, 8, overengineeredToggle, presentationState);
        presentationRow.setAlignment(Pos.CENTER_LEFT);
        presentationCard.getChildren().add(presentationRow);

        content.getChildren().addAll(themeCard, presentationCard, accentCard, presetsCard, customColors, reset);
    }

    private ColorPicker customColorPicker(String key, String fallback) {
        ColorPicker picker = new ColorPicker(Color.web(ThemeManager.loadCustomColor(key, fallback)));
        picker.getStyleClass().add("accent-picker");
        picker.setOnAction(event -> {
            Color selected = picker.getValue();
            if (selected == null) return;
            String hex = toHex(selected);
            ThemeManager.saveCustomColor(key, hex);
            ThemeManager.apply(this, accentColor);
            onAccentChanged.accept(accentColor);
        });
        return picker;
    }

    private HBox colorSettingRow(String title, String description, ColorPicker picker) {
        VBox labels = new VBox(3);
        Label name = new Label(title);
        name.getStyleClass().add("settings-info-label");
        Label detail = new Label(description);
        detail.getStyleClass().add("settings-card-description");
        detail.setWrapText(true);
        labels.getChildren().addAll(name, detail);
        HBox row = new HBox(12, labels, picker);
        row.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(labels, Priority.ALWAYS);
        return row;
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

    private void addPreset(FlowPane container, String name, String hex) {
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

        CheckBox autoUpdate = new CheckBox("Automatically install launcher updates on every launch");
        autoUpdate.setSelected(LauncherSettings.isAutoUpdateOnLaunchEnabled());
        autoUpdate.getStyleClass().add("settings-checkbox");
        autoUpdate.setTooltip(new javafx.scene.control.Tooltip(
                "When an update is available, Vanta downloads, verifies and installs it automatically, then restarts."
        ));
        autoUpdate.setOnAction(event ->
                LauncherSettings.setAutoUpdateOnLaunchEnabled(autoUpdate.isSelected())
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
                autoUpdate,
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

        HBox dataActions = new HBox(8,
                actionButton("OPEN VANTA FOLDER", () -> openDirectory(vantaDirectory)),
                actionButton("EXPORT SETTINGS", this::exportSettingsBackup),
                actionButton("IMPORT SETTINGS", this::importSettingsBackup)
        );
        dataActions.setAlignment(Pos.CENTER_LEFT);
        dataActions.setSpacing(8);
        dataCard.getChildren().add(dataActions);

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
        CheckBox modrinthDownloads = new CheckBox("Show download counts in Modrinth browsing");
        modrinthDownloads.setSelected(LauncherSettings.isShowModrinthDownloadsEnabled());
        modrinthDownloads.getStyleClass().add("settings-checkbox");
        modrinthDownloads.setOnAction(e -> LauncherSettings.setShowModrinthDownloadsEnabled(modrinthDownloads.isSelected()));

        CheckBox autoRepairOnLaunch = new CheckBox("Attempt automatic instance repair on launch");
        autoRepairOnLaunch.setSelected(LauncherSettings.isAutoRepairOnLaunchEnabled());
        autoRepairOnLaunch.getStyleClass().add("settings-checkbox");
        autoRepairOnLaunch.setOnAction(e -> LauncherSettings.setAutoRepairOnLaunchEnabled(autoRepairOnLaunch.isSelected()));

        CheckBox notifications = new CheckBox("Show launcher notifications");
        notifications.setSelected(LauncherSettings.isNotificationsEnabled());
        notifications.getStyleClass().add("settings-checkbox");
        notifications.setOnAction(e -> LauncherSettings.setNotificationsEnabled(notifications.isSelected()));
        Label notificationDurationLabel = new Label();
        notificationDurationLabel.getStyleClass().add("instance-setting-value");
        Slider notificationDuration = new Slider(1, 10, LauncherSettings.getNotificationDurationSeconds());
        notificationDuration.setMajorTickUnit(1);
        notificationDuration.setMinorTickCount(0);
        notificationDuration.setSnapToTicks(true);
        notificationDuration.setShowTickLabels(true);
        notificationDuration.setShowTickMarks(true);
        notificationDuration.setMaxWidth(Double.MAX_VALUE);
        Runnable updateNotificationDuration = () -> notificationDurationLabel.setText(
                (int) Math.round(notificationDuration.getValue()) + " seconds"
        );
        notificationDuration.valueProperty().addListener((o, oldValue, newValue) -> updateNotificationDuration.run());
        updateNotificationDuration.run();
        Button saveNotificationDuration = new Button("SAVE NOTIFICATION DURATION");
        saveNotificationDuration.getStyleClass().add("secondary-button");
        saveNotificationDuration.setOnAction(e -> {
            LauncherSettings.setNotificationDurationSeconds((int) Math.round(notificationDuration.getValue()));
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) manager.success("Notification timing saved", "Success notifications will stay visible for " +
                    LauncherSettings.getNotificationDurationSeconds() + " seconds.");
        });

        CheckBox remember = new CheckBox("Remember the last Settings section");
        remember.setSelected(LauncherSettings.isRememberLastSettingsPageEnabled());
        remember.getStyleClass().add("settings-checkbox");
        remember.setOnAction(e -> LauncherSettings.setRememberLastSettingsPageEnabled(remember.isSelected()));
        CheckBox stateScan = new CheckBox("Scan State automatically when opened");
        stateScan.setSelected(LauncherSettings.isStateScanOnOpenEnabled());
        stateScan.getStyleClass().add("settings-checkbox");
        stateScan.setOnAction(e -> LauncherSettings.setStateScanOnOpenEnabled(stateScan.isSelected()));
        workspace.getChildren().addAll(
                playtime, compact, modrinthDownloads, autoRepairOnLaunch, notifications,
                new Label("Success notification duration"), notificationDurationLabel,
                notificationDuration, saveNotificationDuration, remember, stateScan
        );

        VBox rollback = card(
                "Vanta version rollback",
                "Install an older published Vanta release. This rolls back the launcher itself, not Minecraft instances."
        );

        ComboBox<UpdateInfo> versions = new ComboBox<>();
        versions.setPromptText("Select a Vanta version");
        versions.setMaxWidth(Double.MAX_VALUE);
        versions.setConverter(new StringConverter<>() {
            @Override
            public String toString(UpdateInfo info) {
                return info == null ? "" : info.getLatestVersion().replaceFirst("^[vV]", "");
            }

            @Override
            public UpdateInfo fromString(String string) {
                return null;
            }
        });

        Button rollbackButton = new Button("ROLL BACK VANTA");
        rollbackButton.getStyleClass().add("secondary-button");
        rollbackButton.setDisable(true);
        versions.valueProperty().addListener((obs, oldValue, newValue) ->
                rollbackButton.setDisable(newValue == null)
        );

        rollbackButton.setOnAction(event -> {
            UpdateInfo selected = versions.getValue();
            if (selected == null) {
                return;
            }

            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Vanta will be replaced with version "
                            + selected.getLatestVersion().replaceFirst("^[vV]", "")
                            + " and restarted. Continue?",
                    ButtonType.CANCEL,
                    ButtonType.OK
            );
            confirm.setTitle("Roll Back Vanta");
            confirm.setHeaderText("Roll back launcher version");

            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                onVantaUpdate.accept(selected);
            }
        });

        rollback.getChildren().addAll(
                versions,
                rollbackButton
        );

        content.getChildren().addAll(
                behavior,
                workspace,
                dataCard,
                runtimeCard,
                rollback
        );

        loadRollbackVersions(versions, rollbackButton);
    }


    private void exportSettingsBackup() {
        Path settingsFile = MinecraftLocator.getVantaDirectory().resolve("settings.properties");
        if (!Files.isRegularFile(settingsFile)) {
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) manager.error("Export failed", "The settings file does not exist yet.");
            return;
        }

        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Vanta settings");
        chooser.setInitialFileName("vanta-settings.properties");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Vanta settings (*.properties)", "*.properties"));
        File selected = chooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (selected == null) return;

        Path target = selected.toPath();
        if (!target.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".properties")) {
            target = target.resolveSibling(target.getFileName() + ".properties");
        }

        try {
            Files.copy(settingsFile, target, StandardCopyOption.REPLACE_EXISTING);
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) manager.success("Settings exported", "Backup saved to " + target);
        } catch (IOException ex) {
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) manager.error("Export failed", ex.getMessage() == null ? "Could not write the backup." : ex.getMessage());
        }
    }

    private void importSettingsBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Import Vanta settings");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Vanta settings (*.properties)", "*.properties"));
        File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (selected == null) return;

        Properties parsed = new Properties();
        try (var reader = Files.newBufferedReader(selected.toPath())) {
            parsed.load(reader);
        } catch (IOException | IllegalArgumentException ex) {
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) manager.error("Import failed", "The selected file is not a valid settings backup.");
            return;
        }

        Alert confirm = new Alert(
                Alert.AlertType.CONFIRMATION,
                "Import these settings and replace the current Vanta settings file? Export a backup first if you want to preserve the current configuration.",
                ButtonType.CANCEL,
                ButtonType.OK
        );
        confirm.setTitle("Import Vanta settings");
        confirm.setHeaderText("Replace current launcher settings");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

        Path settingsFile = MinecraftLocator.getVantaDirectory().resolve("settings.properties");
        try {
            Files.createDirectories(settingsFile.getParent());
            Files.copy(selected.toPath(), settingsFile, StandardCopyOption.REPLACE_EXISTING);
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) manager.success("Settings imported", "Restart Vanta to apply every imported option consistently.");
        } catch (IOException ex) {
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) manager.error("Import failed", ex.getMessage() == null ? "Could not replace the settings file." : ex.getMessage());
        }
    }

    private void loadRollbackVersions(
            ComboBox<UpdateInfo> versions,
            Button rollbackButton
    ) {
        versions.setPromptText("Loading available versions...");

        UiExecutor.shared().execute(() -> {
            try {
                UpdateService service = new UpdateService();
                var available = service.getAvailableVersions(loadVersion());

                UiExecutor.shared().dispatchToUi(() -> {
                    versions.getItems().setAll(available);
                    versions.setPromptText(
                            available.isEmpty()
                                    ? "No rollback versions available"
                                    : "Select a Vanta version"
                    );
                });
            } catch (Exception e) {
                UiExecutor.shared().dispatchToUi(() -> {
                    versions.setPromptText("Could not load Vanta versions");
                    rollbackButton.setDisable(true);

                    NotificationManager manager = NotificationManager.getGlobal();
                    if (manager != null) {
                        manager.error(
                                "Rollback versions unavailable",
                                "Vanta could not load published launcher releases."
                        );
                    }
                });
            }
        });

        thread.setDaemon(true);
        thread.start();
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

        HBox ramPresets = new HBox(6);
        for (int gigabytes : new int[]{2, 4, 6, 8, 10, 12, 14, 16}) {
            Button preset = new Button(gigabytes + " GB");
            preset.getStyleClass().add("accent-preset");
            preset.setOnAction(event -> ram.setValue(gigabytes * 1024));
            ramPresets.getChildren().add(preset);
        }

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

        Button browseJava = new Button("BROWSE");
        browseJava.getStyleClass().add("secondary-button");
        browseJava.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Select default Java executable");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                            ? "Java executable (java.exe)" : "Java executable",
                    System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                            ? "java.exe" : "*"
            ));
            File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (selected != null) javaPath.setText(selected.getAbsolutePath());
        });

        Label javaValidationStatus = new Label("Validate a Java executable before saving it as the default.");
        javaValidationStatus.getStyleClass().add("settings-card-description");
        javaValidationStatus.setWrapText(true);
        Button validateJava = new Button("TEST JAVA");
        validateJava.getStyleClass().add("secondary-button");
        validateJava.setTooltip(new javafx.scene.control.Tooltip(
                "Runs the selected Java executable with -version and checks that it starts successfully."
        ));
        validateJava.setOnAction(event -> {
            String configured = javaPath.getText() == null ? "" : javaPath.getText().trim();
            Path executable;
            if (configured.isBlank()) {
                executable = Path.of(System.getProperty("java.home"), "bin",
                        System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win")
                                ? "java.exe" : "java");
            } else {
                executable = Path.of(configured);
            }
            if (!Files.isRegularFile(executable)) {
                javaValidationStatus.setText("Java executable not found: " + executable);
                return;
            }

            validateJava.setDisable(true);
            javaValidationStatus.setText("Starting Java version check…");
            UiExecutor.shared().execute(() -> {
                String result;
                boolean success = false;
                Process process = null;
                try {
                    process = new ProcessBuilder(executable.toString(), "-version")
                            .redirectErrorStream(true)
                            .start();
                    boolean exited = process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS);
                    if (!exited) {
                        process.destroyForcibly();
                        result = "Java did not exit within 10 seconds. Check that the selected executable is valid.";
                    } else {
                        String output;
                        try (var reader = new java.io.BufferedReader(
                                new java.io.InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                            output = reader.lines().limit(8).collect(java.util.stream.Collectors.joining(" "));
                        }
                        success = process.exitValue() == 0;
                        result = (success ? "Java check passed: " : "Java returned an error: ")
                                + (output.isBlank() ? "No version output." : output);
                    }
                } catch (Exception ex) {
                    result = "Java check failed: " +
                            (ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage());
                    if (process != null) process.destroyForcibly();
                }
                final String message = result;
                final boolean passed = success;
                UiExecutor.shared().dispatchToUi(() -> {
                    validateJava.setDisable(false);
                    javaValidationStatus.setText(message);
                    NotificationManager manager = NotificationManager.getGlobal();
                    if (manager != null) {
                        if (passed) manager.success("Java executable valid", message);
                        else manager.error("Java validation failed", message);
                    }
                });
            });
            worker.setDaemon(true);
            worker.start();
        });

        HBox javaPathRow = new HBox(8, javaPath, browseJava);
        HBox.setHgrow(javaPath, Priority.ALWAYS);
        HBox javaValidationRow = new HBox(8, validateJava, javaValidationStatus);
        javaValidationRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(javaValidationStatus, Priority.ALWAYS);

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
                case "Default (no extra arguments)" -> javaArguments.clear();
                case "G1GC balanced" -> javaArguments.setText("-XX:+UseG1GC -XX:+UnlockExperimentalVMOptions -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 -XX:MaxGCPauseMillis=50");
                case "Low-pause G1GC" -> javaArguments.setText("-XX:+UseG1GC -XX:MaxGCPauseMillis=100 -XX:+ParallelRefProcEnabled");
                case "Diagnostics (GC logging)" -> javaArguments.setText("-Xlog:gc*:file=gc.log:time,uptime,level,tags");
                default -> { }
            }
        });

        ComboBox<String> resolutionPreset = new ComboBox<>();
        resolutionPreset.getItems().addAll("Custom resolution", "1280 × 720", "1600 × 900", "1920 × 1080", "2560 × 1440", "3840 × 2160");
        resolutionPreset.getSelectionModel().selectFirst();
        resolutionPreset.setMaxWidth(Double.MAX_VALUE);
        resolutionPreset.setOnAction(event -> {
            String selected = resolutionPreset.getValue();
            if (selected == null || selected.equals("Custom resolution")) return;
            String[] dimensions = selected.split(" × ");
            width.setText(dimensions[0]);
            height.setText(dimensions[1]);
        });

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
                createInput("Resolution preset", resolutionPreset),
                createInput("Java executable", javaPathRow),
                javaValidationRow,
                createInput("JVM argument preset", jvmPreset),
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

        VBox reliability = card("Download reliability", "Control verification, retries and recovery for network downloads.");
        CheckBox verify = new CheckBox("Verify downloaded files before installation");
        verify.setSelected(LauncherSettings.isVerifyDownloadsEnabled());
        verify.getStyleClass().add("settings-checkbox");
        verify.setOnAction(e -> LauncherSettings.setVerifyDownloadsEnabled(verify.isSelected()));
        Slider retries = new Slider(1, 8, LauncherSettings.getDownloadRetries());
        retries.setMajorTickUnit(1); retries.setMinorTickCount(0); retries.setSnapToTicks(true); retries.setShowTickLabels(true); retries.setShowTickMarks(true); retries.setMaxWidth(Double.MAX_VALUE);
        Label retryValue = new Label(LauncherSettings.getDownloadRetries() + " retries"); retryValue.getStyleClass().add("instance-setting-value");
        retries.valueProperty().addListener((o,a,b) -> retryValue.setText((int)Math.round(b.doubleValue()) + " retries"));
        Button saveReliability = new Button("SAVE DOWNLOAD RELIABILITY"); saveReliability.getStyleClass().add("primary-button");
        saveReliability.setOnAction(e -> {
            LauncherSettings.setDownloadRetries((int)Math.round(retries.getValue()));
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) manager.success("Download settings saved", "Retry policy updated.");
        });

        VBox acceleration = card("Download acceleration", "These options affect future downloads. They never bypass checksum verification when a checksum is available.");
        CheckBox parallel = new CheckBox("Download independent files concurrently");
        parallel.setSelected(LauncherSettings.isParallelDownloadsEnabled());
        parallel.getStyleClass().add("settings-checkbox");
        parallel.setOnAction(e -> LauncherSettings.setParallelDownloadsEnabled(parallel.isSelected()));
        CheckBox reuse = new CheckBox("Prefer HTTP/2 multiplexing");
        reuse.setSelected(LauncherSettings.isHttp2Enabled());
        reuse.getStyleClass().add("settings-checkbox");
        reuse.setTooltip(new javafx.scene.control.Tooltip("Prefer HTTP/2 multiplexing when the server supports it. HTTP/1.1 remains available as a compatibility fallback."));
        reuse.setOnAction(e -> LauncherSettings.setHttp2Enabled(reuse.isSelected()));
        CheckBox cache = new CheckBox("Cache verified files for reuse across instances");
        cache.setSelected(LauncherSettings.isDownloadCacheEnabled());
        cache.getStyleClass().add("settings-checkbox");
        cache.setOnAction(e -> LauncherSettings.setDownloadCacheEnabled(cache.isSelected()));
        CheckBox adaptive = new CheckBox("Adapt download concurrency to available CPU capacity");
        adaptive.setSelected(LauncherSettings.isAdaptiveDownloadsEnabled());
        adaptive.getStyleClass().add("settings-checkbox");
        adaptive.setOnAction(e -> LauncherSettings.setAdaptiveDownloadsEnabled(adaptive.isSelected()));
        CheckBox resume = new CheckBox("Resume interrupted downloads when supported");
        resume.setSelected(LauncherSettings.isResumableDownloadsEnabled());
        resume.getStyleClass().add("settings-checkbox");
        resume.setOnAction(e -> LauncherSettings.setResumableDownloadsEnabled(resume.isSelected()));
        CheckBox segmented = new CheckBox("Use parallel segments for large files (experimental)");
        segmented.setSelected(LauncherSettings.isSegmentedDownloadsEnabled());
        segmented.getStyleClass().add("settings-checkbox");
        segmented.setTooltip(new javafx.scene.control.Tooltip("Only works when a server supports byte-range requests. Some hosts throttle parallel ranges, so leave this off if downloads become less reliable."));
        segmented.setOnAction(e -> LauncherSettings.setSegmentedDownloadsEnabled(segmented.isSelected()));
        acceleration.getChildren().addAll(parallel, reuse, cache, adaptive, resume, segmented);

        content.getChildren().addAll(workers, reliability, acceleration, layout);
    }

    private void buildStoragePage() {
        pageHeader(
                "Storage & Cache",
                "Inspect Vanta's local download cache and reclaim space without touching Minecraft instances or personal files."
        );

        Path vantaDirectory = MinecraftLocator.getVantaDirectory();
        Path cacheDirectory = vantaDirectory.resolve("download-cache");

        VBox overview = card(
                "Verified download cache",
                "Vanta can reuse checksum-verified files to avoid downloading the same Minecraft libraries again. Clearing this cache does not remove installed instances; files can be downloaded again when needed."
        );

        addInfoRow(overview, "Cache location", cacheDirectory.toString());
        addInfoRow(overview, "Vanta data location", vantaDirectory.toString());

        Label cacheSize = new Label("Calculating cache size…");
        cacheSize.getStyleClass().add("instance-setting-value");
        Label cacheStatus = new Label("Cache contents are inspected locally. No files are uploaded.");
        cacheStatus.getStyleClass().add("settings-card-description");
        cacheStatus.setWrapText(true);

        Button refresh = new Button("REFRESH SIZE");
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(e -> refreshDownloadCacheSize(cacheDirectory, cacheSize, cacheStatus, refresh));

        Button open = actionButton("OPEN CACHE FOLDER", () -> {
            try {
                Files.createDirectories(cacheDirectory);
                openDirectory(cacheDirectory);
            } catch (IOException ex) {
                NotificationManager manager = NotificationManager.getGlobal();
                if (manager != null) manager.error("Could not open cache", ex.getMessage() == null
                        ? "Vanta could not open the download cache folder." : ex.getMessage());
            }
        });

        Button clear = new Button("CLEAR DOWNLOAD CACHE");
        clear.getStyleClass().add("danger-button");
        clear.setTooltip(new javafx.scene.control.Tooltip(
                "Delete only Vanta's verified download cache. Installed Minecraft files and instances are not affected."
        ));
        clear.setOnAction(e -> {
            Alert confirm = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Delete Vanta's verified download cache? Installed instances and Minecraft files will remain untouched. Cached files can be downloaded again later.",
                    ButtonType.CANCEL,
                    ButtonType.OK
            );
            confirm.setTitle("Clear Download Cache");
            confirm.setHeaderText("Free space used by cached downloads");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;

            clear.setDisable(true);
            refresh.setDisable(true);
            cacheStatus.setText("Clearing cached downloads…");
            UiExecutor.shared().execute(() -> {
                Exception failure = null;
                try {
                    if (Files.exists(cacheDirectory)) {
                        try (var walk = Files.walk(cacheDirectory)) {
                            for (Path item : walk.sorted(java.util.Comparator.reverseOrder()).toList()) {
                                Files.deleteIfExists(item);
                            }
                        }
                    }
                } catch (Exception ex) {
                    failure = ex;
                }
                Exception result = failure;
                UiExecutor.shared().dispatchToUi(() -> {
                    clear.setDisable(false);
                    refresh.setDisable(false);
                    if (result == null) {
                        cacheStatus.setText("Download cache cleared. Vanta will recreate it when needed.");
                        cacheSize.setText("0 bytes");
                        NotificationManager manager = NotificationManager.getGlobal();
                        if (manager != null) manager.success("Cache cleared", "Verified download cache removed. Installed instances were not changed.");
                    } else {
                        cacheStatus.setText("Could not completely clear the cache: " +
                                (result.getMessage() == null ? result.getClass().getSimpleName() : result.getMessage()));
                        NotificationManager manager = NotificationManager.getGlobal();
                        if (manager != null) manager.error("Cache cleanup incomplete", "Some cached files could not be removed.");
                        refreshDownloadCacheSize(cacheDirectory, cacheSize, cacheStatus, refresh);
                    }
                });
            });
            worker.setDaemon(true);
            worker.start();
        });

        HBox actions = new HBox(8, refresh, open, clear);
        actions.setAlignment(Pos.CENTER_LEFT);
        actions.setSpacing(8);
        overview.getChildren().addAll(cacheSize, actions, cacheStatus);

        VBox downloadPolicy = card(
                "Download behavior",
                "These options affect future downloads. Existing verified cache entries remain local until you clear them."
        );
        CheckBox cacheEnabled = new CheckBox("Reuse checksum-verified downloads");
        cacheEnabled.setSelected(LauncherSettings.isDownloadCacheEnabled());
        cacheEnabled.getStyleClass().add("settings-checkbox");
        cacheEnabled.setTooltip(new javafx.scene.control.Tooltip(
                "Reuse files only when their SHA-1 matches the expected checksum."
        ));
        cacheEnabled.setOnAction(e -> {
            LauncherSettings.setDownloadCacheEnabled(cacheEnabled.isSelected());
            cacheStatus.setText(cacheEnabled.isSelected()
                    ? "Verified cache reuse enabled for future downloads."
                    : "Cache reuse disabled. Existing cached files are retained until you clear them.");
        });
        downloadPolicy.getChildren().add(cacheEnabled);

        VBox safety = card(
                "Storage safety",
                "Cache cleanup is restricted to the dedicated Vanta download-cache directory. It does not scan or delete files from your Windows profile, game saves, screenshots, mods, or instance folders."
        );
        content.getChildren().addAll(overview, downloadPolicy, safety);
        refreshDownloadCacheSize(cacheDirectory, cacheSize, cacheStatus, refresh);
    }

    private void refreshDownloadCacheSize(
            Path cacheDirectory,
            Label sizeLabel,
            Label statusLabel,
            Button refreshButton
    ) {
        refreshButton.setDisable(true);
        statusLabel.setText("Inspecting local cache files…");
        UiExecutor.shared().execute(() -> {
            long total = 0L;
            long files = 0L;
            Exception failure = null;
            try {
                if (Files.exists(cacheDirectory)) {
                    try (var walk = Files.walk(cacheDirectory)) {
                        for (Path path : walk.filter(Files::isRegularFile).toList()) {
                            try {
                                total = Math.addExact(total, Files.size(path));
                                files++;
                            } catch (ArithmeticException ignored) {
                                total = Long.MAX_VALUE;
                            }
                        }
                    }
                }
            } catch (Exception ex) {
                failure = ex;
            }
            final long bytes = total;
            final long count = files;
            final Exception error = failure;
            UiExecutor.shared().dispatchToUi(() -> {
                refreshButton.setDisable(false);
                if (error != null) {
                    sizeLabel.setText("Size unavailable");
                    statusLabel.setText("Could not inspect the cache: " +
                            (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                    return;
                }
                sizeLabel.setText(formatStorageSize(bytes) + " across " + count + " file" + (count == 1 ? "" : "s"));
                statusLabel.setText(count == 0
                        ? "The download cache is empty."
                        : "Cache contents were measured locally. No file contents were uploaded or modified.");
            });
        });
        worker.setDaemon(true);
        worker.start();
    }

    private static String formatStorageSize(long bytes) {
        if (bytes < 1024) return bytes + " bytes";
        double value = bytes;
        String[] units = {"KB", "MB", "GB", "TB"};
        int unit = -1;
        do {
            value /= 1024.0;
            unit++;
        } while (value >= 1024.0 && unit < units.length - 1);
        return String.format(java.util.Locale.ROOT, "%.2f %s", value, units[unit]);
    }

    private void buildStatePage() {
        pageHeader(
                "State",
                "Configure how Vanta scans, reports and repairs installed environments."
        );

        VBox scanner = card(
                "Scanner performance",
                "Choose how many background workers Vanta can use during State scans. More workers can improve I/O-heavy scans."
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

        Runnable updateValue = () ->
                scanWorkerValue.setText((int) Math.round(scanWorkers.getValue()) + " scan workers");
        scanWorkers.valueProperty().addListener((o, oldValue, newValue) -> updateValue.run());
        updateValue.run();

        HBox presets = new HBox(8);
        for (int workerCount : new int[]{2, 4, 8, 12, 16, 24}) {
            Button preset = new Button(workerCount + "×");
            preset.getStyleClass().add("accent-preset");
            preset.setOnAction(event -> scanWorkers.setValue(workerCount));
            presets.getChildren().add(preset);
        }

        Button save = new Button("SAVE SCAN WORKERS");
        save.getStyleClass().add("primary-button");
        save.setOnAction(event -> {
            int workers = (int) Math.round(scanWorkers.getValue());
            LauncherSettings.setStateScanWorkers(workers);
            NotificationManager manager = NotificationManager.getGlobal();
            if (manager != null) {
                manager.success("State scanner settings saved",
                        "Vanta will use " + workers + " workers on the next State scan.");
            }
        });

        scanner.getChildren().addAll(
                scanWorkerValue,
                scanWorkers,
                new Label("Quick presets"),
                presets,
                save
        );

        VBox repair = card(
                "Automatic repair",
                "When enabled, Vanta automatically attempts to repair broken instance State results after they are detected."
        );

        CheckBox autoRepair = new CheckBox("Automatically repair broken instances");
        autoRepair.setSelected(LauncherSettings.isStateAutoRepairEnabled());
        autoRepair.getStyleClass().add("settings-checkbox");
        autoRepair.setOnAction(event ->
                LauncherSettings.setStateAutoRepairEnabled(autoRepair.isSelected())
        );

        Label manual = new Label(
                "When disabled, broken instances show a REPAIR button directly in State."
        );
        manual.getStyleClass().add("settings-card-description");
        manual.setWrapText(true);

        repair.getChildren().addAll(autoRepair, manual);

        content.getChildren().addAll(scanner, repair);
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
        UiExecutor.shared().execute(() -> {
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

                UiExecutor.shared().dispatchToUi(() -> {
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
                UiExecutor.shared().dispatchToUi(() -> {
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
        UiExecutor.shared().execute(() -> {
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

                UiExecutor.shared().dispatchToUi(() -> {
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

                UiExecutor.shared().dispatchToUi(() -> {
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
        UiExecutor.shared().execute(() -> {
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

                UiExecutor.shared().dispatchToUi(() -> {
                    NotificationManager manager = NotificationManager.getGlobal();
                    if (manager != null) {
                        manager.success("Java runtime test passed",
                                javaBinary + " executed successfully.");
                    }
                });
            } catch (Throwable ex) {
                UiExecutor.shared().dispatchToUi(() -> {
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
        content.getChildren().add(new PageHeader(
                title,
                subtitle,
                "settings-page-title",
                "settings-page-subtitle"
        ));
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
