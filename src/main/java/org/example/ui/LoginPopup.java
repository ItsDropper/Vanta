package org.example.ui;

import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.net.URL;

import org.example.ui.LauncherSettings;

public class LoginPopup {

    private static Stage stage;
    private static Runnable onClosed;

    public static void setOnClosed(Runnable callback) {
        onClosed = callback;
    }

    public static void show(
            String url,
            String code
    ) {

        Platform.runLater(() -> {

            if (stage != null) {
                stage.close();
            }

            stage = new Stage();

            stage.initStyle(
                    StageStyle.TRANSPARENT
            );

            stage.setResizable(false);

            // -----------------------------------------------------
            // TITLE BAR
            // -----------------------------------------------------

            HBox titleBar =
                    new HBox();

            titleBar.getStyleClass().add(
                    "login-title-bar"
            );

            titleBar.setAlignment(
                    Pos.CENTER_LEFT
            );

            Label titleBarText =
                    new Label("VANTA");

            titleBarText.getStyleClass().add(
                    "login-title-bar-text"
            );

            Region titleSpacer =
                    new Region();

            HBox.setHgrow(
                    titleSpacer,
                    Priority.ALWAYS
            );

            SVGPath closeIcon =
                    new SVGPath();

            closeIcon.setContent(
                    "M 4 4 L 12 12 M 12 4 L 4 12"
            );

            Button closeButton =
                    new Button();

            closeButton.setGraphic(
                    closeIcon
            );

            closeButton.getStyleClass().add(
                    "login-title-bar-close"
            );

            closeButton.setOnAction(event ->
                    stage.close()
            );

            titleBar.getChildren().addAll(
                    titleBarText,
                    titleSpacer,
                    closeButton
            );

            // -----------------------------------------------------
            // WINDOW DRAGGING
            // -----------------------------------------------------

            final double[] dragOffsetX =
                    {0};

            final double[] dragOffsetY =
                    {0};

            titleBar.setOnMousePressed(event -> {

                dragOffsetX[0] =
                        event.getScreenX()
                                - stage.getX();

                dragOffsetY[0] =
                        event.getScreenY()
                                - stage.getY();
            });

            titleBar.setOnMouseDragged(event -> {

                stage.setX(
                        event.getScreenX()
                                - dragOffsetX[0]
                );

                stage.setY(
                        event.getScreenY()
                                - dragOffsetY[0]
                );
            });

            // -----------------------------------------------------
            // HEADER
            // -----------------------------------------------------

            Label logo =
                    new Label("VANTA");

            logo.getStyleClass().add(
                    "login-logo"
            );

            Label title =
                    new Label(
                            "Connect your Microsoft account"
                    );

            title.getStyleClass().add(
                    "login-title"
            );

            Label subtitle =
                    new Label(
                            "Sign in with Microsoft to play Minecraft."
                    );

            subtitle.getStyleClass().add(
                    "login-subtitle"
            );

            // -----------------------------------------------------
            // CODE
            // -----------------------------------------------------

            Label step = new Label("STEP 1 OF 2  •  MICROSOFT");
            step.getStyleClass().add("login-step");

            Label instruction =
                    new Label(
                            "Enter the code below on the Microsoft verification page."
                    );

            instruction.getStyleClass().add(
                    "login-instruction"
            );

            TextField codeField =
                    new TextField(code);

            codeField.setEditable(false);

            codeField.setFocusTraversable(false);

            codeField.setAlignment(
                    Pos.CENTER
            );

            codeField.getStyleClass().add(
                    "verification-code"
            );

            // -----------------------------------------------------
            // COPY
            // -----------------------------------------------------

            Button copyButton =
                    new Button("COPY CODE");

            copyButton.getStyleClass().add(
                    "secondary-button"
            );

            copyButton.setMaxWidth(
                    Double.MAX_VALUE
            );

            copyButton.setOnAction(event -> {

                Clipboard clipboard =
                        Clipboard.getSystemClipboard();

                ClipboardContent content =
                        new ClipboardContent();

                content.putString(code);

                clipboard.setContent(content);

                copyButton.setText(
                        "COPIED"
                );

                PauseTransition pause =
                        new PauseTransition(
                                Duration.seconds(1.5)
                        );

                pause.setOnFinished(
                        e -> copyButton.setText(
                                "COPY CODE"
                        )
                );

                pause.play();
            });

            // -----------------------------------------------------
            // OPEN MICROSOFT
            // -----------------------------------------------------

            Button openButton =
                    new Button(
                            "OPEN MICROSOFT LOGIN"
                    );

            openButton.getStyleClass().add(
                    "primary-button"
            );

            openButton.setMaxWidth(
                    Double.MAX_VALUE
            );

            openButton.setOnAction(event -> openInBrowser(url));

            // -----------------------------------------------------
            // STATUS
            // -----------------------------------------------------

            Label status =
                    new Label(
                            "Waiting for Microsoft authentication..."
                    );

            status.getStyleClass().add(
                    "login-status"
            );

            // -----------------------------------------------------
            // CONTENT
            // -----------------------------------------------------

            VBox content =
                    new VBox(
                            14,
                            logo,
                            step,
                            title,
                            subtitle,
                            instruction,
                            codeField,
                            copyButton,
                            openButton,
                            status
                    );

            content.setAlignment(
                    Pos.CENTER
            );

            content.setPadding(
                    new Insets(32)
            );

            // -----------------------------------------------------
            // ROOT
            // -----------------------------------------------------

            BorderPane root =
                    new BorderPane();

            root.setTop(
                    titleBar
            );

            root.setCenter(
                    content
            );

            root.getStyleClass().add(
                    "login-window"
            );

            ThemeManager.apply(
                    root,
                    ThemeManager.loadAccent()
            );

            // -----------------------------------------------------
            // SCENE
            // -----------------------------------------------------

            Scene scene =
                    new Scene(
                            root,
                            500,
                            470
                    );

            scene.setFill(
                    Color.TRANSPARENT
            );

            // -----------------------------------------------------
            // CSS
            // -----------------------------------------------------

            URL loginCss =
                    LoginPopup.class.getResource(
                            "/css/login.css"
                    );

            if (loginCss == null) {

                throw new IllegalStateException(
                        "Could not find /css/login.css"
                );
            }

            scene.getStylesheets().add(
                    loginCss.toExternalForm()
            );

            URL buttonsCss =
                    LoginPopup.class.getResource(
                            "/css/buttons.css"
                    );

            if (buttonsCss != null) {

                scene.getStylesheets().add(
                        buttonsCss.toExternalForm()
                );
            }

            // -----------------------------------------------------
            // SHOW
            // -----------------------------------------------------

            stage.setScene(
                    scene
            );

            Stage popupStage = stage;
            popupStage.setOnHidden(event -> {
                if (stage == popupStage) {
                    stage = null;
                }
                if (onClosed != null) {
                    onClosed.run();
                }
            });

            popupStage.show();

            if (LauncherSettings.isAutoOpenBrowserEnabled()) {
                PauseTransition browserDelay = new PauseTransition(Duration.millis(250));
                browserDelay.setOnFinished(event -> openInBrowser(url));
                browserDelay.play();
            }
        });
    }

    private static void openInBrowser(String url) {
        try {
            if (!java.awt.Desktop.isDesktopSupported()) {
                return;
            }
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static void close() {

        Platform.runLater(() -> {

            if (stage != null) {

                stage.close();
            }
        });
    }
}