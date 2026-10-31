package dev.itsdropper.vanta.ui.views;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class SkinAtlasLayoutTest {

    @Test
    void classicLeftArmOverlayUsesTheLeftArmAtlasRegion() {
        assertArrayEquals(new SkinAtlasLayout.Uv[]{
                new SkinAtlasLayout.Uv(52, 52, 4, 12),
                new SkinAtlasLayout.Uv(60, 52, 4, 12),
                new SkinAtlasLayout.Uv(48, 52, 4, 12),
                new SkinAtlasLayout.Uv(56, 52, 4, 12),
                new SkinAtlasLayout.Uv(52, 48, 4, 4),
                new SkinAtlasLayout.Uv(56, 48, 4, 4)
        }, SkinAtlasLayout.leftArmOverlay(false));
    }

    @Test
    void slimLeftArmOverlayKeepsThreePixelFrontAndTopFaces() {
        assertArrayEquals(new SkinAtlasLayout.Uv[]{
                new SkinAtlasLayout.Uv(52, 52, 3, 12),
                new SkinAtlasLayout.Uv(60, 52, 3, 12),
                new SkinAtlasLayout.Uv(48, 52, 4, 12),
                new SkinAtlasLayout.Uv(56, 52, 4, 12),
                new SkinAtlasLayout.Uv(52, 48, 3, 4),
                new SkinAtlasLayout.Uv(55, 48, 3, 4)
        }, SkinAtlasLayout.leftArmOverlay(true));
    }
}
