package org.example.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.TranslateTransition;
import javafx.scene.Node;
import javafx.util.Duration;

public final class AnimationUtils {

    private AnimationUtils() {
    }

    public static void slideFadeIn(Node node, double distance) {
        if (!LauncherSettings.isAnimationsEnabled()) {
            node.setOpacity(1);
            node.setTranslateX(0);
            node.setTranslateY(0);
            return;
        }

        node.setOpacity(0);
        node.setTranslateX(distance);

        FadeTransition fade =
                new FadeTransition(Duration.millis(220), node);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide =
                new TranslateTransition(Duration.millis(220), node);
        slide.setFromX(distance);
        slide.setToX(0);

        new ParallelTransition(
                fade,
                slide
        ).play();
    }

    public static void slideFadeVertical(Node node, double distance) {
        if (!LauncherSettings.isAnimationsEnabled()) {
            node.setOpacity(1);
            node.setTranslateX(0);
            node.setTranslateY(0);
            return;
        }

        node.setOpacity(0);
        node.setTranslateY(distance);

        FadeTransition fade =
                new FadeTransition(Duration.millis(200), node);
        fade.setFromValue(0);
        fade.setToValue(1);

        TranslateTransition slide =
                new TranslateTransition(Duration.millis(200), node);
        slide.setFromY(distance);
        slide.setToY(0);

        new ParallelTransition(
                fade,
                slide
        ).play();
    }
}
