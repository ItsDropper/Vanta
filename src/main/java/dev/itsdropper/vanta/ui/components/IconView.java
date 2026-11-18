package dev.itsdropper.vanta.ui.components;

import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.shape.StrokeLineCap;
import javafx.scene.shape.StrokeLineJoin;

import java.util.EnumSet;
import java.util.Set;

/**
 * Small, dependency-free vector icons used throughout the launcher.
 *
 * Icons are drawn on a 24 x 24 grid as outlines with a uniform 2 unit stroke,
 * round caps and round joins. A few glyphs (play, pause, overflow dots) are
 * solid. Colour comes from the stylesheet: {@code .vanta-icon} strokes with the
 * nearest {@code -vantaicon} looked-up colour, and {@code .vanta-icon-solid}
 * fills with it, so icons follow hover, selection and button variants.
 * The programmatic defaults below only apply when no stylesheet is present.
 */
public final class IconView {

    public enum Type {
        HOME,
        INSTANCES,
        MODS,
        ACCOUNTS,
        SETTINGS,
        SEARCH,
        FILTER,
        PLUS,
        REFRESH,
        DOWNLOAD,
        UPLOAD,
        FOLDER,
        TRASH,
        PLAY,
        PAUSE,
        CHECK,
        CLOSE,
        EDIT,
        SAVE,
        ARROW_LEFT,
        ARROW_RIGHT,
        MORE,
        COPY,
        PACKAGE,
        SHIELD,
        SKIN,
        TERMINAL,
        EXPERIMENTAL,
        ACTIVITY
    }

    private static final Set<Type> SOLID = EnumSet.of(Type.PLAY, Type.PAUSE, Type.MORE);

    private IconView() {
    }

    public static Node create(Type type, double size) {
        SVGPath path = new SVGPath();
        path.setContent(pathFor(type));
        path.getStyleClass().add("vanta-icon");

        if (SOLID.contains(type)) {
            path.getStyleClass().add("vanta-icon-solid");
            path.setFill(Color.WHITE);
            path.setStroke(null);
        } else {
            path.setFill(null);
            path.setStroke(Color.WHITE);
            path.setStrokeWidth(2);
            path.setStrokeLineCap(StrokeLineCap.ROUND);
            path.setStrokeLineJoin(StrokeLineJoin.ROUND);
        }

        path.setScaleX(size / 24.0);
        path.setScaleY(size / 24.0);

        javafx.scene.layout.StackPane box =
                new javafx.scene.layout.StackPane(path);
        box.setMinSize(size, size);
        box.setPrefSize(size, size);
        box.setMaxSize(size, size);
        box.setMouseTransparent(true);

        return box;
    }

