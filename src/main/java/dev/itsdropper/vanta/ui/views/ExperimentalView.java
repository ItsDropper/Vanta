package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.launcher.ClasspathBuilder;
import dev.itsdropper.vanta.launcher.service.AccountService;
import dev.itsdropper.vanta.launcher.service.LaunchPerformanceTracker;
import dev.itsdropper.vanta.ui.LauncherSettings;
import dev.itsdropper.vanta.ui.LauncherViewLifecycle;
import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.async.UiExecutor;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class ExperimentalView extends VBox implements LauncherViewLifecycle {

    private final AccountService accountService;
    private final Label authState = new Label();
    private final Label authDetails = new Label();
    private final Label lastRefresh = new Label();
    private final Label cacheStats = new Label();
    private final Label measuredInstance = new Label();
    private final Label baselineStats = new Label();
    private final Label optimizedStats = new Label();
    private final Label vanillaStats = new Label();
    private final Label comparisonStats = new Label();
    private final CheckBox cacheEnabled = new CheckBox(Localization.text("experimental.performance.enableCache"));
    private final Button refreshSession = new Button(Localization.text("experimental.auth.refresh"));
    private final Runnable healthListener = () -> UiExecutor.shared().dispatchToUi(this::refreshStatus);
    private final Timeline ticker = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshMetrics()));

    public ExperimentalView(AccountService accountService) {
        this.accountService = accountService;
        getStyleClass().add("experimental-view");
        setFillWidth(true);

        Label eyebrow = new Label(Localization.text("experimental.eyebrow"));
        eyebrow.getStyleClass().add("page-eyebrow");
        Label title = new Label(Localization.text("experimental.title"));
        title.getStyleClass().add("page-title");
        Label intro = new Label(Localization.text("experimental.intro"));

        VBox authCard = new VBox(10);
        authCard.getStyleClass().add("settings-card");
        Label authTitle = new Label(Localization.text("experimental.auth.title"));
        authTitle.getStyleClass().add("section-title");
        authState.getStyleClass().add("experimental-status");
        authDetails.setWrapText(true);
        lastRefresh.getStyleClass().add("muted");
        refreshSession.setOnAction(event -> refreshSession());
        refreshSession.setDisable(accountService.getCurrentAccount() == null);
        authCard.getChildren().addAll(authTitle, authState, authDetails, lastRefresh, refreshSession);

        VBox performanceCard = new VBox(12);
        performanceCard.getStyleClass().add("settings-card");
        Label performanceTitle = new Label(Localization.text("experimental.performance.title"));
        performanceTitle.getStyleClass().add("section-title");
        Label explanation = new Label(
                Localization.text("experimental.performance.description")
        );
        explanation.setWrapText(true);
        cacheEnabled.setSelected(LauncherSettings.isExperimentalLaunchCacheEnabled());
        cacheEnabled.setOnAction(event -> {
            LauncherSettings.setExperimentalLaunchCacheEnabled(cacheEnabled.isSelected());
            ClasspathBuilder.clearCache();
            refreshMetrics();
        });
        Label benchmarkMethod = new Label(Localization.text("experimental.performance.benchmarkMethod"));
        benchmarkMethod.setWrapText(true);
        Button clearCache = new Button(Localization.text("experimental.performance.clearCache"));
        clearCache.setOnAction(event -> {
            ClasspathBuilder.clearCache();
            refreshMetrics();
        });
        Button resetSamples = new Button(Localization.text("experimental.performance.resetSamples"));
        resetSamples.setOnAction(event -> {
            LaunchPerformanceTracker.clear();
            refreshMetrics();
        });
        cacheStats.getStyleClass().add("muted");
        measuredInstance.setWrapText(true);
        baselineStats.setWrapText(true);
        optimizedStats.setWrapText(true);
        vanillaStats.setWrapText(true);
        comparisonStats.setWrapText(true);
        performanceCard.getChildren().addAll(
                performanceTitle, explanation, benchmarkMethod, cacheEnabled, cacheStats,
                measuredInstance, baselineStats, optimizedStats, comparisonStats, vanillaStats, clearCache, resetSamples
        );

        VBox pageContent = new VBox(18, eyebrow, title, intro, authCard, performanceCard);
        pageContent.setFillWidth(true);
        pageContent.setPadding(new Insets(24));

        ScrollPane scroll = new ScrollPane(pageContent);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("experimental-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);
        refreshStatus();
        refreshMetrics();
        ticker.setCycleCount(Timeline.INDEFINITE);
    }

    private void refreshSession() {
        refreshSession.setDisable(true);
        authDetails.setText(Localization.text("experimental.auth.refreshing"));
        UiExecutor.shared().execute(() -> {
            try {
                accountService.refreshSessionNow();
            } catch (Exception failure) {
                // AccountService publishes a typed, localized health state.
            }
            UiExecutor.shared().dispatchToUi(this::refreshStatus);
        });
    }

    private void refreshStatus() {
        AccountService.SessionHealth health = accountService.getSessionHealth();
        String healthKey = health.name().toLowerCase(Locale.ROOT);
        authState.setText(Localization.text("experimental.auth.health." + healthKey));
        authDetails.setText(Localization.text("experimental.auth.details." + healthKey));
        long refreshedAt = accountService.getLastSuccessfulRefreshAt();
        lastRefresh.setText(refreshedAt <= 0 ? Localization.text("experimental.auth.noRefresh")
                : Localization.text("experimental.auth.lastRefresh").formatted(
                        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                                .withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(refreshedAt))));
        refreshSession.setDisable(accountService.getCurrentAccount() == null
                || health == AccountService.SessionHealth.CLOSED
                || health == AccountService.SessionHealth.AUTHENTICATING
                || health == AccountService.SessionHealth.REFRESHING);
    }

    private void refreshMetrics() {
        ClasspathBuilder.CacheStats stats = ClasspathBuilder.getCacheStats();
        cacheStats.setText(Localization.text("experimental.performance.cacheStats")
                .formatted(stats.hits(), stats.misses(), stats.entries()));
        LaunchPerformanceTracker.Summary modded = LaunchPerformanceTracker.summaryForMostRecent(true);
        LaunchPerformanceTracker.Summary vanilla = LaunchPerformanceTracker.summaryForMostRecent(false);
        measuredInstance.setText(modded.instanceName() == null ? "" : Localization.text("experimental.performance.instance")
                .formatted(modded.instanceName(), modded.loader()));
        baselineStats.setText(Localization.text("experimental.performance.baseline")
                .formatted(format(modded.baselineMedianMillis(), modded.baselineSamples())));
        optimizedStats.setText(Localization.text("experimental.performance.optimized")
                .formatted(format(modded.optimizedMedianMillis(), modded.optimizedSamples())));
        comparisonStats.setText(compare(modded));
        vanillaStats.setText(Localization.text("experimental.performance.vanilla")
                .formatted(format(vanilla.baselineMedianMillis(), vanilla.baselineSamples()),
                        format(vanilla.optimizedMedianMillis(), vanilla.optimizedSamples())));
    }

    private String format(long medianMillis, int samples) {
        return samples == 0 ? Localization.text("experimental.performance.notMeasured")
                : Localization.text("experimental.performance.median").formatted(medianMillis, samples);
    }

    private String compare(LaunchPerformanceTracker.Summary summary) {
        if (summary.baselineSamples() < 3 || summary.optimizedSamples() < 3
                || summary.baselineMedianMillis() <= 0) {
            return Localization.text("experimental.performance.comparison.insufficient");
        }
        double change = (summary.baselineMedianMillis() - summary.optimizedMedianMillis())
                * 100.0 / summary.baselineMedianMillis();
        if (change > 1.0) {
            return Localization.text("experimental.performance.comparison.faster").formatted(change);
        }
        if (change < -1.0) {
            return Localization.text("experimental.performance.comparison.slower").formatted(Math.abs(change));
        }
        return Localization.text("experimental.performance.comparison.similar");
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
