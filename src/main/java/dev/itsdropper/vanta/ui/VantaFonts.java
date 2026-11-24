package dev.itsdropper.vanta.ui;

import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;

/**
 * Registers the bundled typefaces (SIL Open Font License, see /fonts/OFL.txt).
 *
 * Geist carries the interface and JetBrains Mono carries technical values such
 * as versions, sizes and paths. JavaFX selects a weight by family name, so the
 * stylesheets use "Geist", "Geist Medium", "Geist SemiBold", "JetBrains Mono"
 * and "JetBrains Mono Medium".
 */
public final class VantaFonts {
    public static final String SANS = "Geist";
    public static final String SANS_MEDIUM = "Geist Medium";
    public static final String SANS_SEMIBOLD = "Geist SemiBold";
    public static final String MONO = "JetBrains Mono";
    public static final String MONO_MEDIUM = "JetBrains Mono Medium";

    private static final String[] FILES = {
            "Geist-Regular.ttf", "Geist-Medium.ttf", "Geist-SemiBold.ttf",
            "JetBrainsMono-Regular.ttf", "JetBrainsMono-Medium.ttf"
    };
    private static boolean loaded;

    private VantaFonts() {
    }

    public static synchronized void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        for (String file : FILES) {
            try (InputStream stream = VantaFonts.class.getResourceAsStream("/fonts/" + file)) {
                if (stream != null) {
                    Font.loadFont(stream, 12);
                }
            } catch (IOException ignored) {
                // Falls back to the system sans-serif when a weight cannot be read.
            }
        }
    }
}
