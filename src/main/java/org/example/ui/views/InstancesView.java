package org.example.ui.views;

import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

public class InstancesView extends VBox {

    public InstancesView() {
        getStyleClass().add("page");

        setPadding(new Insets(40));
        setSpacing(20);

        // Your existing UI goes here
    }
}
