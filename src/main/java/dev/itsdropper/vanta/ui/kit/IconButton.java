package dev.itsdropper.vanta.ui.kit;

import dev.itsdropper.vanta.ui.components.IconView;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Tooltip;

/** Square icon-only button. Always supplies a tooltip and accessible text. */
public final class IconButton extends Button {

    public enum Style { QUIET, SECONDARY, PRIMARY, DANGER }

    public IconButton(IconView.Type icon, String label) {
        this(icon, label, Style.QUIET);
    }

    public IconButton(IconView.Type icon, String label, Style style) {
        setGraphic(IconView.create(icon, 16));
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        setTooltip(new Tooltip(label));
        setAccessibleText(label);
        getStyleClass().addAll("button-icon", switch (style) {
            case QUIET -> "vanta-button-quiet";
            case SECONDARY -> "vanta-button-secondary";
            case PRIMARY -> "vanta-button-primary";
            case DANGER -> "vanta-button-danger";
        });
    }

    public IconButton compact() {
        getStyleClass().add("button-compact");
        return this;
    }
}
