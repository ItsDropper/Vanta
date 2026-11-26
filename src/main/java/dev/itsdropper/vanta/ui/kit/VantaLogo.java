package dev.itsdropper.vanta.ui.kit;

import javafx.geometry.Pos;
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

    private VantaLogo() {
    }

    /** Official launcher mark (720 x 487): a V whose wings meet at a rounded point. */
    private static final String LAUNCHER_PATH =
            "M0 5Q0 0 5 0L95 0C130 0 155 20 180 45L285 155C310 180 325 205 328 233L349 443A9 9 0 0 0 367 443L387 233C390 205 405 180 430 155L535 45C560 20 585 0 620 0L710 0Q715 0 715 5L715 85C715 125 700 155 680 173L376 473Q358 487 340 473L35 173C15 155 0 125 0 85Z";

    /** Official in-game client mark (612 x 483): two open brackets around a core. */
    private static final String CLIENT_PATH =
            "M194 0L267 0Q273 0 273 6L273 76Q273 82 267 82L231 82C209 82 189 94 176 114L107 214C97 228 95 236 95 246C95 256 97 264 107 278L176 379C189 399 209 409 231 409L267 409Q273 409 273 415L273 477Q273 483 267 483L194 483C164 483 141 469 122 444L14 289C4 274 0 261 0 246C0 231 4 218 14 204L122 49C141 24 164 0 194 0ZM418 0L345 0Q339 0 339 6L339 76Q339 82 345 82L381 82C403 82 423 94 436 114L505 214C515 228 517 236 517 246C517 256 515 264 505 278L436 379C423 399 403 409 381 409L345 409Q339 409 339 415L339 477Q339 483 345 483L418 483C448 483 471 469 490 444L598 289C608 274 612 261 612 246C612 231 608 218 598 204L490 49C471 24 448 0 418 0ZM306 143A102 102 0 1 0 306 347A102 102 0 1 0 306 143Z";

    /** The launcher V mark, {@code size} px wide, in the brand blue. */
    public static Node mark(double size) {
        return shape(LAUNCHER_PATH, size, size * 483 / 720);
    }

    /** The in-game client mark, {@code size} px wide, in the brand blue. */
    public static Node clientMark(double size) {
        return shape(CLIENT_PATH, size, size * 483 / 612);
    }

    private static javafx.scene.layout.Region shape(String content, double width, double height) {
        javafx.scene.layout.Region region = new javafx.scene.layout.Region();
        SVGPath path = new SVGPath();
        path.setContent(content);
        region.setShape(path);
        region.setScaleShape(true);
        region.setMinSize(width, height);
        region.setPrefSize(width, height);
        region.setMaxSize(width, height);
        region.getStyleClass().add("vanta-logo-mark");
        region.setMouseTransparent(true);
        region.setAccessibleText("Vanta");
        return region;
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
