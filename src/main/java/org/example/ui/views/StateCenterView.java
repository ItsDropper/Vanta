package org.example.ui.views;

import javafx.application.Platform;
import javafx.animation.RotateTransition;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.launcher.instance.InstanceManager;
import org.example.launcher.model.Instance;
import org.example.launcher.state.InstanceState;
import org.example.launcher.state.SharedState;
import org.example.launcher.state.InstanceStateEngine;
import org.example.ui.components.IconView;
import org.example.ui.components.InstanceCard;
import org.example.ui.AnimationUtils;
import org.example.ui.LauncherSettings;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;\nimport java.util.IdentityHashMap;\nimport java.util.Map;

public final class StateCenterView extends VBox {
    private final VBox instanceList = new VBox(10);
    private final VBox sharedList = new VBox(10);
    private final ScrollPane instanceScroll = new ScrollPane(instanceList);
    private final Label overallTitle = new Label("READY");
    private final Label overallSubtitle = new Label("Vanta will inspect your environments when you open this page.");
    private final ProgressBar progress = new ProgressBar(0);\n    private final Label progressLabel = new Label("HEALTH");
    private final Button refreshButton = new Button("SCAN NOW", IconView.create(IconView.Type.REFRESH, 15));
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private volatile long scanGeneration;
    private volatile List<SharedState> sharedStates = List.of();\n    private final Map<Instance, HBox> liveInstanceCards = new IdentityHashMap<>();\n    private final Map<String, HBox> liveSharedRows = new java.util.HashMap<>();

    public StateCenterView() {
        getStyleClass().add("state-center");
        setPadding(new Insets(32));
        setSpacing(20);

        Label title = new Label("State");
        title.getStyleClass().add("page-title");
        Label subtitle = new Label("Understand what Vanta knows about every installed environment.");
        subtitle.getStyleClass().add("page-subtitle");
        VBox heading = new VBox(5, title, subtitle);

        VBox heroText = new VBox(5, overallTitle, overallSubtitle);
        overallTitle.getStyleClass().add("state-hero-title");
        overallSubtitle.getStyleClass().add("state-hero-subtitle");

        progress.setPrefWidth(220);
        progress.setPrefHeight(6);
        progress.getStyleClass().add("state-progress");

        Label healthLabel = new Label("HEALTH");
        healthLabel.getStyleClass().add("state-progress-label");
        VBox progressBox = new VBox(6, healthLabel, progress);

        HBox heroRow = new HBox(20, heroText, progressBox);
        heroRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(heroText, Priority.ALWAYS);

        StackPane hero = new StackPane(heroRow);
        hero.getStyleClass().add("state-hero");
        hero.setPadding(new Insets(22));

        refreshButton.getStyleClass().add("state-refresh-button");
        refreshButton.setFocusTraversable(false);
        refreshButton.setOnAction(e -> refresh());

        Label sectionTitle = new Label("ENVIRONMENTS");
        sectionTitle.getStyleClass().add("state-section-title");
        HBox section = new HBox(10, sectionTitle, refreshButton);
        section.setAlignment(Pos.CENTER_LEFT);

        instanceList.setFillWidth(true);
        instanceList.setPadding(new Insets(2));

        instanceScroll.getStyleClass().add("state-instance-scroll");
        instanceScroll.setFitToWidth(true);
        instanceScroll.setFitToHeight(false);
        instanceScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        instanceScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        instanceScroll.setPannable(true);
        instanceScroll.setFocusTraversable(false);
        VBox.setVgrow(instanceScroll, Priority.ALWAYS);

        VBox listCard = new VBox(instanceScroll);
        listCard.getStyleClass().add("state-list-card");
        listCard.setPadding(new Insets(12));
        VBox.setVgrow(listCard, Priority.ALWAYS);

        Label sharedTitle = new Label("SHARED RESOURCES");
        sharedTitle.getStyleClass().add("state-section-title");
        VBox sharedCard = new VBox(12, sharedTitle, sharedList);
        sharedCard.getStyleClass().add("state-list-card");
        sharedCard.setPadding(new Insets(16));

        getChildren().addAll(heading, hero, section, listCard, sharedCard);

        if (LauncherSettings.isAnimationsEnabled()) {
            AnimationUtils.slideFadeVertical(heading, 12);
            AnimationUtils.slideFadeVertical(hero, 16);
            AnimationUtils.slideFadeVertical(section, 12);
            AnimationUtils.slideFadeVertical(listCard, 18);
        }
    }

