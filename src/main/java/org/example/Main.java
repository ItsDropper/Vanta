package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;

import org.example.ui.LauncherView;

public class Main extends Application {

    @Override
    public void start(Stage stage) {

        LauncherView launcherView =
                new LauncherView(stage);

        Scene scene =
                new Scene(
                        launcherView.getRoot(),
                        1180,
                        760
                );

        scene.getStylesheets().add(
                getClass()
                        .getResource("/launcher.css")
                        .toExternalForm()
        );

        stage.setTitle("Vanta");
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.show();
    }

    public static void main(String[] args) {

        try {

            System.out.println(
                    "Vanta started"
            );

            launch(args);

        } catch (Throwable t) {

            t.printStackTrace();

            try {

                java.nio.file.Files.writeString(
                        java.nio.file.Path.of(
                                "launcher-crash.txt"
                        ),
                        t.toString()
                );

            } catch (Exception ignored) {
            }

            throw t;
        }
    }
}

