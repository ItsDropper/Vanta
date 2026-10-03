package org.example.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import org.example.launcher.instance.MrpackExporter;
import org.example.launcher.model.Instance;
import org.example.launcher.service.LaunchService;
import org.example.launcher.service.InstanceUsageManager;
import org.example.ui.LauncherSettings;

import java.io.File;

public class InstanceCard extends StackPane {

    private final Label nameLabel;
    private final Label versionLabel;
    private final Label loaderLabel;
    private final Label playtimeLabel;
    private final Label statusLabel;
    private final Canvas presetIconCanvas;
    private final ImageView customIconView;

    private final Button playButton;
    private final Button settingsButton;
    private final Button menuButton;

    private Instance instance;

    public InstanceCard(
            Instance instance,
            Runnable onPlay,
            Runnable onSettings,
            Runnable onDelete,
            Runnable onRename,
            Runnable onDuplicate,
            Runnable onIcon
    ) {

        this.instance = instance;

        setMaxWidth(
                Double.MAX_VALUE
        );

        getStyleClass().add(
                "instance-card"
        );

        // ---------------------------------------------------------
        // MAIN CONTENT
        // ---------------------------------------------------------

        HBox content =
                new HBox();

        content.setSpacing(
                18
        );

        content.setPadding(
                new Insets(
                        18,
                        20,
                        18,
                        20
                )
        );

        content.setAlignment(
                Pos.CENTER_LEFT
        );

        // ---------------------------------------------------------
        // ICON
        // ---------------------------------------------------------

        presetIconCanvas = createIconGraphic(instance.getIcon(), 30);
        customIconView = new ImageView();
        customIconView.setFitWidth(28);
        customIconView.setFitHeight(28);
        customIconView.setPreserveRatio(true);

        StackPane icon = new StackPane(presetIconCanvas, customIconView);
        icon.setAlignment(Pos.CENTER);

        VBox iconBox = new VBox(icon);

        iconBox.setAlignment(
                Pos.CENTER
        );

        iconBox.getStyleClass().add(
                "instance-icon-box"
        );

        // ---------------------------------------------------------
        // INFORMATION
        // ---------------------------------------------------------

        nameLabel =
                new Label();

        nameLabel.getStyleClass().add(
                "instance-name"
        );

        versionLabel =
                new Label();

        versionLabel.getStyleClass().add(
                "instance-version"
        );

        loaderLabel =
                new Label();

        loaderLabel.getStyleClass().add(
                "instance-loader"
        );

        playtimeLabel =
                new Label();

        playtimeLabel.getStyleClass().add(
                "instance-playtime"
        );

        HBox metadata =
                new HBox(
                        8,
                        versionLabel,
                        loaderLabel,
                        playtimeLabel
                );

        metadata.setAlignment(
                Pos.CENTER_LEFT
        );

        VBox information =
                new VBox(
                        6,
                        nameLabel,
                        metadata
                );

        information.setAlignment(
                Pos.CENTER_LEFT
        );

        HBox.setHgrow(
                information,
                Priority.ALWAYS
        );

        // ---------------------------------------------------------
        // STATUS
        // ---------------------------------------------------------

        statusLabel =
                new Label(
                        "● READY"
                );

        statusLabel.getStyleClass().add(
                "instance-ready"
        );

        // ---------------------------------------------------------
        // PLAY BUTTON
        // ---------------------------------------------------------

        playButton =
                new Button(
                        "PLAY"
                );

        playButton.getStyleClass().add(
                "instance-play-button"
        );

        playButton.setMinWidth(
                90
        );

        playButton.setPrefWidth(
                90
        );

        playButton.setMaxWidth(
                90
        );

        playButton.setMinHeight(
                34
        );

        playButton.setPrefHeight(
                34
        );

        playButton.setMaxHeight(
                34
        );

        playButton.setOnAction(
                event -> {

                    event.consume();

                    onPlay.run();
                }
        );

        // ---------------------------------------------------------
        // SETTINGS
        // ---------------------------------------------------------

        settingsButton =
                new Button(
                        "OPTIONS"
                );

        settingsButton.getStyleClass().add(
                "instance-settings-button"
        );

        settingsButton.setMinWidth(
                82
        );

        settingsButton.setPrefWidth(
                82
        );

        settingsButton.setMaxWidth(
                82
        );

        settingsButton.setMinHeight(
                34
        );

        settingsButton.setPrefHeight(
                34
        );

        settingsButton.setMaxHeight(
                34
        );

        settingsButton.setOnAction(
                event -> {

                    event.consume();

                    onSettings.run();
                }
        );

        // ---------------------------------------------------------
        // RIGHT SIDE
        // ---------------------------------------------------------

        HBox right =
                new HBox(
                        10,
                        statusLabel,
                        settingsButton,
                        playButton
                );

        right.setAlignment(
                Pos.CENTER_RIGHT
        );

        /*
         * Reserve space for the vertical menu button.
         */
        right.setPadding(
                new Insets(
                        0,
                        42,
                        0,
                        0
                )
        );

        // ---------------------------------------------------------
        // CONTENT
        // ---------------------------------------------------------

        content.getChildren().addAll(
                iconBox,
                information,
                right
        );

        StackPane.setAlignment(
                content,
                Pos.CENTER
        );

        // ---------------------------------------------------------
        // MENU BUTTON
        // ---------------------------------------------------------

        menuButton =
                new Button(
                        "⋮"
                );

        menuButton.getStyleClass().add(
                "instance-menu-button"
        );

        menuButton.setFocusTraversable(
                false
        );

        menuButton.setMinWidth(
                28
        );

        menuButton.setPrefWidth(
                28
        );

        menuButton.setMaxWidth(
                28
        );

        menuButton.setMinHeight(
                28
        );

        menuButton.setPrefHeight(
                28
        );

        menuButton.setMaxHeight(
                28
        );

        menuButton.setOnAction(
                event -> {

                    event.consume();

                    showMenu(
                            menuButton,
                            onDelete,
                            onRename,
                            onDuplicate,
                            onIcon
                    );
                }
        );

        StackPane.setAlignment(
                menuButton,
                Pos.TOP_RIGHT
        );

        StackPane.setMargin(
                menuButton,
                new Insets(
                        8,
                        8,
                        0,
                        0
                )
        );

        // ---------------------------------------------------------
        // BUILD
        // ---------------------------------------------------------

        getChildren().addAll(
                content,
                menuButton
        );

        update();
        updateIcon();
    }

