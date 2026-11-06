package dev.itsdropper.vanta.ui.components;

import dev.itsdropper.vanta.launcher.modrinth.ModrinthTag;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bounded renderer for the SVG path data supplied by Modrinth's tag API.
 *
 * SVG path elements inside defs, clipPath, masks and other non-rendered
 * containers are not artwork. Treating those paths as visible artwork can
 * turn clipping geometry into large solid shapes. The viewport clip is a
 * second line of defence against malformed or out-of-viewBox path geometry.
 */
public final class ModrinthTagIcon extends Region {
    private static final Pattern PATH_PATTERN = Pattern.compile(
            "<path\\b[^>]*\\bd\\s*=\\s*[\"']([^\"']+)[\"'][^>]*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern VIEWBOX_PATTERN = Pattern.compile(
            "viewBox\\s*=\\s*[\"']\\s*([-\\d.]+)[ ,]+([-\\d.]+)[ ,]+([\\d.]+)[ ,]+([\\d.]+)[\"']",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern DEFS_PATTERN = Pattern.compile(
            "<defs\\b[^>]*>.*?</defs\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern CLIP_PATH_PATTERN = Pattern.compile(
            "<clipPath\\b[^>]*>.*?</clipPath\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern MASK_PATTERN = Pattern.compile(
            "<mask\\b[^>]*>.*?</mask\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern SYMBOL_PATTERN = Pattern.compile(
            "<symbol\\b[^>]*>.*?</symbol\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern MARKER_PATTERN = Pattern.compile(
            "<marker\\b[^>]*>.*?</marker\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );
    private static final Pattern PATTERN_PATTERN = Pattern.compile(
            "<pattern\\b[^>]*>.*?</pattern\\s*>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    private final javafx.scene.Group paths = new javafx.scene.Group();
    private final Rectangle viewportClip = new Rectangle();

    public ModrinthTagIcon(ModrinthTag tag, Color color, double size) {
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        setMouseTransparent(true);
        setClip(viewportClip);

        if (tag == null || tag.getIcon() == null || tag.getIcon().isBlank()) return;

        String svg = tag.getIcon();
        // These elements define clipping/masking or reusable artwork. Their
        // child paths must never be painted as ordinary visible icon paths.
        svg = DEFS_PATTERN.matcher(svg).replaceAll("");
        svg = CLIP_PATH_PATTERN.matcher(svg).replaceAll("");
        svg = MASK_PATTERN.matcher(svg).replaceAll("");
        svg = SYMBOL_PATTERN.matcher(svg).replaceAll("");
        svg = MARKER_PATTERN.matcher(svg).replaceAll("");
        svg = PATTERN_PATTERN.matcher(svg).replaceAll("");

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
                double minX = Double.parseDouble(viewBox.group(1));
                double minY = Double.parseDouble(viewBox.group(2));
                double width = Double.parseDouble(viewBox.group(3));
                double height = Double.parseDouble(viewBox.group(4));
                if (width > 0 && height > 0) {
                    paths.setUserData(new double[]{minX, minY, width, height});
                }
            } catch (NumberFormatException ignored) {
                // Bounds-based scaling below is a safe fallback.
            }
        }
        getChildren().add(paths);
    }

    @Override
    protected void layoutChildren() {
        double width = getWidth();
        double height = getHeight();
        viewportClip.setWidth(Math.max(0, width));
        viewportClip.setHeight(Math.max(0, height));
        if (paths.getChildren().isEmpty() || width <= 0 || height <= 0) return;

        double minX;
        double minY;
        double sourceWidth;
        double sourceHeight;
        Object viewBox = paths.getUserData();
        if (viewBox instanceof double[] dimensions) {
            minX = dimensions[0];
            minY = dimensions[1];
            sourceWidth = dimensions[2];
            sourceHeight = dimensions[3];
        } else {
            var bounds = paths.getLayoutBounds();
            minX = bounds.getMinX();
            minY = bounds.getMinY();
            sourceWidth = Math.max(1, bounds.getWidth());
            sourceHeight = Math.max(1, bounds.getHeight());
        }

        double scale = Math.min(width / sourceWidth, height / sourceHeight);
        paths.setScaleX(scale);
        paths.setScaleY(scale);
        paths.setTranslateX((width - sourceWidth * scale) / 2 - minX * scale);
        paths.setTranslateY((height - sourceHeight * scale) / 2 - minY * scale);
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
            case "btababric" -> Color.web("#C59B70");
            case "javaagent" -> Color.web("#75A9D6");
            case "liteloader" -> Color.web("#A5C56D");
            case "modloader", "risugamismodloader" -> Color.web("#B59B82");
            case "nilloader" -> Color.web("#9B82C9");
            default -> Color.web("#9AA3B2");
        };
    }
}
