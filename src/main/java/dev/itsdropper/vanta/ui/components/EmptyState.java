package dev.itsdropper.vanta.ui.components;

import dev.itsdropper.vanta.ui.VantaUI;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

/**
 * A consistent empty/preview state for pages that have no local content yet.
 * It explains what belongs in the region and how the user can populate it.
 */
public final class EmptyState extends VBox {
    public EmptyState(String icon, String eyebrow, String title, String description, Node... actions) {
        getStyleClass().add("vanta-empty-state");
        setAlignment(Pos.CENTER);
        setSpacing(VantaUI.spacing(2));
        setPadding(new Insets(VantaUI.spacing(6)));
        setFillWidth(true);
        setMinWidth(0);
        setMaxWidth(620);
        setAccessibleText(title + ". " + description);
        VantaUI.applySurface(this, VantaUI.SurfaceRole.CARD);

        if (icon != null && !icon.isBlank()) {
            Label iconLabel = new Label(icon);
            iconLabel.getStyleClass().add("vanta-empty-state-icon");
            iconLabel.setAccessibleText("");
            getChildren().add(iconLabel);
        }

        if (eyebrow != null && !eyebrow.isBlank()) {
            Label eyebrowLabel = new Label(eyebrow);
            VantaUI.styleText(eyebrowLabel, VantaUI.TextRole.EYEBROW);
            eyebrowLabel.getStyleClass().add("vanta-empty-state-eyebrow");
            getChildren().add(eyebrowLabel);
        }

        Label titleLabel = new Label(title == null ? "" : title);
        titleLabel.setWrapText(true);
        titleLabel.setMaxWidth(Double.MAX_VALUE);
        titleLabel.getStyleClass().add("vanta-empty-state-title");
        VantaUI.styleText(titleLabel, VantaUI.TextRole.SECTION_TITLE);

        Label descriptionLabel = new Label(description == null ? "" : description);
        descriptionLabel.setWrapText(true);
        descriptionLabel.setMaxWidth(520);
        descriptionLabel.setAlignment(Pos.CENTER);
        descriptionLabel.getStyleClass().add("vanta-empty-state-description");
        VantaUI.styleText(descriptionLabel, VantaUI.TextRole.MUTED);

        getChildren().addAll(titleLabel, descriptionLabel);
        if (actions != null && actions.length > 0) {
            VBox actionGroup = new VBox(VantaUI.spacing(2), actions);
            actionGroup.setAlignment(Pos.CENTER);
            actionGroup.setFillWidth(true);
            actionGroup.getStyleClass().add("vanta-empty-state-actions");
            getChildren().add(actionGroup);
        }
    }
}