    // =============================================================
    // MENU
    // =============================================================

    private void showMenu(
            Button source,
            Runnable onDelete,
            Runnable onRename,
            Runnable onDuplicate,
            Runnable onIcon
    ) {

        MenuItem renameItem = new MenuItem("Rename instance");
        MenuItem duplicateItem = new MenuItem("Duplicate instance");
        MenuItem iconItem = new MenuItem("Change icon");

        MenuItem exportItem =
                new MenuItem(
                        "Export instance"
                );

        MenuItem deleteItem =
                new MenuItem(
                        "Delete instance"
                );

        deleteItem.getStyleClass().add(
                "instance-delete-menu-item"
        );

        renameItem.setOnAction(event -> onRename.run());
        duplicateItem.setOnAction(event -> onDuplicate.run());
        iconItem.setOnAction(event -> onIcon.run());

        exportItem.setOnAction(
                event ->
                        exportInstance()
        );

        deleteItem.setOnAction(event -> {
            if (LauncherSettings.isConfirmInstanceDeletionEnabled()) confirmDelete(onDelete);
            else onDelete.run();
        });

        ContextMenu menu =
                new ContextMenu(
                        renameItem,
                        duplicateItem,
                        iconItem,
                        exportItem,
                        deleteItem
                );

        menu.show(
                source,
                javafx.geometry.Side.BOTTOM,
                0,
                0
        );
    }

    // =============================================================
    // DELETE
    // =============================================================

