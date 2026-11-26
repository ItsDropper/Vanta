package dev.itsdropper.vanta.ui.kit;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.shape.SVGPath;

/**
 * The Vanta mark and wordmark. The mark is a V cut into two facets, a gold
 * left arm and a lighter right arm that stops short of the point, and the
 * wordmark sets VANTA in Poppins Bold with open letter spacing.
 */
public final class VantaLogo {

    private static final double MARK_BOX = 28;

    private VantaLogo() {
    }

    /** The V mark scaled to {@code size} px square. */
    public static Node mark(double size) {
        SVGPath left = new SVGPath();
        left.setContent("M2 3H8.6L14 18.4L11.6 25Z");
        left.getStyleClass().add("vanta-logo-arm-primary");

        SVGPath right = new SVGPath();
        right.setContent("M26 3H19.4L14.6 16.6L16.8 22.6Z");
        right.getStyleClass().add("vanta-logo-arm-secondary");

        Group group = new Group(left, right);
        double scale = size / MARK_BOX;
        group.setScaleX(scale);
        group.setScaleY(scale);

        // A Group's layout bounds ignore scale, so wrap it in a fixed box.
        javafx.scene.layout.StackPane box = new javafx.scene.layout.StackPane(group);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);
        return box;
    }

    /** VANTA as separate letters so the spacing can be opened up (JavaFX CSS has no tracking). */
    public static HBox wordmark(double fontSize) {
        HBox word = new HBox(fontSize * 0.14);
        word.setAlignment(Pos.CENTER_LEFT);
        word.getStyleClass().add("vanta-logo-word");
        for (char c : "VANTA".toCharArray()) {
            Label letter = new Label(String.valueOf(c));
            letter.getStyleClass().add("vanta-logo-letter");
            letter.setStyle("-fx-font-size: " + fontSize + "px;");
            word.getChildren().add(letter);
        }
        word.setAccessibleText("Vanta");
        word.setMouseTransparent(true);
        return word;
    }

    /** Mark followed by wordmark. */
    public static HBox full(double markSize, double fontSize) {
        HBox box = new HBox(markSize * 0.42, mark(markSize), wordmark(fontSize));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setAccessibleText("Vanta");
        return box;
    }
}
