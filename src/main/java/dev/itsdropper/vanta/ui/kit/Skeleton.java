package dev.itsdropper.vanta.ui.kit;

import javafx.animation.Animation;
import javafx.animation.Interpolator;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.geometry.Pos;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

/** Placeholder card that breathes while content loads, so waiting feels shorter. */
public final class Skeleton extends VBox {

    private final Timeline pulse;

    public Skeleton(double height, int index) {
        super(10);
        getStyleClass().add("skeleton-card");
        setMinHeight(height);
        setPrefHeight(height);
        setAlignment(Pos.TOP_LEFT);
        Region icon = bar("skeleton-icon", 52, 52);
        Region title = bar("skeleton-line", 150, 12);
        Region sub = bar("skeleton-line", 90, 10);
        Region body = bar("skeleton-line", 230, 10);
        VBox names = new VBox(8, title, sub);
        javafx.scene.layout.HBox head = new javafx.scene.layout.HBox(12, icon, names);
        getChildren().addAll(head, body);

        pulse = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(opacityProperty(), 0.55, Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(900), new KeyValue(opacityProperty(), 1.0, Interpolator.EASE_BOTH)));
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setDelay(Duration.millis(index * 110L));
        sceneProperty().addListener((obs, old, scene) -> {
            if (scene == null) {
                pulse.stop();
            } else {
                pulse.play();
            }
        });
    }

    private static Region bar(String style, double w, double h) {
        Region r = new Region();
        r.getStyleClass().add(style);
        r.setMinSize(w, h);
        r.setPrefSize(w, h);
        r.setMaxSize(w, h);
        return r;
    }

    public static void fill(javafx.scene.layout.Pane target, int count, double height) {
        target.getChildren().clear();
        for (int i = 0; i < count; i++) {
            target.getChildren().add(new Skeleton(height, i));
        }
    }
}
