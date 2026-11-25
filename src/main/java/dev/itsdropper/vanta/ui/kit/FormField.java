package dev.itsdropper.vanta.ui.kit;

import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/** Label, control and hint/error text stacked consistently. */
public final class FormField extends VBox {

    private final Label hint = new Label();

    public FormField(String label, Node control) {
        super(6);
        getStyleClass().add("form-field");
        Label labelNode = new Label(label);
        labelNode.getStyleClass().add("form-field-label");
        hint.setWrapText(true);
        hint.getStyleClass().add("form-field-hint");
        hint.setVisible(false);
        hint.setManaged(false);
        getChildren().addAll(labelNode, control, hint);
    }

    public FormField hint(String text) {
        show(text, false);
        return this;
    }

    public FormField error(String text) {
        show(text, true);
        return this;
    }

    public void clearMessage() {
        hint.setVisible(false);
        hint.setManaged(false);
    }

    private void show(String text, boolean error) {
        hint.setText(text == null ? "" : text);
        boolean visible = text != null && !text.isBlank();
        hint.setVisible(visible);
        hint.setManaged(visible);
        hint.getStyleClass().remove("form-field-error");
        if (error) {
            hint.getStyleClass().add("form-field-error");
        }
    }
}
