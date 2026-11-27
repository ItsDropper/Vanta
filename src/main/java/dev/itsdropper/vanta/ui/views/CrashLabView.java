package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.launcher.service.LaunchHistoryRepository;
import dev.itsdropper.vanta.launcher.repair.RepairDiagnosis;
import dev.itsdropper.vanta.launcher.repair.RepairDiagnosticEngine;
import dev.itsdropper.vanta.ui.LauncherViewLifecycle;
import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.ResponsiveLayout;
import dev.itsdropper.vanta.ui.VantaUI;
import dev.itsdropper.vanta.ui.async.UiExecutor;
import dev.itsdropper.vanta.ui.components.PageHeader;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Local launch timeline and evidence comparison workspace. */
public final class CrashLabView extends VBox implements LauncherViewLifecycle {
    private final LaunchHistoryRepository repository;
    private final Runnable onReviewCases;
    private final ListView<LaunchHistoryRepository.LaunchRecord> timeline = new ListView<>();
    private final TextField search = new TextField();
    private final TextArea details = evidenceArea();
    private javafx.scene.layout.Region emptyHistory;
    private HBox historyWorkspace;
    private final TextArea baselineEvidence = evidenceArea();
    private final TextArea failedEvidence = evidenceArea();
    private final TextArea differences = evidenceArea();
    private final Label status = new Label();
    private List<LaunchHistoryRepository.LaunchRecord> records = List.of();
    private String selectedId;
    private long refreshGeneration;

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Localization.getActiveLocale())
            .withZone(ZoneId.systemDefault());

    public CrashLabView(Runnable onReviewCases) {
        this(new LaunchHistoryRepository(), onReviewCases);
    }

    CrashLabView(LaunchHistoryRepository repository, Runnable onReviewCases) {
        this.repository = java.util.Objects.requireNonNull(repository);
        this.onReviewCases = onReviewCases;
        getStyleClass().addAll("page", "vanta-consistent-page", "crash-lab-page");
        ResponsiveLayout.bindPagePadding(this);
        setSpacing(VantaUI.spacing(4));

        Button refresh = new Button(Localization.text("common.refresh"));
        refresh.getStyleClass().add("secondary-button");
        refresh.setOnAction(event -> refresh());
        Button reviewCases = new Button(Localization.text("crashLab.reviewCases"));
        reviewCases.getStyleClass().add("primary-button");
        reviewCases.setDisable(onReviewCases == null);
        reviewCases.setOnAction(event -> {
            if (onReviewCases != null) onReviewCases.run();
        });
        PageHeader header = new PageHeader(
                Localization.text("crashLab.title"),
                Localization.text("crashLab.description"),
                "page-title",
                "page-subtitle",
                refresh,
                reviewCases);

        search.setPromptText(Localization.text("crashLab.search"));
        search.setAccessibleText(Localization.text("crashLab.searchAccessible"));
        search.getStyleClass().add("instance-search-field");
        search.textProperty().addListener((observable, oldValue, newValue) -> applyFilter());

        timeline.setPlaceholder(new Label(Localization.text("crashLab.empty")));
        timeline.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(LaunchHistoryRepository.LaunchRecord item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String version = item.minecraftVersion().isBlank() ? "Minecraft" : item.minecraftVersion();
                String loader = item.loader().isBlank() ? "" : " · " + item.loader()
                        + (item.loaderVersion().isBlank() ? "" : " " + item.loaderVersion());
                String name = item.instanceName().isBlank() ? item.instanceId() : item.instanceName();
                String elapsed = item.events().size() == 1 ? "" : "  ·  " + formatDuration(item.durationMillis());
                setText(displayOutcome(item.outcome()) + "  ·  " + name + "\n"
                        + version + loader + "\n"
                        + DATE_FORMAT.format(Instant.ofEpochMilli(item.startedAtEpochMillis())) + elapsed);
            }
        });
        timeline.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, selected) -> {
            if (selected != null) {
                selectedId = selected.id();
                showRecord(selected);
            }
        });

        VBox timelineCard = new VBox(10,
                sectionTitle(Localization.text("crashLab.timeline")), search, timeline);
        timelineCard.getStyleClass().addAll("settings-card", "crash-lab-timeline");
        timelineCard.setMinWidth(280);
        timelineCard.setPrefWidth(340);
        timelineCard.setMaxWidth(380);
        VBox.setVgrow(timeline, Priority.ALWAYS);

        details.setPrefRowCount(7);
        VBox detailCard = new VBox(10,
                sectionTitle(Localization.text("crashLab.launchDetails")), details);
        detailCard.getStyleClass().add("settings-card");
        VBox.setVgrow(details, Priority.ALWAYS);

        baselineEvidence.setPrefRowCount(9);
        failedEvidence.setPrefRowCount(9);
        differences.setPrefRowCount(8);
        VBox baselineCard = new VBox(8,
                sectionTitle(Localization.text("crashLab.lastSuccessful")), baselineEvidence);
        VBox failedCard = new VBox(8,
                sectionTitle(Localization.text("crashLab.failedLaunch")), failedEvidence);
        baselineCard.getStyleClass().add("settings-card");
        failedCard.getStyleClass().add("settings-card");
        baselineCard.setMinWidth(0);
        failedCard.setMinWidth(0);
        HBox evidencePair = new HBox(12, baselineCard, failedCard);
        HBox.setHgrow(baselineCard, Priority.ALWAYS);
        HBox.setHgrow(failedCard, Priority.ALWAYS);
        VBox diffCard = new VBox(8,
                sectionTitle(Localization.text("crashLab.newEvidence")), differences);
        diffCard.getStyleClass().add("settings-card");

        VBox right = new VBox(12, detailCard, evidencePair, diffCard);
        right.setMinWidth(0);
        HBox.setHgrow(right, Priority.ALWAYS);
        VBox.setVgrow(detailCard, Priority.ALWAYS);
        HBox workspace = new HBox(14, timelineCard, right);
        workspace.setAlignment(Pos.TOP_LEFT);
        VBox.setVgrow(workspace, Priority.ALWAYS);

        status.setWrapText(true);
        status.getStyleClass().add("muted");
        // With no launch history there is nothing to inspect, so one empty state replaces the panes.
        emptyHistory = new dev.itsdropper.vanta.ui.kit.EmptyState(
                dev.itsdropper.vanta.ui.components.IconView.Type.ACTIVITY,
                "No launches recorded yet",
                "Launch an instance and Vanta keeps its timeline here, with crash evidence if it fails.",
                null, null);
        historyWorkspace = workspace;
        showHistory(false);
        VBox stage = new VBox(emptyHistory, workspace);
        stage.setAlignment(Pos.TOP_CENTER);
        VBox.setVgrow(stage, Priority.ALWAYS);
        VBox.setVgrow(emptyHistory, Priority.ALWAYS);
        getChildren().addAll(header, stage, status);
    }

    private static TextArea evidenceArea() {
        TextArea area = new TextArea();
        area.setEditable(false);
        area.setWrapText(false);
        area.setPrefRowCount(8);
        area.getStyleClass().addAll("diagnostic-log", "crash-lab-evidence");
        return area;
    }

    private Label sectionTitle(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("settings-section-title");
        VantaUI.styleText(label, VantaUI.TextRole.SECTION_TITLE);
        return label;
    }

    private void refresh() {
        long generation = ++refreshGeneration;
        String previousSelection = selectedId;
        status.setText(Localization.text("crashLab.loading"));
        UiExecutor.shared().execute(() -> {
            try {
                List<LaunchHistoryRepository.LaunchRecord> loaded = repository.list();
                UiExecutor.shared().dispatchToUi(() -> {
                    if (generation != refreshGeneration) return;
                    records = loaded;
                    showHistory(!loaded.isEmpty());
                    applyFilter();
                    status.setText(Localization.text("crashLab.localOnly"));
                    if (previousSelection != null) {
                        records.stream().filter(record -> record.id().equals(previousSelection))
                                .findFirst().ifPresent(record -> timeline.getSelectionModel().select(record));
                    }
                    if (timeline.getSelectionModel().getSelectedItem() == null && !timeline.getItems().isEmpty()) {
                        timeline.getSelectionModel().selectFirst();
                    }
                    if (timeline.getItems().isEmpty()) clearSelection();
                });
            } catch (IOException | RuntimeException failure) {
                UiExecutor.shared().dispatchToUi(() -> {
                    if (generation != refreshGeneration) return;
                    status.setText(Localization.text("crashLab.loadFailed").formatted(
                            failure.getMessage() == null
                                    ? failure.getClass().getSimpleName() : failure.getMessage()));
                    records = List.of();
                    showHistory(false);
                    timeline.setItems(FXCollections.observableArrayList());
                    clearSelection();
                });
            }
        });
    }

    private void showHistory(boolean hasRecords) {
        emptyHistory.setVisible(!hasRecords);
        emptyHistory.setManaged(!hasRecords);
        historyWorkspace.setVisible(hasRecords);
        historyWorkspace.setManaged(hasRecords);
    }

    private void applyFilter() {
        String query = search.getText() == null ? "" : search.getText().strip().toLowerCase(Locale.ROOT);
        List<LaunchHistoryRepository.LaunchRecord> filtered = records.stream()
                .filter(record -> query.isBlank()
                        || record.instanceName().toLowerCase(Locale.ROOT).contains(query)
                        || record.instanceId().toLowerCase(Locale.ROOT).contains(query)
                        || record.minecraftVersion().toLowerCase(Locale.ROOT).contains(query)
                        || record.loader().toLowerCase(Locale.ROOT).contains(query)
                        || record.outcome().name().toLowerCase(Locale.ROOT).contains(query)
                        || record.failureTitle().toLowerCase(Locale.ROOT).contains(query)
                        || record.events().stream().anyMatch(event -> event.message().toLowerCase(Locale.ROOT).contains(query)))
                .toList();
        String keep = selectedId;
        timeline.setItems(FXCollections.observableArrayList(filtered));
        if (filtered.isEmpty()) {
            clearSelection();
            return;
        }
        if (keep != null) {
            filtered.stream().filter(record -> record.id().equals(keep))
                    .findFirst().ifPresent(record -> timeline.getSelectionModel().select(record));
        }
        if (timeline.getSelectionModel().getSelectedItem() == null && !filtered.isEmpty()) {
            timeline.getSelectionModel().selectFirst();
        }
    }

    private void showRecord(LaunchHistoryRepository.LaunchRecord record) {
        StringBuilder summary = new StringBuilder();
        summary.append(Localization.text("crashLab.instance")).append(": ")
                .append(record.instanceName()).append(" (").append(record.instanceId()).append(")\n");
        summary.append(Localization.text("crashLab.minecraft")).append(": ")
                .append(record.minecraftVersion()).append("\n");
        summary.append(Localization.text("crashLab.loader")).append(": ")
                .append(record.loader()).append(" ").append(record.loaderVersion()).append("\n");
        summary.append(Localization.text("crashLab.started")).append(": ")
                .append(DATE_FORMAT.format(Instant.ofEpochMilli(record.startedAtEpochMillis()))).append("\n");
        summary.append(Localization.text("crashLab.duration")).append(": ")
                .append(formatDuration(record.durationMillis())).append("\n");
        summary.append(Localization.text("crashLab.outcome")).append(": ").append(displayOutcome(record.outcome()));
        if (record.exitCode() != null) summary.append(" (exit ").append(record.exitCode()).append(")");
        if (!record.failureTitle().isBlank()) {
            summary.append("\n").append(Localization.text("crashLab.failure")).append(": ")
                    .append(record.failureTitle());
        }
        if (record.failureCategory() != null) {
            summary.append("\n").append(Localization.text("crashLab.category")).append(": ")
                    .append(record.failureCategory());
        }
        if (record.outcome() == LaunchHistoryRepository.Outcome.FAILED
                && record.exitCode() != null && record.exitCode() != 0
                && !record.sanitizedLog().isBlank()) {
            List<RepairDiagnosis> diagnoses = RepairDiagnosticEngine.diagnose(record.sanitizedLog());
            if (!diagnoses.isEmpty()) {
                summary.append("\n\n").append(Localization.text("crashLab.diagnosis"))
                        .append(" — ").append(Localization.text("crashLab.advisory")).append("\n");
                for (RepairDiagnosis diagnosis : diagnoses.stream().limit(3).toList()) {
                    summary.append("\n").append(diagnosis.title()).append(" · ")
                            .append(diagnosis.category()).append(" · ").append(diagnosis.confidence()).append("\n")
                            .append(diagnosis.description());
                    if (!diagnosis.evidence().isBlank()) {
                        summary.append("\n").append(Localization.text("crashLab.supportingEvidence"))
                                .append(": ").append(diagnosis.evidence());
                    }
                    if (!diagnosis.suggestedActions().isEmpty()) {
                        summary.append("\n").append(Localization.text("crashLab.suggestedChecks")).append(": ");
                        summary.append(String.join("; ", diagnosis.suggestedActions().stream().limit(3).toList()));
                    }
                    summary.append("\n");
                }
            } else {
                summary.append("\n\n").append(Localization.text("crashLab.noDiagnosis"));
            }
        }
        summary.append("\n\n").append(Localization.text("crashLab.events")).append("\n");
        for (LaunchHistoryRepository.Event event : record.events()) {
            summary.append(DATE_FORMAT.format(Instant.ofEpochMilli(event.timestampEpochMillis())))
                    .append("  ·  ").append(event.outcome()).append("  ·  ")
                    .append(event.message()).append("\n");
        }
        details.setText(summary.toString());

        if (record.outcome() != LaunchHistoryRepository.Outcome.FAILED) {
            boolean activity = record.outcome() != LaunchHistoryRepository.Outcome.SUCCEEDED
                    && record.outcome() != LaunchHistoryRepository.Outcome.STOPPED;
            baselineEvidence.setText(activity
                    ? record.events().stream().map(LaunchHistoryRepository.Event::message)
                            .findFirst().orElse(Localization.text("crashLab.noEvidence"))
                    : Localization.text("crashLab.selectFailure"));
            failedEvidence.setText(record.sanitizedLog().isBlank()
                    ? Localization.text("crashLab.noEvidence") : record.sanitizedLog());
            differences.setText(activity
                    ? Localization.text("crashLab.activityNoCompare")
                    : Localization.text("crashLab.comparisonFailureOnly"));
            return;
        }

        LaunchHistoryRepository.LaunchRecord baseline = records.stream()
                .filter(candidate -> candidate.instanceId().equals(record.instanceId()))
                .filter(candidate -> candidate.outcome() == LaunchHistoryRepository.Outcome.SUCCEEDED)
                .filter(candidate -> candidate.startedAtEpochMillis() < record.startedAtEpochMillis())
                .max(Comparator.comparingLong(LaunchHistoryRepository.LaunchRecord::startedAtEpochMillis))
                .orElse(null);

        if (baseline == null) {
            baselineEvidence.setText(Localization.text("crashLab.noBaseline"));
            differences.setText(Localization.text("crashLab.noBaselineDetails"));
        } else {
            baselineEvidence.setText(formatBaseline(baseline));
            differences.setText(compareEvidence(baseline, record));
        }
        failedEvidence.setText(record.sanitizedLog().isBlank()
                ? Localization.text("crashLab.noEvidence") : record.sanitizedLog());
    }

    private String formatBaseline(LaunchHistoryRepository.LaunchRecord baseline) {
        return Localization.text("crashLab.successfulRun").formatted(
                baseline.instanceName(),
                baseline.minecraftVersion(),
                baseline.loader() + (baseline.loaderVersion().isBlank() ? "" : " " + baseline.loaderVersion()),
                DATE_FORMAT.format(Instant.ofEpochMilli(baseline.startedAtEpochMillis())),
                formatDuration(baseline.durationMillis()))
                + "\n\n" + (baseline.sanitizedLog().isBlank()
                ? Localization.text("crashLab.noEvidence") : baseline.sanitizedLog());
    }

    private String compareEvidence(
            LaunchHistoryRepository.LaunchRecord baseline,
            LaunchHistoryRepository.LaunchRecord failed
    ) {
        Set<String> known = baseline.sanitizedLog().lines()
                .map(CrashLabView::normalizeComparisonLine)
                .collect(java.util.stream.Collectors.toSet());
        List<String> newLines = failed.sanitizedLog().lines()
                .filter(line -> !line.isBlank())
                .filter(line -> !known.contains(normalizeComparisonLine(line)))
                .distinct()
                .limit(100)
                .toList();
        return newLines.isEmpty() ? Localization.text("crashLab.noNewEvidence")
                : String.join("\n", newLines);
    }

    private static String normalizeComparisonLine(String line) {
        return line.toLowerCase(Locale.ROOT)
                .replaceAll("\\b\\d{4}-\\d{2}-\\d{2}[ t]\\d{2}:\\d{2}:\\d{2}(?:[.,]\\d+)?\\b", "[time]")
                .replaceAll("(?i)thread[- ]#?\\d+", "thread")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private void clearSelection() {
        selectedId = null;
        details.clear();
        baselineEvidence.clear();
        failedEvidence.clear();
        differences.clear();
    }

    private static String displayOutcome(LaunchHistoryRepository.Outcome outcome) {
        String value = outcome.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        String[] words = value.split(" ");
        StringBuilder result = new StringBuilder();
        for (String word : words) {
            if (result.length() > 0) result.append(' ');
            result.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
        }
        return result.toString();
    }

    private static String formatDuration(long millis) {
        long seconds = Math.max(0, millis / 1000);
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long remaining = seconds % 60;
        return hours > 0 ? "%dh %02dm %02ds".formatted(hours, minutes, remaining)
                : minutes > 0 ? "%dm %02ds".formatted(minutes, remaining)
                : "%ds".formatted(remaining);
    }

    @Override
    public void onShown() {
        refresh();
    }
}
