package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import org.example.launcher.instance.MrpackInstaller;
import org.example.launcher.model.Instance;

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

        getStyleClass().add(
                "page"
        );

        setPadding(
                new Insets(36)
        );

        setSpacing(
                24
        );

        // =========================================================
        // HEADER
        // =========================================================

        Label title =
                new Label(
                        "Import modpack"
                );

        title.getStyleClass().add(
                "page-title"
        );

        Label subtitle =
                new Label(
                        "Import a Modrinth .mrpack into a new Vanta instance."
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
                        "MODRINTH MODPACK"
                );

        panelTitle.getStyleClass().add(
                "card-title"
        );

        Label description =
                new Label(
                        "Choose a .mrpack file. Vanta will create a new instance and install the modpack contents automatically."
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
                        "CHOOSE .MRPACK"
                );

        importButton.getStyleClass().add(
                "primary-button"
        );

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

        // =========================================================
        // ACTIONS
        // =========================================================

        backButton =
                new Button(
                        "CANCEL"
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

        HBox actions =
                new HBox(
                        12,
                        backButton,
                        importButton
                );

        actions.setAlignment(
                Pos.CENTER_RIGHT
        );

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
                "Import Modrinth Modpack"
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
                "Importing "
                        + file.getName()
                        + "..."
        );

        Thread thread =
                new Thread(() -> {

                    try {

                        Instance instance =
                                MrpackInstaller.importMrpack(
                                        file.toPath()
                                );

                        Platform.runLater(() -> {

                            statusLabel.setText(
                                    "Imported "
                                            + instance.getName()
                                            + " successfully."
                            );

                            onImported.run();
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() -> {

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

        thread.setDaemon(
                true
        );

        thread.start();
    }


}
