package dev.itsdropper.vanta.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Shared project-card layout used by the global and instance Modrinth browsers.
 * The views own data mapping and actions; this component owns presentation.
 */
public final class ModrinthProjectCard extends VBox {
    public ModrinthProjectCard(
            Node icon,
            Label title,
            Label author,
            Label description,
            Node tags,
            int downloads,
            int followers,
            String updated,
            Button menuButton,
            Button installButton,
            boolean grid,
            Runnable onOpen
    ) {
        super(12);
        title.getStyleClass().add("modrinth-project-title");
        author.getStyleClass().add("modrinth-project-author");
        description.getStyleClass().add("modrinth-project-description");
        description.setWrapText(true);
        description.setMaxHeight(42);

        HBox titleLine = new HBox(8, title, author);
        titleLine.setAlignment(Pos.CENTER_LEFT);
        VBox information = new VBox(5, titleLine, description, tags);
        HBox.setHgrow(information, Priority.ALWAYS);

        HBox stats = new HBox(14,
                statLine(IconView.Type.DOWNLOAD, compactNumber(downloads)),
                statLine(IconView.Type.STAR, compactNumber(followers)));
        if (updated != null && !updated.isBlank()) {
            stats.getChildren().add(statLine(IconView.Type.CLOCK, updated));
        }
        stats.getStyleClass().add("modrinth-project-stats");
        stats.setAlignment(Pos.CENTER_LEFT);

        setPadding(new Insets(16));
        getStyleClass().add("modrinth-project-card");

        if (grid) {
            VBox names = new VBox(2, title, author);
            names.setAlignment(Pos.CENTER_LEFT);
            names.setMinWidth(0);
            HBox.setHgrow(names, Priority.ALWAYS);
            HBox nameLine = new HBox(8, names, menuButton);
            nameLine.setAlignment(Pos.TOP_LEFT);
            description.setMaxHeight(40);
            VBox right = new VBox(6, nameLine, description);
            right.setMinWidth(0);
            HBox.setHgrow(right, Priority.ALWAYS);
            HBox heading = new HBox(12, icon, right);
            heading.setAlignment(Pos.TOP_LEFT);
            Region grow = new Region();
            VBox.setVgrow(grow, Priority.ALWAYS);
            Region footerSpace = new Region();
            HBox.setHgrow(footerSpace, Priority.ALWAYS);
            HBox footer = new HBox(10, stats, footerSpace, installButton);
            footer.setAlignment(Pos.CENTER_LEFT);
            getChildren().addAll(heading, tags, grow, footer);
            getStyleClass().add("modrinth-project-grid-card");
        } else {
            HBox actions = new HBox(8, menuButton, installButton);
            actions.setAlignment(Pos.CENTER_RIGHT);
            VBox right = new VBox(8, stats, actions);
            right.setAlignment(Pos.CENTER_RIGHT);
            stats.setAlignment(Pos.CENTER_RIGHT);
            HBox row = new HBox(14, icon, information, right);
            row.setAlignment(Pos.CENTER_LEFT);
            HBox.setHgrow(information, Priority.ALWAYS);
            getChildren().add(row);
        }

        setCursor(javafx.scene.Cursor.HAND);
        if (onOpen != null) {
            setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY
                        && !(event.getTarget() instanceof Button)) {
                    onOpen.run();
                }
            });
        }
    }

    private HBox statLine(IconView.Type symbol, String value) {
        javafx.scene.Node icon = IconView.create(symbol, 13);
        icon.getStyleClass().add("modrinth-stat-icon");
        Label text = new Label(value);
        text.getStyleClass().add("modrinth-stat-value");
        HBox line = new HBox(6, icon, text);
        line.setAlignment(Pos.CENTER_RIGHT);
        return line;
    }

    private static String compactNumber(long value) {
        if (value >= 1_000_000_000L) return String.format(java.util.Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        if (value >= 1_000_000L) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 10_000L) return String.format(java.util.Locale.ROOT, "%.1fK", value / 1_000.0);
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }
}
