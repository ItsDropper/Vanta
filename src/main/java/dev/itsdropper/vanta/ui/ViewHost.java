package dev.itsdropper.vanta.ui;

import javafx.scene.Node;
import javafx.scene.layout.StackPane;

import java.util.Objects;

/**
 * Owns the active view inside the launcher content region.
 *
 * Navigation code should describe which view to display, while this class
 * consistently handles scene replacement, visibility normalization and
 * optional entrance/interaction polish. Keeping this behavior in one place
 * prevents individual navigation paths from drifting apart.
 */
public final class ViewHost {

    private static final double ENTRANCE_DISTANCE = 14.0;

    private final StackPane container;

    public ViewHost(StackPane container) {
        this.container = Objects.requireNonNull(container, "container");
    }

    /**
     * Replaces the active view without adding a transition.
     * Useful for state changes where an animated entrance would be distracting.
     */
    public void show(Node view) {
        Objects.requireNonNull(view, "view");

        Node previousView = getActiveView();
        boolean changingView = previousView != view;
        if (changingView && previousView != null) {
            AnimationUtils.cancelEntrance(previousView);
            if (previousView instanceof LauncherViewLifecycle lifecycle) {
                lifecycle.onHidden();
            }
        }

        container.getChildren().setAll(view);
        view.setVisible(true);
        view.setManaged(true);
        view.setOpacity(1.0);
        view.setTranslateX(0.0);
        view.setTranslateY(0.0);

        if (changingView && view instanceof LauncherViewLifecycle lifecycle) {
            lifecycle.onShown();
        }
    }

    /**
     * Replaces the active view and applies the launcher's standard entrance
     * and interactive animations when motion is enabled in settings.
     */
    public void showAnimated(Node view) {
        show(view);
        AnimationUtils.installInteractiveAnimations(view);

        if (LauncherSettings.isAnimationsEnabled()) {
            AnimationUtils.slideFadeIn(view, ENTRANCE_DISTANCE);
        }
    }

    public Node getActiveView() {
        return container.getChildren().isEmpty()
                ? null
                : container.getChildren().getFirst();
    }
}
