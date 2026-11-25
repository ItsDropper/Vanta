package dev.itsdropper.vanta.ui.kit;

import javafx.scene.control.ToggleButton;

/** Selectable filter chip. */
public final class Chip extends ToggleButton {
    public Chip(String text) {
        super(text);
        getStyleClass().add("chip");
    }
}
