package org.example.ui.components;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

public class InstanceCard extends VBox {

    public InstanceCard() {

        setSpacing(10);
        setPadding(new Insets(22));
        getStyleClass().add("info-card");

        Label title = new Label("INSTANCE");
        title.getStyleClass().add("card-title");

        Label name = new Label("Minecraft 1.21.11");
        name.getStyleClass().add("card-main");

        Label details = new Label("Fabric • Ready to launch");
        details.getStyleClass().add("card-secondary");

        getChildren().addAll(
                title,
                name,
                details
        );
    }
}