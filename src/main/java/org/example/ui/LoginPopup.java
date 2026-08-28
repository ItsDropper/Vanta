package org.example.ui;

import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class LoginPopup {

    private static Stage stage;

    public static void show(String url, String code) {
        Platform.runLater(() -> {

            stage = new Stage();
            stage.setTitle("Microsoft Login");

            Label label = new Label("Open this link and enter the code:");

            TextArea text = new TextArea(
                    "Link:\n" +
                            url +
                            "\n\nCode:\n" +
                            code +
                            "\n\nWaiting for login..."
            );

            text.setEditable(false);
            text.setWrapText(true);
            text.setPrefSize(400, 200);

            Button copyButton = new Button("Copy Code");

            copyButton.setOnAction(e -> {
                Clipboard clipboard = Clipboard.getSystemClipboard();
                ClipboardContent content = new ClipboardContent();

                content.putString(code);

                clipboard.setContent(content);
            });;

            VBox root = new VBox(10, label, text, copyButton);

            Scene scene = new Scene(root, 450, 300);

            stage.setScene(scene);
            stage.show();
        });
    }

    public static void close() {
        Platform.runLater(() -> {
            if (stage != null) {
                stage.close();
            }
        });
    }
}