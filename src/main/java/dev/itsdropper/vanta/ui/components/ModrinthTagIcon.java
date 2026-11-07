package dev.itsdropper.vanta.ui.components;

import dev.itsdropper.vanta.launcher.modrinth.ModrinthTag;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.PNGTranscoder;

import java.io.ByteArrayOutputStream;
import java.io.StringReader;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Renders the complete SVG artwork returned by Modrinth's tag API.
 *
 * SVGPath only supports path data; it silently loses transforms, groups,
 * strokes, circles, polygons, masks and other valid SVG constructs. Batik
 * rasterizes the complete document once, and the resulting image is cached
 * so repeated tags do not repeatedly parse or transcode the same artwork.
 */
public final class ModrinthTagIcon extends Region {
    private static final int RASTER_SIZE = 64;
    private static final ConcurrentMap<String, Image> IMAGE_CACHE = new ConcurrentHashMap<>();

    private final ImageView imageView = new ImageView();

    public ModrinthTagIcon(ModrinthTag tag, Color color, double size) {
        setMinSize(size, size);
        setPrefSize(size, size);
        setMaxSize(size, size);
        setMouseTransparent(true);

        imageView.setFitWidth(size);
        imageView.setFitHeight(size);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.setMouseTransparent(true);

        Image image = tag == null ? null : loadImage(tag.getIcon(), color);
        if (image != null && !image.isError()) {
            imageView.setImage(image);
            getChildren().add(imageView);
        } else {
            getChildren().add(createFallback(tag, color, size));
        }
    }

    private static Image loadImage(String svg, Color color) {
        if (svg == null || svg.isBlank()) return null;
        String tint = toHex(color == null ? Color.GRAY : color);
        String cacheKey = tint + '\\n' + svg;
        return IMAGE_CACHE.computeIfAbsent(cacheKey, ignored -> transcode(svg, tint));
    }

    private static Image transcode(String source, String tint) {
        try {
            String svg = source.replaceAll("(?i)currentColor", tint);
            int closingTag = svg.toLowerCase(Locale.ROOT).lastIndexOf("</svg>");
            if (closingTag >= 0) {
                String style = "<style>"
                        + "path:not([fill='none']),circle:not([fill='none']),"
                        + "rect:not([fill='none']),ellipse:not([fill='none']),"
                        + "polygon:not([fill='none']),polyline:not([fill='none'])"
                        + "{fill:" + tint + " !important;}"
                        + "path[stroke]:not([stroke='none']),circle[stroke]:not([stroke='none']),"
                        + "rect[stroke]:not([stroke='none']),ellipse[stroke]:not([stroke='none']),"
                        + "polygon[stroke]:not([stroke='none']),polyline[stroke]:not([stroke='none'])"
                        + "{stroke:" + tint + " !important;}"
                        + "</style>";
                svg = svg.substring(0, closingTag) + style + svg.substring(closingTag);
            }

            PNGTranscoder transcoder = new PNGTranscoder();
            transcoder.addTranscodingHint(PNGTranscoder.KEY_WIDTH, (float) RASTER_SIZE);
            transcoder.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, (float) RASTER_SIZE);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            transcoder.transcode(
                    new TranscoderInput(new StringReader(svg)),
                    new TranscoderOutput(output)
            );
            byte[] bytes = output.toByteArray();
            if (bytes.length == 0) return null;
            Image image = new Image(new java.io.ByteArrayInputStream(bytes), 0, 0, true, true);
            return image.isError() ? null : image;
        } catch (Exception ignored) {
            // The tag remains readable and receives a deterministic fallback.
            return null;
        }
    }

    private static StackPane createFallback(ModrinthTag tag, Color color, double size) {
        Color accent = color == null ? Color.GRAY : color;
        Circle background = new Circle(size * 0.46, accent.deriveColor(0, 0.65, 1.0, 0.24));
        Label glyph = new Label(fallbackGlyph(tag));
        glyph.setTextFill(accent);
        glyph.setStyle("-fx-font-size: " + Math.max(8, size * 0.62)
                + "px; -fx-font-weight: bold; -fx-padding: 0;");
        StackPane fallback = new StackPane(background, glyph);
        fallback.setMinSize(size, size);
        fallback.setPrefSize(size, size);
        fallback.setMaxSize(size, size);
        fallback.setMouseTransparent(true);
        return fallback;
    }

    private static String fallbackGlyph(ModrinthTag tag) {
        String name = tag == null ? null : tag.getName();
        if (name == null || name.isBlank()) return "•";
        String normalized = name.replaceAll("[^\\p{Alnum}]", "");
        if (normalized.isBlank()) return "•";
        return normalized.substring(0, 1).toUpperCase(Locale.ROOT);
    }

    private static String toHex(Color color) {
        return String.format(Locale.ROOT, "#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    @Override
    protected void layoutChildren() {
        imageView.relocate(0, 0);
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
