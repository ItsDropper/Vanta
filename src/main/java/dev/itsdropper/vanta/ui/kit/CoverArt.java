package dev.itsdropper.vanta.ui.kit;

import javafx.beans.property.DoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelWriter;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Procedural pixel-art landscape that gives every instance its own cover.
 *
 * The scene is drawn at 128x72 and scaled with nearest-neighbour sampling so
 * the pixels stay hard-edged. It is seeded from the instance name, so an
 * instance always gets the same picture, and the palette follows the loader
 * (Fabric dusk, Forge steel, NeoForge ember, Quilt night, Vanilla day).
 * Nothing here is copied from the game or any other launcher.
 */
public final class CoverArt extends StackPane {

    private static final int W = 128;
    private static final int H = 72;
    private static final Map<String, Image> CACHE = new HashMap<>();

    private static final int[][] BAYER = {
            {0, 8, 2, 10},
            {12, 4, 14, 6},
            {3, 11, 1, 9},
            {15, 7, 13, 5}
    };

    private final DoubleProperty corner = new SimpleDoubleProperty(0);
    private final Rectangle clip = new Rectangle();

    public CoverArt(String seed, String loader) {
        getStyleClass().add("cover-art");
        ImageView view = new ImageView(image(seed, loader));
        view.setSmooth(false);
        view.setPreserveRatio(false);
        view.fitWidthProperty().bind(widthProperty());
        view.fitHeightProperty().bind(heightProperty());
        view.setMouseTransparent(true);
        getChildren().add(view);

        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        clip.arcWidthProperty().bind(corner.multiply(2));
        clip.arcHeightProperty().bind(corner.multiply(2));
        setClip(clip);
        setMinSize(0, 0);
    }

    /** Rounds the picture's corners (radius in pixels). */
    public CoverArt rounded(double radius) {
        corner.set(radius);
        return this;
    }

    /** Fixes the size and returns this for chaining. */
    public CoverArt sized(double width, double height) {
        setMinSize(width, height);
        setPrefSize(width, height);
        setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        return this;
    }

    // ---------------------------------------------------------------- drawing

    private static synchronized Image image(String seed, String loader) {
        String key = LoaderTheme.key(loader) + "|" + (seed == null ? "" : seed);
        return CACHE.computeIfAbsent(key, ignored -> render(seed == null ? "" : seed, LoaderTheme.key(loader)));
    }

    private record Palette(int[] sky, int sun, boolean night, boolean clouds,
                           int far, int mid, int grass, int grassLight, int dirt, int stone, int tree, int treeLight) {
    }

    private static int rgb(int hex) {
        return 0xFF000000 | hex;
    }

