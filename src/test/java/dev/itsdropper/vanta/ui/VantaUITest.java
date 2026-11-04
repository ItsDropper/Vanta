package dev.itsdropper.vanta.ui;

import javafx.scene.paint.Color;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import javafx.scene.shape.PathElement;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VantaUITest {
    private static double[] coordinateBounds(Path path) {
        double minX = Double.POSITIVE_INFINITY;
        double minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY;
        double maxY = Double.NEGATIVE_INFINITY;

        for (PathElement element : path.getElements()) {
            double x;
            double y;
            if (element instanceof MoveTo move) {
                x = move.getX();
                y = move.getY();
            } else if (element instanceof LineTo line) {
                x = line.getX();
                y = line.getY();
            } else {
                continue;
            }

            assertTrue(Double.isFinite(x), "Path X coordinate must be finite");
            assertTrue(Double.isFinite(y), "Path Y coordinate must be finite");
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
        }

        return new double[]{minX, minY, maxX, maxY};
    }

    @Test
    void spacingUsesFourPixelGrid() {
        assertEquals(4.0, VantaUI.spacing(1));
        assertEquals(16.0, VantaUI.spacing(4));
        assertEquals(32.0, VantaUI.spacing(8));
        assertThrows(IllegalArgumentException.class, () -> VantaUI.spacing(-2));
    }

    @Test
    void textRolesExposeSemanticStyleClasses() {
        assertEquals("vanta-type-page-title", VantaUI.TextRole.PAGE_TITLE.styleClass());
        assertEquals("vanta-type-muted", VantaUI.TextRole.MUTED.styleClass());
        assertEquals(7, VantaUI.TextRole.values().length);
    }

    @Test
    void squirclePathIsClosedAndFitsRequestedBounds() {
        Path path = VantaUI.createSquirclePath(240.0, 120.0, 18.0, 4.0);
        double[] bounds = coordinateBounds(path);
        assertEquals(0.0, bounds[0], 0.001);
        assertEquals(0.0, bounds[1], 0.001);
        assertEquals(240.0, bounds[2], 0.001);
        assertEquals(120.0, bounds[3], 0.001);
        assertInstanceOf(ClosePath.class, path.getElements().get(path.getElements().size() - 1));
        assertTrue(path.getElements().size() >= 80);
        path.getElements().forEach(element -> {
            if (element instanceof javafx.scene.shape.MoveTo move) {
                assertTrue(Double.isFinite(move.getX()));
                assertTrue(Double.isFinite(move.getY()));
            } else if (element instanceof javafx.scene.shape.LineTo line) {
                assertTrue(Double.isFinite(line.getX()));
                assertTrue(Double.isFinite(line.getY()));
            }
        });
    }

    @Test
    void squircleRadiusIsClampedToHalfTheShortestDimension() {
        Path path = VantaUI.createSquirclePath(80.0, 40.0, 100.0, 4.0);
        double[] bounds = coordinateBounds(path);
        assertEquals(0.0, bounds[0], 0.001);
        assertEquals(0.0, bounds[1], 0.001);
        assertEquals(80.0, bounds[2], 0.001);
        assertEquals(40.0, bounds[3], 0.001);
    }

    @Test
    void calculatesWcagContrastAndThresholds() {
        assertEquals(21.0, VantaUI.contrastRatio(Color.WHITE, Color.BLACK), 0.001);
        Color mediumGray = Color.rgb(119, 119, 119);
        assertFalse(VantaUI.meetsWcagAA(mediumGray, Color.WHITE, false));
        assertTrue(VantaUI.meetsWcagAA(mediumGray, Color.WHITE, true));
    }

    @Test
    void ensureContrastRaisesForegroundToTheRequestedThreshold() {
        Color darkSurface = Color.rgb(37, 42, 48);
        Color dimText = Color.rgb(105, 113, 125);
        assertFalse(VantaUI.meetsWcagAA(dimText, darkSurface, false));

        Color adjusted = VantaUI.ensureContrast(dimText, darkSurface, 4.5);
        assertTrue(VantaUI.meetsWcagAA(adjusted, darkSurface, false));
        assertTrue(adjusted.getRed() >= dimText.getRed());
    }

    @Test
    void ensureContrastLeavesReadableColorsUnchangedAndRejectsBadThresholds() {
        assertEquals(Color.WHITE, VantaUI.ensureContrast(Color.WHITE, Color.BLACK, 4.5));
        assertThrows(IllegalArgumentException.class,
                () -> VantaUI.ensureContrast(Color.WHITE, Color.BLACK, 22.0));
    }

    @Test
    void choosesTheHigherContrastTextColor() {
        assertEquals(Color.WHITE, VantaUI.accessibleTextColor(Color.BLACK));
        assertEquals(Color.BLACK, VantaUI.accessibleTextColor(Color.WHITE));
    }

    @Test
    void rejectsInvalidSquircleDimensionsAndExponents() {
        assertThrows(IllegalArgumentException.class,
                () -> VantaUI.createSquirclePath(0.0, 100.0, 12.0, 4.0));
        assertThrows(IllegalArgumentException.class,
                () -> VantaUI.createSquirclePath(100.0, 100.0, 12.0, 1.5));
        assertThrows(IllegalArgumentException.class,
                () -> VantaUI.createSquirclePath(100.0, 100.0, -1.0, 4.0));
    }
}
