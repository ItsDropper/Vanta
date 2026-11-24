package dev.itsdropper.vanta.ui.kit;

import dev.itsdropper.vanta.ui.components.IconView;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.util.Locale;

/** Inline message that explains a state and, when possible, how to fix it. */
public final class Callout extends HBox {

    public enum Tone { INFO, SUCCESS, WARNING, DANGER }

    public Callout(Tone tone, String title, String message, Node... actions) {
        getStyleClass().addAll("callout", "callout-" + tone.name().toLowerCase(Locale.ROOT));
        setAlignment(Pos.TOP_LEFT);
        setSpacing(12);

        IconView.Type type = switch (tone) {
            case INFO -> IconView.Type.INFO;
            case SUCCESS -> IconView.Type.CHECK;
            case WARNING, DANGER -> IconView.Type.ALERT;
        };
        Node icon = IconView.create(type, 18);
        icon.getStyleClass().add("callout-icon");

        VBox copy = new VBox(2);
        copy.setMinWidth(0);
        if (title != null && !title.isBlank()) {
            Label titleLabel = new Label(title);
            titleLabel.setWrapText(true);
            titleLabel.getStyleClass().add("callout-title");
            copy.getChildren().add(titleLabel);
        }
        if (message != null && !message.isBlank()) {
            Label messageLabel = new Label(message);
            messageLabel.setWrapText(true);
            messageLabel.getStyleClass().add("callout-message");
            copy.getChildren().add(messageLabel);
        }
        HBox.setHgrow(copy, Priority.ALWAYS);
        getChildren().addAll(icon, copy);
        if (actions != null && actions.length > 0) {
            HBox group = new HBox(8, actions);
            group.setAlignment(Pos.CENTER_RIGHT);
            getChildren().add(group);
        }
        setAccessibleText((title == null ? "" : title + ". ") + (message == null ? "" : message));
    }
}
