package dev.itsdropper.vanta.ui.kit;

import dev.itsdropper.vanta.ui.components.IconView;
import javafx.beans.property.StringProperty;
import javafx.css.PseudoClass;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

/** Text field with a leading search glyph and a clear affordance. */
public final class SearchField extends HBox {

    private static final PseudoClass FOCUS_WITHIN = PseudoClass.getPseudoClass("focus-within");
    private final TextField field = new TextField();

    public SearchField(String prompt) {
        getStyleClass().add("search-field");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(8);

        Node glyph = IconView.create(IconView.Type.SEARCH, 15);
        field.setPromptText(prompt);
        field.getStyleClass().add("search-field-input");
        field.setAccessibleText(prompt);
        HBox.setHgrow(field, Priority.ALWAYS);

        Button clear = new Button();
        clear.setGraphic(IconView.create(IconView.Type.CLOSE, 12));
        clear.getStyleClass().add("search-field-clear");
        clear.setFocusTraversable(false);
        clear.setAccessibleText("Clear search");
        clear.visibleProperty().bind(field.textProperty().isNotEmpty());
        clear.managedProperty().bind(clear.visibleProperty());
        clear.setOnAction(event -> {
            field.clear();
            field.requestFocus();
        });

        field.focusedProperty().addListener((observable, was, is) ->
                pseudoClassStateChanged(FOCUS_WITHIN, is));
        getChildren().addAll(glyph, field, clear);
        // Clicking the padding focuses the field.
        setOnMouseClicked(event -> field.requestFocus());
    }

    public TextField textField() {
        return field;
    }

    public StringProperty textProperty() {
        return field.textProperty();
    }

    public String getText() {
        return field.getText();
    }
}
