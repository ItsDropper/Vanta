package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.ThemeManager;
import dev.itsdropper.vanta.ui.async.UiExecutor;
import javafx.geometry.Insets;
import javafx.beans.binding.Bindings;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.stage.FileChooser;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import dev.itsdropper.vanta.launcher.instance.InstanceManager;
import dev.itsdropper.vanta.launcher.instance.InstanceBackupService;
import dev.itsdropper.vanta.launcher.instance.InstanceRecoveryService;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.service.LaunchService;
import dev.itsdropper.vanta.launcher.service.MultiLaunchService;
import dev.itsdropper.vanta.ui.components.InstanceCard;
import dev.itsdropper.vanta.ui.VantaUI;
import dev.itsdropper.vanta.ui.components.PageHeader;
import dev.itsdropper.vanta.ui.components.EmptyState;
import dev.itsdropper.vanta.ui.LauncherSettings;
import dev.itsdropper.vanta.telemetry.TelemetryService;

import java.util.ArrayList;
import java.util.List;
import java.util.Comparator;
import java.util.Locale;
import java.util.function.Consumer;

public class InstancesView extends VBox {

    private final MultiLaunchService launchService;

    private final VBox instanceList;
    private final Label statusLabel;
    private final Label countLabel;
    private Button backupButton;

    private final Consumer<Instance> onInstanceSettings;
    private final Runnable onCreateInstance;

    private final TextField searchField;
    private final ComboBox<String> sortField;

    private List<Instance> loadedInstances =
            List.of();

    // Only the newest scan may replace the visible instance list.
    private long refreshRequestId;

    public InstancesView(
            MultiLaunchService launchService,
            Runnable onCreateInstance,
            Consumer<Instance> onInstanceSettings
    ) {
        this(launchService, onCreateInstance, onInstanceSettings, false);
    }