    public void onShown() {
        refresh();
    }

    public void refresh() {
        if (!scanning.compareAndSet(false, true)) {
            return;
        }

        long generation = ++scanGeneration;
        refreshButton.setDisable(true);
        refreshButton.setText("SCANNING...");
        animateRefreshButton(true);
        overallTitle.setText("SCANNING...");
        overallSubtitle.setText("Parallel scan starting...");
        progressLabel.setText("SCAN PROGRESS");
        progress.setProgress(0);
        instanceList.getChildren().clear();
        sharedList.getChildren().clear();
        liveInstanceCards.clear();
        liveSharedRows.clear();

        Thread coordinator = new Thread(() -> startParallelScan(generation), "Vanta-State-Coordinator");
        coordinator.setDaemon(true);
        coordinator.setUncaughtExceptionHandler((t, ex) -> {
            ex.printStackTrace();
            Platform.runLater(() -> failScan(generation, "The State scanner stopped unexpectedly."));
        });
        coordinator.start();
    }

    private void startParallelScan(long generation) {
        final List<Instance> instances;

        try {
            instances = InstanceManager.discoverInstances();
        } catch (Throwable ex) {
            ex.printStackTrace();
            Platform.runLater(() -> failScan(generation, "Vanta could not discover the installed environments."));
            return;
        }

        final int totalTasks = instances.size() + 2;
        final int workers = Math.max(1, Math.min(LauncherSettings.getStateScanWorkers(), Math.max(1, totalTasks)));
        final java.util.concurrent.atomic.AtomicInteger completed = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger healthy = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger attention = new java.util.concurrent.atomic.AtomicInteger();
        final java.util.concurrent.atomic.AtomicInteger broken = new java.util.concurrent.atomic.AtomicInteger();

        Platform.runLater(() -> {
            if (generation != scanGeneration) return;

            for (Instance instance : instances) {
                HBox card = createScanningCard(instance);
                liveInstanceCards.put(instance, card);
                instanceList.getChildren().add(card);
            }

            addSharedPlaceholder("Libraries");
            addSharedPlaceholder("Assets");

            overallSubtitle.setText(
                    totalTasks + " scan tasks queued • " + workers + " workers active"
            );

            org.example.ui.components.NotificationManager manager =
                    org.example.ui.components.NotificationManager.getGlobal();
            if (manager != null) {
                manager.showProgress(
                        "State scan",
                        "0/" + totalTasks + " complete • " + workers + " workers active"
                );
                manager.setProgress(0);
            }
        });

        java.util.concurrent.ExecutorService executor =
                java.util.concurrent.Executors.newFixedThreadPool(
                        workers,
                        runnable -> {
                            Thread thread = new Thread(runnable);
                            thread.setName("Vanta-State-Worker");
                            thread.setDaemon(true);
                            return thread;
                        }
                );

        for (Instance instance : instances) {
            executor.submit(() -> {
                InstanceState state;
                try {
                    state = InstanceStateEngine.inspect(instance);
                } catch (Throwable ex) {
                    ex.printStackTrace();
                    state = new InstanceState(
                            InstanceState.Level.BROKEN,
                            "SCAN FAILED",
                            "Vanta could not inspect this environment.",
                            0, 1, 0, 0, ""
                    );
                }

                final InstanceState finalState = state;
                Platform.runLater(() -> {
                    if (generation != scanGeneration) return;
                    replaceInstanceCard(instance, finalState);
                    updateLiveSummary(instances.size(), healthy.get(), attention.get(), broken.get(), completed.get(), totalTasks);
                });

                int done = completed.incrementAndGet();
                if (state.isHealthy()) healthy.incrementAndGet();
                else if (state.getLevel() == InstanceState.Level.ATTENTION) attention.incrementAndGet();
                else broken.incrementAndGet();

                updateScanProgress(generation, done, totalTasks, workers,
                        healthy.get(), attention.get(), broken.get(), instance.getName());
                maybeFinishParallelScan(
                        executor, generation, done, totalTasks, instances,
                        healthy, attention, broken
                );
            });
        }

        executor.submit(() -> {
            SharedState state = InstanceStateEngine.inspectShared(
                    "Libraries",
                    org.example.launcher.MinecraftLocator.getLibrariesDirectory(),
                    true
            );
            int done = completed.incrementAndGet();

            Platform.runLater(() -> {
                if (generation != scanGeneration) return;
                replaceSharedRow(state);
            });

            updateScanProgress(generation, done, totalTasks, workers,
                    healthy.get(), attention.get(), broken.get(), "Libraries");
            maybeFinishParallelScan(
                    executor, generation, done, totalTasks, instances,
                    healthy, attention, broken
            );
        });

        executor.submit(() -> {
            SharedState state = InstanceStateEngine.inspectShared(
                    "Assets",
                    org.example.launcher.MinecraftLocator.getVantaDirectory().resolve("assets"),
                    false
            );
            int done = completed.incrementAndGet();

            Platform.runLater(() -> {
                if (generation != scanGeneration) return;
                replaceSharedRow(state);
            });

            updateScanProgress(generation, done, totalTasks, workers,
                    healthy.get(), attention.get(), broken.get(), "Assets");
            maybeFinishParallelScan(
                    executor, generation, done, totalTasks, instances,
                    healthy, attention, broken
            );
        });
    }