    private static String pathFor(Type type) {
        return switch (type) {
            case HOME ->
                    "M4 11 12 4l8 7M6.5 9.5V20h11V9.5M10 20v-5.5h4V20";
            case INSTANCES ->
                    "M4 4h6.5v6.5H4zM13.5 4H20v6.5h-6.5zM4 13.5h6.5V20H4zM13.5 13.5H20V20h-6.5z";
            case MODS ->
                    "M12 3.5 20 8l-8 4.5L4 8zM4 12l8 4.5 8-4.5M4 16l8 4.5 8-4.5";
            case ACCOUNTS ->
                    "M12 12.5a4 4 0 1 0 0-8 4 4 0 0 0 0 8zM4.5 20c.6-3.6 3.7-5.5 7.5-5.5s6.9 1.9 7.5 5.5";
            case SETTINGS ->
                    "M4 8h9m4 0h3M4 16h3m4 0h9M15 5.5v5M9 13.5v5";
            case SEARCH ->
                    "M10.5 17a6.5 6.5 0 1 0 0-13 6.5 6.5 0 0 0 0 13zM15.5 15.5 20 20";
            case FILTER ->
                    "M4 6h16M7 12h10M10 18h4";
            case PLUS ->
                    "M12 5v14M5 12h14";
            case REFRESH ->
                    "M18.5 12a6.5 6.5 0 1 1-1.9-4.6M18.5 4.5v3.5H15";
            case DOWNLOAD ->
                    "M12 4v11M7.5 10.5 12 15l4.5-4.5M5 19.5h14";
            case UPLOAD ->
                    "M12 15V4M7.5 8.5 12 4l4.5 4.5M5 19.5h14";
            case FOLDER ->
                    "M3.5 7.5A1.5 1.5 0 0 1 5 6h4l2 2h8a1.5 1.5 0 0 1 1.5 1.5v8A1.5 1.5 0 0 1 19 19H5a1.5 1.5 0 0 1-1.5-1.5z";
            case TRASH ->
                    "M5 7h14M10 7V5h4v2M7 7l.8 12h8.4L17 7M10.5 11v5M13.5 11v5";
            case PLAY ->
                    "M8 5.5v13l10.5-6.5z";
            case PAUSE ->
                    "M7 5.5h3.5v13H7zM13.5 5.5H17v13h-3.5z";
            case CHECK ->
                    "M5 12.5l4.5 4.5L19 7.5";
            case CLOSE ->
                    "M6 6l12 12M18 6 6 18";
            case EDIT ->
                    "M4.5 19.5l.8-3.8L16 5a1.9 1.9 0 0 1 2.7 0l.3.3a1.9 1.9 0 0 1 0 2.7L8.3 18.7zM14 7l3 3";
            case SAVE ->
                    "M5 5.5A1.5 1.5 0 0 1 6.5 4H16l3 3v11.5a1.5 1.5 0 0 1-1.5 1.5h-11A1.5 1.5 0 0 1 5 18.5zM8 4v4.5h6.5V4M8 20v-5.5h8V20";
            case ARROW_LEFT ->
                    "M14.5 6l-6 6 6 6";
            case ARROW_RIGHT ->
                    "M9.5 6l6 6-6 6";
            case MORE ->
                    "M5 12a1.4 1.4 0 1 0 2.8 0 1.4 1.4 0 1 0-2.8 0zM10.6 12a1.4 1.4 0 1 0 2.8 0 1.4 1.4 0 1 0-2.8 0zM16.2 12a1.4 1.4 0 1 0 2.8 0 1.4 1.4 0 1 0-2.8 0z";
            case COPY ->
                    "M9.5 8.5h8A1.5 1.5 0 0 1 19 10v8a1.5 1.5 0 0 1-1.5 1.5h-8A1.5 1.5 0 0 1 8 18v-8a1.5 1.5 0 0 1 1.5-1.5zM5 15V6a1.5 1.5 0 0 1 1.5-1.5H15";
            case PACKAGE ->
                    "M12 3.5l7.5 4v9L12 20.5l-7.5-4v-9zM4.5 7.5l7.5 4 7.5-4M12 11.5v9";
            case SHIELD ->
                    "M12 3.5 19 6v5.5c0 4.2-2.8 7.2-7 9-4.2-1.8-7-4.8-7-9V6zM9 12l2.2 2.2L15.2 10";
            case SKIN ->
                    "M8.5 4 4 6.5 5.5 10 8 9v10.5h8V9l2.5 1L20 6.5 15.5 4c-.5 1.2-1.9 2-3.5 2S9 5.2 8.5 4z";
            case TERMINAL ->
                    "M4 5.5h16v13H4zM7.5 10l2.5 2-2.5 2M12.5 14.5h4";
            case EXPERIMENTAL ->
                    "M9.5 4h5M10.5 4v5.2L5.5 17.5A1.8 1.8 0 0 0 7 20h10a1.8 1.8 0 0 0 1.5-2.5L13.5 9.2V4M8 14.5h8";
            case ACTIVITY ->
                    "M3.5 12H7l2.5-6 4 12 2.5-6h4.5";
        };
    }
}
