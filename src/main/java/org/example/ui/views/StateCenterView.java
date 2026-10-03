package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.example.launcher.instance.InstanceManager;
import org.example.launcher.model.Instance;
import org.example.launcher.state.InstanceState;
import org.example.launcher.state.InstanceStateEngine;
import org.example.ui.components.IconView;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StateCenterView extends VBox {
    private static void debug(String message) { System.out.println("[Vanta State DEBUG] " + message); }
    private final VBox instanceList = new VBox(10);
    private final ScrollPane instanceScroll = new ScrollPane(instanceList);
    private final Label overallTitle = new Label("READY");
    private final Label overallSubtitle = new Label("Vanta will inspect your environments when you open this page.");
    private final ProgressBar progress = new ProgressBar(0);
    private final Button refreshButton = new Button("SCAN NOW", IconView.create(IconView.Type.REFRESH, 15));
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private volatile long scanGeneration;

    public StateCenterView() {
        debug("CONSTRUCTOR START");
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

        getChildren().addAll(heading, hero, section, listCard);
        debug("CONSTRUCTOR COMPLETE");
    }

    public void onShown() {
        debug("ON_SHOWN");
        refresh();
    }

    public void refresh() {
        debug("REFRESH ENTER");
        if (!scanning.compareAndSet(false, true)) {
            debug("REFRESH SKIPPED: already scanning");
            return;
        }

        long generation = ++scanGeneration;
        refreshButton.setDisable(true);
        refreshButton.setText("SCANNING...");
        overallTitle.setText("SCANNING...");
        overallSubtitle.setText("Checking instance structure and installed content.");
        progress.setProgress(-1);
        instanceList.getChildren().clear();

        debug("SCAN THREAD STARTING generation=" + generation);

        Thread thread = new Thread(() -> {
            List<Instance> instances;
            List<InstanceState> states = new ArrayList<>();

            try {
                debug("DISCOVERY START");
                instances = InstanceManager.discoverInstances();
                debug("DISCOVERY COMPLETE instances=" + instances.size());
                for (Instance instance : instances) {
                    debug("INSPECT START name=" + safe(instance.getName(), "<null>") + " id=" + safe(instance.getId(), "<null>"));
                    try {
                        states.add(InstanceStateEngine.inspect(instance));
                        debug("INSPECT COMPLETE name=" + safe(instance.getName(), "<null>") + " state=" + states.get(states.size()-1).getLevel());
                    } catch (Throwable ex) {
                        debug("INSPECT FAILED: " + ex);
                        ex.printStackTrace();
                        states.add(new InstanceState(InstanceState.Level.BROKEN, "SCAN FAILED",
                                "Vanta could not inspect this environment.", 0, 1, 0, 0, ""));
                    }
                }
            } catch (Throwable ex) {
                debug("SCAN THREAD FAILED: " + ex);
                ex.printStackTrace();
                List<Instance> failedInstances = List.of();
                Platform.runLater(() -> {
                    debug("FX CALLBACK: FAILED SCAN");
                    finishScan(generation, false, failedInstances, states);
                });
                return;
            }

            List<Instance> finalInstances = instances;
            debug("SCAN COMPLETE instances=" + finalInstances.size() + " states=" + states.size());
            Platform.runLater(() -> {
                debug("FX CALLBACK: SUCCESS SCAN");
                finishScan(generation, true, finalInstances, states);
            });
        }, "Vanta-State-Engine");

        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, ex) -> {
            debug("UNCAUGHT THREAD EXCEPTION: " + ex);
            ex.printStackTrace();
        });
        thread.start();
        debug("SCAN THREAD STARTED");
    }

    private void finishScan(long generation, boolean success, List<Instance> instances, List<InstanceState> states) {
        debug("FINISH_SCAN ENTER success=" + success + " generation=" + generation + " current=" + scanGeneration + " instances=" + instances.size() + " states=" + states.size());
        if (generation != scanGeneration) {
            debug("FINISH_SCAN SKIPPED: stale generation");
            return;
        }

        try {
            if (!success) {
                overallTitle.setText("SCAN FAILED");
                overallSubtitle.setText("Vanta could not discover the installed environments.");
                progress.setProgress(0);
                instanceList.getChildren().clear();
                return;
            }
            debug("RENDER START");
            render(instances, states);
            debug("RENDER COMPLETE");
        } catch (Throwable ex) {
            debug("FINISH_SCAN/RENDER FAILED: " + ex);
            ex.printStackTrace();
            overallTitle.setText("SCAN FAILED");
            overallSubtitle.setText("The State view could not render the scan results.");
            progress.setProgress(0);
            instanceList.getChildren().clear();
        } finally {
            debug("FINISH_SCAN FINALLY");
            scanning.set(false);
            refreshButton.setDisable(false);
            refreshButton.setText("SCAN AGAIN");
        }
    }

    private void render(List<Instance> instances, List<InstanceState> states) {
        debug("RENDER: clearing list");
        instanceList.getChildren().clear();

        debug("RENDER: states empty=" + states.isEmpty());

        if (states.isEmpty()) {
            overallTitle.setText("NO ENVIRONMENTS");
            overallSubtitle.setText("Create an instance and Vanta will start tracking its state.");
            progress.setProgress(0);
            addEmptyState();
            return;
        }

        int healthy = 0, attention = 0, broken = 0;
        for (InstanceState state : states) {
            if (state.isHealthy()) healthy++;
            else if (state.getLevel() == InstanceState.Level.ATTENTION) attention++;
            else broken++;
        }

        int affected = attention + broken;
        overallTitle.setText(affected == 0 ? "ALL SYSTEMS HEALTHY"
                : affected + " ENVIRONMENT" + (affected == 1 ? "" : "S") + " NEED ATTENTION");
        overallSubtitle.setText(healthy + " healthy  •  " + attention + " attention  •  " + broken + " broken");
        debug("RENDER: healthy=" + healthy + " attention=" + attention + " broken=" + broken);
        progress.setProgress((double) healthy / states.size());

        for (int i = 0; i < instances.size() && i < states.size(); i++) {
            debug("CARD START index=" + i + " name=" + safe(instances.get(i).getName(), "<null>"));
            addStateCard(instances.get(i), states.get(i));
            debug("CARD COMPLETE index=" + i);
        }
    }

    private void addEmptyState() {
        VBox empty = new VBox(8);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(42));

        StackPane icon = new StackPane(IconView.create(IconView.Type.SHIELD, 30));
        icon.getStyleClass().add("state-empty-icon");

        Label title = new Label("Nothing to inspect yet");
        title.getStyleClass().add("state-empty-title");
        Label text = new Label("Your installed Vanta instances will appear here.");
        text.getStyleClass().add("state-empty-text");

        empty.getChildren().addAll(icon, title, text);
        instanceList.getChildren().add(empty);
    }

    private void addStateCard(Instance instance, InstanceState state) {
        debug("CARD: HBox");
        HBox card = new HBox(16);
        card.getStyleClass().add("state-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(16));

        StackPane icon = new StackPane(IconView.create(
                state.isHealthy() ? IconView.Type.SHIELD : IconView.Type.PACKAGE, 21));
        debug("CARD: icon COMPLETE");
        icon.getStyleClass().add("state-icon-" + state.getLevel().name().toLowerCase());

        debug("CARD: text VBox");
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

        debug("CARD: right VBox");
        VBox right = new VBox(5, status, stats);
        right.setAlignment(Pos.CENTER_RIGHT);
        card.getChildren().addAll(icon, text, right);
        debug("CARD: adding to instanceList");
        instanceList.getChildren().add(card);
        debug("CARD: added to instanceList");
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