    private void maybeFinishParallelScan(
            java.util.concurrent.ExecutorService executor,
            long generation,
            int done,
            int total,
            List<Instance> instances,
            java.util.concurrent.atomic.AtomicInteger healthy,
            java.util.concurrent.atomic.AtomicInteger attention,
            java.util.concurrent.atomic.AtomicInteger broken
    ) {
        if (done != total) {
            return;
        }

        executor.shutdown();

        Platform.runLater(() -> {
            if (generation != scanGeneration) return;

            progressLabel.setText("HEALTH");
            progress.setProgress(instances.isEmpty()
                    ? 1.0
                    : (double) healthy.get() / instances.size());

            if (instances.isEmpty()) {
                overallTitle.setText("NO ENVIRONMENTS");
                overallSubtitle.setText(
                        "Shared resources were scanned successfully."
                );
            } else {
                int affected = attention.get() + broken.get();
                overallTitle.setText(
                        affected == 0
                                ? "ALL SYSTEMS HEALTHY"
                                : affected + " ENVIRONMENT" + (affected == 1 ? "" : "S") + " NEED ATTENTION"
                );
                overallSubtitle.setText(
                        healthy.get() + " healthy  •  "
                                + attention.get() + " attention  •  "
                                + broken.get() + " broken"
                );
            }

            org.example.ui.components.NotificationManager manager =
                    org.example.ui.components.NotificationManager.getGlobal();
            if (manager != null) {
                manager.setProgress(1.0);
                manager.success(
                        "State scan complete",
                        total + "/" + total + " checks complete • "
                                + healthy.get() + " healthy • "
                                + attention.get() + " attention • "
                                + broken.get() + " broken"
                );
            }

            scanning.set(false);
            refreshButton.setDisable(false);
            refreshButton.setText("SCAN AGAIN");
            animateRefreshButton(false);
        });
    }

