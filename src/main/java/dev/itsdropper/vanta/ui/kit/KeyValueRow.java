package dev.itsdropper.vanta.ui.kit;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/** Label on the left, value on the right. Values are mono by default. */
public final class KeyValueRow extends HBox {

    public KeyValueRow(String key, String value) {
        this(key, mono(value));
    }

    public KeyValueRow(String key, Node value) {
        getStyleClass().add("key-value-row");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("key-value-key");
        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        getChildren().addAll(keyLabel, fill, value);
    }

    private static Label mono(String value) {
        Label label = new Label(value == null ? "" : value);
        label.getStyleClass().add("value-mono-strong");
        return label;
    }
}
