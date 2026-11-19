package dev.itsdropper.vanta.ui;

import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.scene.layout.Region;

/**
 * Shared responsive layout rules for top-level launcher pages.
 *
 * Page gutters scale with the available width so content remains usable in
 * compact windows without sacrificing the spacing of the desktop layout.
 */
public final class ResponsiveLayout {

    private ResponsiveLayout() {
    }

    /** Horizontal page gutter for a given page width; shared by every top-level page. */
    public static double gutter(double width) {
        return width < 600 ? 16 : width < 900 ? 20 : 28;
    }

    public static void bindPagePadding(Region region) {
        region.paddingProperty().bind(Bindings.createObjectBinding(() -> {
            double width = region.getWidth();
            double horizontal = width < 600 ? 16 : width < 900 ? 20 : 28;
            double vertical = width < 600 ? 16 : 24;
            return new Insets(vertical, horizontal, vertical, horizontal);
        }, region.widthProperty()));
    }
}
