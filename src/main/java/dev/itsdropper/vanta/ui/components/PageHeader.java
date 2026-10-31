package dev.itsdropper.vanta.ui.components;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Shared title/subtitle/action layout for top-level launcher pages.
 * Page-specific typography remains controlled by the style classes supplied
 * by the owning view, while spacing and action alignment stay consistent.
 */
public final class PageHeader extends HBox {

    public PageHeader(
            String title,
            String subtitle,
            String titleStyleClass,
            String subtitleStyleClass,
            Node... actions
    ) {
        getStyleClass().add("page-header");
        setAlignment(Pos.CENTER_LEFT);
        setSpacing(16);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().addAll("page-header-title", titleStyleClass);

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().addAll("page-header-subtitle", subtitleStyleClass);
        subtitleLabel.setWrapText(true);

        VBox copy = new VBox(5, titleLabel, subtitleLabel);
        copy.getStyleClass().add("page-header-copy");
        HBox.setHgrow(copy, Priority.ALWAYS);

        getChildren().add(copy);

        if (actions != null && actions.length > 0) {
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            getChildren().add(spacer);

            HBox actionGroup = new HBox(8, actions);
            actionGroup.setAlignment(Pos.CENTER_RIGHT);
            actionGroup.getStyleClass().add("page-header-actions");
            getChildren().add(actionGroup);
        }
    }
}
