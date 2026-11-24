package dev.itsdropper.vanta.ui.kit;

import dev.itsdropper.vanta.ui.components.IconView;
import javafx.scene.layout.StackPane;

/** Rounded icon plate tinted by loader, used wherever an instance is shown. */
public final class InstancePlate extends StackPane {

    public InstancePlate(String iconName, String loader, double size) {
        getStyleClass().addAll("plate", "plate-" + LoaderTheme.key(loader));
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        getChildren().add(IconView.create(resolve(iconName), Math.round(size * 0.46)));
    }

    public static IconView.Type resolve(String iconName) {
        if (iconName != null) {
            try {
                return IconView.Type.valueOf(iconName.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                // Unknown icon names fall back to the default plate glyph.
            }
        }
        return IconView.Type.SHIELD;
    }
}
