package org.example.ui;

import javafx.scene.Node;
import javafx.scene.paint.Color;
import org.example.launcher.MinecraftLocator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Central launcher appearance state.
 *
 * Theme colors are exposed as JavaFX looked-up colors so the same theme
 * reaches the launcher shell, every view, cards, controls, overlays and
 * popup-style components.
 */
public final class ThemeManager {

    public static final String DEFAULT_ACCENT = "#5688ED";
    public static final String DEFAULT_THEME = "Vanta";

    public record Theme(
            String name,
            String accent,
            String background,
            String surface,
            String surfaceRaised,
            String surfaceHover,
            String border,
            String text,
            String muted,
            String subtle,
            String danger,
            String success
    ) {}

    private static final Path SETTINGS_FILE =
            MinecraftLocator.getVantaDirectory().resolve("settings.properties");

    private static final Map<String, Theme> THEMES = new LinkedHashMap<>();

    static {
        register(new Theme("Vanta", "#5688ED", "#0B0E13", "#151A22", "#1A202A", "#202936", "#293443",
                "#F4F7FB", "#8B96A8", "#667386", "#E48A94", "#69D99B"));
        register(new Theme("Midnight", "#7C9CFF", "#090B12", "#121622", "#181E2C", "#222A3A", "#2B3447",
                "#F2F5FF", "#8994AA", "#646F84", "#EF8C98", "#69D9A2"));
        register(new Theme("Nord", "#88C0D0", "#0E1419", "#182127", "#202B33", "#293740", "#354651",
                "#EAF2F4", "#91A5AE", "#6E8088", "#E48A94", "#83D6A5"));
        register(new Theme("Obsidian", "#A78BFA", "#0C0A11", "#17131F", "#211A2B", "#2B2237", "#3A2E49",
                "#F5F0FF", "#9A91A8", "#71687D", "#F08D9A", "#72D7A0"));
        register(new Theme("Ember", "#FF9B54", "#120D0A", "#1C1511", "#281D16", "#35251B", "#4A3527",
                "#FFF4EA", "#B19A87", "#7D6B5C", "#EF8A8A", "#75D39A"));
        register(new Theme("Forest", "#63D39A", "#0A110E", "#121D18", "#192820", "#21352A", "#30483A",
                "#ECF8F0", "#91A69A", "#687D70", "#E58C98", "#6FDEA0"));
        register(new Theme("Rose", "#F27C9B", "#120B10", "#1D131A", "#281B24", "#35232E", "#4A3140",
                "#FFF0F5", "#AE929E", "#7D6872", "#F08B99", "#73D6A0"));
        register(new Theme("Solar", "#F2C45F", "#12100A", "#1D1910", "#282116", "#352B1B", "#4B3E27",
                "#FFF9E8", "#AEA486", "#7D735B", "#E58B91", "#76D39A"));
    }

    private ThemeManager() {}

    private static void register(Theme theme) {
        THEMES.put(theme.name(), theme);
    }

    public static Map<String, Theme> getThemes() {
        return Map.copyOf(THEMES);
    }

    public static Theme getTheme(String name) {
        return THEMES.getOrDefault(name, THEMES.get(DEFAULT_THEME));
    }

    public static String loadThemeName() {
        return getString("themePreset", DEFAULT_THEME);
    }

    public static void saveThemeName(String name) {
        if (THEMES.containsKey(name)) setString("themePreset", name);
    }

    public static Theme loadTheme() {
        return getTheme(loadThemeName());
    }

    public static String loadAccent() {
        String custom = getString("accentColor", "");
        if (isValidAccent(custom)) return custom.toUpperCase();
        return loadTheme().accent();
    }

    public static void saveAccent(String accent) {
        if (!isValidAccent(accent)) return;
        setString("accentColor", accent.toUpperCase());
    }

    public static void apply(Node node, String accent) {
        if (node == null) return;

        Theme base = loadTheme();
        String effectiveAccent = isValidAccent(accent) ? accent.toUpperCase() : base.accent();
        Color color = Color.web(effectiveAccent);

        String soft = toHex(color.darker().darker());
        String hover = toHex(color.brighter());
        String rgb = Math.round(color.getRed() * 255) + "," +
                Math.round(color.getGreen() * 255) + "," +
                Math.round(color.getBlue() * 255);

        node.setStyle(
                "-vanta-accent: " + effectiveAccent + ";" +
                "-vanta-accent-soft: " + soft + ";" +
                "-vanta-accent-hover: " + hover + ";" +
                "-vanta-accent-rgb: " + rgb + ";" +
                "-vanta-bg: " + base.background() + ";" +
                "-vanta-surface: " + base.surface() + ";" +
                "-vanta-surface-raised: " + base.surfaceRaised() + ";" +
                "-vanta-surface-hover: " + base.surfaceHover() + ";" +
                "-vanta-border: " + base.border() + ";" +
                "-vanta-text: " + base.text() + ";" +
                "-vanta-muted: " + base.muted() + ";" +
                "-vanta-subtle: " + base.subtle() + ";" +
                "-vanta-danger: " + base.danger() + ";" +
                "-vanta-success: " + base.success() + ";"
        );
    }

    public static void applyTheme(Node node, String themeName) {
        if (!THEMES.containsKey(themeName)) return;
        saveThemeName(themeName);
        apply(node, loadAccent());
    }

    public static boolean isValidAccent(String accent) {
        return accent != null && accent.matches("#[0-9a-fA-F]{6}");
    }

    private static String getString(String key, String fallback) {
        Properties properties = load();
        return properties.getProperty(key, fallback);
    }

    private static void setString(String key, String value) {
        Properties properties = load();
        properties.setProperty(key, value);
        save(properties);
    }

    private static Properties load() {
        Properties properties = new Properties();
        if (!Files.exists(SETTINGS_FILE)) return properties;
        try (var reader = Files.newBufferedReader(SETTINGS_FILE)) {
            properties.load(reader);
        } catch (IOException ignored) {}
        return properties;
    }

    private static void save(Properties properties) {
        try {
            Files.createDirectories(SETTINGS_FILE.getParent());
            try (var writer = Files.newBufferedWriter(SETTINGS_FILE)) {
                properties.store(writer, "Vanta launcher settings");
            }
        } catch (IOException ignored) {}
    }

    private static String toHex(Color color) {
        return String.format("#%02X%02X%02X",
                Math.round(color.getRed() * 255),
                Math.round(color.getGreen() * 255),
                Math.round(color.getBlue() * 255));
    }
}
