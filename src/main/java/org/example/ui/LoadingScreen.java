package org.example.ui;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.util.Duration;

public final class LoadingScreen {

    private final StackPane root = new StackPane();
    private final Label status = new Label("INITIALIZING VANTA");
    private final Region progressFill = new Region();

    public LoadingScreen() {
        root.getStyleClass().add("vanta-loading");

        StackPane atmosphere = new StackPane();
        atmosphere.getStyleClass().add("loading-atmosphere");

        Circle glow = new Circle(170);
        glow.getStyleClass().add("loading-glow");

        Circle ringOuter = new Circle(70);
        ringOuter.getStyleClass().add("loading-ring-outer");

        Circle ringInner = new Circle(48);
        ringInner.getStyleClass().add("loading-ring-inner");

        StackPane mark = new StackPane();
        mark.getStyleClass().add("loading-mark");
        mark.getChildren().addAll(ringOuter, ringInner);

        Line slash = new Line(-22, 22, 22, -22);
        slash.getStyleClass().add("loading-slash");
        mark.getChildren().add(slash);

        atmosphere.getChildren().add(glow);

        Label logo = new Label("VANTA");
        logo.getStyleClass().add("loading-logo");

        Label subtitle = new Label("ENVIRONMENT MANAGER");
        subtitle.getStyleClass().add("loading-subtitle");

        status.getStyleClass().add("loading-status");

        StackPane progressTrack = new StackPane();
        progressTrack.getStyleClass().add("loading-progress-track");

        progressFill.getStyleClass().add("loading-progress-fill");
        progressFill.setPrefWidth(320);
        progressFill.setMaxWidth(320);
        progressFill.setScaleX(0.01);
        progressFill.setTranslateX(-158.4);
        progressFill.scaleXProperty().addListener((obs, oldValue, newValue) ->
                progressFill.setTranslateX(-160 + (160 * newValue.doubleValue()))
        );

        progressTrack.getChildren().add(progressFill);

        VBox content = new VBox(8, mark, logo, subtitle, status, progressTrack);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("loading-content");

        root.getChildren().addAll(atmosphere, content);

        animate(glow, mark, progressFill);
    }

    public StackPane getRoot() {
        return root;
    }

    public void setStatus(String text) {
        status.setText(text);
    }

    public void finish(Runnable onFinished) {
        progressFill.getProperties().put("finished", Boolean.TRUE);

        ScaleTransition progress = new ScaleTransition(
                Duration.millis(140),
                progressFill
        );
        progress.setFromX(progressFill.getScaleX());
        progress.setToX(1.0);
        progress.setInterpolator(Interpolator.EASE_OUT);
        progress.setOnFinished(event -> {
            FadeTransition fade = new FadeTransition(
                    Duration.millis(220),
                    root
            );
            fade.setFromValue(1);
            fade.setToValue(0);
            fade.setInterpolator(Interpolator.EASE_IN);
            fade.setOnFinished(done -> onFinished.run());
            fade.play();
        });
        progress.play();
    }

    private void animate(Node glow, Node mark, Node progress) {
        ScaleTransition pulse = new ScaleTransition(
                Duration.millis(1200),
                glow
        );
        pulse.setFromX(0.88);
        pulse.setFromY(0.88);
        pulse.setToX(1.06);
        pulse.setToY(1.06);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setInterpolator(Interpolator.EASE_BOTH);
        pulse.play();

        RotateTransition rotate = new RotateTransition(
                Duration.seconds(7),
                mark
        );
        rotate.setByAngle(360);
        rotate.setCycleCount(Animation.INDEFINITE);
        rotate.setInterpolator(Interpolator.LINEAR);
        rotate.play();

        Timeline loading = new Timeline(
                new KeyFrame(
                        Duration.ZERO,
                        new KeyValue(
                                progress.scaleXProperty(),
                                0.01,
                                Interpolator.EASE_OUT
                        )
                ),
                new KeyFrame(
                        Duration.millis(460),
                        new KeyValue(
                                progress.scaleXProperty(),
                                0.82,
                                Interpolator.EASE_OUT
                        )
                )
        );
        loading.setOnFinished(event -> {
            if (!Boolean.TRUE.equals(progress.getProperties().get("finished"))) {
                loading.playFromStart();
            }
        });
        loading.play();
    }
}
