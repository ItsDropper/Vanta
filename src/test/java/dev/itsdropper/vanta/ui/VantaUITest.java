package dev.itsdropper.vanta.ui;

import javafx.geometry.Bounds;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.Path;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VantaUITest {
    @Test
    void spacingUsesFourPixelGrid() {
        assertEquals(4.0, VantaUI.spacing(1));
        assertEquals(16.0, VantaUI.spacing(4));
        assertEquals(32.0, VantaUI.spacing(8));
        assertEquals(0.0, VantaUI.spacing(-2));
    }

    @Test
    void squirclePathIsClosedAndFitsRequestedBounds() {
        Path path = VantaUI.createSquirclePath(240.0, 120.0, 18.0, 4.0);
        Bounds bounds = path.getBoundsInLocal();
        assertEquals(0.0, bounds.getMinX(), 0.001);
        assertEquals(0.0, bounds.getMinY(), 0.001);
        assertEquals(240.0, bounds.getMaxX(), 0.001);
        assertEquals(120.0, bounds.getMaxY(), 0.001);
        assertInstanceOf(ClosePath.class, path.getElements().get(path.getElements().size() - 1));
        assertTrue(path.getElements().size() >= 80);
    }

    @Test
    void squircleRadiusIsClampedToHalfTheShortestDimension() {
        Path path = VantaUI.createSquirclePath(80.0, 40.0, 100.0, 4.0);
        Bounds bounds = path.getBoundsInLocal();
        assertEquals(80.0, bounds.getWidth(), 0.001);
        assertEquals(40.0, bounds.getHeight(), 0.001);
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
