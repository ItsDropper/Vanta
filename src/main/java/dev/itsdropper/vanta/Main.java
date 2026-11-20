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
import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.telemetry.TelemetryService;
import dev.itsdropper.vanta.security.RuntimeSecurityPolicy;

public class Main extends Application {

    private static final double WINDOW_WIDTH = 1440;
    private static final double WINDOW_HEIGHT = 900;
    private static final long MINIMUM_LOADING_MS = 520;

    @Override
    public void start(Stage stage) {
        dev.itsdropper.vanta.ui.VantaFonts.ensureLoaded();
        Localization.initializeFromSettings();
        List<String> arguments = getParameters().getRaw();
        boolean screenshotMode = isScreenshotCapture(arguments);
        if (!screenshotMode) {
            TelemetryService.installUncaughtExceptionHandler();
        }

        stage.initStyle(screenshotMode ? StageStyle.UNDECORATED : StageStyle.TRANSPARENT);

        if (screenshotMode) {
            startScreenshotCapture(stage, arguments);
            return;
        }

        // Install shutdown handling before showing the splash screen. The
        // launcher view and its services are initialized asynchronously.
        stage.addEventHandler(javafx.stage.WindowEvent.WINDOW_CLOSE_REQUEST,
                event -> LauncherProcessLifecycle.exit());

        // Keep Vanta alive while its window is intentionally hidden during a
        // Minecraft session. The close handler above always terminates the JVM.
        javafx.application.Platform.setImplicitExit(false);

        RuntimeSecurityPolicy.getInstance().initialize();

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
        int captureArgument = arguments.indexOf("--capture-ui-screenshots");
        if (captureArgument < 0) {
            captureArgument = arguments.indexOf("--capture-sidebar-screenshots");
        }
        if (captureArgument >= 0 && captureArgument + 1 < arguments.size()
                && !arguments.get(captureArgument + 1).startsWith("--")) {
            outputDirectory = Path.of(arguments.get(captureArgument + 1));
        }

        LauncherView launcher = new LauncherView(stage, () -> {}, true);
        Scene scene = new Scene(launcher.getRoot(), WINDOW_WIDTH, WINDOW_HEIGHT);
        scene.setFill(Color.web("#0B0D12"));
        applyLauncherStylesheets(scene);

        stage.setTitle("Vanta — UI screenshot capture");
        stage.setMinWidth(1180);
        stage.setMinHeight(760);
        stage.setResizable(false);
        stage.setScene(scene);
        stage.show();

        Path destination = outputDirectory;
        PauseTransition warmup = new PauseTransition(Duration.millis(400));
        warmup.setOnFinished(event -> launcher.captureUiScreenshots(destination, failure -> {
            if (failure != null) {
                System.err.println("UI screenshot capture failed.");
                failure.printStackTrace();
                System.exit(1);
                return;
            }
            System.out.println("Captured all major UI views to " + destination.toAbsolutePath());
            javafx.application.Platform.exit();
        }));
        warmup.play();
    }

    private void applyLauncherStylesheets(Scene scene) {
        String[] stylesheets = {
                "/css/base.css", "/css/shell.css", "/css/home.css", "/css/library.css",
                "/css/content.css", "/css/manage.css", "/css/utilities.css", "/css/tokens.css",
                "/css/vanta.css"
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
                try {
                    dev.itsdropper.vanta.launcher.update.UpdateTransaction.acknowledgeHealthyStartup(
                            getParameters().getRaw().toArray(String[]::new));
                } catch (java.io.IOException healthFailure) {
                    System.err.println("[Vanta Update Health] Could not publish startup acknowledgement: "
                            + healthFailure.getMessage());
                }
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

    private static boolean isScreenshotCapture(List<String> arguments) {
        return arguments.contains("--capture-ui-screenshots")
                || arguments.contains("--capture-sidebar-screenshots");
    }

    private static boolean isScreenshotCapture(String[] arguments) {
        for (String argument : arguments) {
            if ("--capture-ui-screenshots".equals(argument)
                    || "--capture-sidebar-screenshots".equals(argument)) {
                return true;
            }
        }
        return false;
    }

    static boolean isUpdaterInvocation(String[] args) {
        return args != null && args.length > 0 && "--vanta-updater".equals(args[0]);
    }

    static boolean isUpdaterCleanupInvocation(String[] args) {
        return args != null && args.length >= 4 && "--vanta-cleanup-updater".equals(args[0]);
    }

    public static void main(String[] args) {
        if (isUpdaterCleanupInvocation(args)) {
            try {
                dev.itsdropper.vanta.launcher.update.UpdaterWorkspaceCleanup.schedule(
                        Path.of(args[1]),
                        Long.parseLong(args[2]),
                        Long.parseLong(args[3])
                );
            } catch (RuntimeException invalidCleanupArguments) {
                System.err.println("[Vanta Updater Cleanup] Ignoring invalid cleanup arguments.");
            }
            launch(args);
            return;
        }

        if (isUpdaterInvocation(args)) {
            dev.itsdropper.vanta.launcher.update.UpdaterMain.main(
                    java.util.Arrays.copyOfRange(args, 1, args.length)
            );
            return;
        }

        if (!dev.itsdropper.vanta.launcher.update.UpdateTransaction.isHealthInvocation(args)) {
            try {
                java.nio.file.Path installation = java.util.Optional.ofNullable(
                        dev.itsdropper.vanta.launcher.update.ApplicationLocator.getApplicationDirectoryForRecovery())
                        .orElseGet(dev.itsdropper.vanta.launcher.update.ApplicationLocator::getApplicationDirectory);
                dev.itsdropper.vanta.launcher.update.UpdateTransaction.ensureNoActiveUpdate(installation);
                dev.itsdropper.vanta.launcher.update.UpdateTransaction.recoverBeforeLaunch(installation);
            } catch (java.io.IOException recoveryFailure) {
                System.err.println("[Vanta Update Recovery] " + recoveryFailure.getMessage());
                recoveryFailure.printStackTrace();
                return;
            } catch (IllegalStateException unPackagedDevelopmentLaunch) {
                // Self-update recovery applies only to packaged jpackage images.
            }
        }

        dev.itsdropper.vanta.launcher.update.UpdaterWorkspaceCleanup.scheduleStaleWorkspaceSweep();

        boolean screenshotMode = isScreenshotCapture(args);
        if (!screenshotMode) {
            TelemetryService.installUncaughtExceptionHandler();
        }
        try {
            System.out.println("Vanta started");
            launch(args);
        } catch (Throwable t) {
            if (!screenshotMode) {
                TelemetryService.reportCrash(t);
            }
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
