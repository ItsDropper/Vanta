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
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

public class LoginPopup {

    private static Stage stage;

    public static void show(
            String url,
            String code
    ) {

        Platform.runLater(() -> {

            if (stage != null) {
                stage.close();
            }

            stage = new Stage();

            stage.setTitle("Vanta — Connect Microsoft Account");
            stage.setResizable(false);

            // -----------------------------------------------------
            // HEADER
            // -----------------------------------------------------

            Label logo = new Label("VANTA");
            logo.getStyleClass().add("login-logo");

            Label title = new Label(
                    "Connect your Microsoft account"
            );
            title.getStyleClass().add("login-title");

            Label subtitle = new Label(
                    "Sign in with Microsoft to play Minecraft."
            );
            subtitle.getStyleClass().add("login-subtitle");

            // -----------------------------------------------------
            // CODE
            // -----------------------------------------------------

            Label instruction = new Label(
                    "Enter this code on the Microsoft verification page."
            );
            instruction.getStyleClass().add("login-instruction");

            TextField codeField =
                    new TextField(code);

            codeField.setEditable(false);
            codeField.setFocusTraversable(false);
            codeField.setAlignment(Pos.CENTER);

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

                copyButton.setText("COPIED");

                PauseTransition pause =
                        new PauseTransition(
                                Duration.seconds(1.5)
                        );

                pause.setOnFinished(
                        e -> copyButton.setText("COPY CODE")
                );

                pause.play();
            });

            // -----------------------------------------------------
            // OPEN MICROSOFT
            // -----------------------------------------------------

            Button openButton =
                    new Button("OPEN MICROSOFT LOGIN");

            openButton.getStyleClass().add(
                    "primary-button"
            );

            openButton.setMaxWidth(
                    Double.MAX_VALUE
            );

            openButton.setOnAction(event -> {

                try {

                    java.awt.Desktop
                            .getDesktop()
                            .browse(
                                    java.net.URI.create(url)
                            );

                } catch (Exception ex) {

                    ex.printStackTrace();
                }
            });

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
            // ROOT
            // -----------------------------------------------------

            VBox root =
                    new VBox(
                            14,
                            logo,
                            title,
                            subtitle,
                            instruction,
                            codeField,
                            copyButton,
                            openButton,
                            status
                    );

            root.setAlignment(
                    Pos.CENTER
            );

            root.setPadding(
                    new Insets(32)
            );

            root.getStyleClass().add(
                    "login-window"
            );

            Scene scene =
                    new Scene(
                            root,
                            500,
                            470
                    );

            scene.getStylesheets().add(
                    LoginPopup.class
                            .getResource("/launcher.css")
                            .toExternalForm()
            );

            stage.setScene(scene);
            stage.show();
        });
    }

    public static void close() {

        Platform.runLater(() -> {

            if (stage != null) {

                stage.close();
                stage = null;
            }
        });
    }
}