package org.example;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import org.example.ui.LauncherView;
import org.example.ui.LoadingScreen;

public class Main extends Application {

    private static final double WINDOW_WIDTH = 1180;
    private static final double WINDOW_HEIGHT = 760;
    private static final long MINIMUM_LOADING_MS = 520;

    @Override
    public void start(Stage stage) {
        stage.initStyle(StageStyle.TRANSPARENT);

        LoadingScreen loadingScreen = new LoadingScreen();

        Scene scene = new Scene(
                loadingScreen.getRoot(),
                WINDOW_WIDTH,
                WINDOW_HEIGHT
        );
        scene.setFill(Color.TRANSPARENT);

        var loadingCss = getClass().getResource("/css/loading.css");
        if (loadingCss == null) {
            throw new IllegalStateException("Missing CSS resource: /css/loading.css");
        }
        scene.getStylesheets().add(loadingCss.toExternalForm());

        stage.setTitle("Vanta");
        stage.setMinWidth(1024);
        stage.setMinHeight(680);
        stage.setScene(scene);
        stage.show();

        long startedAt = System.nanoTime();

        PauseTransition firstFrame =
                new PauseTransition(Duration.millis(80));

        firstFrame.setOnFinished(event ->
                initializeLauncher(
                        stage,
                        scene,
                        loadingScreen,
                        startedAt
                )
        );

        firstFrame.play();
    }

    private void initializeLauncher(
            Stage stage,
            Scene scene,
            LoadingScreen loadingScreen,
            long startedAt
    ) {
        loadingScreen.setStatus("BUILDING YOUR WORKSPACE");

        LauncherView launcherView = new LauncherView(stage);

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
                "/css/title-bar.css",
                "/css/design-system.css"
        };

        for (String stylesheet : stylesheets) {
            var resource = getClass().getResource(stylesheet);

            if (resource == null) {
                throw new IllegalStateException(
                        "Missing CSS resource: " + stylesheet
                );
            }

            scene.getStylesheets().add(resource.toExternalForm());
        }

        long elapsedMs =
                (System.nanoTime() - startedAt) / 1_000_000L;

        long remainingMs =
                Math.max(0, MINIMUM_LOADING_MS - elapsedMs);

        PauseTransition minimumDisplay =
                new PauseTransition(Duration.millis(remainingMs));

        minimumDisplay.setOnFinished(event -> {
            loadingScreen.setStatus("READY");
            loadingScreen.finish(() -> {
                scene.setRoot(launcherView.getRoot());
                stage.setScene(scene);
            });
        });

        minimumDisplay.play();
    }

    public static void main(String[] args) {
        try {
            System.out.println("Vanta started");
            launch(args);
        } catch (Throwable t) {
            t.printStackTrace();

            try {
                java.nio.file.Files.writeString(
                        java.nio.file.Path.of("launcher-crash.txt"),
                        t.toString()
                );
            } catch (Exception ignored) {
            }

            throw t;
        }
    }
}
