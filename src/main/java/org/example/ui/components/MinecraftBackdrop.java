package org.example.ui.components;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;

public final class MinecraftBackdrop extends StackPane {

    private final Canvas canvas = new Canvas();
    private final AnimationTimer animator;
    private long startNanos;

    public MinecraftBackdrop() {
        getStyleClass().add("minecraft-backdrop");
        setMouseTransparent(true);

        canvas.widthProperty().bind(widthProperty());
        canvas.heightProperty().bind(heightProperty());
        getChildren().add(canvas);

        animator = new AnimationTimer() {
            @Override
            public void handle(long now) {
                if (startNanos == 0) {
                    startNanos = now;
                }
                draw((now - startNanos) / 1_000_000_000.0);
            }
        };

        animator.start();
    }

    private void draw(double time) {
        double width = getWidth();
        double height = getHeight();

        if (width <= 0 || height <= 0) {
            return;
        }

        GraphicsContext g = canvas.getGraphicsContext2D();

        g.setFill(Color.web("#0b121b"));
        g.fillRect(0, 0, width, height);

        double horizon = height * 0.57;

        g.setFill(Color.web("#15263a"));
        g.fillRect(0, 0, width, horizon);

        g.setFill(Color.rgb(190, 210, 225, 0.08));
        for (int i = 0; i < 5; i++) {
            double x = ((time * (8 + i * 2) + i * 180) % (width + 220)) - 220;
            double y = 34 + i * 23;
            cloud(g, x, y, 1.0 + i * 0.08);
        }

        g.setFill(Color.web("#172d3a"));
        mountain(g, 0, horizon + 4, 190, 130);
        mountain(g, 160, horizon - 6, 260, 155);
        mountain(g, 410, horizon + 10, 210, 120);
        mountain(g, 610, horizon - 4, 300, 165);

        double groundTop = horizon + 55;
        g.setFill(Color.web("#1c3b32"));
        g.fillRect(0, groundTop, width, height - groundTop);

        g.setFill(Color.web("#284d3b"));
        for (int x = -20; x < width + 40; x += 34) {
            double blockHeight = 18 + ((x / 34) % 3) * 7;
            g.fillRect(x, groundTop - blockHeight, 30, blockHeight);
        }

        g.setFill(Color.rgb(48, 101, 119, 0.55));
        g.fillRect(width * 0.56, groundTop + 28, width * 0.44, height * 0.18);

        g.setFill(Color.rgb(8, 16, 22, 0.50));
        for (int x = 0; x < width; x += 58) {
            double h = 22 + ((x / 58) % 4) * 8;
            g.fillRect(x, height - h, 54, h);
        }

        g.setFill(Color.rgb(4, 8, 13, 0.30));
        g.fillRect(0, 0, width, height * 0.24);

        g.setFill(Color.rgb(4, 8, 13, 0.38));
        g.fillRect(0, height * 0.72, width, height * 0.28);
    }

    private static void cloud(GraphicsContext g, double x, double y, double scale) {
        double s = 28 * scale;
        g.fillRect(x, y, s * 3.0, s * 0.55);
        g.fillRect(x + s * 0.55, y - s * 0.32, s * 1.35, s * 0.55);
        g.fillRect(x + s * 1.45, y - s * 0.18, s * 1.05, s * 0.42);
    }

    private static void mountain(
            GraphicsContext g,
            double x,
            double base,
            double width,
            double height
    ) {
        double[] xs = {
                x,
                x + width * 0.28,
                x + width * 0.52,
                x + width * 0.76,
                x + width
        };

        double[] ys = {
                base,
                base - height * 0.62,
                base - height,
                base - height * 0.48,
                base
        };

        g.fillPolygon(xs, ys, xs.length);
    }
}
