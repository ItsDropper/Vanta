package dev.itsdropper.vanta.ui.kit;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** Horizontal strip of search, filters and actions. */
public final class Toolbar extends HBox {

    public Toolbar(Node... children) {
        super(8, children);
        getStyleClass().add("kit-toolbar");
        setAlignment(Pos.CENTER_LEFT);
    }

    public static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }
}
