package dev.itsdropper.vanta.ui.components;

import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
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
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().addAll("page-header-title", titleStyleClass);
        titleLabel.setWrapText(true);

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().addAll("page-header-subtitle", subtitleStyleClass);
        subtitleLabel.setWrapText(true);

        VBox copy = new VBox(5, titleLabel, subtitleLabel);
        copy.getStyleClass().add("page-header-copy");
        copy.setMinWidth(0);
        HBox.setHgrow(copy, Priority.ALWAYS);

        getChildren().add(copy);

        if (actions != null && actions.length > 0) {
            FlowPane actionGroup = new FlowPane(8, 8, actions);
            actionGroup.setAlignment(Pos.CENTER_RIGHT);
            actionGroup.setMinWidth(0);
            actionGroup.setMaxWidth(Double.MAX_VALUE);
            actionGroup.getStyleClass().add("page-header-actions");

            VBox compactLayout = new VBox(10);
            compactLayout.setFillWidth(true);
            compactLayout.setMinWidth(0);
            compactLayout.setMaxWidth(Double.MAX_VALUE);

            widthProperty().addListener((observable, oldWidth, newWidth) -> {
                boolean compact = newWidth.doubleValue() < 620;
                if (compact && !getChildren().contains(compactLayout)) {
                    getChildren().clear();
                    actionGroup.setAlignment(Pos.CENTER_LEFT);
                    compactLayout.getChildren().setAll(copy, actionGroup);
                    getChildren().setAll(compactLayout);
                } else if (!compact && getChildren().contains(compactLayout)) {
                    compactLayout.getChildren().clear();
                    actionGroup.setAlignment(Pos.CENTER_RIGHT);
                    getChildren().setAll(copy, actionGroup);
                }
            });

            getChildren().add(actionGroup);
        }
    }
}
