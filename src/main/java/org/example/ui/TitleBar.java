package org.example.ui;

import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;

import org.example.launcher.service.AccountService;
import org.example.ui.components.AccountSwitcher;

public class TitleBar extends HBox {

    private double dragX;
    private double dragY;

    public TitleBar(
            Stage stage,
            AccountService accountService
    ) {

        getStyleClass().add(
                "title-bar"
        );

        setAlignment(
                Pos.CENTER_RIGHT
        );

        // =========================================================
        // ACCOUNT SWITCHER
        // =========================================================

        AccountSwitcher accountSwitcher =
                new AccountSwitcher(
                        accountService
                );

        // =========================================================
        // WINDOW ICONS
        // =========================================================

        SVGPath minimizeIcon =
                new SVGPath();

        minimizeIcon.setContent(
                "M 3 8 L 13 8"
        );

        SVGPath maximizeIcon =
                new SVGPath();

        maximizeIcon.setContent(
                "M 3 3 L 13 3 L 13 13 L 3 13 Z"
        );

        SVGPath restoreIcon =
                new SVGPath();

        restoreIcon.setContent(
                "M 5 5 L 13 5 L 13 13 L 5 13 Z " +
                        "M 3 3 L 3 11 L 5 11 L 5 5 " +
                        "L 11 5 L 11 3 Z"
        );

        SVGPath closeIcon =
                new SVGPath();

        closeIcon.setContent(
                "M 4 4 L 12 12 M 12 4 L 4 12"
        );

        // =========================================================
        // BUTTONS
        // =========================================================

        Button minimize =
                new Button();

        Button maximize =
                new Button();

        Button close =
                new Button();

        minimize.setGraphic(
                minimizeIcon
        );

        maximize.setGraphic(
                maximizeIcon
        );

        close.setGraphic(
                closeIcon
        );

        minimize.getStyleClass().add(
                "title-bar-button"
        );

        maximize.getStyleClass().add(
                "title-bar-button"
        );

        close.getStyleClass().add(
                "title-bar-close"
        );

        // =========================================================
        // WINDOW ACTIONS
        // =========================================================

        minimize.setOnAction(event ->
                stage.setIconified(
                        true
                )
        );

        maximize.setOnAction(event -> {

            stage.setMaximized(
                    !stage.isMaximized()
            );

            maximize.setGraphic(
                    stage.isMaximized()
                            ? restoreIcon
                            : maximizeIcon
            );
        });

        close.setOnAction(event ->
                stage.close()
        );

        // =========================================================
        // LAYOUT
        // =========================================================

        Region spacer =
                new Region();

        HBox.setHgrow(
                spacer,
                Priority.ALWAYS
        );

        getChildren().addAll(
                spacer,
                accountSwitcher,
                minimize,
                maximize,
                close
        );

        // =========================================================
        // WINDOW DRAGGING
        // =========================================================

        setOnMousePressed(event -> {

            dragX =
                    event.getScreenX()
                            - stage.getX();

            dragY =
                    event.getScreenY()
                            - stage.getY();
        });

        setOnMouseDragged(event -> {

            if (!stage.isMaximized()) {

                stage.setX(
                        event.getScreenX()
                                - dragX
                );

                stage.setY(
                        event.getScreenY()
                                - dragY
                );
            }
        });
    }
}