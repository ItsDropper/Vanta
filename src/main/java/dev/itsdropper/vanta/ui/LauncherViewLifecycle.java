package dev.itsdropper.vanta.ui;

/**
 * Optional lifecycle contract for launcher views managed by {@link ViewHost}.
 *
 * Views use these callbacks to refresh visible data and release view-specific
 * work when navigation changes, without coupling each screen to LauncherView.
 */
public interface LauncherViewLifecycle {

    /** Called after this view becomes the active content. */
    default void onShown() {
    }

    /** Called immediately before this view is replaced by another view. */
    default void onHidden() {
    }
}
