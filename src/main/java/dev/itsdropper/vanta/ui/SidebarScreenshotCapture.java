package dev.itsdropper.vanta.ui;

import dev.itsdropper.vanta.ui.components.Sidebar;
import javafx.animation.PauseTransition;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Captures all top-level sidebar destinations from a running JavaFX scene. */
public final class SidebarScreenshotCapture {
    private static final double PAGE_SETTLE_MILLIS = 700.0;
    private static final int OVERVIEW_COLUMNS = 3;
    private static final int THUMBNAIL_WIDTH = 480;
    private static final int THUMBNAIL_HEIGHT = 300;
    private static final int LABEL_HEIGHT = 38;

    private SidebarScreenshotCapture() {
    }

    public static void capture(
            Parent root,
            Consumer<Sidebar.Page> navigate,
            Path outputDirectory,
            Consumer<Throwable> completion
    ) {
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(navigate, "navigate");
        Objects.requireNonNull(outputDirectory, "outputDirectory");
        Objects.requireNonNull(completion, "completion");

        try {
            Files.createDirectories(outputDirectory);
            List<Sidebar.Page> pages = List.of(Sidebar.Page.values());
            capturePage(root, navigate, outputDirectory, completion, pages, 0);
        } catch (Throwable failure) {
            completion.accept(failure);
        }
    }

    private static void capturePage(
            Parent root,
            Consumer<Sidebar.Page> navigate,
            Path outputDirectory,
            Consumer<Throwable> completion,
            List<Sidebar.Page> pages,
            int index
    ) {
        if (index >= pages.size()) {
            try {
                writeIndex(outputDirectory, pages);
                writeOverview(outputDirectory, pages);
                completion.accept(null);
            } catch (Throwable failure) {
                completion.accept(failure);
            }
            return;
        }

        Sidebar.Page page = pages.get(index);
        try {
            navigate.accept(page);
        } catch (Throwable failure) {
            completion.accept(failure);
            return;
        }

        PauseTransition settle = new PauseTransition(Duration.millis(PAGE_SETTLE_MILLIS));
        settle.setOnFinished(event -> {
            try {
                root.applyCss();
                root.layout();
                if (index == 0) {
                    System.out.printf(Locale.ROOT,
                            "UI capture scene: attached=%s scene=%.0fx%.0f root=%.0fx%.0f%n",
                            root.getScene() != null,
                            root.getScene() == null ? 0.0 : root.getScene().getWidth(),
                            root.getScene() == null ? 0.0 : root.getScene().getHeight(),
                            root.getWidth(),
                            root.getHeight());
                    dumpNodeTree(root, 0, 4);
                }
                SnapshotParameters parameters = new SnapshotParameters();
                parameters.setFill(javafx.scene.paint.Color.web("#0B0D12"));
                WritableImage image = root.snapshot(parameters, null);
                writePng(image, outputDirectory.resolve(fileName(page)));
            } catch (Throwable failure) {
                completion.accept(failure);
                return;
            }
            capturePage(root, navigate, outputDirectory, completion, pages, index + 1);
        });
        settle.play();
    }

    private static void dumpNodeTree(Node node, int depth, int maxDepth) {
        String indent = "  ".repeat(depth);
        var bounds = node.getBoundsInLocal();
        System.out.printf(Locale.ROOT,
                "%s%s visible=%s managed=%s opacity=%.2f bounds=%.1fx%.1f%n",
                indent,
                node.getClass().getSimpleName(),
                node.isVisible(),
                node.isManaged(),
                node.getOpacity(),
                bounds.getWidth(),
                bounds.getHeight());

        if (depth >= maxDepth || !(node instanceof Parent parent)) {
            return;
        }
        int childCount = parent.getChildrenUnmodifiable().size();
        int shown = Math.min(childCount, 24);
        for (int i = 0; i < shown; i++) {
            dumpNodeTree(parent.getChildrenUnmodifiable().get(i), depth + 1, maxDepth);
        }
        if (childCount > shown) {
            System.out.println(indent + "  … " + (childCount - shown) + " more children");
        }
    }

