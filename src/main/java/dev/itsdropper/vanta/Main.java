package dev.itsdropper.vanta;

import javafx.animation.PauseTransition;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.paint.Color;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.nio.file.Path;
import java.util.List;

import dev.itsdropper.vanta.ui.LauncherView;
import dev.itsdropper.vanta.ui.LoadingScreen;
import dev.itsdropper.vanta.ui.ThemeManager;
import dev.itsdropper.vanta.telemetry.TelemetryService;

public class Main extends Application {

    private static final double WINDOW_WIDTH = 1440;
    private static final double WINDOW_HEIGHT = 900;
    private static final long MINIMUM_LOADING_MS = 520;

    @Override
    public void start(Stage stage) {
        TelemetryService.installUncaughtExceptionHandler();

        // Keep the JavaFX application alive when the launcher window is
        // hidden while Minecraft is running.
        javafx.application.Platform.setImplicitExit(false);

        stage.initStyle(StageStyle.TRANSPARENT);

        List<String> arguments = getParameters().getRaw();
        if (arguments.contains("--capture-sidebar-screenshots")) {
            startScreenshotCapture(stage, arguments);
            return;
        }

        LoadingScreen loadingScreen = new LoadingScreen();
        ThemeManager.apply(loadingScreen.getRoot(), ThemeManager.loadAccent());

        Scene scene = new Scene(
                loadingScreen.getRoot(),
                WINDOW_WIDTH,
                WINDOW_HEIGHT
        );
        scene.setFill(Color.TRANSPARENT);

        var loadingCss = getClass().getResource("/css/shell.css");
        if (loadingCss == null) {
            throw new IllegalStateException("Missing CSS resource: /css/shell.css");
        }
        scene.getStylesheets().add(loadingCss.toExternalForm());

        stage.setTitle("Vanta");
        stage.setMinWidth(1180);
        stage.setMinHeight(760);
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

    private void startScreenshotCapture(Stage stage, List<String> arguments) {
        Path outputDirectory = Path.of("build", "ui-screenshots");
        int captureArgument = arguments.indexOf("--capture-sidebar-screenshots");
        if (captureArgument >= 0 && captureArgument + 1 < arguments.size()
                && !arguments.get(captureArgument + 1).startsWith("--")) {
            outputDirectory = Path.of(arguments.get(captureArgument + 1));
        }

        LauncherView launcher = new LauncherView(stage, () -> {}, true);
        Scene scene = new Scene(launcher.getRoot(), WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.setFill(Color.TRANSPARENT);
        applyLauncherStylesheets(scene);

        stage.setTitle("Vanta — UI screenshot capture");
        stage.setMinWidth(1180);
        stage.setMinHeight(760);
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();

        Path destination = outputDirectory;
        PauseTransition warmup = new PauseTransition(Duration.millis(400));
        warmup.setOnFinished(event -> launcher.captureSidebarScreenshots(destination, failure -> {
            if (failure != null) {
                System.err.println("Sidebar screenshot capture failed.");
                failure.printStackTrace();
                System.exit(1);
                return;
            }
            System.out.println("Captured all sidebar pages to " + destination.toAbsolutePath());
            javafx.application.Platform.exit();
        }));
        warmup.play();
    }

    private void applyLauncherStylesheets(Scene scene) {
        String[] stylesheets = {
                "/css/base.css", "/css/shell.css", "/css/home.css", "/css/library.css",
                "/css/content.css", "/css/manage.css", "/css/utilities.css", "/css/tokens.css"
        };
        for (String stylesheet : stylesheets) {
            var resource = getClass().getResource(stylesheet);
            if (resource == null) {
                throw new IllegalStateException("Missing CSS resource: " + stylesheet);
            }
            scene.getStylesheets().add(resource.toExternalForm());
        }
    }

    private void initializeLauncher(
            Stage stage,
            Scene scene,
            LoadingScreen loadingScreen,
            long startedAt
    ) {
        loadingScreen.setStatus("BUILDING YOUR WORKSPACE");

        final boolean[] launcherReady = {false};
        final boolean[] minimumTimeElapsed = {false};
        final boolean[] finished = {false};
        final LauncherView[] launcherHolder = new LauncherView[1];

        Runnable tryFinish = () -> {
            if (finished[0] || !launcherReady[0] || !minimumTimeElapsed[0]) {
                return;
            }

            finished[0] = true;
            loadingScreen.setStatus("READY");

            LauncherView readyLauncher = launcherHolder[0];
            loadingScreen.finish(() -> {
                scene.setRoot(readyLauncher.getRoot());
                stage.setScene(scene);
            });
        };

        launcherHolder[0] = new LauncherView(stage, () -> {
            launcherReady[0] = true;
            tryFinish.run();
        });

        // Replace the splash-only stylesheet with the consolidated launcher set.
        scene.getStylesheets().clear();
        applyLauncherStylesheets(scene);

        long elapsedMs =
                (System.nanoTime() - startedAt) / 1_000_000L;

        long remainingMs =
                Math.max(0, MINIMUM_LOADING_MS - elapsedMs);

        PauseTransition minimumDisplay =
                new PauseTransition(Duration.millis(remainingMs));

        minimumDisplay.setOnFinished(event -> {
            minimumTimeElapsed[0] = true;
            tryFinish.run();
        });

        minimumDisplay.play();
    }

    public static void main(String[] args) {
        TelemetryService.installUncaughtExceptionHandler();
        try {
            System.out.println("Vanta started");
            launch(args);
        } catch (Throwable t) {
            TelemetryService.reportCrash(t);
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
