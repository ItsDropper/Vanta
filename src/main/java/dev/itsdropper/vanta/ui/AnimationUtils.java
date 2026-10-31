package dev.itsdropper.vanta.ui;

import javafx.animation.FadeTransition;
import javafx.animation.ParallelTransition;
import javafx.animation.Transition;
import javafx.animation.ScaleTransition;
import javafx.animation.TranslateTransition;
import javafx.collections.ListChangeListener;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.input.MouseEvent;
import javafx.util.Duration;

public final class AnimationUtils {

    private AnimationUtils() {
    }

    public static void slideFadeIn(Node node, double distance) {
        if (!LauncherSettings.isAnimationsEnabled()) {
            cancelEntrance(node);
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

        ParallelTransition transition = new ParallelTransition(fade, slide);
        replaceEntranceTransition(node, transition);
        transition.setOnFinished(event -> {
            if (node.getProperties().get("vanta.entrance-transition") == transition) {
                node.getProperties().remove("vanta.entrance-transition");
            }
            node.setOpacity(1.0);
            node.setTranslateX(0.0);
            node.setTranslateY(0.0);
        });
        transition.playFromStart();
    }

    public static void installInteractiveAnimations(Node node) {
        if (!LauncherSettings.isAnimationsEnabled() || node == null) {
            return;
        }

        boolean installHandlers = node.getProperties().putIfAbsent(
                "vanta.interactive-animation",
                Boolean.TRUE
        ) == null;

        if (installHandlers && node instanceof Button) {
            // Add handlers instead of replacing a control's existing
            // mouse handlers. Views own behavior; this utility owns polish.
            node.addEventHandler(MouseEvent.MOUSE_ENTERED,
                    event -> animateScale(node, 1.025, Duration.millis(120)));
            node.addEventHandler(MouseEvent.MOUSE_EXITED,
                    event -> animateScale(node, 1.0, Duration.millis(140)));
            node.addEventHandler(MouseEvent.MOUSE_PRESSED,
                    event -> animateScale(node, 0.975, Duration.millis(70)));
            node.addEventHandler(MouseEvent.MOUSE_RELEASED,
                    event -> animateScale(node, node.isHover() ? 1.025 : 1.0,
                            Duration.millis(100)));
        }

        if (node instanceof Parent parent) {
            if (parent.getProperties().putIfAbsent(
                    "vanta.interactive-animation-child-listener",
                    Boolean.TRUE
            ) == null) {
                parent.getChildrenUnmodifiable().addListener(
                        (ListChangeListener<Node>) change -> {
                            while (change.next()) {
                                if (change.wasAdded()) {
                                    for (Node child : change.getAddedSubList()) {
                                        installInteractiveAnimations(child);
                                    }
                                }
                            }
                        }
                );
            }

            for (Node child : parent.getChildrenUnmodifiable()) {
                installInteractiveAnimations(child);
            }
        }
    }

    private static void replaceEntranceTransition(Node node, ParallelTransition transition) {
        Object previous = node.getProperties().get("vanta.entrance-transition");
        if (previous instanceof Transition active) {
            active.stop();
        }
        node.getProperties().put("vanta.entrance-transition", transition);
    }

    /** Stops a pending entrance animation before its view leaves the host. */
    public static void cancelEntrance(Node node) {
        if (node == null) {
            return;
        }
        Object previous = node.getProperties().remove("vanta.entrance-transition");
        if (previous instanceof Transition active) {
            active.stop();
        }
        node.setOpacity(1.0);
        node.setTranslateX(0.0);
        node.setTranslateY(0.0);
    }

    /**
     * Cancels launcher-owned animations throughout a view subtree.
     * Only properties owned by AnimationUtils are reset; authored transforms
     * on unrelated controls and 3D previews are left untouched.
     */
    public static void cancelAnimations(Node node) {
        if (node == null) {
            return;
        }
        cancelEntrance(node);
        Object scale = node.getProperties().remove("vanta.scale-transition");
        if (scale instanceof ScaleTransition transition) {
            transition.stop();
            node.setScaleX(1.0);
            node.setScaleY(1.0);
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                cancelAnimations(child);
            }
        }
    }

    private static void animateScale(Node node, double target, Duration duration) {
        if (!LauncherSettings.isAnimationsEnabled()) {
            Object previous = node.getProperties().remove("vanta.scale-transition");
            if (previous instanceof ScaleTransition transition) transition.stop();
            node.setScaleX(1.0);
            node.setScaleY(1.0);
            return;
        }

        Object previous = node.getProperties().get("vanta.scale-transition");
        if (previous instanceof ScaleTransition transition) {
            transition.stop();
        }

        ScaleTransition transition = new ScaleTransition(duration, node);
        transition.setToX(target);
        transition.setToY(target);
        node.getProperties().put("vanta.scale-transition", transition);
        transition.playFromStart();
    }

    public static void slideFadeVertical(Node node, double distance) {
        if (!LauncherSettings.isAnimationsEnabled()) {
            cancelEntrance(node);
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

        ParallelTransition transition = new ParallelTransition(fade, slide);
        replaceEntranceTransition(node, transition);
        transition.setOnFinished(event -> {
            if (node.getProperties().get("vanta.entrance-transition") == transition) {
                node.getProperties().remove("vanta.entrance-transition");
            }
            node.setOpacity(1.0);
            node.setTranslateX(0.0);
            node.setTranslateY(0.0);
        });
        transition.playFromStart();
    }
}
