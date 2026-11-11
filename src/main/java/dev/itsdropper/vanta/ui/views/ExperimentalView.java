package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.launcher.ClasspathBuilder;
import dev.itsdropper.vanta.launcher.service.AccountService;
import dev.itsdropper.vanta.launcher.service.LaunchPerformanceTracker;
import dev.itsdropper.vanta.ui.LauncherSettings;
import dev.itsdropper.vanta.ui.LauncherViewLifecycle;
import dev.itsdropper.vanta.ui.async.UiExecutor;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class ExperimentalView extends VBox implements LauncherViewLifecycle {

    private final AccountService accountService;
    private final Label authState = new Label();
    private final Label authDetails = new Label();
    private final Label lastRefresh = new Label();
    private final Label cacheStats = new Label();
    private final Label baselineStats = new Label();
    private final Label optimizedStats = new Label();
    private final Label vanillaStats = new Label();
    private final CheckBox cacheEnabled = new CheckBox("Enable experimental launch-preparation cache");
    private final Button refreshSession = new Button("Refresh Minecraft session");
    private final Runnable healthListener = () -> UiExecutor.shared().dispatchToUi(this::refreshStatus);
    private final Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshMetrics()));

    public ExperimentalView(AccountService accountService) {
        this.accountService = accountService;
        getStyleClass().add("experimental-view");
        setSpacing(18);
        setPadding(new Insets(24));

        Label eyebrow = new Label("EARLY ACCESS");
        eyebrow.getStyleClass().add("page-eyebrow");
        Label title = new Label("Experimental");
        title.getStyleClass().add("page-title");
        Label intro = new Label("Isolated experiments for launcher performance and reliability. Features here are opt-in and may change.");

        VBox authCard = new VBox(10);
        authCard.getStyleClass().add("settings-card");
        Label authTitle = new Label("Authentication health");
        authTitle.getStyleClass().add("section-title");
        authState.getStyleClass().add("experimental-status");
        authDetails.setWrapText(true);
        lastRefresh.getStyleClass().add("muted");
        refreshSession.setOnAction(event -> refreshSession());
        refreshSession.setDisable(accountService.getCurrentAccount() == null);
        authCard.getChildren().addAll(authTitle, authState, authDetails, lastRefresh, refreshSession);

        VBox performanceCard = new VBox(12);
        performanceCard.getStyleClass().add("settings-card");
        Label performanceTitle = new Label("Modded launch preparation");
        performanceTitle.getStyleClass().add("section-title");
        Label explanation = new Label(
                "This experiment caches parsed version metadata and resolved classpath/native paths. "
                + "It is invalidated when version metadata changes or a required file disappears. "
                + "It does not change Minecraft, loader or mod initialization inside the game process."
        );
        explanation.setWrapText(true);
        cacheEnabled.setSelected(LauncherSettings.isExperimentalLaunchCacheEnabled());
        cacheEnabled.setOnAction(event -> {
            LauncherSettings.setExperimentalLaunchCacheEnabled(cacheEnabled.isSelected());
            ClasspathBuilder.clearCache();
            refreshMetrics();
        });
        Button clearCache = new Button("Clear experiment cache");
        clearCache.setOnAction(event -> {
            ClasspathBuilder.clearCache();
            refreshMetrics();
        });
        cacheStats.getStyleClass().add("muted");
        baselineStats.setWrapText(true);
        optimizedStats.setWrapText(true);
        vanillaStats.setWrapText(true);
        performanceCard.getChildren().addAll(
                performanceTitle, explanation, cacheEnabled, cacheStats,
                baselineStats, optimizedStats, vanillaStats, clearCache
        );

        getChildren().addAll(eyebrow, title, intro, authCard, performanceCard);
        VBox.setVgrow(performanceCard, Priority.NEVER);
        refreshStatus();
        refreshMetrics();
        ticker.setCycleCount(Timeline.INDEFINITE);
    }

    private void refreshSession() {
        refreshSession.setDisable(true);
        authDetails.setText("Refreshing the saved Microsoft session...");
        UiExecutor.shared().execute(() -> {
            Exception failure = null;
            try {
                accountService.refreshSessionNow();
            } catch (Exception ex) {
                failure = ex;
            }
            Exception result = failure;
            UiExecutor.shared().dispatchToUi(() -> {
                refreshSession.setDisable(accountService.getCurrentAccount() == null);
                if (result != null) {
                    authDetails.setText(result.getMessage() == null
                            ? result.getClass().getSimpleName() : result.getMessage());
                }
                refreshStatus();
            });
        });
    }

    private void refreshStatus() {
        AccountService.SessionHealth health = accountService.getSessionHealth();
        authState.setText(health.name().replace('_', ' '));
        authDetails.setText(accountService.getSessionHealthMessage());
        long refreshedAt = accountService.getLastSuccessfulRefreshAt();
        lastRefresh.setText(refreshedAt <= 0 ? "No successful refresh recorded."
                : "Last successful refresh: " + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(refreshedAt)));
        refreshSession.setDisable(accountService.getCurrentAccount() == null
                || health == AccountService.SessionHealth.CLOSED
                || health == AccountService.SessionHealth.AUTHENTICATING
                || health == AccountService.SessionHealth.REFRESHING);
    }

    private void refreshMetrics() {
        ClasspathBuilder.CacheStats stats = ClasspathBuilder.getCacheStats();
        cacheStats.setText("Cache: " + stats.hits() + " hits · " + stats.misses()
                + " misses · " + stats.entries() + " entries");
        LaunchPerformanceTracker.Summary modded = LaunchPerformanceTracker.summary(true);
        LaunchPerformanceTracker.Summary vanilla = LaunchPerformanceTracker.summary(false);
        baselineStats.setText("Modded preparation — baseline: "
                + format(modded.baselineMedianMillis(), modded.baselineSamples()));
        optimizedStats.setText("Modded preparation — cache enabled: "
                + format(modded.optimizedMedianMillis(), modded.optimizedSamples()));
        vanillaStats.setText("Vanilla control — baseline: "
                + format(vanilla.baselineMedianMillis(), vanilla.baselineSamples())
                + " · cache enabled: " + format(vanilla.optimizedMedianMillis(), vanilla.optimizedSamples()));
    }

    private String format(long medianMillis, int samples) {
        return samples == 0 ? "not measured yet" : medianMillis + " ms median (" + samples + " samples)";
    }

    @Override
    public void onShown() {
        accountService.addSessionHealthListener(healthListener);
        refreshStatus();
        ticker.play();
    }

    @Override
    public void onHidden() {
        accountService.removeSessionHealthListener(healthListener);
        ticker.stop();
    }
}
