package org.example.ui.components;

import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.Stop;

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

        // Darken the image and fade it into the Home page background at the edges.
        Rectangle overlay = new Rectangle();
        overlay.setManaged(false);
        overlay.widthProperty().bind(widthProperty());
        overlay.heightProperty().bind(heightProperty());
        overlay.setFill(new LinearGradient(
                0, 0, 1, 0,
                true,
                CycleMethod.NO_CYCLE,
                new Stop(0.00, Color.rgb(7, 12, 18, 0.92)),
                new Stop(0.12, Color.rgb(7, 12, 18, 0.42)),
                new Stop(0.50, Color.rgb(7, 12, 18, 0.18)),
                new Stop(0.88, Color.rgb(7, 12, 18, 0.42)),
                new Stop(1.00, Color.rgb(7, 12, 18, 0.92))
        ));
        overlay.setMouseTransparent(true);

        // Add a vertical fade as well so the screenshot does not fight the Home content.
        Rectangle verticalFade = new Rectangle();
        verticalFade.setManaged(false);
        verticalFade.widthProperty().bind(widthProperty());
        verticalFade.heightProperty().bind(heightProperty());
        verticalFade.setFill(new LinearGradient(
                0, 0, 0, 1,
                true,
                CycleMethod.NO_CYCLE,
                new Stop(0.00, Color.rgb(7, 12, 18, 0.72)),
                new Stop(0.18, Color.rgb(7, 12, 18, 0.12)),
                new Stop(0.72, Color.rgb(7, 12, 18, 0.12)),
                new Stop(1.00, Color.rgb(7, 12, 18, 0.88))
        ));
        verticalFade.setMouseTransparent(true);

        getChildren().addAll(background, overlay, verticalFade);
    }
}