    public InstancesView(
            MultiLaunchService launchService,
            Runnable onCreateInstance,
            Consumer<Instance> onInstanceSettings,
            boolean screenshotPreview
    ) {
        this.launchService =
                launchService;

        this.onInstanceSettings =
                onInstanceSettings;
        this.onCreateInstance = onCreateInstance;

        getStyleClass().add(
                "instances-page"
        );

        paddingProperty().bind(
                Bindings.createObjectBinding(() -> {
                    double width = getWidth();
                    double horizontal = width < 850 ? 20 : width < 1100 ? 28 : 36;
                    return new Insets(VantaUI.spacing(7), horizontal, VantaUI.spacing(6), horizontal);
                }, widthProperty())
        );

        setSpacing(VantaUI.spacing(5));

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        countLabel =
                new Label(
                        Localization.text("instances.countZero")
                );

        countLabel.getStyleClass().add(
                "instances-count"
        );
        VantaUI.styleText(countLabel, VantaUI.TextRole.EYEBROW);

        Button createButton =
                new Button(
                        Localization.text("common.create")
                );

        createButton.getStyleClass().add(
                "instance-create-button"
        );

        createButton.setOnAction(
                event ->
                        onCreateInstance.run()
        );

        searchField =
                new TextField();

        searchField.setPromptText(
                Localization.text("instances.searchPrompt")
        );
        searchField.setAccessibleText(Localization.text("instances.searchAccessible"));

        searchField.getStyleClass().add(
                "instance-search-field"
        );

        searchField.setPrefWidth(
                220
        );

        searchField.textProperty().addListener(
                (observable, oldValue, newValue) ->
                        filterInstances(newValue)
        );

        createButton.setAccessibleText(Localization.text("instances.createAccessible"));
        createButton.setTooltip(new javafx.scene.control.Tooltip("Create a new Minecraft instance"));

        backupButton = new Button(Localization.text("instances.backup"));
        backupButton.getStyleClass().add("secondary-button");
        backupButton.setAccessibleText(Localization.text("instances.backupAccessible"));
        backupButton.setTooltip(new javafx.scene.control.Tooltip(
                Localization.text("instances.backupHelp")
        ));
        backupButton.setOnAction(event -> backUpInstance());

        Button restoreButton = new Button(Localization.text("instances.restore"));
        restoreButton.getStyleClass().add("secondary-button");
        restoreButton.setAccessibleText(Localization.text("instances.restoreAccessible"));
        restoreButton.setTooltip(new javafx.scene.control.Tooltip(
                Localization.text("instances.restoreHelp")
        ));
        restoreButton.setOnAction(event -> restoreInstanceBackup());

        Button verifyBackupButton = new Button(Localization.text("instances.verifyBackup"));
        verifyBackupButton.getStyleClass().add("secondary-button");
        verifyBackupButton.setAccessibleText(Localization.text("instances.verifyAccessible"));
        verifyBackupButton.setTooltip(new javafx.scene.control.Tooltip(
                Localization.text("instances.verifyHelp")
        ));
        verifyBackupButton.setOnAction(event -> verifyInstanceBackup());

        PageHeader headerTop = new PageHeader(
                "Instances",
                "Manage your Minecraft installations.",
                "page-title",
                "page-subtitle",
                countLabel,
                backupButton,
                restoreButton,
                verifyBackupButton,
                createButton
        );

        Button refreshButton = new Button(Localization.text("common.refresh"));
        refreshButton.getStyleClass().add("secondary-button");
        refreshButton.setAccessibleText(Localization.text("instances.refreshAccessible"));
        refreshButton.setTooltip(new javafx.scene.control.Tooltip(Localization.text("instances.rescan")));
        refreshButton.setOnAction(event -> refresh());

        searchField.setMinWidth(180);
        searchField.setPrefWidth(360);
        searchField.setMaxWidth(560);

        Button clearSearchButton = new Button(Localization.text("instances.clear"));
        clearSearchButton.getStyleClass().add("secondary-button");
        clearSearchButton.setAccessibleText(Localization.text("instances.clearAccessible"));
        clearSearchButton.setTooltip(new javafx.scene.control.Tooltip(Localization.text("common.clearSearch")));
        clearSearchButton.visibleProperty().bind(Bindings.isNotEmpty(searchField.textProperty()));
        clearSearchButton.managedProperty().bind(clearSearchButton.visibleProperty());
        clearSearchButton.setOnAction(event -> {
            searchField.clear();
            searchField.requestFocus();
        });

        sortField = new ComboBox<>();
        sortField.getStyleClass().add("instance-sort-field");
        sortField.getItems().setAll("Name (A–Z)", "Name (Z–A)", "Version (newest)", "Loader");
        sortField.setValue("Name (A–Z)");
        sortField.setPromptText(Localization.text("instances.sort"));
        sortField.setAccessibleText(Localization.text("instances.sortAccessible"));
        sortField.setTooltip(new javafx.scene.control.Tooltip(Localization.text("instances.sortHelp")));
        sortField.setMinWidth(150);
        sortField.setPrefWidth(170);
        sortField.setOnAction(event -> filterInstances(searchField.getText()));

        Button recoveryButton = new Button(Localization.text("instances.recovery"));
        recoveryButton.getStyleClass().add("secondary-button");
        recoveryButton.setAccessibleText(Localization.text("instances.recoveryAccessible"));
        recoveryButton.setTooltip(new javafx.scene.control.Tooltip(
                Localization.text("instances.recoveryHelp")
        ));
        recoveryButton.setOnAction(event -> manageRecoveryPoints(recoveryButton));

        FlowPane searchRow = new FlowPane(VantaUI.spacing(2), VantaUI.spacing(2));
        searchRow.getStyleClass().add("instances-toolbar");
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchRow.setRowValignment(javafx.geometry.VPos.CENTER);
        searchRow.prefWrapLengthProperty().bind(searchRow.widthProperty());
        searchRow.getChildren().addAll(searchField, clearSearchButton, sortField, refreshButton, recoveryButton);

        VBox header = new VBox(VantaUI.spacing(3), headerTop, searchRow);
        header.getStyleClass().add("instances-header");

        // ---------------------------------------------------------
        // LIST
        // ---------------------------------------------------------

        instanceList =
                new VBox(
                        0
                );

        instanceList.getStyleClass().add(
                "instance-list"
        );
        instanceList.setAlignment(Pos.TOP_CENTER);
        instanceList.setFillWidth(true);

        ScrollPane scrollPane =
                new ScrollPane(
                        instanceList
                );

        scrollPane.setFitToWidth(
                true
        );
        scrollPane.setFitToHeight(screenshotPreview);

        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        scrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        scrollPane.getStyleClass().add(
                "instances-scroll"
        );

        VBox.setVgrow(
                scrollPane,
                Priority.ALWAYS
        );

        // ---------------------------------------------------------
        // STATUS
        // ---------------------------------------------------------

        statusLabel =
                new Label(
                        Localization.text("instances.scanning")
                );

        statusLabel.getStyleClass().add(
                "instances-status"
        );
        VantaUI.styleText(statusLabel, VantaUI.TextRole.MUTED);

        // ---------------------------------------------------------
        // BUILD
        // ---------------------------------------------------------

        getChildren().addAll(
                header,
                scrollPane,
                statusLabel
        );

        // ---------------------------------------------------------
        // LAUNCH LISTENER
        // ---------------------------------------------------------

        launchService.addStateListener(
                this::onLaunchStateChanged
        );

        if (screenshotPreview) {
            instanceList.setAlignment(Pos.CENTER);
            countLabel.setText(Localization.text("instances.library"));
            statusLabel.setText(Localization.text("instances.previewNotice"));
            statusLabel.setVisible(false);
            statusLabel.setManaged(false);
            instanceList.getChildren().setAll(new EmptyState(
                    "▦",
                    Localization.text("instances.yourLibrary"),
                    Localization.text("instances.previewEmptyTitle"),
                    Localization.text("instances.previewEmptyDescription")
            ));
        } else {
            refresh();
        }
    }

    // =============================================================
    // REFRESH
    // =============================================================

    public void refresh() {

        long requestId = ++refreshRequestId;
        statusLabel.setText(Localization.text("instances.scanning"));

        UiExecutor.shared().execute(() -> {
            List<Instance> instances;
            try {
                instances = InstanceManager.discoverInstances();
            } catch (Throwable ex) {
                ex.printStackTrace();
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != refreshRequestId) return;
                    // A failed rescan must not erase the last successfully loaded list.
                    // Keep existing cards and the real count visible so a transient filesystem
                    // error cannot make the user's instances appear to have disappeared.
                    statusLabel.setText(loadedInstances.isEmpty()
                            ? Localization.text("instances.scanFailed")
                            : Localization.text("instances.refreshFailed"));
                });
                return;
            }

