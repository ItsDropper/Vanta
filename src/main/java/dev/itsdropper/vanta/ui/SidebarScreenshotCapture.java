package dev.itsdropper.vanta.ui;

import dev.itsdropper.vanta.ui.components.Sidebar;
import javafx.animation.PauseTransition;
import javafx.scene.Parent;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Consumer;

/** Captures all top-level sidebar destinations from a running JavaFX scene. */
public final class SidebarScreenshotCapture {
    private static final double PAGE_SETTLE_MILLIS = 700.0;
    private SidebarScreenshotCapture() { }

    public static void capture(Parent root, Consumer<Sidebar.Page> navigate,
                               Path outputDirectory, Consumer<Throwable> completion) {
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

    private static void capturePage(Parent root, Consumer<Sidebar.Page> navigate,
                                   Path outputDirectory, Consumer<Throwable> completion,
                                   List<Sidebar.Page> pages, int index) {
        if (index >= pages.size()) {
            try {
                writeIndex(outputDirectory, pages);
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
                WritableImage image = root.snapshot(new SnapshotParameters(), null);
                writePng(image, outputDirectory.resolve(fileName(page)));
            } catch (Throwable failure) {
                completion.accept(failure);
                return;
            }
            capturePage(root, navigate, outputDirectory, completion, pages, index + 1);
        });
        settle.play();
    }

    private static void writePng(WritableImage image, Path destination) throws IOException {
        int width = (int) Math.ceil(image.getWidth());
        int height = (int) Math.ceil(image.getHeight());
        if (width <= 0 || height <= 0) throw new IOException("JavaFX produced an empty screenshot");
        PixelReader reader = image.getPixelReader();
        if (reader == null) throw new IOException("Screenshot has no pixel reader");
        int[] pixels = new int[width * height];
        reader.getPixels(0, 0, width, height, PixelFormat.getIntArgbInstance(), pixels, 0, width);
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
        for (Sidebar.Page page : pages) {
            lines.add(String.format(Locale.ROOT, "%02d. %s — %s — %s",
                    page.shortcutNumber(), page.title(), fileName(page), page.description()));
        }
        lines.add("");
        Files.writeString(outputDirectory.resolve("index.md"), String.join(System.lineSeparator(), lines),
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
    }

    private static String fileName(Sidebar.Page page) {
        String slug = page.title().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return String.format(Locale.ROOT, "%02d-%s.png", page.shortcutNumber(), slug);
    }
}
