package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.launcher.download.DownloadCenterService;
import dev.itsdropper.vanta.launcher.download.DownloadCenterService.Task;
import dev.itsdropper.vanta.launcher.download.DownloadCenterService.Status;
import dev.itsdropper.vanta.ui.components.IconView;
import dev.itsdropper.vanta.ui.components.PageHeader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Locale;

/** Live, local-only view of downloads initiated by Vanta's existing installers. */
public final class DownloadCenterView extends VBox implements dev.itsdropper.vanta.ui.LauncherViewLifecycle {
    private final DownloadCenterService service = DownloadCenterService.getInstance();
    private final VBox taskList = new VBox(10);
    private final Label activeCount = new Label("0 active");
    private final Label completedCount = new Label("0 completed");
    private final Label failedCount = new Label("0 failed");
    private final Label summary = new Label("Downloads started by Vanta appear here automatically.");
    private final java.util.function.Consumer<List<Task>> listener = this::renderTasks;
    private boolean listening;

    public DownloadCenterView() {
        getStyleClass().addAll("page", "download-center-page");
        setSpacing(18);
        setPadding(new Insets(8, 8, 8, 8));
        setMinWidth(0);

        Label eyebrow = new Label("TRANSFER ACTIVITY");
        eyebrow.getStyleClass().add("download-eyebrow");
        PageHeader pageHeader = new PageHeader(
                "Download Center",
                "One place to track installs, libraries, mods, assets and updates.",
                "page-title",
                "page-subtitle"
        );
        VBox heading = new VBox(8, eyebrow, pageHeader);

        HBox stats = new HBox(10,
                statCard("IN PROGRESS", activeCount, "download-stat-active"),
                statCard("COMPLETED", completedCount, "download-stat-complete"),
                statCard("FAILED", failedCount, "download-stat-failed"));
        stats.getStyleClass().add("download-stats");
        for (Node child : stats.getChildren()) HBox.setHgrow(child, Priority.ALWAYS);

        HBox listHeading = new HBox(10);
        listHeading.setAlignment(Pos.CENTER_LEFT);
        Label queueTitle = new Label("Recent transfers");
        queueTitle.getStyleClass().add("download-section-title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Button clear = new Button("Clear history", IconView.create(IconView.Type.TRASH, 14));
        clear.getStyleClass().add("secondary-button");
        clear.setOnAction(event -> service.clearFinished());
        listHeading.getChildren().addAll(queueTitle, spacer, clear);

        taskList.getStyleClass().add("download-task-list");
        ScrollPane scroll = new ScrollPane(taskList);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("download-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        summary.getStyleClass().add("download-summary");
        getChildren().addAll(heading, stats, summary, listHeading, scroll);
    }

    @Override
    public void onShown() {
        if (!listening) {
            listening = true;
            service.addListener(listener);
        }
    }

    @Override
    public void onHidden() {
        if (listening) {
            service.removeListener(listener);
            listening = false;
        }
    }

    private VBox statCard(String caption, Label value, String style) {
        Label label = new Label(caption);
        label.getStyleClass().add("download-stat-label");
        value.getStyleClass().add("download-stat-value");
        VBox card = new VBox(7, label, value);
        card.getStyleClass().addAll("download-stat-card", style);
        card.setPadding(new Insets(15, 16, 15, 16));
        return card;
    }

    private void renderTasks(List<Task> tasks) {
        long active = tasks.stream().filter(Task::active).count();
        long complete = tasks.stream().filter(t -> t.status() == Status.COMPLETED).count();
        long failed = tasks.stream().filter(t -> t.status() == Status.FAILED).count();
        activeCount.setText(Long.toString(active));
        completedCount.setText(Long.toString(complete));
        failedCount.setText(Long.toString(failed));
        summary.setText(active == 0
                ? "You're all caught up. New transfers will appear here automatically."
                : active + " transfer" + (active == 1 ? "" : "s") + " currently running.");

        taskList.getChildren().clear();
        if (tasks.isEmpty()) {
            VBox empty = new VBox(10);
            empty.setAlignment(Pos.CENTER);
            empty.setPadding(new Insets(38, 24, 38, 24));
            Label icon = new Label("↓");
            icon.getStyleClass().add("download-empty-icon");
            Label title = new Label("Nothing downloading");
            title.getStyleClass().add("download-empty-title");
            Label body = new Label("When Vanta installs Minecraft files, mods, assets or updates,\nthe transfer will show up here with its current status.");
            body.setWrapText(true);
            body.setAlignment(Pos.CENTER);
            body.getStyleClass().add("download-empty-body");
            empty.getStyleClass().add("download-empty-card");
            empty.getChildren().addAll(icon, title, body);
            taskList.getChildren().add(empty);
            return;
        }
        for (Task task : tasks) taskList.getChildren().add(taskCard(task));
    }

    private Node taskCard(Task task) {
        Label file = new Label(task.name());
        file.getStyleClass().add("download-task-name");
        file.setMaxWidth(Double.MAX_VALUE);
        file.setWrapText(true);
        Label source = new Label(displaySource(task.source()) + (task.target() == null ? "" : "  ·  " + task.target().getFileName()));
        source.getStyleClass().add("download-task-source");
        source.setWrapText(true);

        Label state = new Label(switch (task.status()) {
            case DOWNLOADING -> "DOWNLOADING";
            case VERIFYING -> "VERIFYING";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
        });
        state.getStyleClass().addAll("download-status", "download-status-" + task.status().name().toLowerCase(Locale.ROOT));
        HBox top = new HBox(12, new VBox(5, file, source), state);
        top.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(top.getChildren().get(0), Priority.ALWAYS);

        ProgressBar progress = new ProgressBar();
        progress.setMaxWidth(Double.MAX_VALUE);
        if (task.status() == Status.VERIFYING) {
            progress.setProgress(-1);
        } else if (task.status() == Status.COMPLETED) {
            progress.setProgress(1);
        } else if (task.totalBytes() > 0) {
            progress.setProgress(task.progress());
        } else if (task.active()) {
            progress.setProgress(-1);
        } else {
            progress.setProgress(0);
        }
        progress.getStyleClass().add("download-progress");

        Label detail = new Label(task.status() == Status.FAILED
                ? task.error()
                : task.status() == Status.COMPLETED
                    ? "Finished · " + formatBytes(task.receivedBytes())
                    : task.status() == Status.VERIFYING
                        ? "Finalizing download…"
                        : formatBytes(task.receivedBytes()) + (task.totalBytes() > 0
                            ? " of " + formatBytes(task.totalBytes()) + " · " + Math.round(task.progress() * 100) + "%"
                            : " received"));
        detail.getStyleClass().add(task.status() == Status.FAILED ? "download-task-error" : "download-task-detail");
        detail.setWrapText(true);

        VBox card = new VBox(10, top, progress, detail);
        card.getStyleClass().addAll("download-task-card", "download-task-" + task.status().name().toLowerCase(Locale.ROOT));
        card.setPadding(new Insets(15, 16, 15, 16));
        if (task.status() == Status.FAILED) {
            boolean retryRequested = service.isRetryRequested(task.id());
            Button retry = new Button(retryRequested ? "Retry queued" : "Retry",
                    IconView.create(IconView.Type.REFRESH, 14));
            retry.getStyleClass().add("secondary-button");
            retry.setDisable(retryRequested);
            retry.setOnAction(event -> service.retry(task));
            HBox actions = new HBox(retry);
            actions.setAlignment(Pos.CENTER_RIGHT);
            card.getChildren().add(actions);
        }
        return card;
    }

    private static String displaySource(String raw) {
        try {
            String host = java.net.URI.create(raw).getHost();
            return host == null ? "Remote source" : host;
        } catch (RuntimeException ignored) {
            return "Remote source";
        }
    }

    private static String formatBytes(long bytes) {
        if (bytes < 0) return "Unknown size";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024L * 1024) return String.format(Locale.ROOT, "%.1f KB", bytes / 1024.0);
        if (bytes < 1024L * 1024 * 1024) return String.format(Locale.ROOT, "%.1f MB", bytes / (1024.0 * 1024));
        return String.format(Locale.ROOT, "%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
