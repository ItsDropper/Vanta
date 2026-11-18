package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.components.PageHeader;
import dev.itsdropper.vanta.ui.async.UiExecutor;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;
import java.nio.file.Path;
import java.util.function.Consumer;

import dev.itsdropper.vanta.launcher.instance.MrpackInstaller;
import dev.itsdropper.vanta.launcher.instance.PrismLauncherImporter;
import dev.itsdropper.vanta.launcher.instance.ModrinthAppImporter;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.File;

public class ImportInstanceView extends VBox {


    private final Runnable onBack;
    private final Runnable onImported;
    private final Consumer<String> onPrismImported;

    private final Button importButton;
    private final Button prismImportButton;
    private final Button modrinthAppImportButton;
    private final Button backButton;
    private final Label statusLabel;
    private final ProgressIndicator progress;

    public ImportInstanceView(Runnable onBack, Runnable onImported) {
        this(onBack, onImported, null);
    }

    public ImportInstanceView(
            Runnable onBack,
            Runnable onImported,
            Consumer<String> onPrismImported
    ) {
        this.onBack = onBack;
        this.onImported = onImported;
        this.onPrismImported = onPrismImported;

        // =========================================================
        // PAGE
        // =========================================================

        getStyleClass().addAll(
                "page",
                "vanta-consistent-page",
                "import-instance-page"
        );

        dev.itsdropper.vanta.ui.ResponsiveLayout.bindPagePadding(this);

        setSpacing(
                24
        );

        // =========================================================
        // HEADER
        // =========================================================

        PageHeader header = new PageHeader(
                Localization.text("importInstance.title"),
                Localization.text("importInstance.subtitle"),
                "page-title",
                "page-subtitle"
        );

        // =========================================================
        // PANEL
        // =========================================================

        VBox panel =
                new VBox(
                        18
                );

        panel.getStyleClass().addAll(
                "create-instance-panel", "import-instance-panel"
        );

        panel.setPadding(
                new Insets(28)
        );

        panel.setMaxWidth(
                680
        );

        Label panelTitle =
                new Label(
                        Localization.text("importInstance.eyebrow")
                );

        panelTitle.getStyleClass().addAll(
                "card-title", "import-panel-eyebrow"
        );

        Label description =
                new Label(
                        Localization.text("importInstance.description")
                );

        description.setWrapText(
                true
        );

        description.getStyleClass().addAll(
                "create-status", "import-panel-description"
        );

        // =========================================================
        // IMPORT
        // =========================================================

        importButton =
                new Button(
                        Localization.text("importInstance.choose")
                );

        importButton.getStyleClass().addAll(
                "primary-button", "import-primary-action"
        );
        importButton.setAccessibleText(Localization.text("importInstance.chooseAccessible"));
        importButton.setTooltip(new javafx.scene.control.Tooltip("Select a readable .mrpack file"));

        importButton.setPrefHeight(
                46
        );

        importButton.setPrefWidth(
                180
        );

        importButton.setOnAction(event -> chooseFile());

        prismImportButton = new Button(Localization.text("prismImport.button"));
        prismImportButton.getStyleClass().addAll("secondary-button", "import-prism-action");
        prismImportButton.setAccessibleText(Localization.text("prismImport.button"));
        prismImportButton.setPrefHeight(46);
        prismImportButton.setPrefWidth(220);
        prismImportButton.setTooltip(new javafx.scene.control.Tooltip(
                Localization.text("prismImport.description")));
        prismImportButton.setOnAction(event -> choosePrismDirectory());

        modrinthAppImportButton = new Button(Localization.text("modrinthAppImport.button"));
        modrinthAppImportButton.getStyleClass().addAll("secondary-button", "import-modrinth-app-action");
        modrinthAppImportButton.setAccessibleText(Localization.text("modrinthAppImport.button"));
        modrinthAppImportButton.setPrefHeight(46);
        modrinthAppImportButton.setPrefWidth(220);
        modrinthAppImportButton.setTooltip(new javafx.scene.control.Tooltip(
                Localization.text("modrinthAppImport.description")));
        modrinthAppImportButton.setOnAction(event -> chooseModrinthAppDirectory());

        // =========================================================
        // PROGRESS
        // =========================================================

        progress =
                new ProgressIndicator();

        progress.setVisible(
                false
        );

        progress.setManaged(
                false
        );

        progress.setPrefSize(
                28,
                28
        );

        HBox progressRow =
                new HBox(
                        10,
                        progress
                );

        progressRow.setAlignment(
                Pos.CENTER_LEFT
        );
        progressRow.getStyleClass().add("import-progress-row");

        // =========================================================
        // STATUS
        // =========================================================

        statusLabel =
                new Label();

        statusLabel.getStyleClass().addAll(
                "create-status", "import-status"
        );
        statusLabel.setWrapText(true);
        statusLabel.setAccessibleText(Localization.text("importInstance.statusAccessible"));

        // =========================================================
        // ACTIONS
        // =========================================================

        backButton =
                new Button(
                        Localization.text("common.cancel")
                );

        backButton.getStyleClass().add(
                "secondary-button"
        );

        backButton.setPrefHeight(
                46
        );

        backButton.setPrefWidth(
                110
        );

        backButton.setOnAction(
                event ->
                        onBack.run()
        );

        FlowPane actions = new FlowPane(12, 8, backButton, prismImportButton, modrinthAppImportButton, importButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMaxWidth(Double.MAX_VALUE);

        // =========================================================
        // BUILD
        // =========================================================

        Label formatBadge = new Label(Localization.text("importInstance.formats"));
        formatBadge.getStyleClass().add("import-format-badge");
        Label dropGlyph = new Label("↥");
        dropGlyph.getStyleClass().add("import-drop-glyph");
        VBox intro = new VBox(10, dropGlyph, panelTitle, description, formatBadge);
        intro.getStyleClass().add("import-intro");

        panel.getChildren().addAll(intro, progressRow, statusLabel, actions);

        getChildren().addAll(
                header,
                panel
        );
    }

// =============================================================
// CHOOSE FILE
// =============================================================

    private void choosePrismDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(Localization.text("prismImport.chooser"));
        Path detected = PrismLauncherImporter.findDefaultPrismRoot();
        if (detected != null && java.nio.file.Files.isDirectory(detected)) {
            chooser.setInitialDirectory(detected.toFile());
        }

        Window window = getScene() == null ? null : getScene().getWindow();
        File selected = chooser.showDialog(window);
        if (selected == null) return;
        Path root = selected.toPath();

        if (!PrismLauncherImporter.looksLikePrismRoot(root)) {
            statusLabel.setText(Localization.text("prismImport.invalid"));
            return;
        }

        setImportBusy(true);
        statusLabel.setText(Localization.text("prismImport.scanning"));
        UiExecutor.shared().execute(() -> {
            try {
                PrismLauncherImporter.ImportResult result = PrismLauncherImporter.importAll(
                        root,
                        message -> UiExecutor.shared().dispatchToUi(() -> statusLabel.setText(message))
                );
                UiExecutor.shared().dispatchToUi(() -> {
                    String summary = Localization.text("prismImport.complete")
                            + " " + result.importedInstances() + ".";
                    if (result.globalSettingsImported()) {
                        summary += " " + Localization.text("prismImport.settingsApplied");
                    }
                    if (!result.skippedInstances().isEmpty()) {
                        summary += "\n" + Localization.text("prismImport.skipped")
                                + "\n" + String.join("\n", result.skippedInstances());
                    }
                    statusLabel.setText(summary);
                    setImportBusy(false);
                    if (onPrismImported != null) onPrismImported.accept(summary);
                    else if (result.importedInstances() > 0) onImported.run();
                });
            } catch (Throwable failure) {
                UiExecutor.shared().dispatchToUi(() -> {
                    statusLabel.setText(Localization.text("prismImport.failed")
                            + " " + (failure.getMessage() == null
                            ? failure.getClass().getSimpleName() : failure.getMessage()));
                    setImportBusy(false);
                });
            }
        });
    }

