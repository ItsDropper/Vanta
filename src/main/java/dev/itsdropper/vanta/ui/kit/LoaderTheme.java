package dev.itsdropper.vanta.ui.kit;

import java.util.Locale;

/**
 * Maps a mod loader name onto the style key used by the loader hues in the
 * stylesheets ({@code badge-loader-fabric}, {@code plate-fabric}, …). Colour
 * here is information: it tells loaders apart at a glance.
 */
public final class LoaderTheme {
    private LoaderTheme() {
    }

    public static String key(String loader) {
        if (loader == null) {
            return "vanilla";
        }
        String value = loader.trim().toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
        if (value.isEmpty() || value.equals("vanilla") || value.equals("none")) {
            return "vanilla";
        }
        if (value.contains("neoforge")) {
            return "neoforge";
        }
        if (value.contains("forge")) {
            return "forge";
        }
        if (value.contains("fabric")) {
            return "fabric";
        }
        if (value.contains("quilt")) {
            return "quilt";
        }
        return "other";
    }

    public static String label(String loader) {
        return switch (key(loader)) {
            case "vanilla" -> "Vanilla";
            case "neoforge" -> "NeoForge";
            case "forge" -> "Forge";
            case "fabric" -> "Fabric";
            case "quilt" -> "Quilt";
            default -> loader == null ? "Vanilla" : loader.trim();
        };
    }
}