    private static Palette palette(String loader) {
        return switch (loader) {
            case "fabric" -> new Palette(
                    new int[]{rgb(0x1B2430), rgb(0x34475A), rgb(0x7C8C94), rgb(0xCDB28C), rgb(0xE8C48A)},
                    rgb(0xFFE6A8), false, false,
                    rgb(0x56686F), rgb(0x3A5250), rgb(0x7DA05A), rgb(0x9CBF70), rgb(0x5A4331), rgb(0x6C645C),
                    rgb(0x2C5738), rgb(0x3F7549));
            case "forge" -> new Palette(
                    new int[]{rgb(0x1C2D4F), rgb(0x34527F), rgb(0x6C8DBB), rgb(0xA9C2E6), rgb(0xCBDAF0)},
                    rgb(0xF4F8FF), false, true,
                    rgb(0x546E94), rgb(0x3C5578), rgb(0x6EA05A), rgb(0x8CC070), rgb(0x4E3F34), rgb(0x5D6470),
                    rgb(0x2C4D3A), rgb(0x3E6B4D));
            case "neoforge" -> new Palette(
                    new int[]{rgb(0x24101C), rgb(0x5B2430), rgb(0xA8453A), rgb(0xE4803F), rgb(0xF6B060)},
                    rgb(0xFFD79A), false, false,
                    rgb(0x6A3638), rgb(0x472629), rgb(0x8E9040), rgb(0xB0AE55), rgb(0x4A2F25), rgb(0x5E5048),
                    rgb(0x3B3C22), rgb(0x56582F));
            case "quilt" -> new Palette(
                    new int[]{rgb(0x0C091B), rgb(0x1E1647), rgb(0x3E2D80), rgb(0x6D55B8), rgb(0x9B84DC)},
                    rgb(0xEFE9FF), true, false,
                    rgb(0x372B68), rgb(0x261E4D), rgb(0x5B8E7C), rgb(0x7CB09B), rgb(0x2F2640), rgb(0x433A5C),
                    rgb(0x1E3B3C), rgb(0x2D5656));
            case "vanilla" -> new Palette(
                    new int[]{rgb(0x4C95DC), rgb(0x78B4EA), rgb(0x9FD0F2), rgb(0xBFE3F5), rgb(0xD8F0F8)},
                    rgb(0xFFF3B0), false, true,
                    rgb(0x7FB3A0), rgb(0x5C9A6A), rgb(0x6DBE45), rgb(0x8ADA5F), rgb(0x7A5638), rgb(0x7B7B7B),
                    rgb(0x2F7A38), rgb(0x449A4C));
            default -> new Palette(
                    new int[]{rgb(0x15171B), rgb(0x23262D), rgb(0x3A3E48), rgb(0x585C66), rgb(0x7A7E88)},
                    rgb(0xE8E6E0), true, false,
                    rgb(0x3A3E48), rgb(0x2B2E36), rgb(0x5E6B58), rgb(0x7A8873), rgb(0x3A332E), rgb(0x4B4B50),
                    rgb(0x2A3A33), rgb(0x3A4E45));
        };
    }

