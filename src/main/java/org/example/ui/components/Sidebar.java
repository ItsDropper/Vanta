package org.example.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.EnumMap;
import java.util.Map;
import java.util.function.Consumer;

public class Sidebar extends VBox {

    public enum Page {
        HOME,
        INSTANCES,
        MODS,
        ACCOUNTS,
        SETTINGS
    }

    private Consumer<Page> pageListener;
    private final Map<Page, Button> navigationButtons =
            new EnumMap<>(Page.class);

    public Sidebar() {

        getStyleClass().add(
                "sidebar"
        );

        setPadding(
                new Insets(24, 16, 24, 16)
        );

        setSpacing(8);

        setPrefWidth(210);

        // ---------------------------------------------------------
        // BRAND
        // ---------------------------------------------------------

        Label logo =
                new Label("VANTA");

        logo.getStyleClass().add(
                "sidebar-logo"
        );

        Label subtitle =
                new Label("MINECRAFT LAUNCHER");

        subtitle.getStyleClass().add(
                "sidebar-subtitle"
        );

        VBox branding =
                new VBox(
                        4,
                        logo,
                        subtitle
                );

        branding.setPadding(
                new Insets(
                        4,
                        8,
                        28,
                        8
                )
        );

        getChildren().add(
                branding
        );

        // ---------------------------------------------------------
        // NAVIGATION
        // ---------------------------------------------------------

        addButton(
                "HOME",
                Page.HOME
        );

        addButton(
                "INSTANCES",
                Page.INSTANCES
        );

        addButton(
                "MODS",
                Page.MODS
        );

        Region spacer =
                new Region();

        VBox.setVgrow(
                spacer,
                Priority.ALWAYS
        );

        getChildren().add(
                spacer
        );

        addButton(
                "ACCOUNTS",
                Page.ACCOUNTS
        );

        addButton(
                "SETTINGS",
                Page.SETTINGS
        );
    }

    private void addButton(
            String text,
            Page page
    ) {

        Button button =
                new Button(text);

        button.setMaxWidth(
                Double.MAX_VALUE
        );

        button.setAlignment(
                Pos.CENTER_LEFT
        );

        button.getStyleClass().add(
                "sidebar-button"
        );

        navigationButtons.put(
                page,
                button
        );

        button.setOnAction(
                event -> {

                    setSelectedPage(
                            page
                    );

                    if (pageListener != null) {
                        pageListener.accept(page);
                    }
                }
        );

        getChildren().add(
                button
        );
    }

    public void setOnPageSelected(
            Consumer<Page> listener
    ) {

        this.pageListener = listener;

        setSelectedPage(
                Page.HOME
        );
    }

    public void setSelectedPage(
            Page selectedPage
    ) {

        for (
                Map.Entry<Page, Button> entry
                : navigationButtons.entrySet()
        ) {

            entry.getValue()
                    .getStyleClass()
                    .remove(
                            "sidebar-button-active"
                    );
        }

        Button selectedButton =
                navigationButtons.get(
                        selectedPage
                );

        if (selectedButton != null) {

            selectedButton
                    .getStyleClass()
                    .add(
                            "sidebar-button-active"
                    );
        }
    }
}
