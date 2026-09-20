package org.example.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.launcher.model.Instance;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class ScreenshotsView extends VBox {

    private final Instance instance;
    private final Runnable onBack;

    private final FlowPane screenshotGrid;
    private final Label statusLabel;

    public ScreenshotsView(
            Instance instance,
            Runnable onBack
    ) {

        this.instance =
                instance;

        this.onBack =
                onBack;

        getStyleClass().add(
                "page"
        );

        setPadding(
                new Insets(36)
        );

        setSpacing(
                20
        );

        // =========================================================
        // HEADER
        // =========================================================

        Button backButton =
                new Button(
                        "← BACK"
                );

        backButton.getStyleClass().add(
                "secondary-button"
        );

        backButton.setOnAction(
                event -> onBack.run()
        );

        Label title =
                new Label(
                        "Screenshots"
                );

        title.getStyleClass().add(
                "page-title"
        );

        Label subtitle =
                new Label(
                        instance.getName()
                                + " • "
                                + "Minecraft screenshots"
                );

        subtitle.getStyleClass().add(
                "page-subtitle"
        );

        VBox headerText =
                new VBox(
                        5,
                        title,
                        subtitle
                );

        VBox header =
                new VBox(
                        14,
                        backButton,
                        headerText
                );

        // =========================================================
        // ACTIONS
        // =========================================================

        Button openFolderButton =
                new Button(
                        "OPEN FOLDER"
                );

        openFolderButton.getStyleClass().add(
                "secondary-button"
        );

        openFolderButton.setOnAction(
                event -> openFolder()
        );

        Button refreshButton =
                new Button(
                        "REFRESH"
                );

        refreshButton.getStyleClass().add(
                "secondary-button"
        );

        refreshButton.setOnAction(
                event -> loadScreenshots()
        );

        HBox actions =
                new HBox(
                        10,
                        openFolderButton,
                        refreshButton
                );

        actions.setAlignment(
                Pos.CENTER_LEFT
        );

        // =========================================================
        // GRID
        // =========================================================

        screenshotGrid =
                new FlowPane();

        screenshotGrid.setHgap(
                16
        );

        screenshotGrid.setVgap(
                16
        );

        screenshotGrid.setPrefWrapLength(
                900
        );

        screenshotGrid.getStyleClass().add(
                "screenshot-grid"
        );

        ScrollPane scrollPane =
                new ScrollPane(
                        screenshotGrid
                );

        scrollPane.setFitToWidth(
                true
        );

        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        scrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        scrollPane.getStyleClass().add(
                "screenshots-scroll"
        );

        VBox.setVgrow(
                scrollPane,
                Priority.ALWAYS
        );

        // =========================================================
        // STATUS
        // =========================================================

        statusLabel =
                new Label();

        statusLabel.getStyleClass().add(
                "instances-status"
        );

        // =========================================================
        // BUILD
        // =========================================================

        getChildren().addAll(
                header,
                actions,
                scrollPane,
                statusLabel
        );

        loadScreenshots();
    }

    // =============================================================
    // LOAD
    // =============================================================

    private void loadScreenshots() {

        screenshotGrid
                .getChildren()
                .clear();

        Path directory =
                instance.getDirectory()
                        .resolve("screenshots");

        try {

            Files.createDirectories(
                    directory
            );

            List<Path> screenshots;

            try (var stream =
                         Files.list(
                                 directory
                         )) {

                screenshots =
                        stream
                                .filter(
                                        path ->
                                                !Files.isDirectory(
                                                        path
                                                )
                                )
                                .filter(
                                        this::isScreenshot
                                )
                                .sorted(
                                        Comparator.comparingLong(
                                                        this::getLastModified
                                                )
                                                .reversed()
                                )
                                .toList();
            }

            if (screenshots.isEmpty()) {

                showEmptyState();

                statusLabel.setText(
                        "0 screenshots."
                );

                return;
            }

            for (Path screenshot :
                    screenshots) {

                screenshotGrid
                        .getChildren()
                        .add(
                                createScreenshotCard(
                                        screenshot
                                )
                        );
            }

            statusLabel.setText(
                    screenshots.size()
                            + " "
                            + (
                            screenshots.size() == 1
                                    ? "screenshot"
                                    : "screenshots"
                    )
                            + "."
            );

        } catch (IOException e) {

            e.printStackTrace();

            statusLabel.setText(
                    "Failed to load screenshots."
            );
        }
    }

    private boolean isScreenshot(
            Path path
    ) {

        String name =
                path.getFileName()
                        .toString()
                        .toLowerCase();

        return name.endsWith(".png")
                || name.endsWith(".jpg")
                || name.endsWith(".jpeg");
    }

    // =============================================================
    // CARD
    // =============================================================

    private VBox createScreenshotCard(
            Path file
    ) {

        Image image =
                new Image(
                        file.toUri().toString(),
                        260,
                        150,
                        true,
                        true,
                        true
                );

        ImageView imageView =
                new ImageView(
                        image
                );

        imageView.setFitWidth(
                260
        );

        imageView.setFitHeight(
                150
        );

        imageView.setPreserveRatio(
                true
        );

        imageView.setSmooth(
                true
        );

        StackPane preview =
                new StackPane(
                        imageView
                );

        preview.setPrefSize(
                260,
                150
        );

        preview.getStyleClass().add(
                "screenshot-preview"
        );

        Label name =
                new Label(
                        file.getFileName()
                                .toString()
                );

        name.getStyleClass().add(
                "screenshot-name"
        );

        name.setMaxWidth(
                260
        );

        VBox card =
                new VBox(
                        10,
                        preview,
                        name
                );

        card.setPadding(
                new Insets(10)
        );

        card.setPrefWidth(
                282
        );

        card.getStyleClass().add(
                "screenshot-card"
        );

        preview.setOnMouseClicked(
                event -> {

                    if (event.getButton()
                            == MouseButton.PRIMARY) {

                        openPreview(
                                file
                        );
                    }
                }
        );

        return card;
    }

    // =============================================================
    // PREVIEW
    // =============================================================

    private void openPreview(
            Path file
    ) {

        Stage stage =
                new Stage();

        stage.initStyle(
                javafx.stage.StageStyle.TRANSPARENT
        );

        Image image =
                new Image(
                        file.toUri().toString()
                );

        ImageView imageView =
                new ImageView(
                        image
                );

        imageView.setPreserveRatio(
                true
        );

        imageView.setSmooth(
                true
        );

        StackPane imageContainer =
                new StackPane(
                        imageView
                );

        imageContainer.setAlignment(
                Pos.CENTER
        );

        imageContainer.getStyleClass().add(
                "screenshot-preview-container"
        );

        ScrollPane imageScroll =
                new ScrollPane(
                        imageContainer
                );

        imageScroll.setPannable(
                true
        );

        imageScroll.setFitToWidth(
                true
        );

        imageScroll.setFitToHeight(
                true
        );

        imageScroll.getStyleClass().add(
                "screenshot-image-scroll"
        );

        // =========================================================
        // ZOOM
        // =========================================================

        final double[] zoom =
                {1.0};

        Runnable updateZoom =
                () -> {

                    imageView.setScaleX(
                            zoom[0]
                    );

                    imageView.setScaleY(
                            zoom[0]
                    );
                };

        Button zoomOut =
                new Button(
                        "−"
                );

        zoomOut.getStyleClass().add(
                "secondary-button"
        );

        zoomOut.setOnAction(
                event -> {

                    zoom[0] =
                            Math.max(
                                    0.1,
                                    zoom[0] - 0.1
                            );

                    updateZoom.run();
                }
        );

        Button zoomIn =
                new Button(
                        "+"
                );

        zoomIn.getStyleClass().add(
                "secondary-button"
        );

        zoomIn.setOnAction(
                event -> {

                    zoom[0] =
                            Math.min(
                                    5.0,
                                    zoom[0] + 0.1
                            );

                    updateZoom.run();
                }
        );

        Button resetZoom =
                new Button(
                        "100%"
                );

        resetZoom.getStyleClass().add(
                "secondary-button"
        );

        resetZoom.setOnAction(
                event -> {

                    zoom[0] =
                            1.0;

                    updateZoom.run();
                }
        );

        Button fitButton =
                new Button(
                        "FIT"
                );

        fitButton.getStyleClass().add(
                "secondary-button"
        );

        fitButton.setOnAction(
                event -> {

                    double availableWidth =
                            Math.max(
                                    100,
                                    imageScroll.getViewportBounds()
                                            .getWidth()
                            );

                    double availableHeight =
                            Math.max(
                                    100,
                                    imageScroll.getViewportBounds()
                                            .getHeight()
                            );

                    double imageWidth =
                            image.getWidth();

                    double imageHeight =
                            image.getHeight();

                    if (imageWidth <= 0
                            || imageHeight <= 0) {

                        return;
                    }

                    double scaleX =
                            availableWidth
                                    / imageWidth;

                    double scaleY =
                            availableHeight
                                    / imageHeight;

                    zoom[0] =
                            Math.min(
                                    1.0,
                                    Math.min(
                                            scaleX,
                                            scaleY
                                    )
                            );

                    updateZoom.run();
                }
        );

        Slider zoomSlider =
                new Slider(
                        0.1,
                        5.0,
                        1.0
                );

        zoomSlider.setPrefWidth(
                140
        );

        zoomSlider.valueProperty()
                .addListener(
                        (observable, oldValue, newValue) -> {

                            zoom[0] =
                                    newValue.doubleValue();

                            updateZoom.run();
                        }
                );

        imageScroll.addEventFilter(
                ScrollEvent.SCROLL,
                event -> {

                    if (event.isControlDown()) {

                        double amount =
                                event.getDeltaY() > 0
                                        ? 0.1
                                        : -0.1;

                        zoom[0] =
                                Math.max(
                                        0.1,
                                        Math.min(
                                                5.0,
                                                zoom[0] + amount
                                        )
                                );

                        zoomSlider.setValue(
                                zoom[0]
                        );

                        event.consume();
                    }
                }
        );

        // =========================================================
// CUSTOM TITLE BAR
// =========================================================

        SVGPath minimizeIcon =
                new SVGPath();

        minimizeIcon.setContent(
                "M 3 8 L 13 8"
        );

        SVGPath maximizeIcon =
                new SVGPath();

        maximizeIcon.setContent(
                "M 3 3 L 13 3 L 13 13 L 3 13 Z"
        );

        SVGPath restoreIcon =
                new SVGPath();

        restoreIcon.setContent(
                "M 5 5 L 13 5 L 13 13 L 5 13 Z " +
                        "M 3 3 L 3 11 L 5 11 L 5 5 " +
                        "L 11 5 L 11 3 Z"
        );

        SVGPath closeIcon =
                new SVGPath();

        closeIcon.setContent(
                "M 4 4 L 12 12 M 12 4 L 4 12"
        );

        Button minimizeButton =
                new Button();

        Button maximizeButton =
                new Button();

        Button closeWindowButton =
                new Button();

        minimizeButton.setGraphic(
                minimizeIcon
        );

        maximizeButton.setGraphic(
                maximizeIcon
        );

        closeWindowButton.setGraphic(
                closeIcon
        );

        minimizeButton.getStyleClass().add(
                "title-bar-button"
        );

        maximizeButton.getStyleClass().add(
                "title-bar-button"
        );

        closeWindowButton.getStyleClass().add(
                "title-bar-close"
        );

        minimizeButton.setOnAction(
                event ->
                        stage.setIconified(
                                true
                        )
        );

        maximizeButton.setOnAction(
                event -> {

                    stage.setMaximized(
                            !stage.isMaximized()
                    );

                    maximizeButton.setGraphic(
                            stage.isMaximized()
                                    ? restoreIcon
                                    : maximizeIcon
                    );
                }
        );

        closeWindowButton.setOnAction(
                event ->
                        stage.close()
        );

        Label fileName =
                new Label(
                        file.getFileName()
                                .toString()
                );

        fileName.getStyleClass().add(
                "screenshot-dialog-name"
        );

        Region titleSpacer =
                new Region();

        HBox.setHgrow(
                titleSpacer,
                Priority.ALWAYS
        );

        HBox windowButtons =
                new HBox(
                        minimizeButton,
                        maximizeButton,
                        closeWindowButton
                );

        windowButtons.setAlignment(
                Pos.CENTER_RIGHT
        );

        HBox titleBar =
                new HBox(
                        fileName,
                        titleSpacer,
                        windowButtons
                );

        titleBar.setAlignment(
                Pos.CENTER_LEFT
        );

        titleBar.setPadding(
                new Insets(
                        8,
                        10,
                        8,
                        16
                )
        );

        titleBar.getStyleClass().add(
                "title-bar"
        );

        // =========================================================
        // BOTTOM BAR
        // =========================================================

        Button copyButton =
                new Button(
                        "COPY"
                );

        copyButton.getStyleClass().add(
                "secondary-button"
        );

        copyButton.setOnAction(
                event ->
                        copyScreenshot(
                                image,
                                stage
                        )
        );

        Button saveButton =
                new Button(
                        "SAVE AS"
                );

        saveButton.getStyleClass().add(
                "secondary-button"
        );

        saveButton.setOnAction(
                event ->
                        saveScreenshot(
                                file,
                                stage
                        )
        );

        Button openButton =
                new Button(
                        "OPEN"
                );

        openButton.getStyleClass().add(
                "secondary-button"
        );

        openButton.setOnAction(
                event ->
                        openScreenshot(
                                file
                        )
        );

        Button deleteButton =
                new Button(
                        "DELETE"
                );

        deleteButton.getStyleClass().add(
                "danger-button"
        );

        deleteButton.setOnAction(
                event -> {

                    if (deleteScreenshot(file)) {

                        stage.close();

                        loadScreenshots();
                    }
                }
        );

        Button closeButton =
                new Button(
                        "CLOSE"
                );

        closeButton.getStyleClass().add(
                "primary-button"
        );

        closeButton.setOnAction(
                event ->
                        stage.close()
        );

        HBox zoomControls =
                new HBox(
                        6,
                        zoomOut,
                        zoomSlider,
                        zoomIn,
                        resetZoom,
                        fitButton
                );

        zoomControls.setAlignment(
                Pos.CENTER_LEFT
        );

        HBox bottomBar =
                new HBox(
                        10,
                        copyButton,
                        saveButton,
                        openButton,
                        deleteButton,
                        zoomControls,
                        closeButton
                );

        bottomBar.setAlignment(
                Pos.CENTER_RIGHT
        );

        bottomBar.setPadding(
                new Insets(
                        10,
                        14,
                        14,
                        14
                )
        );

        // =========================================================
        // ROOT
        // =========================================================

        BorderPane root =
                new BorderPane();

        root.setTop(
                titleBar
        );

        root.setCenter(
                imageScroll
        );

        root.setBottom(
                bottomBar
        );

        root.getStyleClass().add(
                "screenshot-dialog"
        );

        Scene scene =
                new Scene(
                        root,
                        1200,
                        800
                );

        scene.setFill(
                javafx.scene.paint.Color.TRANSPARENT
        );

        if (getScene() != null
                && !getScene().getStylesheets().isEmpty()) {

            scene.getStylesheets().addAll(
                    getScene().getStylesheets()
            );
        }

        // =========================================================
        // WINDOW DRAGGING
        // =========================================================

        final double[] dragX =
                {0};

        final double[] dragY =
                {0};

        titleBar.setOnMousePressed(
                event -> {

                    dragX[0] =
                            event.getScreenX()
                                    - stage.getX();

                    dragY[0] =
                            event.getScreenY()
                                    - stage.getY();
                }
        );

        titleBar.setOnMouseDragged(
                event -> {

                    if (!stage.isMaximized()) {

                        stage.setX(
                                event.getScreenX()
                                        - dragX[0]
                        );

                        stage.setY(
                                event.getScreenY()
                                        - dragY[0]
                        );
                    }
                }
        );

        stage.setScene(
                scene
        );

        stage.setMinWidth(
                900
        );

        stage.setMinHeight(
                650
        );

        stage.show();

        stage.setOnShown(
                event -> {

                    imageScroll.requestFocus();

                    fitButton.fire();
                }
        );
    }

    // =============================================================
    // COPY
    // =============================================================

    private void copyScreenshot(
            Image image,
            Stage stage
    ) {

        if (image.isError()) {

            showError(
                    "Failed to load the screenshot."
            );

            return;
        }

        ClipboardContent content =
                new ClipboardContent();

        content.putImage(
                image
        );

        Clipboard.getSystemClipboard()
                .setContent(
                        content
                );

        statusLabel.setText(
                "Screenshot copied to clipboard."
        );
    }

    // =============================================================
    // SAVE AS
    // =============================================================

    private void saveScreenshot(
            Path source,
            Stage stage
    ) {

        FileChooser chooser =
                new FileChooser();

        chooser.setTitle(
                "Save Screenshot"
        );

        chooser.setInitialFileName(
                source.getFileName()
                        .toString()
        );

        chooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter(
                        "PNG / JPEG",
                        "*.png",
                        "*.jpg",
                        "*.jpeg"
                ),
                new FileChooser.ExtensionFilter(
                        "All Files",
                        "*.*"
                )
        );

        var target =
                chooser.showSaveDialog(
                        stage
                );

        if (target == null) {
            return;
        }

        try {

            Files.copy(
                    source,
                    target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
            );

            statusLabel.setText(
                    "Screenshot saved."
            );

        } catch (IOException e) {

            e.printStackTrace();

            showError(
                    "Failed to save the screenshot."
            );
        }
    }

    // =============================================================
    // OPEN
    // =============================================================

    private void openScreenshot(
            Path file
    ) {

        try {

            if (!Desktop.isDesktopSupported()) {

                showError(
                        "Opening files is not supported."
                );

                return;
            }

            Desktop.getDesktop().open(
                    file.toFile()
            );

        } catch (IOException e) {

            e.printStackTrace();

            showError(
                    "Failed to open the screenshot."
            );
        }
    }

    // =============================================================
    // OPEN FOLDER
    // =============================================================

    private void openFolder() {

        Path directory =
                instance.getDirectory()
                        .resolve("screenshots");

        try {

            Files.createDirectories(
                    directory
            );

            if (!Desktop.isDesktopSupported()) {

                showError(
                        "Opening folders is not supported."
                );

                return;
            }

            Desktop.getDesktop().open(
                    directory.toFile()
            );

        } catch (IOException e) {

            e.printStackTrace();

            showError(
                    "Failed to open the screenshots folder."
            );
        }
    }

    // =============================================================
    // DELETE
    // =============================================================

    private boolean deleteScreenshot(
            Path file
    ) {

        Stage dialog =
                new Stage();

        dialog.initStyle(
                javafx.stage.StageStyle.TRANSPARENT
        );

        Label title =
                new Label(
                        "Delete screenshot?"
                );

        title.getStyleClass().add(
                "screenshot-delete-title"
        );

        Label description =
                new Label(
                        "This will permanently delete:"
                );

        description.getStyleClass().add(
                "screenshot-delete-description"
        );

        Label fileName =
                new Label(
                        file.getFileName()
                                .toString()
                );

        fileName.getStyleClass().add(
                "screenshot-delete-file"
        );

        VBox text =
                new VBox(
                        6,
                        title,
                        description,
                        fileName
                );

        // =========================================================
        // BUTTONS
        // =========================================================

        Button cancelButton =
                new Button(
                        "CANCEL"
                );

        cancelButton.getStyleClass().add(
                "secondary-button"
        );

        cancelButton.setOnAction(
                event ->
                        dialog.close()
        );

        Button deleteButton =
                new Button(
                        "DELETE"
                );

        deleteButton.getStyleClass().add(
                "danger-button"
        );

        final boolean[] deleted =
                {false};

        deleteButton.setOnAction(
                event -> {

                    try {

                        Files.deleteIfExists(
                                file
                        );

                        deleted[0] =
                                true;

                        dialog.close();

                    } catch (IOException e) {

                        e.printStackTrace();

                        showError(
                                "Failed to delete the screenshot."
                        );

                        dialog.close();
                    }
                }
        );

        HBox buttons =
                new HBox(
                        10,
                        cancelButton,
                        deleteButton
                );

        buttons.setAlignment(
                Pos.CENTER_RIGHT
        );

        // =========================================================
        // ROOT
        // =========================================================

        VBox root =
                new VBox(
                        20,
                        text,
                        buttons
                );

        root.setPadding(
                new Insets(
                        24
                )
        );

        root.setPrefWidth(
                430
        );

        root.getStyleClass().add(
                "screenshot-delete-dialog"
        );

        Scene scene =
                new Scene(
                        root
                );

        scene.setFill(
                javafx.scene.paint.Color.TRANSPARENT
        );

        if (getScene() != null
                && !getScene().getStylesheets().isEmpty()) {

            scene.getStylesheets().addAll(
                    getScene().getStylesheets()
            );
        }

        dialog.setScene(
                scene
        );

        dialog.setResizable(
                false
        );

        // Center over the screenshot window if possible.
        if (getScene() != null
                && getScene().getWindow() != null) {

            javafx.stage.Window owner =
                    getScene().getWindow();

            dialog.initOwner(
                    owner
            );

            dialog.setX(
                    owner.getX()
                            + (
                            owner.getWidth()
                                    - 430
                    ) / 2
            );

            dialog.setY(
                    owner.getY()
                            + (
                            owner.getHeight()
                                    - 190
                    ) / 2
            );
        }

        dialog.showAndWait();

        return deleted[0];
    }

    // =============================================================
    // EMPTY
    // =============================================================

    private void showEmptyState() {

        Label icon =
                new Label(
                        "▣"
                );

        icon.getStyleClass().add(
                "instance-empty-icon"
        );

        Label title =
                new Label(
                        "No screenshots"
                );

        title.getStyleClass().add(
                "instance-empty-title"
        );

        Label description =
                new Label(
                        "Screenshots taken in Minecraft will appear here."
                );

        description.getStyleClass().add(
                "instance-empty-description"
        );

        VBox empty =
                new VBox(
                        8,
                        icon,
                        title,
                        description
                );

        empty.setAlignment(
                Pos.CENTER
        );

        empty.setPadding(
                new Insets(60)
        );

        empty.getStyleClass().add(
                "instance-empty"
        );

        screenshotGrid
                .getChildren()
                .add(
                        empty
                );
    }

    // =============================================================
    // HELPERS
    // =============================================================

    private long getLastModified(
            Path path
    ) {

        try {

            return Files.getLastModifiedTime(
                    path
            ).toMillis();

        } catch (IOException e) {

            return 0;
        }
    }

    private void showError(
            String message
    ) {

        Alert alert =
                new Alert(
                        Alert.AlertType.ERROR
                );

        alert.setTitle(
                "Vanta"
        );

        alert.setHeaderText(
                null
        );

        alert.setContentText(
                message
        );

        alert.showAndWait();
    }
}