package dev.itsdropper.vanta.ui.views;

/**
 * UV coordinates for the outer left-arm layer in a modern 64x64 Minecraft skin.
 *
 * The left arm occupies a different atlas region from the right arm. Keeping
 * this mapping explicit prevents the outer sleeve from accidentally sampling
 * the base arm texture.
 */
public final class SkinAtlasLayout {

    private SkinAtlasLayout() {
    }

    public record Uv(int x, int y, int width, int height) {
    }

    /** Returns front, back, right, left, top and bottom UV rectangles. */
    public static Uv[] leftArmOverlay(boolean slim) {
        int frontWidth = slim ? 3 : 4;
        int topBottomWidth = slim ? 3 : 4;
        return new Uv[]{
                new Uv(52, 52, frontWidth, 12),
                new Uv(60, 52, frontWidth, 12),
                new Uv(48, 52, 4, 12),
                new Uv(56, 52, 4, 12),
                new Uv(52, 48, topBottomWidth, 4),
                new Uv(slim ? 55 : 56, 48, topBottomWidth, 4)
        };
    }
}