    private static void writePng(WritableImage image, Path destination) throws IOException {
        int width = (int) Math.ceil(image.getWidth());
        int height = (int) Math.ceil(image.getHeight());
        if (width <= 0 || height <= 0) {
            throw new IOException("JavaFX produced an empty screenshot");
        }

        PixelReader reader = image.getPixelReader();
        if (reader == null) {
            throw new IOException("Screenshot has no pixel reader");
        }

        int[] pixels = new int[width * height];
        reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);

        Set<Integer> sampledColors = new HashSet<>();
        int sampleStep = Math.max(1, pixels.length / 12_000);
        for (int i = 0; i < pixels.length; i += sampleStep) {
            sampledColors.add(pixels[i]);
            if (sampledColors.size() >= 64) {
                break;
            }
        }
        if (sampledColors.size() < 32) {
            throw new IOException("Screenshot appears blank or unrendered ("
                    + sampledColors.size() + " sampled colors)");
        }

        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        buffered.setRGB(0, 0, width, height, pixels, 0, width);
        if (!ImageIO.write(buffered, "png", destination.toFile())) {
            throw new IOException("No PNG image writer is available");
        }
    }

    private static void writeIndex(Path outputDirectory, List<Sidebar.Page> pages) throws IOException {
        List<String> lines = new ArrayList<>();
        lines.add("# Vanta sidebar screenshots");
        lines.add("");
        lines.add("Captured from production JavaFX views at the launcher screenshot viewport.");
        lines.add("");
        lines.add("![All sidebar destinations](overview.png)");
        lines.add("");
        for (Sidebar.Page page : pages) {
            lines.add(String.format(Locale.ROOT, "%02d. %s — %s — %s",
                    page.shortcutNumber(), page.title(), fileName(page), page.description()));
        }
        lines.add("");
        Files.writeString(outputDirectory.resolve("index.md"),
                String.join(System.lineSeparator(), lines),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    /** Builds a compact 3×3 review image while preserving each full-size page PNG. */
    private static void writeOverview(Path outputDirectory, List<Sidebar.Page> pages) throws IOException {
        int rows = (int) Math.ceil(pages.size() / (double) OVERVIEW_COLUMNS);
        int cellHeight = THUMBNAIL_HEIGHT + LABEL_HEIGHT;
        BufferedImage overview = new BufferedImage(
                OVERVIEW_COLUMNS * THUMBNAIL_WIDTH,
                rows * cellHeight,
                BufferedImage.TYPE_INT_RGB
        );

        Graphics2D graphics = overview.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            graphics.setColor(new Color(11, 13, 18));
            graphics.fillRect(0, 0, overview.getWidth(), overview.getHeight());
            graphics.setFont(new Font("SansSerif", Font.BOLD, 15));

            for (int i = 0; i < pages.size(); i++) {
                Sidebar.Page page = pages.get(i);
                BufferedImage screenshot = ImageIO.read(outputDirectory.resolve(fileName(page)).toFile());
                if (screenshot == null) {
                    throw new IOException("Could not decode screenshot for " + page.title());
                }

                int column = i % OVERVIEW_COLUMNS;
                int row = i / OVERVIEW_COLUMNS;
                int x = column * THUMBNAIL_WIDTH;
                int y = row * cellHeight;
                graphics.drawImage(screenshot, x, y, THUMBNAIL_WIDTH, THUMBNAIL_HEIGHT, null);
                graphics.setColor(new Color(19, 23, 32));
                graphics.fillRect(x, y + THUMBNAIL_HEIGHT, THUMBNAIL_WIDTH, LABEL_HEIGHT);
                graphics.setColor(new Color(235, 239, 247));
                graphics.drawString(
                        String.format(Locale.ROOT, "%d  %s", page.shortcutNumber(), page.title()),
                        x + 16,
                        y + THUMBNAIL_HEIGHT + 24
                );
            }
        } finally {
            graphics.dispose();
        }

        if (!ImageIO.write(overview, "png", outputDirectory.resolve("overview.png").toFile())) {
            throw new IOException("No PNG image writer is available for the overview");
        }
    }

    private static String fileName(Sidebar.Page page) {
        String slug = page.title().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return String.format(Locale.ROOT, "%02d-%s.png", page.shortcutNumber(), slug);
    }
}
