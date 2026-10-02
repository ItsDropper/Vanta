package org.example.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.animation.ScaleTransition;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
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

    public static void installInteractiveAnimations(Node node) {
        if (!LauncherSettings.isAnimationsEnabled() || node == null) {
            return;
        }

        if (node.getProperties().putIfAbsent(
                "vanta.interactive-animation",
                Boolean.TRUE
        ) != null) {
            return;
        }

        if (node instanceof Button) {
            installButtonAnimation(node);
        } else if (node instanceof javafx.scene.layout.Region) {
            installSurfaceAnimation(node);
        }

        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                installInteractiveAnimations(child);
            }
        }
    }

    private static void installButtonAnimation(Node node) {
        node.setOnMouseEntered(event -> {
            ScaleTransition transition =
                    new ScaleTransition(Duration.millis(120), node);
            transition.setToX(1.025);
            transition.setToY(1.025);
            transition.play();
        });

        node.setOnMouseExited(event -> {
            ScaleTransition transition =
                    new ScaleTransition(Duration.millis(140), node);
            transition.setToX(1);
            transition.setToY(1);
            transition.play();
        });

        node.setOnMousePressed(event -> {
            ScaleTransition transition =
                    new ScaleTransition(Duration.millis(70), node);
            transition.setToX(0.975);
            transition.setToY(0.975);
            transition.play();
        });

        node.setOnMouseReleased(event -> {
            ScaleTransition transition =
                    new ScaleTransition(Duration.millis(90), node);
            transition.setToX(1.025);
            transition.setToY(1.025);
            transition.play();
        });
    }

    private static void installSurfaceAnimation(Node node) {
        node.setOnMouseEntered(event -> {
            node.setTranslateY(-2);
        });

        node.setOnMouseExited(event -> {
            node.setTranslateY(0);
        });
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
