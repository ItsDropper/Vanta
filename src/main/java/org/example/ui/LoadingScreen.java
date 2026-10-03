package org.example.ui;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Line;
import javafx.util.Duration;

public final class LoadingScreen {

    private final StackPane root = new StackPane();
    private final Label status = new Label("INITIALIZING VANTA");
    private final HBox progress = new HBox();

    public LoadingScreen() {
        root.getStyleClass().add("vanta-loading");

        StackPane atmosphere = new StackPane();
        atmosphere.getStyleClass().add("loading-atmosphere");

        Circle glow = new Circle(145);
        glow.getStyleClass().add("loading-glow");

        Circle ringOuter = new Circle(112);
        ringOuter.getStyleClass().add("loading-ring-outer");

        Circle ringInner = new Circle(76);
        ringInner.getStyleClass().add("loading-ring-inner");

        StackPane mark = new StackPane();
        mark.getStyleClass().add("loading-mark");
        mark.getChildren().addAll(ringOuter, ringInner);

        Line slash = new Line(-34, 34, 34, -34);
        slash.getStyleClass().add("loading-slash");
        mark.getChildren().add(slash);

        atmosphere.getChildren().addAll(glow, mark);

        Label logo = new Label("VANTA");
        logo.getStyleClass().add("loading-logo");

        Label subtitle = new Label("ENVIRONMENT MANAGER");
        subtitle.getStyleClass().add("loading-subtitle");

        status.getStyleClass().add("loading-status");

        progress.getStyleClass().add("loading-progress");
        for (int i = 0; i < 5; i++) {
            Region segment = new Region();
            segment.getStyleClass().add("loading-progress-segment");
            progress.getChildren().add(segment);
        }

        VBox content = new VBox(10, logo, subtitle, status, progress);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("loading-content");

        root.getChildren().addAll(atmosphere, content);

        animate(glow, mark, progress);
    }

    public StackPane getRoot() {
        return root;
    }

    public void setStatus(String text) {
        status.setText(text);
    }

    public void finish(Runnable onFinished) {
        FadeTransition fade = new FadeTransition(Duration.millis(260), root);
        fade.setFromValue(1);
        fade.setToValue(0);
        fade.setInterpolator(Interpolator.EASE_IN);
        fade.setOnFinished(event -> onFinished.run());
        fade.play();
    }

    private void animate(Node glow, Node mark, HBox progress) {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(1050), glow);
        pulse.setFromX(0.82);
        pulse.setFromY(0.82);
        pulse.setToX(1.08);
        pulse.setToY(1.08);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.setInterpolator(Interpolator.EASE_BOTH);
        pulse.play();

        RotateTransition rotate = new RotateTransition(Duration.seconds(4.5), mark);
        rotate.setByAngle(360);
        rotate.setCycleCount(Animation.INDEFINITE);
        rotate.setInterpolator(Interpolator.LINEAR);
        rotate.play();

        Timeline bars = new Timeline();
        for (int i = 0; i < progress.getChildren().size(); i++) {
            Node segment = progress.getChildren().get(i);
            KeyFrame frame = new KeyFrame(
                    Duration.millis(180 + i * 110),
                    new KeyValue(segment.opacityProperty(), 1.0, Interpolator.EASE_OUT)
            );
            bars.getKeyFrames().add(frame);
        }
        bars.setCycleCount(Animation.INDEFINITE);
        bars.setAutoReverse(true);
        bars.play();
    }
}
