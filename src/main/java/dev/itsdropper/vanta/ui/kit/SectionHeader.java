package dev.itsdropper.vanta.ui.kit;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/** Title (and optional description) on the left, actions on the right. */
public final class SectionHeader extends HBox {

    public SectionHeader(String title) {
        this(title, null);
    }

    public SectionHeader(String title, String description, Node... actions) {
        getStyleClass().add("section-header");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(12);

        Label titleLabel = new Label(title == null ? "" : title);
        titleLabel.getStyleClass().add("section-header-title");
        VBox copy = new VBox(2, titleLabel);
        copy.setMinWidth(0);
        if (description != null && !description.isBlank()) {
            Label descriptionLabel = new Label(description);
            descriptionLabel.setWrapText(true);
            descriptionLabel.getStyleClass().add("section-header-description");
            copy.getChildren().add(descriptionLabel);
        }
        HBox.setHgrow(copy, Priority.ALWAYS);
        getChildren().add(copy);

        if (actions != null && actions.length > 0) {
            Region fill = new Region();
            HBox group = new HBox(8, actions);
            group.setAlignment(Pos.CENTER_RIGHT);
            getChildren().addAll(fill, group);
            HBox.setHgrow(fill, Priority.NEVER);
        }
    }
}
