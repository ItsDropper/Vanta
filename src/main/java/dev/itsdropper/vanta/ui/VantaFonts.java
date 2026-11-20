package dev.itsdropper.vanta.ui;

import javafx.scene.text.Font;

import java.io.IOException;
import java.io.InputStream;

/**
 * Registers the bundled Poppins weights (SIL Open Font License, see /fonts/OFL.txt).
 * JavaFX selects a weight by family name, so the stylesheets use "Poppins",
 * "Poppins Light", "Poppins Medium" and "Poppins SemiBold".
 */
public final class VantaFonts {
    private static final String[] FILES = {
            "Poppins-Light.ttf", "Poppins-Regular.ttf", "Poppins-Medium.ttf", "Poppins-SemiBold.ttf"
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
