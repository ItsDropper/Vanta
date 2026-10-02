package org.example.ui.components;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

import java.io.InputStream;

public final class MinecraftBackdrop extends StackPane {

    private static final String BACKGROUND_RESOURCE =
            "/images/home-background.png";

    public MinecraftBackdrop() {
        getStyleClass().add("minecraft-backdrop");
        setMouseTransparent(true);

        ImageView background = new ImageView();
        background.setManaged(false);
        background.setPreserveRatio(false);
        background.setSmooth(true);
        background.setMouseTransparent(true);

        try (InputStream stream =
                     MinecraftBackdrop.class.getResourceAsStream(
                             BACKGROUND_RESOURCE
                     )) {

            if (stream != null) {
                background.setImage(new Image(stream));
            }
        } catch (Exception ignored) {
            // The backdrop remains empty if the optional image cannot be loaded.
        }

        background.fitWidthProperty().bind(widthProperty());
        background.fitHeightProperty().bind(heightProperty());

        Rectangle overlay = new Rectangle();
        overlay.setManaged(false);
        overlay.widthProperty().bind(widthProperty());
        overlay.heightProperty().bind(heightProperty());
        overlay.setFill(Color.rgb(7, 12, 18, 0.42));
        overlay.setMouseTransparent(true);

        getChildren().addAll(background, overlay);
    }
}
