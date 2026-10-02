package org.example.ui.components;

import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

/**
 * Small, dependency-free vector icons used throughout the launcher.
 * Icons are SVG paths rendered by JavaFX, so they remain crisp at any scale.
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
        TERMINAL
    }

    private IconView() {
    }

    public static Node create(Type type, double size) {
        SVGPath path = new SVGPath();
        path.setContent(pathFor(type));
        path.setFill(Color.WHITE);
        path.setStroke(null);
        path.setScaleX(size / 24.0);
        path.setScaleY(size / 24.0);
        path.getStyleClass().add("vanta-icon");

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
                    "M3 10.5 12 3l9 7.5v9a1.5 1.5 0 0 1-1.5 1.5h-5v-6h-5v6h-5A1.5 1.5 0 0 1 3 19.5z";
            case INSTANCES ->
                    "M4 4h6v6H4zm10 0h6v6h-6zM4 14h6v6H4zm10 0h6v6h-6z";
            case MODS ->
                    "M8 3h8v3h3v8h-3v7H8v-7H5V6h3zm2 2v3h4V5zm-3 5v2h10v-2z";
            case ACCOUNTS ->
                    "M12 12a4 4 0 1 0 0-8 4 4 0 0 0 0 8m-7 9a7 7 0 0 1 14 0z";
            case SETTINGS ->
                    "M12 8a4 4 0 1 0 0 8 4 4 0 0 0 0-8m0-6 1.2 3.1 2.1.9 2.9-1.1 1.8 1.8-1.1 2.9.9 2.1L22 13v2l-3.1 1.2-.9 2.1 1.1 2.9-1.8 1.8-2.9-1.1-2.1.9L11 22H9l-1.2-3.1-2.1-.9-2.9 1.1L1 17.3l1.1-2.9L1.2 12.3 1 10.5l3.1-1.2.9-2.1L4 4.3 5.7 2.6l2.9 1.1L10.7 2z";
            case SEARCH ->
                    "M10.5 4a6.5 6.5 0 1 0 4.1 11.5l4.9 4.9 1.4-1.4-4.9-4.9A6.5 6.5 0 0 0 10.5 4m0 2a4.5 4.5 0 1 1 0 9 4.5 4.5 0 0 1 0-9";
            case FILTER ->
                    "M4 5h16l-6.5 7.2V19l-3 1v-7.8z";
            case PLUS ->
                    "M11 5h2v6h6v2h-6v6h-2v-6H5v-2h6z";
            case REFRESH ->
                    "M20 11a8 8 0 0 0-14.9-3H3l3-3 3 3H7.4A6 6 0 1 1 6.2 15H4.1A8 8 0 0 0 20 11m-4 2 3 3-3 3v-2.1A6 6 0 0 1 6.2 15h2.1a4 4 0 0 0 5.7 1.8V15z";
            case DOWNLOAD ->
                    "M11 3h2v10l3.5-3.5 1.4 1.4-5.9 5.9-5.9-5.9 1.4-1.4L11 13zm-6 15h14v2H5z";
            case UPLOAD ->
                    "M11 21h2V11l3.5 3.5 1.4-1.4-5.9-5.9-5.9 5.9 1.4 1.4L11 11zm-6-18h14v2H5z";
            case FOLDER ->
                    "M3 6a2 2 0 0 1 2-2h5l2 2h7a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z";
            case TRASH ->
                    "M5 7h14v14H5zm3-4h8l1 2h4v2H3V5h4zm1 7h2v8H9zm4 0h2v8h-2z";
            case PLAY ->
                    "M8 5v14l11-7z";
            case PAUSE ->
                    "M6 5h4v14H6zm8 0h4v14h-4z";
            case CHECK ->
                    "M9.2 16.6 5.6 13l-1.4 1.4 5 5L20 8.6 18.6 7.2z";
            case CLOSE ->
                    "M6.4 5 12 10.6 17.6 5 19 6.4 13.4 12l5.6 5.6-1.4 1.4-5.6-5.6L6.4 19 5 17.6l5.6-5.6L5 6.4z";
            case EDIT ->
                    "M4 17.3V21h3.7L19 9.7 15.3 6zm16-9.6a1 1 0 0 0 0-1.4l-2.3-2.3a1 1 0 0 0-1.4 0l-1.5 1.5 3.7 3.7z";
            case SAVE ->
                    "M5 3h12l3 3v15H4V3zm2 2v5h8V5zm1 9h8v2H8zm0 4h8v2H8z";
            case ARROW_LEFT ->
                    "M14.5 5 7.5 12l7 7 1.5-1.5-5.5-5.5 5.5-5.5z";
            case ARROW_RIGHT ->
                    "m9.5 5 7 7-7 7L8 17.5l5.5-5.5L8 6.5z";
            case MORE ->
                    "M5 10a2 2 0 1 0 0 4 2 2 0 0 0 0-4m7 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4m7 0a2 2 0 1 0 0 4 2 2 0 0 0 0-4";
            case COPY ->
                    "M8 8V5a2 2 0 0 1 2-2h9a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2h-3v3a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2zm2-3v3h4a2 2 0 0 1 2 2v2h3V5zm2 8h-7v6h7z";
            case PACKAGE ->
                    "m12 2 9 4.5v11L12 22l-9-4.5v-11zm0 2.2L6.4 7 12 9.8 17.6 7zM5 8.6v3.8l6 3v-3.8zm8 3v3.8l6-3V8.6z";
            case SHIELD ->
                    "M12 2 20 5v6c0 5.2-3.4 9.2-8 11-4.6-1.8-8-5.8-8-11V5zm0 3L7 6.9V11c0 3.7 2.2 6.6 5 8 2.8-1.4 5-4.3 5-8V6.9z";
            case TERMINAL ->
                    "M4 4h16v16H4zm3 4 4 4-4 4 1.4 1.4 5.4-5.4-5.4-5.4zM13 16h4v-2h-4z";
        };
    }
}