    private void confirmDelete(
            Runnable onDelete
    ) {

        Stage dialog =
                new Stage(
                        StageStyle.TRANSPARENT
                );

        dialog.initModality(
                Modality.APPLICATION_MODAL
        );

        Window owner =
                getScene() != null
                        ? getScene().getWindow()
                        : null;

        if (owner != null) {
            dialog.initOwner(owner);
        }

        // ---------------------------------------------------------
        // TITLE
        // ---------------------------------------------------------

        Label title =
                new Label(
                        "Delete instance?"
                );

        title.getStyleClass().add(
                "delete-dialog-title"
        );

        // ---------------------------------------------------------
        // MESSAGE
        // ---------------------------------------------------------

        Label message =
                new Label(
                        "Are you sure you want to delete \""
                                + instance.getName()
                                + "\"?\n\n"
                                + "This will permanently delete the "
                                + "instance and all of its files."
                );

        message.getStyleClass().add(
                "delete-dialog-message"
        );

        message.setWrapText(
                true
        );

        // ---------------------------------------------------------
        // CANCEL BUTTON
        // ---------------------------------------------------------

        Button cancelButton =
                new Button(
                        "Cancel"
                );

        cancelButton.getStyleClass().add(
                "delete-dialog-cancel"
        );

        cancelButton.setOnAction(
                event ->
                        dialog.close()
        );

        // ---------------------------------------------------------
        // DELETE BUTTON
        // ---------------------------------------------------------

        Button deleteButton =
                new Button(
                        "Delete"
                );

        deleteButton.getStyleClass().add(
                "delete-dialog-delete"
        );

        deleteButton.setOnAction(
                event -> {

                    dialog.close();

                    onDelete.run();
                }
        );

        // ---------------------------------------------------------
        // BUTTONS
        // ---------------------------------------------------------

        HBox buttons =
                new HBox(
                        8,
                        cancelButton,
                        deleteButton
                );

        buttons.setAlignment(
                Pos.CENTER_RIGHT
        );

        // ---------------------------------------------------------
        // DIALOG CONTENT
        // ---------------------------------------------------------

        VBox dialogContent =
                new VBox(
                        10,
                        title,
                        message,
                        new Region(),
                        buttons
                );

        dialogContent.setAlignment(
                Pos.TOP_LEFT
        );

        dialogContent.getStyleClass().add(
                "delete-dialog"
        );

        // ---------------------------------------------------------
        // TRANSPARENT ROOT
        // ---------------------------------------------------------

        StackPane dialogRoot =
                new StackPane();

        dialogRoot.setStyle(
                "-fx-background-color: transparent;"
        );

        dialogRoot.getChildren().add(
                dialogContent
        );

        Scene scene =
                new Scene(
                        dialogRoot,
                        380,
                        190
                );

        scene.setFill(
                Color.TRANSPARENT
        );

        // ---------------------------------------------------------
        // STYLESHEETS
        // ---------------------------------------------------------

        scene.getStylesheets().add(
                getClass()
                        .getResource(
                                "/css/global.css"
                        )
                        .toExternalForm()
        );

        scene.getStylesheets().add(
                getClass()
                        .getResource(
                                "/css/typography.css"
                        )
                        .toExternalForm()
        );

        scene.getStylesheets().add(
                getClass()
                        .getResource(
                                "/css/buttons.css"
                        )
                        .toExternalForm()
        );

        scene.getStylesheets().add(
                getClass()
                        .getResource(
                                "/css/instances.css"
                        )
                        .toExternalForm()
        );

        dialog.setScene(
                scene
        );

        dialog.setResizable(
                false
        );

        dialog.showAndWait();
    }

    // =============================================================
    // EXPORT
    // =============================================================

    private void exportInstance() {

        FileChooser chooser =
                new FileChooser();

        chooser.setTitle(
                "Export Minecraft Instance"
        );

        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter(
                        "Modrinth Modpack (*.mrpack)",
                        "*.mrpack"
                )
        );

        chooser.setInitialFileName(
                sanitizeFileName(
                        instance.getName()
                ) + ".mrpack"
        );

        Window window =
                getScene() != null
                        ? getScene().getWindow()
                        : null;

        File selected =
                chooser.showSaveDialog(
                        window
                );

        if (selected == null) {
            return;
        }

        String fileName =
                selected.getName();

        if (!fileName
                .toLowerCase()
                .endsWith(".mrpack")) {

            selected =
                    new File(
                            selected.getParentFile(),
                            fileName + ".mrpack"
                    );
        }

        File finalSelected =
                selected;

        menuButton.setDisable(
                true
        );

        Thread exportThread =
                new Thread(
                        () -> {

                            try {

                                MrpackExporter.exportMrpack(
                                        instance,
                                        finalSelected.toPath()
                                );

                                Platform.runLater(() -> {

                                    menuButton.setDisable(
                                            false
                                    );

                                    showExportSuccess(
                                            finalSelected
                                    );
                                });

                            } catch (Exception e) {

                                e.printStackTrace();

                                Platform.runLater(() -> {

                                    menuButton.setDisable(
                                            false
                                    );

                                    showExportError(
                                            e
                                    );
                                });
                            }
                        }
                );

        exportThread.setName(
                "Vanta-Mrpack-Export"
        );

        exportThread.setDaemon(
                true
        );