    private void chooseModrinthAppDirectory() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle(Localization.text("modrinthAppImport.chooser"));
        Path detected = ModrinthAppImporter.findDefaultAppDirectory();
        if (detected != null && java.nio.file.Files.isDirectory(detected)) {
            chooser.setInitialDirectory(detected.toFile());
        }

        Window window = getScene() == null ? null : getScene().getWindow();
        File selected = chooser.showDialog(window);
        if (selected == null) return;
        Path root = selected.toPath();

        if (!dev.itsdropper.vanta.launcher.instance.ModrinthAppImporter.looksLikeAppDirectory(root)) {
            statusLabel.setText(Localization.text("modrinthAppImport.invalid"));
            return;
        }

        setImportBusy(true);
        statusLabel.setText(Localization.text("modrinthAppImport.scanning"));
        UiExecutor.shared().execute(() -> {
            try {
                var result = dev.itsdropper.vanta.launcher.instance.ModrinthAppImporter.importAll(
                        root,
                        message -> UiExecutor.shared().dispatchToUi(() -> statusLabel.setText(message))
                );
                UiExecutor.shared().dispatchToUi(() -> {
                    String summary = Localization.text("modrinthAppImport.complete")
                            + " " + result.importedInstances() + ".";
                    if (result.globalSettingsImported()) {
                        summary += " " + Localization.text("modrinthAppImport.settingsApplied");
                    }
                    if (!result.skippedInstances().isEmpty()) {
                        summary += "\\n" + Localization.text("modrinthAppImport.skipped")
                                + "\\n" + String.join("\\n", result.skippedInstances());
                    }
                    statusLabel.setText(summary);
                    setImportBusy(false);
                    if (onPrismImported != null) onPrismImported.accept(summary);
                    else if (result.importedInstances() > 0) onImported.run();
                });
            } catch (Throwable failure) {
                UiExecutor.shared().dispatchToUi(() -> {
                    statusLabel.setText(Localization.text("modrinthAppImport.failed")
                            + " " + (failure.getMessage() == null
                            ? failure.getClass().getSimpleName() : failure.getMessage()));
                    setImportBusy(false);
                });
            }
        });
    }

    private void setImportBusy(boolean busy) {
        importButton.setDisable(busy);
        prismImportButton.setDisable(busy);
        modrinthAppImportButton.setDisable(busy);
        backButton.setDisable(busy);
        progress.setVisible(busy);
        progress.setManaged(busy);
    }

    private void chooseFile() {

        FileChooser chooser =
                new FileChooser();

        chooser.setTitle(
                Localization.text("importInstance.action")
        );

        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Modrinth Modpacks (*.mrpack)",
                        "*.mrpack"
                )
        );

        Window window =
                getScene() != null
                        ? getScene().getWindow()
                        : null;

        File file =
                chooser.showOpenDialog(
                        window
                );

        if (file == null) {
            return;
        }

        importModpack(
                file
        );
    }