            UiExecutor.shared().dispatchToUi(() -> {
                if (requestId != refreshRequestId) return;

                loadedInstances = List.copyOf(instances);
                updateCount(loadedInstances.size());

                if (loadedInstances.isEmpty()) {
                    instanceList.getChildren().setAll(createEmptyState());
                    statusLabel.setText(Localization.text("instances.empty"));
                    return;
                }

                // Re-apply the current query instead of unexpectedly clearing it
                // after rename, duplicate, icon change, or manual refresh.
                filterInstances(searchField.getText());
            });
        });

    }


    private void manageRecoveryPoints(Button actionButton) {
        if (loadedInstances.isEmpty()) {
            statusLabel.setText(Localization.text("instances.noRecovery"));
            return;
        }

        List<String> instanceLabels = loadedInstances.stream()
                .map(instance -> instance.getName() + " · Minecraft " + instance.getMinecraftVersion()
                        + " · " + instance.getDisplayLoader() + " [" + instance.getId() + "]")
                .toList();
        ChoiceDialog<String> instanceChooser = new ChoiceDialog<>(instanceLabels.get(0), instanceLabels);
        instanceChooser.setTitle(Localization.text("instances.recoveryTitle"));
        instanceChooser.setHeaderText(Localization.text("instances.chooseInstance"));
        instanceChooser.setContentText("Instance:");
        String selectedLabel = instanceChooser.showAndWait().orElse(null);
        if (selectedLabel == null) return;

        int selectedIndex = instanceLabels.indexOf(selectedLabel);
        if (selectedIndex < 0 || selectedIndex >= loadedInstances.size()) return;
        Instance selected = loadedInstances.get(selectedIndex);
        LaunchService.LaunchState launchState = launchService.getState(selected);
        if (launchState == LaunchService.LaunchState.PREPARING
                || launchState == LaunchService.LaunchState.STARTING
                || launchState == LaunchService.LaunchState.RUNNING
                || launchState == LaunchService.LaunchState.CLOSING) {
            new Alert(Alert.AlertType.WARNING,
                    "Wait until this instance has completely stopped before creating or restoring a recovery point.")
                    .showAndWait();
            return;
        }

        ChoiceDialog<String> actionChooser = new ChoiceDialog<>(
                "Create recovery point", List.of("Create recovery point", "Restore a recovery point"));
        actionChooser.setTitle(Localization.text("instances.recoveryTitle"));
        actionChooser.setHeaderText(selected.getName());
        actionChooser.setContentText(Localization.text("instances.chooseOperation"));
        String action = actionChooser.showAndWait().orElse(null);
        if (action == null) return;

        actionButton.setDisable(true);
        if (action.equals("Create recovery point")) {
            statusLabel.setText("Creating a targeted recovery point for " + selected.getName() + "…");
            UiExecutor.shared().execute(() -> {
                try {
                    int recoveredTransactions =
                            InstanceRecoveryService.recoverInterruptedRestores(selected);
                    InstanceRecoveryService.RecoveryPoint point =
                            InstanceRecoveryService.createRecoveryPoint(selected, "Manual recovery point");
                    UiExecutor.shared().dispatchToUi(() -> {
                        actionButton.setDisable(false);
                        if (recoveredTransactions > 0) {
                            new Alert(Alert.AlertType.INFORMATION,
                                    "Vanta reconciled " + recoveredTransactions
                                            + " interrupted recovery transaction"
                                            + (recoveredTransactions == 1 ? "" : "s")
                                            + " before creating this point. Any incomplete restore was rolled back.")
                                    .show();
                        }
                        statusLabel.setText("Recovery point created: " + point.id());
                        new Alert(Alert.AlertType.INFORMATION,
                                "Recovery point created.\n\n"
                                        + "Files: " + point.fileCount() + "\n"
                                        + "Expanded size: " + String.format(Locale.ROOT, "%.2f MiB",
                                        point.expandedBytes() / (1024.0 * 1024.0)) + "\n"
                                        + "Retention: latest five recovery points per instance.\n\n"
                                        + "Worlds, screenshots, logs, and instance settings were not included.")
                                .show();
                    });
                } catch (Throwable failure) {
                    UiExecutor.shared().dispatchToUi(() -> {
                        actionButton.setDisable(false);
                        String message = failure.getMessage() == null
                                ? failure.getClass().getSimpleName() : failure.getMessage();
                        statusLabel.setText("Recovery point creation failed: " + message);
                        new Alert(Alert.AlertType.ERROR, "Could not create recovery point.\n\n" + message).show();
                    });
                }
            });
            return;
        }

        statusLabel.setText("Loading recovery-point history…");
        UiExecutor.shared().execute(() -> {
            try {
                int recoveredTransactions =
                        InstanceRecoveryService.recoverInterruptedRestores(selected);
                List<InstanceRecoveryService.RecoveryPoint> points =
                        InstanceRecoveryService.listRecoveryPoints(selected);
                UiExecutor.shared().dispatchToUi(() -> {
                    if (recoveredTransactions > 0) {
                        new Alert(Alert.AlertType.INFORMATION,
                                "Vanta reconciled " + recoveredTransactions
                                        + " interrupted recovery transaction"
                                        + (recoveredTransactions == 1 ? "" : "s")
                                        + ". Any incomplete restore was rolled back before its temporary files were removed.")
                                .show();
                    }
                    if (points.isEmpty()) {
                        actionButton.setDisable(false);
                        statusLabel.setText("No valid recovery points exist for " + selected.getName() + ".");
                        new Alert(Alert.AlertType.INFORMATION,
                                "This instance has no valid recovery points yet.").show();
                        return;
                    }

                    List<String> labels = points.stream()
                            .map(point -> java.time.Instant.ofEpochMilli(point.createdAtEpochMillis())
                                    + " · " + point.reason() + " · " + point.fileCount() + " files")
                            .toList();
                    ChoiceDialog<String> pointChooser = new ChoiceDialog<>(labels.get(0), labels);
                    pointChooser.setTitle(Localization.text("instances.restorePoint"));
                    pointChooser.setHeaderText("Choose a recovery point for " + selected.getName());
                    pointChooser.setContentText("Recovery point:");
                    String chosen = pointChooser.showAndWait().orElse(null);
                    if (chosen == null) {
                        actionButton.setDisable(false);
                        return;
                    }

                    int pointIndex = labels.indexOf(chosen);
                    if (pointIndex < 0 || pointIndex >= points.size()) {
                        actionButton.setDisable(false);
                        return;
                    }
                    InstanceRecoveryService.RecoveryPoint point = points.get(pointIndex);
                    Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION,
                            "Recovery point: " + point.id() + "\n"
                                    + "Reason: " + point.reason() + "\n"
                                    + "Files: " + point.fileCount() + "\n"
                                    + "Expanded size: " + String.format(Locale.ROOT, "%.2f MiB",
                                    point.expandedBytes() / (1024.0 * 1024.0)) + "\n\n"
                                    + "This will replace the instance's current mods, config, resourcepacks, shaderpacks, and imported loader profiles "
                                    + "with the selected recovery point.\n\n"
                                    + "Worlds, screenshots, options, and instance metadata are not changed.\n\n"
                                    + "Vanta stages the replacement and attempts rollback if publication fails.");
                    confirmation.setTitle(Localization.text("instances.confirmRecovery"));
                    confirmation.setHeaderText("Restore " + point.id() + "?");
                    if (confirmation.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
                        actionButton.setDisable(false);
                        return;
                    }

                    statusLabel.setText(Localization.text("instances.restoringPoint"));
                    UiExecutor.shared().execute(() -> {
                        try {
                            InstanceRecoveryService.restoreRecoveryPoint(selected, point.id());
                            UiExecutor.shared().dispatchToUi(() -> {
                                actionButton.setDisable(false);
                                statusLabel.setText("Recovery point restored for " + selected.getName() + ".");
                                new Alert(Alert.AlertType.INFORMATION,
                                        "Recovery completed.\n\n"
                                                + "Mods, configs, resource packs, and shader packs were restored. "
                                                + "Worlds and other user data were left untouched.").show();
                            });
                        } catch (Throwable failure) {
                            UiExecutor.shared().dispatchToUi(() -> {
                                actionButton.setDisable(false);
                                String message = failure.getMessage() == null
                                        ? failure.getClass().getSimpleName() : failure.getMessage();
                                statusLabel.setText("Recovery failed: " + message);
                                new Alert(Alert.AlertType.ERROR,
                                        "Recovery failed. Vanta attempted to roll back any published directories.\n\n"
                                                + message).show();
                            });
                        }
                    });
                });
            } catch (Throwable failure) {
                UiExecutor.shared().dispatchToUi(() -> {
                    actionButton.setDisable(false);
                    String message = failure.getMessage() == null
                            ? failure.getClass().getSimpleName() : failure.getMessage();
                    statusLabel.setText("Could not load recovery history: " + message);
                    new Alert(Alert.AlertType.ERROR, "Could not load recovery history.\n\n" + message).show();
                });
            }
        });
    }

    private void verifyInstanceBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Localization.text("instances.verifyTitle"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("ZIP backups (*.zip)", "*.zip"));
        java.io.File archive = chooser.showOpenDialog(
                getScene() == null ? null : getScene().getWindow());
        if (archive == null) return;

        backupButton.setDisable(true);
        statusLabel.setText(Localization.text("instances.verifyingBackup"));
        UiExecutor.shared().execute(() -> {
            try {
                InstanceBackupService.BackupVerification report =
                        InstanceBackupService.verifyBackup(archive.toPath());
                UiExecutor.shared().dispatchToUi(() -> {
                    backupButton.setDisable(false);
                    String size = String.format(Locale.ROOT, "%.2f GiB",
                            report.expandedBytes() / (1024.0 * 1024.0 * 1024.0));
                    statusLabel.setText("Backup verified: " + archive.getName()
                            + " · " + report.entryCount() + " entries");
                    Alert success = new Alert(Alert.AlertType.INFORMATION,
                            "Archive integrity checks passed.\n\n"
                                    + "Instance: " + report.instanceName() + "\n"
                                    + "Minecraft: " + report.minecraftVersion() + "\n"
                                    + "Loader: " + report.loader() + "\n"
                                    + "Entries: " + report.entryCount() + "\n"
                                    + "Expanded size: " + size + "\n\n"
                                    + "This verifies archive structure and CRC integrity; it does not prove that every game dependency is present or that Minecraft will launch.");
                    success.setTitle(Localization.text("instances.backupVerified"));
                    success.setHeaderText(archive.getName());
                    success.show();
                });
            } catch (Throwable failure) {
                UiExecutor.shared().dispatchToUi(() -> {
                    backupButton.setDisable(false);
                    String message = failure.getMessage() == null
                            ? failure.getClass().getSimpleName() : failure.getMessage();
                    statusLabel.setText(Localization.text("instances.backupVerifyFailedPrefix") + message);
                    new Alert(Alert.AlertType.ERROR,
                            "Vanta could not verify this backup.\n\n" + message).show();
                });
            }
        });
    }

    private void restoreInstanceBackup() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle(Localization.text("instances.selectBackup"));
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP backups (*.zip)", "*.zip"));
        java.io.File archive = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
        if (archive == null) return;

        String filename = archive.getName();
        String suggestedName = filename.replaceFirst("(?i)-backup\\.zip$", "")
                .replaceFirst("(?i)\\.zip$", "") + " (Restored)";
        TextInputDialog nameDialog = new TextInputDialog(suggestedName);
        nameDialog.setTitle(Localization.text("instances.restoreBackup"));
        nameDialog.setHeaderText(Localization.text("instances.restoreAsNew"));
        nameDialog.setContentText(Localization.text("instances.instanceName"));
        String name = nameDialog.showAndWait().orElse(null);
        if (name == null) return;
        if (name.isBlank()) {
            new Alert(Alert.AlertType.WARNING, "Enter a name for the restored instance.").showAndWait();
            return;
        }

        backupButton.setDisable(true);
        statusLabel.setText(Localization.text("instances.restoringBackup"));
        UiExecutor.shared().execute(() -> {
            try {
                InstanceBackupService.restoreBackup(archive.toPath(), name);
                UiExecutor.shared().dispatchToUi(() -> {
                    backupButton.setDisable(false);
                    statusLabel.setText("Backup restored as " + name.trim() + ".");
                    refresh();
                    new Alert(Alert.AlertType.INFORMATION,
                            "The backup was restored as a separate instance. The original instance and backup file were not changed.")
                            .show();
                });
            } catch (Throwable ex) {
                UiExecutor.shared().dispatchToUi(() -> {
                    backupButton.setDisable(false);
                    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    statusLabel.setText(Localization.text("instances.restoreFailedPrefix") + message);
                    new Alert(Alert.AlertType.ERROR, "Vanta could not restore this backup.\n\n" + message).show();
                });
            }
        });
    }

    private void backUpInstance() {
        if (loadedInstances.isEmpty()) {
            statusLabel.setText(Localization.text("instances.noneToBackup"));
            return;
        }

        List<String> instanceLabels = loadedInstances.stream()
                .map(instance -> instance.getName() + " · Minecraft " + instance.getMinecraftVersion()
                        + " · " + instance.getDisplayLoader() + " [" + instance.getId() + "]")
                .toList();
        ChoiceDialog<String> chooser = new ChoiceDialog<>(instanceLabels.get(0), instanceLabels);
        chooser.setTitle(Localization.text("instances.backupTitle"));
        chooser.setHeaderText(Localization.text("instances.chooseBackup"));
        chooser.setContentText("Instance:");
        String selectedLabel = chooser.showAndWait().orElse(null);
        if (selectedLabel == null) return;
        Instance selected = loadedInstances.get(instanceLabels.indexOf(selectedLabel));
        if (selected == null) return;

        if (launchService.isRunning(selected)) {
            new Alert(Alert.AlertType.WARNING,
                    "Close this Minecraft instance before backing it up. Backing up a live world can produce an inconsistent save.")
                    .showAndWait();
            return;
        }

        FileChooser saveChooser = new FileChooser();
        saveChooser.setTitle(Localization.text("instances.saveBackup"));
        saveChooser.setInitialFileName(safeFileName(selected.getName()) + "-backup.zip");
        saveChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("ZIP backup (*.zip)", "*.zip"));
        java.io.File selectedFile = saveChooser.showSaveDialog(getScene() == null ? null : getScene().getWindow());
        if (selectedFile == null) return;

        java.nio.file.Path destination = selectedFile.toPath();
        if (!destination.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            destination = destination.resolveSibling(destination.getFileName() + ".zip");
        }
        final java.nio.file.Path output = destination;
        backupButton.setDisable(true);
        statusLabel.setText("Backing up " + selected.getName() + "… Worlds and settings are included.");

        UiExecutor.shared().execute(() -> {
            try {
                InstanceBackupService.createBackup(selected, output);
                TelemetryService.track("feature_backup_used");
                UiExecutor.shared().dispatchToUi(() -> {
                    backupButton.setDisable(false);
                    statusLabel.setText("Backup created: " + output.getFileName());
                    Alert success = new Alert(Alert.AlertType.INFORMATION,
                            "Full instance backup created. Worlds, mods, configs, resource packs, shader packs, screenshots, options, and instance metadata are preserved. Transient logs and extracted native libraries are excluded.");
                    success.setTitle(Localization.text("instances.backupComplete"));
                    success.setHeaderText(selected.getName());
                    success.show();
                });
            } catch (Throwable ex) {
                UiExecutor.shared().dispatchToUi(() -> {
                    backupButton.setDisable(false);
                    String message = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
                    statusLabel.setText(Localization.text("instances.backupFailedPrefix") + message);
                    new Alert(Alert.AlertType.ERROR, "Vanta could not create the backup.\n\n" + message).show();
                });
            }
        });
    }

    private static String safeFileName(String name) {
        String safe = name == null ? "Vanta-Modpack" : name
                .replaceAll("[^a-zA-Z0-9._ -]", "_")
                .trim();
        return safe.isBlank() ? "Vanta-Modpack" : safe;
    }

    private void filterInstances(String query) {
        if (loadedInstances.isEmpty()) {
            return;
        }

        String normalized = query == null
                ? ""
                : query.trim().toLowerCase(Locale.ROOT);

        Comparator<Instance> nameOrder = Comparator.<Instance, String>comparing(
                instance -> safeLower(instance.getName())
        );
        Comparator<Instance> selectedOrder = switch (sortField.getValue() == null
                ? "Name (A–Z)" : sortField.getValue()) {
            case "Name (Z–A)" -> nameOrder.reversed();
            case "Version (newest)" -> (left, right) -> {
                int versionOrder = compareNatural(right.getMinecraftVersion(), left.getMinecraftVersion());
                return versionOrder != 0 ? versionOrder : nameOrder.compare(left, right);
            };
            case "Loader" -> Comparator.<Instance, String>comparing(
                    instance -> safeLower(instance.getDisplayLoader())
            ).thenComparing(nameOrder);
            default -> nameOrder;
        };

        List<Instance> filtered = loadedInstances.stream()
                .filter(instance ->
                        normalized.isBlank()
                                || safeLower(instance.getName()).contains(normalized)
                                || safeLower(instance.getId()).contains(normalized)
                                || safeLower(instance.getMinecraftVersion()).contains(normalized)
                                || safeLower(instance.getDisplayLoader()).contains(normalized))
                .sorted(selectedOrder)
                .toList();

        if (filtered.isEmpty()) {
            instanceList.getChildren().setAll(createSearchEmptyState(query));
            statusLabel.setText(Localization.text("instances.noSearchMatches"));
            return;
        }

        renderInstances(filtered);
        statusLabel.setText(filtered.size() + " of " + loadedInstances.size()
                + " instance" + (loadedInstances.size() == 1 ? "" : "s") + " shown");
    }

    private static String safeLower(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }

    private static int compareNatural(String left, String right) {
        String a = safeLower(left);
        String b = safeLower(right);
        int ai = 0;
        int bi = 0;
        while (ai < a.length() && bi < b.length()) {
            char ac = a.charAt(ai);
            char bc = b.charAt(bi);
            if (Character.isDigit(ac) && Character.isDigit(bc)) {
                int aEnd = ai;
                int bEnd = bi;
                while (aEnd < a.length() && Character.isDigit(a.charAt(aEnd))) aEnd++;
                while (bEnd < b.length() && Character.isDigit(b.charAt(bEnd))) bEnd++;
                int aSignificant = ai;
                int bSignificant = bi;
                while (aSignificant < aEnd && a.charAt(aSignificant) == '0') aSignificant++;
                while (bSignificant < bEnd && b.charAt(bSignificant) == '0') bSignificant++;
                int aLength = aEnd - aSignificant;
                int bLength = bEnd - bSignificant;
                if (aLength != bLength) return Integer.compare(aLength, bLength);
                for (int offset = 0; offset < aLength; offset++) {
                    int digitOrder = Character.compare(a.charAt(aSignificant + offset), b.charAt(bSignificant + offset));
                    if (digitOrder != 0) return digitOrder;
                }
                ai = aEnd;
                bi = bEnd;
                continue;
            }
            int characterOrder = Character.compare(ac, bc);
            if (characterOrder != 0) return characterOrder;
            ai++;
            bi++;
        }
        return Integer.compare(a.length() - ai, b.length() - bi);
    }

    private VBox createSearchEmptyState(String query) {
        Label icon = new Label("⌕");
        icon.getStyleClass().add("instance-empty-icon");

        Label title = new Label(Localization.text("instances.noMatchesTitle"));
        title.getStyleClass().add("instance-empty-title");
        VantaUI.styleText(title, VantaUI.TextRole.SECTION_TITLE);

        Label description = new Label("No instance matches \"" + (query == null ? "" : query.trim())
                + "\". Try a name, Minecraft version, or loader.");
        description.setWrapText(true);
        description.getStyleClass().add("instance-empty-description");
        VantaUI.styleText(description, VantaUI.TextRole.MUTED);

        Button clearButton = new Button(Localization.text("common.clearSearchUpper"));
        clearButton.getStyleClass().add("secondary-button");
        clearButton.setOnAction(event -> searchField.clear());

        VBox empty = new VBox(VantaUI.spacing(2), icon, title, description, clearButton);
        empty.setAlignment(Pos.CENTER);
        empty.setPadding(new Insets(VantaUI.spacing(12)));
        empty.getStyleClass().add("instance-empty");
        empty.setMaxWidth(Double.MAX_VALUE);
        VantaUI.applySurface(empty, VantaUI.SurfaceRole.CARD);
        return empty;
    }

    private void updateCount(int count) {

        countLabel.setText(
                count
                        + " INSTANCE"
                        + (count == 1 ? "" : "S")
        );
    }

    private void renderInstances(List<Instance> instances) {

        instanceList.getChildren().clear();

        for (Instance instance : instances) {

            InstanceCard card =
                    new InstanceCard(
                            instance,
                            () -> launch(instance),
                            () -> onInstanceSettings.accept(instance),
                            () -> deleteInstance(instance),
                            () -> showRenamePopup(instance),
                            () -> showDuplicatePopup(instance),
                            () -> showIconPopup(instance)
                    );

            if (LauncherSettings.isCompactInstancesEnabled()) card.getStyleClass().add("instance-card-compact");
            instanceList.getChildren().add(card);
        }

        updateCards();
    }


    private void showRenamePopup(Instance instance) {
        showNamePopup(instance, false);
    }

    private void showDuplicatePopup(Instance instance) {
        showNamePopup(instance, true);
    }

    private void showNamePopup(Instance instance, boolean duplicate) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        VBox root = new VBox(VantaUI.spacing(3));
        root.getStyleClass().add("instance-action-popup");
        root.setPadding(new Insets(VantaUI.spacing(4)));
        VantaUI.applySurface(root, VantaUI.SurfaceRole.PANEL);
        Label title = new Label(duplicate ? "DUPLICATE INSTANCE" : "RENAME INSTANCE");
        title.getStyleClass().add("instance-action-title");
        Label subtitle = new Label(duplicate ? "Create a complete copy with its own instance ID." : "Change the display name without moving the instance.");
        subtitle.getStyleClass().add("instance-action-subtitle");
        subtitle.setWrapText(true);
        TextField field = new TextField(duplicate ? "Copy of " + instance.getName() : instance.getName());
        field.getStyleClass().add("instance-action-field");
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = new Button(Localization.text("common.cancel"));
        cancel.getStyleClass().add("secondary-button");
        Button apply = new Button(duplicate ? "DUPLICATE" : "SAVE");
        apply.getStyleClass().add("primary-button");
        cancel.setOnAction(e -> popup.hide());
        apply.setOnAction(e -> {
            String name = field.getText() == null ? "" : field.getText().trim();
            if (name.isBlank()) return;
            popup.hide();
            statusLabel.setText((duplicate ? "Duplicating " : "Renaming ") + instance.getName() + "...");
            UiExecutor.shared().execute(() -> {
                try {
                    if (duplicate) InstanceManager.duplicateInstance(instance, name);
                    else InstanceManager.renameInstance(instance, name);
                    UiExecutor.shared().dispatchToUi(this::refresh);
                } catch (Throwable ex) {
                    UiExecutor.shared().dispatchToUi(() -> statusLabel.setText(ex.getMessage() == null ? "Operation failed." : ex.getMessage()));
                }
            });
        });
        actions.getChildren().addAll(cancel, apply);
        root.getChildren().addAll(title, subtitle, field, actions);
        ThemeManager.apply(root, ThemeManager.loadAccent());

        popup.getContent().add(root);
        showPopupCentered(popup, 430, duplicate ? 190 : 175);
        UiExecutor.shared().dispatchToUi(() -> { field.requestFocus(); field.selectAll(); });
    }

    private void showIconPopup(Instance instance) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        VBox root = new VBox(VantaUI.spacing(3));
        root.getStyleClass().add("instance-action-popup");
        root.setPadding(new Insets(VantaUI.spacing(4)));
        VantaUI.applySurface(root, VantaUI.SurfaceRole.PANEL);
        Label title = new Label(Localization.text("instances.iconTitle"));
        title.getStyleClass().add("instance-action-title");
        Label subtitle = new Label(Localization.text("instances.iconHelp"));
        subtitle.getStyleClass().add("instance-action-subtitle");
        subtitle.setWrapText(true);
        HBox choices = new HBox(8);
        choices.setAlignment(Pos.CENTER_LEFT);
        String[][] icons = {{"SHIELD","⬢"},{"PACKAGE","◆"},{"PICKAXE","⛏"},{"STAR","★"},{"FIRE","✦"},{"WORLD","◎"},{"CROWN","♛"},{"DIAMOND","◇"}};
        for (String[] icon : icons) {
            Button button = new Button("", InstanceCard.createIconGraphic(icon[0], 24));
            button.getStyleClass().add("instance-icon-choice");
            button.setTooltip(new javafx.scene.control.Tooltip(icon[0]));
            button.setOnAction(e -> {
                popup.hide();
                UiExecutor.shared().execute(() -> {
                    try { InstanceManager.setInstanceIcon(instance, icon[0]); UiExecutor.shared().dispatchToUi(this::refresh); }
                    catch (Throwable ex) { UiExecutor.shared().dispatchToUi(() -> statusLabel.setText(ex.getMessage() == null ? "Could not change icon." : ex.getMessage())); }
                });
            });
            choices.getChildren().add(button);
        }
        Button upload = new Button(Localization.text("instances.uploadPng"));
        upload.getStyleClass().add("secondary-button");
        upload.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle(Localization.text("instances.chooseIcon"));
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG images", "*.png"));
            java.io.File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (selected == null) return;
            popup.hide();
            UiExecutor.shared().execute(() -> {
                try {
                    java.nio.file.Files.copy(selected.toPath(), instance.getDirectory().resolve("icon.png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    InstanceManager.setInstanceIcon(instance, "CUSTOM");
                    UiExecutor.shared().dispatchToUi(this::refresh);
                } catch (Throwable ex) {
                    UiExecutor.shared().dispatchToUi(() -> statusLabel.setText(ex.getMessage() == null ? "Could not upload icon." : ex.getMessage()));
                }
            });
        });
        root.getChildren().addAll(title, subtitle, choices, upload);
        ThemeManager.apply(root, ThemeManager.loadAccent());

        popup.getContent().add(root);
        showPopupCentered(popup, 430, 150);
    }

    private void showPopupCentered(Popup popup, double width, double height) {
        javafx.stage.Window window = getScene() == null ? null : getScene().getWindow();
        if (window == null) return;
        popup.show(window, window.getX() + Math.max(0, (window.getWidth() - width) / 2),
                window.getY() + Math.max(0, (window.getHeight() - height) / 2));
    }

    // =============================================================
    // DELETE
    // =============================================================

    private void deleteInstance(
            Instance instance
    ) {

        if (instance == null) {
            return;
        }

        LaunchService.LaunchState state =
                launchService.getState(instance);

        if (state == LaunchService.LaunchState.PREPARING
                || state == LaunchService.LaunchState.STARTING
                || state == LaunchService.LaunchState.CLOSING) {

            statusLabel.setText(
                    "Wait until this instance has finished starting or closing."
            );

            return;
        }

        if (launchService.isRunning(instance)) {
            statusLabel.setText(
                    Localization.text("instances.cannotDeleteRunning")
            );
            return;
        }

        statusLabel.setText(
                Localization.text("instances.deletingPrefix")
                        + instance.getName()
                        + "..."
        );

        UiExecutor.shared().execute(() -> {

                    try {

                        InstanceManager.deleteInstance(
                                instance
                        );

                        UiExecutor.shared().dispatchToUi(() -> {

                            statusLabel.setText(
                                    Localization.text("instances.deletedPrefix")
                                            + instance.getName()
                            );

                            refresh();
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        UiExecutor.shared().dispatchToUi(() ->
                                statusLabel.setText(
                                        ex.getMessage() != null
                                                ? ex.getMessage()
                                                : "Failed to delete instance."
                                )
                        );
                    }
        });



    }

    // =============================================================
    // LAUNCH STATE
    // =============================================================

    private void onLaunchStateChanged(
            LaunchService.LaunchState state
    ) {

        UiExecutor.shared().dispatchToUi(
                this::updateCards
        );
    }

    private void updateCards() {

        LaunchService.LaunchState state =
                launchService.getState();

        Instance runningInstance =
                launchService.getRunningInstance();

        for (
                javafx.scene.Node node
                : instanceList.getChildren()
        ) {

            if (!(node instanceof InstanceCard card)) {
                continue;
            }

            Instance cardInstance =
                    card.getInstance();

            boolean isThisInstance =
                    runningInstance != null
                            && cardInstance != null
                            && runningInstance
                            .getId()
                            .equals(
                                    cardInstance.getId()
                            );

            card.setLaunchState(
                    state,
                    isThisInstance
            );
        }
    }

    // =============================================================
    // EMPTY STATE
    // =============================================================

    private VBox createEmptyState() {

        Label icon =
                new Label(
                        "Settings"
                );

        icon.getStyleClass().add(
                "instance-empty-icon"
        );

        Label title =
                new Label(
                        Localization.text("instances.emptyTitle")
                );

        title.getStyleClass().add(
                "instance-empty-title"
        );

        Label description =
                new Label(
                        Localization.text("instances.emptyDescription")
                );

        description.getStyleClass().add(
                "instance-empty-description"
        );

        Button createButton = new Button(Localization.text("common.createInstance"));
        createButton.getStyleClass().add("primary-button");
        createButton.setAccessibleText(Localization.text("instances.createFirstAccessible"));
        createButton.setOnAction(event -> onCreateInstance.run());

        VBox empty =
                new VBox(
                        12,
                        icon,
                        title,
                        description,
                        createButton
                );

        empty.setAlignment(
                Pos.CENTER
        );

        empty.setPadding(
                new Insets(
                        70
                )
        );

        empty.getStyleClass().add(
                "instance-empty"
        );

        return empty;
    }


    // =============================================================
    // LAUNCH
    // =============================================================

    private void launch(
            Instance instance
    ) {

        LaunchService.LaunchState state =
                launchService.getState(instance);

        if (state == LaunchService.LaunchState.RUNNING) {
            launchService.close(instance);
            return;
        }

        if (state == LaunchService.LaunchState.PREPARING
                || state == LaunchService.LaunchState.STARTING
                || state == LaunchService.LaunchState.CLOSING) {
            return;
        }

        statusLabel.setText(
                Localization.text("instances.launchingPrefix")
                        + instance.getName()
                        + "..."
        );

        UiExecutor.shared().execute(() -> {

                    try {

                        launchService.launch(
                                instance
                        );

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        UiExecutor.shared().dispatchToUi(() ->
                                statusLabel.setText(
                                        ex.getMessage() != null
                                                ? ex.getMessage()
                                                : "Failed to launch Minecraft."
                                )
                        );
                    }
        });



    }
}