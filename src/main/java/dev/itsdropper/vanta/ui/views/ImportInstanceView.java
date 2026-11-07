package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
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
import javafx.stage.Window;

import dev.itsdropper.vanta.launcher.instance.MrpackInstaller;
import dev.itsdropper.vanta.launcher.model.Instance;

import java.io.File;

public class ImportInstanceView extends VBox {


    private final Runnable onBack;
    private final Runnable onImported;

    private final Button importButton;
    private final Button backButton;
    private final Label statusLabel;
    private final ProgressIndicator progress;

    public ImportInstanceView(
            Runnable onBack,
            Runnable onImported
    ) {

        this.onBack =
                onBack;

        this.onImported =
                onImported;

        // =========================================================
        // PAGE
        // =========================================================

        getStyleClass().addAll(
                "page",
                "vanta-consistent-page"
        );

        dev.itsdropper.vanta.ui.ResponsiveLayout.bindPagePadding(this);

        setSpacing(
                24
        );

        // =========================================================
        // HEADER
        // =========================================================

        Label title =
                new Label(
                        Localization.text("importInstance.title")
                );

        title.getStyleClass().add(
                "page-title"
        );

        Label subtitle =
                new Label(
                        Localization.text("importInstance.subtitle")
                );

        subtitle.getStyleClass().add(
                "page-subtitle"
        );

        VBox header =
                new VBox(
                        6,
                        title,
                        subtitle
                );

        // =========================================================
        // PANEL
        // =========================================================

        VBox panel =
                new VBox(
                        18
                );

        panel.getStyleClass().add(
                "create-instance-panel"
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

        panelTitle.getStyleClass().add(
                "card-title"
        );

        Label description =
                new Label(
                        Localization.text("importInstance.description")
                );

        description.setWrapText(
                true
        );

        description.getStyleClass().add(
                "create-status"
        );

        // =========================================================
        // IMPORT
        // =========================================================

        importButton =
                new Button(
                        Localization.text("importInstance.choose")
                );

        importButton.getStyleClass().add(
                "primary-button"
        );
        importButton.setAccessibleText(Localization.text("importInstance.chooseAccessible"));
        importButton.setTooltip(new javafx.scene.control.Tooltip("Select a readable .mrpack file"));

        importButton.setPrefHeight(
                46
        );

        importButton.setPrefWidth(
                180
        );

        importButton.setOnAction(
                event ->
                        chooseFile()
        );

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

        // =========================================================
        // STATUS
        // =========================================================

        statusLabel =
                new Label();

        statusLabel.getStyleClass().add(
                "create-status"
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

        FlowPane actions = new FlowPane(12, 8, backButton, importButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMaxWidth(Double.MAX_VALUE);

        // =========================================================
        // BUILD
        // =========================================================

        panel.getChildren().addAll(
                panelTitle,
                description,
                progressRow,
                statusLabel,
                actions
        );

        getChildren().addAll(
                header,
                panel
        );
    }

// =============================================================
// CHOOSE FILE
// =============================================================

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