    private void updateScanProgress(
            long generation,
            int done,
            int total,
            int workers,
            int healthy,
            int attention,
            int broken,
            String completedName
    ) {
        double fraction = total == 0 ? 1.0 : (double) done / total;

        Platform.runLater(() -> {
            if (generation != scanGeneration) return;

            progress.setProgress(fraction);
            overallSubtitle.setText(
                    done + "/" + total + " complete • "
                            + healthy + " healthy • "
                            + attention + " attention • "
                            + broken + " broken"
            );

            org.example.ui.components.NotificationManager manager =
                    org.example.ui.components.NotificationManager.getGlobal();
            if (manager != null) {
                manager.updateProgress(
                        done + "/" + total + " complete • finished " + safe(completedName, "task")
                                + " • " + workers + " workers"
                );
                manager.setProgress(fraction);
            }
        });
    }

    private void updateLiveSummary(
            int instanceCount,
            int healthy,
            int attention,
            int broken,
            int done,
            int total
    ) {
        if (done < total) {
            overallSubtitle.setText(
                    done + "/" + total + " complete • "
                            + healthy + " healthy • "
                            + attention + " attention • "
                            + broken + " broken"
            );
        }
    }

    private void failScan(long generation, String message) {
        if (generation != scanGeneration) {
            return;
        }

        overallTitle.setText("SCAN FAILED");
        overallSubtitle.setText(message);
        progressLabel.setText("SCAN PROGRESS");
        progress.setProgress(0);
        scanning.set(false);
        refreshButton.setDisable(false);
        refreshButton.setText("SCAN AGAIN");
        animateRefreshButton(false);

        org.example.ui.components.NotificationManager manager =
                org.example.ui.components.NotificationManager.getGlobal();
        if (manager != null) {
            manager.error("State scan failed", message);
        }
    }

