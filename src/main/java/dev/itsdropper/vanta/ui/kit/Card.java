package dev.itsdropper.vanta.ui.kit;

import javafx.geometry.Insets;
import javafx.scene.Node;
import javafx.scene.layout.VBox;

/** The standard raised surface. Compose content as children. */
public final class Card extends VBox {

    public Card(Node... children) {
        super(12, children);
        getStyleClass().add("kit-card");
        setPadding(new Insets(16));
    }

    /** Titled card with a header row. */
    public static Card titled(String title, String description, Node... content) {
        Card card = new Card(new SectionHeader(title, description));
        card.getChildren().addAll(content);
        return card;
    }

    /** Hover and focus affordances for cards that open something. */
    public Card interactive() {
        getStyleClass().add("kit-card-interactive");
        setFocusTraversable(true);
        return this;
    }

    public Card padded(double all) {
        setPadding(new Insets(all));
        return this;
    }

    public Card spaced(double gap) {
        setSpacing(gap);
        return this;
    }
}
