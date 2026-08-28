package org.example.ui.views;

import javafx.geometry.Insets;
import javafx.scene.layout.VBox;

public class SettingsView extends VBox {

    public SettingsView() {

        getStyleClass().add("page");

        setPadding(new Insets(40));
        setSpacing(20);

        // existing UI
    }
}