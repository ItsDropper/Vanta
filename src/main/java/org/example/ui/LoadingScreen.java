package org.example.ui;

import javafx.animation.*;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.shape.Circle;
import javafx.util.Duration;

public final class LoadingScreen {

    private final StackPane root = new StackPane();
    private final Label status = new Label("INITIALIZING VANTA");
    private final Region progressFill = new Region();

    public LoadingScreen() {
        root.getStyleClass().add("vanta-loading");

        VBox content = new VBox(10);
        content.setAlignment(Pos.CENTER);
        content.getStyleClass().add("loading-content");

        HBox brand = new HBox(9);
        brand.setAlignment(Pos.CENTER);

        Circle mark = new Circle(5);
        mark.getStyleClass().add("loading-mark");

        Label logo = new Label("VANTA");
        logo.getStyleClass().add("loading-logo");

        brand.getChildren().addAll(mark, logo);

        Label subtitle = new Label("ENVIRONMENT MANAGER");
        subtitle.getStyleClass().add("loading-subtitle");

        status.getStyleClass().add("loading-status");

        StackPane progressTrack = new StackPane();
        progressTrack.getStyleClass().add("loading-progress-track");

        progressFill.getStyleClass().add("loading-progress-fill");
        progressFill.setPrefWidth(280);
        progressFill.setMaxWidth(280);
        progressFill.setScaleX(0.01);

        progressTrack.getChildren().add(progressFill);

        content.getChildren().addAll(brand, subtitle, status, progressTrack);
        root.getChildren().add(content);

        animate(progressFill);
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
                Duration.millis(120),
                progressFill
        );
        progress.setFromX(progressFill.getScaleX());
        progress.setToX(1.0);
        progress.setInterpolator(Interpolator.EASE_OUT);
        progress.setOnFinished(event -> {
            FadeTransition fade = new FadeTransition(
                    Duration.millis(150),
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

    private void animate(Region progress) {
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
                                0.86,
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