// =============================================================
// IMPORT
// =============================================================

    private void importModpack(
            File file
    ) {

        if (file == null || !file.isFile() || !file.canRead()
                || !file.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".mrpack")) {
            statusLabel.setText(Localization.text("importInstance.validation"));
            return;
        }

        importButton.setDisable(
                true
        );
        prismImportButton.setDisable(true);
        modrinthAppImportButton.setDisable(true);

        backButton.setDisable(
                true
        );

        progress.setVisible(
                true
        );

        progress.setManaged(
                true
        );

        statusLabel.setText(
                Localization.text("importInstance.importingPrefix")
                        + file.getName()
                        + "..."
        );

        UiExecutor.shared().execute(() -> {

                    try {

                        Instance instance =
                                MrpackInstaller.importMrpack(
                                        file.toPath()
                                );

                        UiExecutor.shared().dispatchToUi(() -> {

                            statusLabel.setText(
                                    Localization.text("importInstance.importedPrefix")
                                            + instance.getName()
                                            + " successfully."
                            );

                            onImported.run();
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        UiExecutor.shared().dispatchToUi(() -> {

                            importButton.setDisable(
                                    false
                            );
                            prismImportButton.setDisable(false);
                            modrinthAppImportButton.setDisable(false);

                            backButton.setDisable(
                                    false
                            );

                            progress.setVisible(
                                    false
                            );

                            progress.setManaged(
                                    false
                            );

                            statusLabel.setText(
                                    ex.getMessage() != null
                                            ? ex.getMessage()
                                            : "Failed to import modpack."
                            );
                        });
                    }
        });


    }


}
