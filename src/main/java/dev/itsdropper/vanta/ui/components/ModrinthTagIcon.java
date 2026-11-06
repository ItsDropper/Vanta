package dev.itsdropper.vanta.ui.components;

import dev.itsdropper.vanta.launcher.modrinth.ModrinthTag;
import javafx.scene.Group;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Small renderer for the SVG path data supplied by Modrinth's tag API.
 * The API returns SVG markup rather than image URLs, so paths are rendered
 * directly with JavaFX and tinted by the caller.
 */
public final class ModrinthTagIcon extends Region {
    private static final Pattern PATH_PATTERN = Pattern.compile(
            "<path\\b[^>]*\\bd\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern VIEWBOX_PATTERN = Pattern.compile(
            "viewBox\\s*=\\s*[\"']\\s*[-\\d.]+[ ,]+[-\\d.]+[ ,]+([\\d.]+)[ ,]+([\\d.]+)[\"']",
            Pattern.CASE_INSENSITIVE
    );

    private final Group paths = new Group();

    public ModrinthTagIcon(ModrinthTag tag, Color color, double size) {
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        setMouseTransparent(true);
        if (tag == null || tag.getIcon() == null || tag.getIcon().isBlank()) return;

        String svg = tag.getIcon();
        Matcher matcher = PATH_PATTERN.matcher(svg);
        while (matcher.find()) {
            SVGPath path = new SVGPath();
            path.setContent(matcher.group(1));
            path.setFill(color == null ? Color.GRAY : color);
            paths.getChildren().add(path);
        }
        if (paths.getChildren().isEmpty()) return;

        Matcher viewBox = VIEWBOX_PATTERN.matcher(svg);
        if (viewBox.find()) {
            try {
                double width = Double.parseDouble(viewBox.group(1));
                double height = Double.parseDouble(viewBox.group(2));
                if (width > 0 && height > 0) {
                    paths.setUserData(new double[]{width, height});
                }
            } catch (NumberFormatException ignored) {
                // Bounds-based scaling below is a safe fallback.
            }
        }
        getChildren().add(paths);
    }

    @Override
    protected void layoutChildren() {
        if (paths.getChildren().isEmpty()) return;
        double width = getWidth();
        double height = getHeight();
        double sourceWidth;
        double sourceHeight;
        Object viewBox = paths.getUserData();
        if (viewBox instanceof double[] dimensions) {
            sourceWidth = dimensions[0];
            sourceHeight = dimensions[1];
        } else {
            var bounds = paths.getLayoutBounds();
            sourceWidth = Math.max(1, bounds.getWidth());
            sourceHeight = Math.max(1, bounds.getHeight());
        }
        double scale = Math.min(width / sourceWidth, height / sourceHeight);
        paths.setScaleX(scale);
        paths.setScaleY(scale);
        var bounds = paths.getBoundsInLocal();
        paths.setTranslateX((width - bounds.getWidth()) / 2 - bounds.getMinX());
        paths.setTranslateY((height - bounds.getHeight()) / 2 - bounds.getMinY());
    }

    public static Color loaderColor(String loader) {
        String key = loader == null ? "" : loader.toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
        return switch (key) {
            case "fabric" -> Color.web("#D6C4A1");
            case "forge" -> Color.web("#E8753D");
            case "neoforge" -> Color.web("#D65B43");
            case "quilt" -> Color.web("#D98DB8");
            case "rift" -> Color.web("#8B78D6");
            case "ornithe" -> Color.web("#77A68A");
            case "legacyfabric" -> Color.web("#B79B70");
            case "babric" -> Color.web("#A6A0D8");
            default -> Color.web("#9AA3B2");
        };
    }
}
