package dev.itsdropper.vanta.ui.kit;

import javafx.beans.property.StringProperty;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** A single figure with a name and an optional qualifier. */
public final class StatTile extends VBox {

    private final Label value = new Label();
    private final Label hint = new Label();

    public StatTile(String name, String figure, String qualifier) {
        super(4);
        getStyleClass().add("stat-tile");
        setPadding(new Insets(14, 16, 14, 16));

        Label label = new Label(name);
        label.getStyleClass().add("stat-tile-label");
        value.setText(figure);
        value.getStyleClass().add("stat-tile-value");
        hint.setText(qualifier == null ? "" : qualifier);
        hint.getStyleClass().add("stat-tile-hint");
        hint.setManaged(qualifier != null && !qualifier.isBlank());
        hint.setVisible(hint.isManaged());
        getChildren().addAll(label, value, hint);
        setAccessibleText(name + ": " + figure + (qualifier == null ? "" : " " + qualifier));
    }

    public StringProperty valueProperty() {
        return value.textProperty();
    }

    public StringProperty hintProperty() {
        return hint.textProperty();
    }
}
