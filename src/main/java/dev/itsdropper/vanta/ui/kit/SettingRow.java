package dev.itsdropper.vanta.ui.kit;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** One setting: what it is and why, with its control on the right. */
public final class SettingRow extends HBox {

    public SettingRow(String title, String description, Node control) {
        getStyleClass().add("setting-row");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(24);

        Label titleLabel = new Label(title);
        titleLabel.setWrapText(true);
        titleLabel.getStyleClass().add("setting-row-title");
        VBox copy = new VBox(2, titleLabel);
        copy.setMinWidth(0);
        if (description != null && !description.isBlank()) {
            Label descriptionLabel = new Label(description);
            descriptionLabel.setWrapText(true);
            descriptionLabel.getStyleClass().add("setting-row-description");
            copy.getChildren().add(descriptionLabel);
        }
        HBox.setHgrow(copy, Priority.ALWAYS);
        getChildren().addAll(copy, control);
        if (control instanceof Switch toggle) {
            setOnMouseClicked(event -> {
                if (event.getTarget() != toggle) {
                    toggle.setSelected(!toggle.isSelected());
                }
            });
            getStyleClass().add("setting-row-clickable");
        }
    }
}
