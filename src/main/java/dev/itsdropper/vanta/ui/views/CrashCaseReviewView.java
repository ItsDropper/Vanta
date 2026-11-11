package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.launcher.repair.CrashCaseRepository;
import dev.itsdropper.vanta.launcher.repair.FailureCategory;
import dev.itsdropper.vanta.ui.Localization;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Developer-facing local review queue for sanitized crash reports.
 * This screen has no network integration and never uploads crash data.
 */
public final class CrashCaseReviewView extends VBox {
    private final CrashCaseRepository repository;
    private final Runnable onBack;
    private final ListView<CrashCaseRepository.CrashCase> cases = new ListView<>();
    private final TextArea details = new TextArea();
    private final TextArea verifiedFix = new TextArea();
    private final ComboBox<FailureCategory> category = new ComboBox<>();
    private final Label status = new Label();
    private final Button confirm = new Button();
    private final Button reject = new Button();
    private final Button export = new Button();

    public CrashCaseReviewView(Runnable onBack) {
        this(new CrashCaseRepository(), onBack);
    }

    CrashCaseReviewView(CrashCaseRepository repository, Runnable onBack) {
        this.repository = Objects.requireNonNull(repository);
        this.onBack = Objects.requireNonNull(onBack);

        getStyleClass().addAll("page", "vanta-consistent-page");
        setFillWidth(true);
        setSpacing(18);
        setPadding(new Insets(24));

        Button back = new Button(Localization.text("common.backArrow"));
        back.getStyleClass().add("secondary-button");
        back.setOnAction(event -> this.onBack.run());

        Label title = new Label(Localization.text("crashCases.title"));
        title.getStyleClass().add("page-title");
        Label description = new Label(Localization.text("crashCases.description"));
        description.setWrapText(true);
        Label privacy = new Label(Localization.text("crashCases.localOnly"));
        privacy.setWrapText(true);
        privacy.getStyleClass().add("muted");

        VBox header = new VBox(8, back, title, description, privacy);
        header.getStyleClass().add("repair-page-header");

        cases.setPlaceholder(new Label(Localization.text("crashCases.empty")));
        cases.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(CrashCaseRepository.CrashCase item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    return;
                }
                String label = item.status() + " · "
                        + (item.confirmedCategory() != null ? item.confirmedCategory()
                        : item.proposedCategory() == null ? Localization.text("crashCases.unlabelled")
                        : item.proposedCategory())
                        + " · " + item.occurrences() + " "
                        + Localization.text("crashCases.occurrences");
                String firstLine = item.sanitizedLog().lines().findFirst().orElse("");
                setText(label + "\n" + firstLine);
            }
        });
        cases.getSelectionModel().selectedItemProperty().addListener((observable, previous, selected) -> {
            details.setText(selected == null ? "" : selected.sanitizedLog());
            verifiedFix.setText(selected == null ? "" : selected.verifiedFix());
            updateReviewControls(selected);
        });
        VBox queue = new VBox(8,
                sectionTitle(Localization.text("crashCases.queue")),
                cases);
        queue.setMinWidth(260);
        VBox.setVgrow(cases, Priority.ALWAYS);

        details.setEditable(false);
        details.setWrapText(false);
        details.setPromptText(Localization.text("crashCases.selectPrompt"));
        details.setPrefRowCount(18);
        details.getStyleClass().add("diagnostic-log");
        VBox evidence = new VBox(8,
                sectionTitle(Localization.text("crashCases.evidence")),
                details);
        VBox.setVgrow(details, Priority.ALWAYS);

        verifiedFix.setPromptText(Localization.text("crashCases.fixPrompt"));
        verifiedFix.setWrapText(true);
        verifiedFix.setPrefRowCount(4);
        verifiedFix.getStyleClass().add("diagnostic-log");
        VBox fixNotes = new VBox(8,
                sectionTitle(Localization.text("crashCases.fixNotes")),
                verifiedFix);

        HBox reviewRow = new HBox(8);
        category.setItems(FXCollections.observableArrayList(
                Arrays.stream(FailureCategory.values())
                        .filter(value -> value != FailureCategory.UNKNOWN)
                        .toList()));
        category.setPromptText(Localization.text("crashCases.categoryPrompt"));
        category.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(category, Priority.ALWAYS);
        confirm.setText(Localization.text("crashCases.confirm"));
        confirm.getStyleClass().add("primary-button");
        confirm.setOnAction(event -> confirmSelected());
        reject.setText(Localization.text("crashCases.reject"));
        reject.getStyleClass().add("secondary-button");
        reject.setOnAction(event -> rejectSelected());
        reviewRow.getChildren().addAll(category, confirm, reject);

        Button importButton = new Button(Localization.text("crashCases.import"));
        importButton.getStyleClass().add("secondary-button");
        importButton.setOnAction(event -> importLog(importButton.getScene() == null
                ? null : importButton.getScene().getWindow()));
        export.setText(Localization.text("crashCases.export"));
        export.getStyleClass().add("secondary-button");
        export.setOnAction(event -> exportCorpus(export.getScene() == null
                ? null : export.getScene().getWindow()));
        HBox fileActions = new HBox(8, importButton, export);

        status.setWrapText(true);
        status.getStyleClass().add("muted");

        Button exportFixes = new Button(Localization.text("crashCases.exportFixes"));
        exportFixes.getStyleClass().add("secondary-button");
        exportFixes.setOnAction(event -> exportFixKnowledge(exportFixes.getScene() == null
                ? null : exportFixes.getScene().getWindow()));
        fileActions.getChildren().add(exportFixes);

        VBox review = new VBox(10, evidence, fixNotes, reviewRow, fileActions, status);
        review.setMinWidth(0);
        HBox.setHgrow(queue, Priority.ALWAYS);
        HBox.setHgrow(review, Priority.ALWAYS);
        HBox content = new HBox(18, queue, review);
        VBox.setVgrow(content, Priority.ALWAYS);

        getChildren().addAll(header, content);
        refresh();
    }

    private Label sectionTitle(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("settings-section-title");
        return label;
    }

    private void updateReviewControls(CrashCaseRepository.CrashCase selected) {
        boolean pending = selected != null
                && selected.status() == CrashCaseRepository.ReviewStatus.PENDING;
        confirm.setDisable(!pending);
        reject.setDisable(!pending);
        category.setDisable(!pending);
        if (pending && selected.proposedCategory() != null
                && selected.proposedCategory() != FailureCategory.UNKNOWN) {
            category.setValue(selected.proposedCategory());
        } else if (!pending) {
            category.setValue(null);
        }
    }

    private void confirmSelected() {
        CrashCaseRepository.CrashCase selected = cases.getSelectionModel().getSelectedItem();
        FailureCategory label = category.getValue();
        if (selected == null || label == null) {
            status.setText(Localization.text("crashCases.chooseCategory"));
            return;
        }
        try {
            repository.confirm(selected.id(), label, verifiedFix.getText());
            status.setText(Localization.text("crashCases.confirmed"));
            refresh();
        } catch (IOException | RuntimeException failure) {
            showFailure(failure);
        }
    }

    private void rejectSelected() {
        CrashCaseRepository.CrashCase selected = cases.getSelectionModel().getSelectedItem();
        if (selected == null) return;
        try {
            repository.reject(selected.id());
            status.setText(Localization.text("crashCases.rejected"));
            refresh();
        } catch (IOException | RuntimeException failure) {
            showFailure(failure);
        }
    }

    private void importLog(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Localization.text("crashCases.importTitle"));
        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Minecraft logs and crash reports", "*.log", "*.txt", "*.crash"),
                new FileChooser.ExtensionFilter("All files", "*.*"));
        File selected = chooser.showOpenDialog(owner);
        if (selected == null) return;
        try {
            CrashCaseRepository.CrashCase imported = repository.importLog(selected.toPath());
            status.setText(Localization.text("crashCases.imported")
                    .formatted(imported.occurrences()));
            refresh(imported.id());
        } catch (IOException | RuntimeException failure) {
            showFailure(failure);
        }
    }

    private void exportFixKnowledge(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Localization.text("crashCases.exportFixesTitle"));
        chooser.setInitialFileName("vanta-verified-crash-fixes.json");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Verified fix knowledge (*.json)", "*.json"));
        File selected = chooser.showSaveDialog(owner);
        if (selected == null) return;
        try {
            int count = repository.exportVerifiedFixes(selected.toPath());
            status.setText(Localization.text("crashCases.exportFixesDone").formatted(count));
        } catch (IOException | RuntimeException failure) {
            showFailure(failure);
        }
    }

    private void exportCorpus(Window owner) {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Localization.text("crashCases.exportTitle"));
        chooser.setInitialFileName("vanta-reviewed-crash-cases.tsv");
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Reviewed training cases (*.tsv)", "*.tsv"));
        File selected = chooser.showSaveDialog(owner);
        if (selected == null) return;
        try {
            int count = repository.exportConfirmedCorpus(selected.toPath());
            status.setText(Localization.text("crashCases.exported").formatted(count));
        } catch (IOException | RuntimeException failure) {
            showFailure(failure);
        }
    }

    private void refresh() {
        refresh(null);
    }

    private void refresh(String selectedId) {
        try {
            List<CrashCaseRepository.CrashCase> all = repository.listCases().stream()
                    .sorted(Comparator.comparingLong(CrashCaseRepository.CrashCase::createdAtEpochMillis)
                            .reversed())
                    .toList();
            cases.setItems(FXCollections.observableArrayList(all));
            if (selectedId != null) {
                all.stream().filter(item -> item.id().equals(selectedId)).findFirst()
                        .ifPresent(cases.getSelectionModel()::select);
            } else if (!all.isEmpty()) {
                cases.getSelectionModel().selectFirst();
            }
            export.setDisable(all.stream().noneMatch(item ->
                    item.status() == CrashCaseRepository.ReviewStatus.CONFIRMED));
        } catch (IOException | RuntimeException failure) {
            showFailure(failure);
        }
    }

    private void showFailure(Throwable failure) {
        status.setText(Localization.text("crashCases.operationFailed")
                .formatted(failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()));
    }
}
