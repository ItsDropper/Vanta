package org.example;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;

import org.example.ui.LauncherView;

public class Main extends Application {

    @Override
    public void start(Stage stage) {

        stage.initStyle(StageStyle.TRANSPARENT);

        LauncherView launcherView =
                new LauncherView(stage);

        Scene scene =
                new Scene(
                        launcherView.getRoot(),
                        1180,
                        760
                );

        scene.setFill(Color.TRANSPARENT);

        String[] stylesheets = {
                "/css/global.css",
                "/css/typography.css",
                "/css/sidebar.css",
                "/css/buttons.css",
                "/css/cards.css",
                "/css/home.css",
                "/css/accounts.css",
                "/css/instances.css",
                "/css/instance-settings.css",
                "/css/forms.css",
                "/css/login.css",
                "/css/scrollbars.css",
                "/css/modrinth.css",
                "/css/markdown.css",
                "/css/title-bar.css"
        };

        for (String stylesheet : stylesheets) {

            var resource =
                    getClass().getResource(stylesheet);

            if (resource == null) {
                throw new IllegalStateException(
                        "Missing CSS resource: " + stylesheet
                );
            }

            scene.getStylesheets().add(
                    resource.toExternalForm()
            );
        }

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

