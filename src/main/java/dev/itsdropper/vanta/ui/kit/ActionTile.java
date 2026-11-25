package dev.itsdropper.vanta.ui.kit;

import dev.itsdropper.vanta.ui.components.IconView;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Large clickable row: icon, title, one line of explanation. */
public final class ActionTile extends Button {

    public ActionTile(IconView.Type icon, String title, String description, Runnable action) {
        getStyleClass().add("action-tile");
        setContentDisplay(ContentDisplay.GRAPHIC_ONLY);
        setMaxWidth(Double.MAX_VALUE);
        setAccessibleText(title + ". " + description);

        StackPane plate = new StackPane(IconView.create(icon, 18));
        plate.getStyleClass().add("action-tile-plate");
        plate.setMinSize(40, 40);
        plate.setPrefSize(40, 40);
        plate.setMaxSize(40, 40);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("action-tile-title");
        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);
        descriptionLabel.getStyleClass().add("action-tile-description");
        VBox copy = new VBox(2, titleLabel, descriptionLabel);
        copy.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(copy, Priority.ALWAYS);

        var chevron = IconView.create(IconView.Type.ARROW_RIGHT, 16);
        chevron.getStyleClass().add("action-tile-chevron");

        HBox row = new HBox(14, plate, copy, chevron);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMouseTransparent(true);
        setGraphic(row);
        setOnAction(event -> {
            if (action != null) {
                action.run();
            }
        });
    }
}