        exportThread.start();
    }

    // =============================================================
    // EXPORT SUCCESS
    // =============================================================

    private void showExportSuccess(
            File file
    ) {

        Alert alert =
                new Alert(
                        Alert.AlertType.INFORMATION
                );

        alert.setTitle(
                "Export Complete"
        );

        alert.setHeaderText(
                "Instance exported successfully"
        );

        alert.setContentText(
                "Saved to:\n"
                        + file.getAbsolutePath()
        );

        alert.showAndWait();
    }

    // =============================================================
    // EXPORT ERROR
    // =============================================================

    private void showExportError(
            Exception exception
    ) {

        Alert alert =
                new Alert(
                        Alert.AlertType.ERROR
                );

        alert.setTitle(
                "Export Failed"
        );

        alert.setHeaderText(
                "Could not export instance"
        );

        String message =
                exception.getMessage();

        if (message == null
                || message.isBlank()) {

            message =
                    exception.getClass()
                            .getSimpleName();
        }

        alert.setContentText(
                message
        );

        alert.showAndWait();
    }

    // =============================================================
    // FILE NAME
    // =============================================================

    private String sanitizeFileName(
            String name
    ) {

        if (name == null
                || name.isBlank()) {

            return "Vanta-Instance";
        }

        return name
                .replaceAll(
                        "[\\\\/:*?\"<>|]",
                        "_"
                )
                .trim();
    }

    // =============================================================
    // UPDATE
    // =============================================================

    private void updateIcon() {
        String icon = instance.getIcon();
        boolean custom = "CUSTOM".equalsIgnoreCase(icon);
        presetIconCanvas.setVisible(!custom);
        presetIconCanvas.setManaged(!custom);
        customIconView.setVisible(custom);
        customIconView.setManaged(custom);
        if (custom) {
            File file = instance.getDirectory().resolve("icon.png").toFile();
            if (file.isFile()) {
                customIconView.setImage(new Image(file.toURI().toString(), 28, 28, true, true));
            } else {
                customIconView.setImage(null);
                customIconView.setVisible(false);
                presetIconCanvas.setVisible(true);
                presetIconCanvas.setManaged(true);
            }
        } else {
            drawIconGraphic(presetIconCanvas, icon);
        }
    }

    private void updateIconFromDisk() {
        updateIcon();
    }

    private void update() {

        nameLabel.setText(
                instance.getName()
        );

        versionLabel.setText(
                "Minecraft "
                        + instance.getMinecraftVersion()
        );

        loaderLabel.setText(
                instance.getDisplayLoader()
        );

        playtimeLabel.setText(
                InstanceUsageManager.formatPlaytime(
                        InstanceUsageManager.getPlaytimeSeconds(instance)
                ) + " played"
        );
        boolean showPlaytime = LauncherSettings.isShowPlaytimeEnabled();
        playtimeLabel.setVisible(showPlaytime);
        playtimeLabel.setManaged(showPlaytime);
    }

    public static Canvas createIconGraphic(String icon, double size) {
        Canvas canvas = new Canvas(size, size);
        drawIconGraphic(canvas, icon);
        return canvas;
    }

    private static void drawIconGraphic(Canvas canvas, String value) {
        double s = canvas.getWidth() / 30.0;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        g.setStroke(javafx.scene.paint.Color.web("#dbe7ff"));
        g.setFill(javafx.scene.paint.Color.web("#dbe7ff"));
        g.setLineWidth(2.2 * s);
        String icon = value == null ? "SHIELD" : value.toUpperCase();

        switch (icon) {
            case "PACKAGE" -> {
                g.strokeRect(6*s,8*s,18*s,15*s);
                g.strokeLine(6*s,8*s,15*s,4*s);
                g.strokeLine(15*s,4*s,24*s,8*s);
                g.strokeLine(15*s,4*s,15*s,16*s);
                g.strokeLine(6*s,8*s,15*s,16*s);
                g.strokeLine(24*s,8*s,15*s,16*s);
            }
            case "PICKAXE" -> {
                g.strokeLine(7*s,22*s,22*s,7*s);
                g.strokeLine(7*s,8*s,22*s,8*s);
                g.strokeLine(7*s,8*s,7*s,13*s);
                g.strokeLine(22*s,7*s,25*s,10*s);
            }
            case "STAR" -> {
                double[] x={15,18,24,19,21,15,9,11,6,12};
                double[] y={4,11,11,15,23,19,23,15,11,11};
                for(int i=0;i<x.length;i++){x[i]*=s;y[i]*=s;}
                g.fillPolygon(x,y,x.length);
            }
            case "FIRE" -> {
                g.beginPath(); g.moveTo(15*s,4*s); g.lineTo(21*s,13*s); g.lineTo(20*s,9*s);
                g.lineTo(25*s,17*s); g.quadraticCurveTo(25*s,25*s,15*s,26*s);
                g.quadraticCurveTo(5*s,25*s,7*s,16*s); g.quadraticCurveTo(8*s,11*s,12*s,8*s);
                g.lineTo(12*s,16*s); g.closePath(); g.fill();
            }
            case "WORLD" -> {
                g.strokeOval(5*s,5*s,20*s,20*s);
                g.strokeOval(11*s,5*s,8*s,20*s);
                g.strokeLine(5*s,15*s,25*s,15*s);
            }
            case "CROWN" -> {
                double[] x={5,7,11,15,19,23,25,23,7};
                double[] y={8,21,18,21,18,8,21,24,24};
                for(int i=0;i<x.length;i++){x[i]*=s;y[i]*=s;}
                g.fillPolygon(x,y,x.length);
            }
            case "DIAMOND" -> {
                double[] x={15,5,15,25};
                double[] y={5,15,25,15};
                for(int i=0;i<x.length;i++){x[i]*=s;y[i]*=s;}
                g.strokePolygon(x,y,x.length);
                g.strokeLine(5*s,15*s,25*s,15*s);
                g.strokeLine(15*s,5*s,15*s,25*s);
            }
            default -> {
                g.beginPath(); g.moveTo(15*s,4*s); g.lineTo(24*s,8*s); g.lineTo(22*s,20*s);
                g.lineTo(15*s,26*s); g.lineTo(8*s,20*s); g.lineTo(6*s,8*s); g.closePath(); g.stroke();
            }
        }
    }

    // =============================================================
    // LAUNCH STATE
    // =============================================================

    public void setLaunchState(
            LaunchService.LaunchState state,
            boolean isThisInstance
    ) {

        Platform.runLater(() -> {

            switch (state) {

                case IDLE -> {

                    resetToReady();

                    statusLabel.setText(
                            "● READY"
                    );
                }

                case PREPARING -> {

                    if (!isThisInstance) {
                        resetToReady();
                        return;
                    }

                    playButton.setDisable(
                            true
                    );

                    playButton.setText(
                            "PREPARING..."
                    );

                    setPlayingStyle(
                            false
                    );

                    statusLabel.setText(
                            "● PREPARING"
                    );
                }

                case STARTING -> {

                    if (!isThisInstance) {
                        resetToReady();
                        return;
                    }

                    playButton.setDisable(
                            true
                    );

                    playButton.setText(
                            "STARTING..."
                    );

                    setPlayingStyle(
                            false
                    );

                    statusLabel.setText(
                            "● STARTING"
                    );
                }

                case RUNNING -> {

                    if (!isThisInstance) {
                        resetToReady();
                        return;
                    }

                    playButton.setDisable(
                            false
                    );

                    playButton.setText(
                            "CLOSE"
                    );

                    setPlayingStyle(
                            true
                    );

                    statusLabel.setText(
                            "● RUNNING"
                    );
                }

                case CLOSING -> {

                    if (!isThisInstance) {
                        resetToReady();
                        return;
                    }

                    playButton.setDisable(
                            true
                    );

                    playButton.setText(
                            "CLOSING..."
                    );

                    setPlayingStyle(
                            true
                    );

                    statusLabel.setText(
                            "● CLOSING"
                    );
                }

                case ERROR -> {

                    if (!isThisInstance) {
                        resetToReady();
                        return;
                    }

                    playButton.setDisable(
                            false
                    );

                    playButton.setText(
                            "PLAY"
                    );

                    setPlayingStyle(
                            false
                    );

                    statusLabel.setText(
                            "● ERROR"
                    );
                }
            }
        });
    }

    private void resetToReady() {

        playButton.setDisable(
                false
        );

        playButton.setText(
                "PLAY"
        );

        setPlayingStyle(
                false
        );

        statusLabel.setText(
                "● READY"
        );
    }

    private void setPlayingStyle(
            boolean playing
    ) {

        playButton.getStyleClass().remove(
                "instance-playing-button"
        );

        if (playing) {

            playButton.getStyleClass().add(
                    "instance-playing-button"
            );
        }
    }

    // =============================================================
    // GET INSTANCE
    // =============================================================

    public Instance getInstance() {

        return instance;
    }

    // =============================================================
    // SET INSTANCE
    // =============================================================

    public void setInstance(
            Instance instance
    ) {

        if (instance == null) {
            return;
        }

        this.instance =
                instance;

        update();
    }

}
