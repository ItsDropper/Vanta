package dev.itsdropper.vanta.ui;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.layout.Parent;
import javafx.scene.layout.Region;
import javafx.scene.shape.ClosePath;
import javafx.scene.shape.LineTo;
import javafx.scene.shape.MoveTo;
import javafx.scene.shape.Path;
import java.util.Objects;

/** Shared visual primitives for Vanta's JavaFX interface. */
public final class VantaUI {
    public static final double GRID_UNIT = 4.0;
    public static final double RADIUS_CONTROL = 10.0;
    public static final double RADIUS_CARD = 14.0;
    public static final double RADIUS_PANEL = 18.0;
    public static final double RADIUS_WINDOW = 22.0;
    public static final double SQUIRCLE_EXPONENT = 4.0;
    private static final int CURVE_SEGMENTS = 20;

    public enum SurfaceRole {
        SIDEBAR("vanta-surface-sidebar", RADIUS_PANEL),
        PANEL("vanta-surface-panel", RADIUS_PANEL),
        CARD("vanta-surface-card", RADIUS_CARD),
        INSET("vanta-surface-inset", RADIUS_CONTROL);
        private final String styleClass;
        private final double radius;
        SurfaceRole(String styleClass, double radius) {
            this.styleClass = styleClass;
            this.radius = radius;
        }
    }

    public enum ButtonVariant {
        PRIMARY("vanta-button-primary"),
        SECONDARY("vanta-button-secondary"),
        QUIET("vanta-button-quiet"),
        NAVIGATION("vanta-button-navigation");
        private final String styleClass;
        ButtonVariant(String styleClass) {
            this.styleClass = styleClass;
        }
    }

    private VantaUI() { }

    public static double spacing(int units) {
        return Math.max(0, units) * GRID_UNIT;
    }

    public static void install(Parent root) {
        Objects.requireNonNull(root, "root");
        addStyleClass(root, "vanta-ui-root");
        ThemeManager.apply(root, ThemeManager.loadAccent());
    }

    public static Button styleButton(Button button, ButtonVariant variant) {
        Objects.requireNonNull(button, "button");
        Objects.requireNonNull(variant, "variant");
        addStyleClass(button, "vanta-button");
        addStyleClass(button, variant.styleClass);
        return button;
    }

    public static void applySurface(Region region, SurfaceRole role) {
        Objects.requireNonNull(region, "region");
        Objects.requireNonNull(role, "role");
        addStyleClass(region, "vanta-surface");
        addStyleClass(region, role.styleClass);
        applySquircle(region, role.radius);
    }

    public static void applySquircle(Region region, double radius) {
        Objects.requireNonNull(region, "region");
        if (!Double.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("radius must be finite and non-negative");
        }
        Runnable updateClip = () -> {
            double width = region.getWidth();
            double height = region.getHeight();
            if (width <= 0 || height <= 0) return;
            region.setClip(createSquirclePath(width, height,
                    Math.min(radius, Math.min(width, height) / 2.0), SQUIRCLE_EXPONENT));
        };
        region.widthProperty().addListener((observable, previous, current) -> updateClip.run());
        region.heightProperty().addListener((observable, previous, current) -> updateClip.run());
        updateClip.run();
    }

    public static Path createSquirclePath(double width, double height, double radius, double exponent) {
        if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("width and height must be finite and positive");
        }
        if (!Double.isFinite(radius) || radius < 0) {
            throw new IllegalArgumentException("radius must be finite and non-negative");
        }
        if (!Double.isFinite(exponent) || exponent < 2 || exponent > 8) {
            throw new IllegalArgumentException("exponent must be between 2 and 8");
        }

        double r = Math.min(radius, Math.min(width, height) / 2.0);
        Path path = new Path();
        if (r == 0) {
            path.getElements().addAll(new MoveTo(0, 0), new LineTo(width, 0),
                    new LineTo(width, height), new LineTo(0, height), new ClosePath());
            return path;
        }

        double power = 2.0 / exponent;
        path.getElements().add(new MoveTo(r, 0));
        path.getElements().add(new LineTo(width - r, 0));
        for (int i = 1; i <= CURVE_SEGMENTS; i++) {
            double angle = Math.PI * 0.5 * i / CURVE_SEGMENTS;
            path.getElements().add(new LineTo(
                    width - r + r * Math.pow(Math.sin(angle), power),
                    r - r * Math.pow(Math.cos(angle), power)));
        }

        path.getElements().add(new LineTo(width, height - r));
        for (int i = 1; i <= CURVE_SEGMENTS; i++) {
            double angle = Math.PI * 0.5 * i / CURVE_SEGMENTS;
            path.getElements().add(new LineTo(
                    width - r + r * Math.pow(Math.cos(angle), power),
                    height - r + r * Math.pow(Math.sin(angle), power)));
        }

        path.getElements().add(new LineTo(r, height));
        for (int i = 1; i <= CURVE_SEGMENTS; i++) {
            double angle = Math.PI * 0.5 * i / CURVE_SEGMENTS;
            path.getElements().add(new LineTo(
                    r - r * Math.pow(Math.sin(angle), power),
                    height - r + r * Math.pow(Math.cos(angle), power)));
        }

        path.getElements().add(new LineTo(0, r));
        for (int i = 1; i <= CURVE_SEGMENTS; i++) {
            double angle = Math.PI * 0.5 * i / CURVE_SEGMENTS;
            path.getElements().add(new LineTo(
                    r - r * Math.pow(Math.cos(angle), power),
                    r - r * Math.pow(Math.sin(angle), power)));
        }
        path.getElements().add(new ClosePath());
        return path;
    }

    private static void addStyleClass(Node node, String styleClass) {
        if (!node.getStyleClass().contains(styleClass)) node.getStyleClass().add(styleClass);
    }
}
