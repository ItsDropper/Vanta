package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import org.example.launcher.MinecraftLauncher;
import org.example.launcher.account.AccountManager;
import org.example.launcher.auth.Account;
import org.example.launcher.auth.AuthManager;

public class Main extends Application {

    @Override
    public void start(Stage stage) {

        Button launchButton = new Button("Launch Minecraft");

        launchButton.setOnAction(e -> {
            System.out.println("BUTTON CLICKED");

            new Thread(() -> {
                try {
                    Account account = AccountManager.loadAccount();

                    if (account == null) {
                        account = AuthManager.login();
                    }

                    MinecraftLauncher.launch(account);
                } catch (Throwable ex) {
                    ex.printStackTrace();

                    javafx.application.Platform.runLater(() -> {
                        new javafx.scene.control.Alert(
                                javafx.scene.control.Alert.AlertType.ERROR,
                                ex.toString()
                        ).showAndWait();
                    });

                }
            }).start();
        });

        StackPane root = new StackPane();
        root.getChildren().add(launchButton);

        Scene scene = new Scene(root, 500, 300);

        stage.setTitle("Vanta");
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {
        try {
            System.out.println("Main started");
            launch(args);
        } catch (Throwable t) {
            t.printStackTrace();

            try {
                java.nio.file.Files.writeString(
                        java.nio.file.Path.of("launcher-crash.txt"),
                        t.toString()
                );
            } catch (Exception ignored) {}

            throw t;
        }
    }
}