    private HBox createScanningCard(Instance instance) {
        HBox card = new HBox(16);
        card.getStyleClass().add("state-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));

        StackPane icon = new StackPane(IconView.create(IconView.Type.REFRESH, 22));
        icon.getStyleClass().add("state-instance-icon");
        icon.getStyleClass().add("state-icon-attention");

        VBox text = new VBox(5);
        Label name = new Label(safe(instance.getName(), "Unnamed instance"));
        name.getStyleClass().add("state-instance-name");
        Label detail = new Label(
                safe(instance.getMinecraftVersion(), "Unknown version")
                        + "  •  " + safe(instance.getDisplayLoader(), "Unknown loader")
        );
        detail.getStyleClass().add("state-instance-detail");
        Label summary = new Label("Scanning...");
        summary.getStyleClass().add("state-instance-summary");
        text.getChildren().addAll(name, detail, summary);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label status = new Label("SCANNING");
        status.getStyleClass().add("state-status-attention");
        Label stats = new Label("Waiting for a State worker");
        stats.getStyleClass().add("state-stats");

        VBox right = new VBox(5, status, stats);
        right.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().addAll(icon, text, right);
        return card;
    }

    private void replaceInstanceCard(Instance instance, InstanceState state) {
        HBox replacement = createStateCard(instance, state);
        HBox old = liveInstanceCards.get(instance);
        if (old == null) {
            instanceList.getChildren().add(replacement);
            liveInstanceCards.put(instance, replacement);
            return;
        }

        int index = instanceList.getChildren().indexOf(old);
        if (index >= 0) {
            instanceList.getChildren().set(index, replacement);
        }
        liveInstanceCards.put(instance, replacement);

        if (LauncherSettings.isAnimationsEnabled()) {
            AnimationUtils.slideFadeVertical(replacement, 8);
        }
    }

    private void addSharedPlaceholder(String name) {
        HBox row = createSharedRow(name, null);
        liveSharedRows.put(name, row);
        sharedList.getChildren().add(row);
    }

    private void replaceSharedRow(SharedState state) {
        HBox replacement = createSharedRow(state.getName(), state);
        HBox old = liveSharedRows.get(state.getName());
        if (old == null) {
            sharedList.getChildren().add(replacement);
        } else {
            int index = sharedList.getChildren().indexOf(old);
            if (index >= 0) {
                sharedList.getChildren().set(index, replacement);
            }
        }
        liveSharedRows.put(state.getName(), replacement);
    }

    private HBox createSharedRow(String name, SharedState state) {
        HBox row = new HBox(14);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("state-shared-row");

        StackPane icon = new StackPane(IconView.create(
                "Libraries".equals(name) ? IconView.Type.PACKAGE : IconView.Type.FOLDER, 20));
        icon.getStyleClass().add("state-shared-icon");

        VBox text = new VBox(3);
        Label nameLabel = new Label(name);
        nameLabel.getStyleClass().add("state-shared-name");
        Label summary = new Label(
                state == null ? "Waiting for a State worker..." : state.getSummary()
        );
        summary.getStyleClass().add("state-shared-summary");
        text.getChildren().addAll(nameLabel, summary);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label count = new Label(
                state == null ? "WAITING" : state.getFiles() + " files"
        );
        count.getStyleClass().add("state-shared-count");

        Label status = new Label(
                state == null ? "SCANNING" : state.getLevel().name()
        );
        status.getStyleClass().add(
                state == null
                        ? "state-shared-attention"
                        : "state-shared-" + state.getLevel().name().toLowerCase()
        );

        row.getChildren().addAll(icon, text, count, status);
        return row;
    }

    private HBox createStateCard(Instance instance, InstanceState state) {
        HBox card = new HBox(16);
        card.getStyleClass().add("state-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));

        StackPane icon = new StackPane();
        icon.getStyleClass().add("state-instance-icon");
        if ("CUSTOM".equalsIgnoreCase(instance.getIcon())
                && instance.getDirectory().resolve("icon.png").toFile().isFile()) {
            ImageView customIcon = new ImageView(new Image(
                    instance.getDirectory().resolve("icon.png").toUri().toString(), 28, 28, true, true));
            customIcon.setFitWidth(28);
            customIcon.setFitHeight(28);
            customIcon.setPreserveRatio(true);
            icon.getChildren().add(customIcon);
        } else {
            icon.getChildren().add(InstanceCard.createIconGraphic(instance.getIcon(), 28));
        }
        icon.getStyleClass().add("state-icon-" + state.getLevel().name().toLowerCase());

        VBox text = new VBox(5);
        Label name = new Label(safe(instance.getName(), "Unnamed instance"));
        name.getStyleClass().add("state-instance-name");
        Label detail = new Label(safe(instance.getMinecraftVersion(), "Unknown version")
                + "  •  " + safe(instance.getDisplayLoader(), "Unknown loader"));
        detail.getStyleClass().add("state-instance-detail");
        Label summary = new Label(safe(state.getSummary(), "No diagnostic summary."));
        summary.getStyleClass().add("state-instance-summary");
        Label fingerprint = new Label("STATE " + safe(state.getFingerprint(), ""));
        fingerprint.getStyleClass().add("state-fingerprint");

        text.getChildren().addAll(name, detail, summary, fingerprint);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label status = new Label(state.getTitle());
        status.getStyleClass().add("state-status-" + state.getLevel().name().toLowerCase());
        Label stats = new Label(state.getChecksPassed() + "/" + state.getChecksTotal()
                + " checks  •  " + state.getMods() + " mods  •  " + state.getConfigs() + " configs");
        stats.getStyleClass().add("state-stats");

        VBox right = new VBox(5, status, stats);
        right.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().addAll(icon, text, right);
        return card;
    }

    private void animateRefreshButton(boolean scanning) {
        if (!LauncherSettings.isAnimationsEnabled()) {
            refreshButton.setRotate(0);
            return;
        }

        if (scanning) {
            RotateTransition rotate = new RotateTransition(Duration.millis(850), refreshButton);
            rotate.setByAngle(360);
            rotate.setCycleCount(RotateTransition.INDEFINITE);
            rotate.play();
            refreshButton.getProperties().put("vanta.state.scan-rotation", rotate);
        } else {
            Object existing = refreshButton.getProperties().remove("vanta.state.scan-rotation");
            if (existing instanceof RotateTransition rotate) {
                rotate.stop();
            }
            refreshButton.setRotate(0);
        }
    }
    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
