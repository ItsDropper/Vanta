package dev.itsdropper.vanta.ui;

import javafx.scene.Parent;
import javafx.scene.Scene;

import java.util.ArrayList;
import java.util.List;

/**
 * The launcher's stylesheets, in cascade order. Every scene, dialog and popup
 * attaches the same list so controls look identical wherever they appear.
 *
 * <ul>
 *   <li>tokens: derived colours, type and surface roles</li>
 *   <li>base: standard controls and the button vocabulary</li>
 *   <li>utilities: the reusable component kit</li>
 *   <li>shell: window, sidebar, title bar and overlays</li>
 *   <li>home, library, content, browse, manage, create, settings, progress: feature areas</li>
 *   <li>vanta: cross-cutting variants that must win the cascade</li>
 * </ul>
 */
public final class Stylesheets {

    private static final String[] ORDER = {
            "/css/tokens.css",
            "/css/base.css",
            "/css/utilities.css",
            "/css/shell.css",
            "/css/home.css",
            "/css/library.css",
            "/css/content.css",
            "/css/manage.css",
            "/css/browse.css",
            "/css/create.css",
            "/css/settings.css",
            "/css/progress.css",
            "/css/vanta.css"
    };

    private Stylesheets() {
    }

    public static List<String> all() {
        List<String> urls = new ArrayList<>(ORDER.length);
        for (String path : ORDER) {
            var resource = Stylesheets.class.getResource(path);
            if (resource == null) {
                throw new IllegalStateException("Missing CSS resource: " + path);
            }
            urls.add(resource.toExternalForm());
        }
        return urls;
    }

    public static void addTo(Scene scene) {
        for (String url : all()) {
            if (!scene.getStylesheets().contains(url)) {
                scene.getStylesheets().add(url);
            }
        }
    }

    public static void addTo(Parent parent) {
        for (String url : all()) {
            if (!parent.getStylesheets().contains(url)) {
                parent.getStylesheets().add(url);
            }
        }
    }
}
