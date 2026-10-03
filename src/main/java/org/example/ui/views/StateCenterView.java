package org.example.ui.views;

import javafx.application.Platform;
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
import org.example.launcher.state.InstanceStateEngine;
import org.example.ui.components.IconView;
import org.example.ui.components.InstanceCard;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public final class StateCenterView extends VBox {
    private final VBox instanceList = new VBox(10);
    private final ScrollPane instanceScroll = new ScrollPane(instanceList);
    private final Label overallTitle = new Label("READY");
    private final Label overallSubtitle = new Label("Vanta will inspect your environments when you open this page.");
    private final ProgressBar progress = new ProgressBar(0);
    private final Button refreshButton = new Button("SCAN NOW", IconView.create(IconView.Type.REFRESH, 15));
    private final AtomicBoolean scanning = new AtomicBoolean(false);
    private volatile long scanGeneration;

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

        getChildren().addAll(heading, hero, section, listCard);
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
        overallTitle.setText("SCANNING...");
        overallSubtitle.setText("Checking instance structure and installed content.");
        progress.setProgress(-1);
        instanceList.getChildren().clear();


        Thread thread = new Thread(() -> {
            List<Instance> instances;
            List<InstanceState> states = new ArrayList<>();

            try {
                instances = InstanceManager.discoverInstances();
                for (Instance instance : instances) {
                    try {
                        states.add(InstanceStateEngine.inspect(instance));
                    } catch (Throwable ex) {
                        ex.printStackTrace();
                        states.add(new InstanceState(InstanceState.Level.BROKEN, "SCAN FAILED",
                                "Vanta could not inspect this environment.", 0, 1, 0, 0, ""));
                    }
                }
            } catch (Throwable ex) {
                ex.printStackTrace();
                List<Instance> failedInstances = List.of();
                Platform.runLater(() -> {
                    finishScan(generation, false, failedInstances, states);
                });
                return;
            }

            List<Instance> finalInstances = instances;
            Platform.runLater(() -> {
                finishScan(generation, true, finalInstances, states);
            });
        }, "Vanta-State-Engine");

        thread.setDaemon(true);
        thread.setUncaughtExceptionHandler((t, ex) -> {
            ex.printStackTrace();
        });
        thread.start();
    }

    private void finishScan(long generation, boolean success, List<Instance> instances, List<InstanceState> states) {
        if (generation != scanGeneration) {
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
            render(instances, states);
        } catch (Throwable ex) {
            ex.printStackTrace();
            overallTitle.setText("SCAN FAILED");
            overallSubtitle.setText("The State view could not render the scan results.");
            progress.setProgress(0);
            instanceList.getChildren().clear();
        } finally {
            scanning.set(false);
            refreshButton.setDisable(false);
            refreshButton.setText("SCAN AGAIN");
        }
    }

    private void render(List<Instance> instances, List<InstanceState> states) {
        instanceList.getChildren().clear();


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
        progress.setProgress((double) healthy / states.size());

        for (int i = 0; i < instances.size() && i < states.size(); i++) {
            addStateCard(instances.get(i), states.get(i));
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
        instanceList.getChildren().add(card);
    }

    private static String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