    private static Image render(String seed, String loader) {
        Palette p = palette(loader);
        SplittableRandom rng = new SplittableRandom(seed.hashCode() * 31L + loader.hashCode());
        int[] px = new int[W * H];

        // sky: banded gradient, dithered between bands
        int bands = p.sky.length;
        for (int y = 0; y < H; y++) {
            double t = Math.min(1.0, y / (H * 0.72));
            double scaled = t * (bands - 1);
            int low = (int) Math.floor(scaled);
            int high = Math.min(bands - 1, low + 1);
            double frac = scaled - low;
            for (int x = 0; x < W; x++) {
                double threshold = (BAYER[y & 3][x & 3] + 0.5) / 16.0;
                px[y * W + x] = frac > threshold ? p.sky[high] : p.sky[low];
            }
        }

        if (p.night) {
            int stars = 34 + rng.nextInt(18);
            for (int i = 0; i < stars; i++) {
                int x = rng.nextInt(W);
                int y = rng.nextInt((int) (H * 0.5));
                px[y * W + x] = rgb(0xDAD6F5);
                if (rng.nextInt(7) == 0 && x + 1 < W) {
                    px[y * W + x + 1] = rgb(0x8F89C8);
                }
            }
        }

        // sun or moon, with a dithered halo
        int sunX = 24 + rng.nextInt(W - 48);
        int sunY = 9 + rng.nextInt(14);
        int radius = 5 + rng.nextInt(3);
        for (int y = -radius - 4; y <= radius + 4; y++) {
            for (int x = -radius - 4; x <= radius + 4; x++) {
                int tx = sunX + x;
                int ty = sunY + y;
                if (tx < 0 || ty < 0 || tx >= W || ty >= H) {
                    continue;
                }
                double d = Math.sqrt(x * x + y * y);
                if (d <= radius) {
                    px[ty * W + tx] = p.sun;
                } else if (d <= radius + 3 && ((tx + ty) & 1) == 0) {
                    px[ty * W + tx] = blend(px[ty * W + tx], p.sun, 0.28);
                }
            }
        }

        if (p.clouds) {
            int count = 2 + rng.nextInt(3);
            for (int i = 0; i < count; i++) {
                int cx = rng.nextInt(W - 24);
                int cy = 6 + rng.nextInt(20);
                int width = 12 + rng.nextInt(12);
                for (int row = 0; row < 3; row++) {
                    int inset = row == 1 ? 0 : 2 + rng.nextInt(3);
                    for (int x = inset; x < width - inset; x++) {
                        int tx = cx + x;
                        int ty = cy + row;
                        if (tx < W && ty < H) {
                            px[ty * W + tx] = blend(px[ty * W + tx], rgb(0xFFFFFF), row == 2 ? 0.30 : 0.50);
                        }
                    }
                }
            }
        }

        // three hill layers: far haze, mid, near with grass and soil
        hills(px, rng, p.far, 0.50, 9, 0.0);
        hills(px, rng, p.mid, 0.63, 8, 0.0);
        int[] near = hills(px, rng, p.grass, 0.80, 6, 1.0);

        // soil and stone strata under the near layer
        for (int x = 0; x < W; x++) {
            int top = near[x];
            for (int y = top; y < H; y++) {
                int depth = y - top;
                int colour;
                if (depth == 0) {
                    colour = p.grassLight;
                } else if (depth < 2) {
                    colour = p.grass;
                } else if (depth < 7 + (x * 7 % 3)) {
                    colour = p.dirt;
                } else {
                    colour = p.stone;
                }
                // speckle for texture
                if (depth > 2 && rng.nextInt(9) == 0) {
                    colour = blend(colour, rgb(0x000000), 0.18);
                }
                px[y * W + x] = colour;
            }
        }

        // trees on the near layer
        int trees = 4 + rng.nextInt(5);
        for (int i = 0; i < trees; i++) {
            int tx = 5 + rng.nextInt(W - 10);
            int base = near[tx];
            int height = 5 + rng.nextInt(4);
            boolean pine = rng.nextBoolean();
            for (int y = 0; y < 3; y++) {
                put(px, tx, base - 1 - y, p.dirt);
            }
            for (int layer = 0; layer < height; layer++) {
                int span = pine ? 1 + (height - layer) / 2 : (layer < height - 2 ? 3 : 2);
                int y = base - 3 - layer;
                for (int x = -span; x <= span; x++) {
                    put(px, tx + x, y, (x + layer) % 3 == 0 ? p.treeLight : p.tree);
                }
            }
        }

        WritableImage image = new WritableImage(W, H);
        PixelWriter writer = image.getPixelWriter();
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                writer.setArgb(x, y, px[y * W + x]);
            }
        }
        return image;
    }

    /** Fills a ridge layer and returns the ridge height per column. */
    private static int[] hills(int[] px, SplittableRandom rng, int colour, double baseline, int amplitude, double flatten) {
        double p1 = rng.nextDouble() * 6.28;
        double p2 = rng.nextDouble() * 6.28;
        double f1 = 0.035 + rng.nextDouble() * 0.03;
        double f2 = 0.08 + rng.nextDouble() * 0.05;
        int[] ridge = new int[W];
        for (int x = 0; x < W; x++) {
            double wave = Math.sin(x * f1 + p1) * 0.65 + Math.sin(x * f2 + p2) * 0.35;
            wave *= 1.0 - flatten * 0.35;
            int top = (int) Math.round(H * baseline + wave * amplitude);
            top = Math.max(2, Math.min(H - 2, top));
            ridge[x] = top;
            for (int y = top; y < H; y++) {
                px[y * W + x] = colour;
            }
        }
        return ridge;
    }

    private static void put(int[] px, int x, int y, int colour) {
        if (x >= 0 && y >= 0 && x < W && y < H) {
            px[y * W + x] = colour;
        }
    }

    private static int blend(int from, int to, double amount) {
        int r = (int) (((from >> 16) & 255) * (1 - amount) + ((to >> 16) & 255) * amount);
        int g = (int) (((from >> 8) & 255) * (1 - amount) + ((to >> 8) & 255) * amount);
        int b = (int) ((from & 255) * (1 - amount) + (to & 255) * amount);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
