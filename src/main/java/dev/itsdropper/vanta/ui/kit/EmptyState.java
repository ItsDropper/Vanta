package dev.itsdropper.vanta.ui.kit;

import dev.itsdropper.vanta.ui.components.IconView;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

/** Centered invitation shown where a list has nothing in it yet. */
public final class EmptyState extends VBox {

    public EmptyState(IconView.Type icon, String title, String description, String actionText, Runnable action) {
        super(10);
        getStyleClass().add("empty-state");
        setAlignment(Pos.CENTER);
        StackPane plate = new StackPane(IconView.create(icon, 22));
        plate.getStyleClass().add("empty-state-plate");
        plate.setMinSize(52, 52);
        plate.setPrefSize(52, 52);
        plate.setMaxSize(52, 52);
        Label heading = new Label(title);
        heading.getStyleClass().add("empty-state-title");
        getChildren().add(plate);
        getChildren().add(heading);
        if (description != null && !description.isBlank()) {
            Label body = new Label(description);
            body.getStyleClass().add("empty-state-description");
            body.setWrapText(true);
            body.setMaxWidth(380);
            body.setAlignment(Pos.CENTER);
            body.setTextAlignment(javafx.scene.text.TextAlignment.CENTER);
            getChildren().add(body);
        }
        if (actionText != null && action != null) {
            Button button = new Button(actionText);
            button.getStyleClass().add("primary-button");
            button.setOnAction(event -> action.run());
            VBox.setMargin(button, new javafx.geometry.Insets(6, 0, 0, 0));
            getChildren().add(button);
        }
    }
}
