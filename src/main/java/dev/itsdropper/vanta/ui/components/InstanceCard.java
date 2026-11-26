package dev.itsdropper.vanta.ui.components;

import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.ThemeManager;
import dev.itsdropper.vanta.ui.async.UiExecutor;

import javafx.animation.FadeTransition;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBase;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
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
import javafx.util.Duration;

import dev.itsdropper.vanta.launcher.instance.MrpackExporter;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.service.LaunchService;
import dev.itsdropper.vanta.launcher.service.InstanceUsageManager;
import dev.itsdropper.vanta.ui.AnimationUtils;
import dev.itsdropper.vanta.ui.LauncherSettings;
import dev.itsdropper.vanta.ui.kit.Badge;
import dev.itsdropper.vanta.ui.kit.CoverArt;
import dev.itsdropper.vanta.ui.kit.IconButton;

import java.io.File;

/**
 * One instance in the library. In grid layout the pixel-art cover leads and
 * the actions appear over it on hover; in list layout it is a dense row.
 */
public class InstanceCard extends StackPane {

    public enum Layout { GRID, LIST }

    private static final Color ICON_INK = Color.rgb(219, 231, 255);

    private final Layout layout;

    private final Label nameLabel;
    private final Badge versionBadge;
    private final Badge loaderBadge;
    private final Label playtimeLabel;
    private final Badge statusBadge;
    private final Canvas presetIconCanvas;
    private final ImageView customIconView;

    private final Button playButton;
    private final IconButton settingsButton;
    private final IconButton menuButton;

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
        this(instance, onPlay, onSettings, onDelete, onRename, onDuplicate, onIcon, Layout.GRID);
    }

    public InstanceCard(
            Instance instance,
            Runnable onPlay,
            Runnable onSettings,
            Runnable onDelete,
            Runnable onRename,
            Runnable onDuplicate,
            Runnable onIcon,
            Layout layout
    ) {
        this.instance = instance;
        this.layout = layout;

        setMaxWidth(Double.MAX_VALUE);
        getStyleClass().addAll("instance-card", layout == Layout.GRID ? "instance-card-grid" : "instance-card-list");

        // ---------------------------------------------------------- identity
        nameLabel = new Label();
        nameLabel.getStyleClass().add("instance-name");
        nameLabel.setMinWidth(0);
        nameLabel.setMaxWidth(Double.MAX_VALUE);
        nameLabel.setTextOverrun(OverrunStyle.ELLIPSIS);

        versionBadge = Badge.mono("");
        loaderBadge = Badge.loader(instance.getLoader());
        playtimeLabel = new Label();
        playtimeLabel.getStyleClass().addAll("instance-playtime", "value-mono");

        statusBadge = Badge.status("", Badge.Tone.SUCCESS);
        statusBadge.getStyleClass().add("instance-status");
        hideStatus();

        presetIconCanvas = createIconGraphic(instance.getIcon(), 18);
        customIconView = new ImageView();
        customIconView.setFitWidth(18);
        customIconView.setFitHeight(18);
        customIconView.setPreserveRatio(true);
        StackPane iconPlate = new StackPane(presetIconCanvas, customIconView);
        iconPlate.getStyleClass().add("instance-icon-plate");
        iconPlate.setMinSize(28, 28);
        iconPlate.setPrefSize(28, 28);
        iconPlate.setMaxSize(28, 28);
        iconPlate.setMouseTransparent(true);

        // ----------------------------------------------------------- actions
        playButton = new Button(Localization.text("common.play"));
        playButton.setGraphic(IconView.create(IconView.Type.PLAY, 14));
        playButton.getStyleClass().addAll("vanta-button-primary", "button-compact", "instance-play-button");
        playButton.setAccessibleText(Localization.text("instanceCard.launchPrefix") + instance.getName());
        playButton.setTooltip(new Tooltip(Localization.text("instanceCard.launchAccessiblePrefix") + instance.getName()));
        playButton.setMinWidth(Region.USE_PREF_SIZE);
        playButton.setOnAction(event -> {
            event.consume();
            onPlay.run();
        });
        AnimationUtils.installInteractiveAnimations(playButton);

        settingsButton = new IconButton(IconView.Type.SETTINGS,
                Localization.text("instanceCard.optionsForPrefix") + instance.getName(),
                IconButton.Style.SECONDARY).compact();
        settingsButton.getStyleClass().add("instance-settings-button");
        settingsButton.setTooltip(new Tooltip(Localization.text("instanceCard.optionsAccessible")));
        settingsButton.setOnAction(event -> {
            event.consume();
            onSettings.run();
        });

        menuButton = new IconButton(IconView.Type.MORE,
                Localization.text("instanceCard.moreActionsForPrefix") + instance.getName(),
                IconButton.Style.SECONDARY).compact();
        menuButton.getStyleClass().add("instance-menu-button");
        menuButton.setTooltip(new Tooltip(Localization.text("instanceCard.moreActions")));
        menuButton.setOnAction(event -> {
            event.consume();
            showMenu(menuButton, onDelete, onRename, onDuplicate, onIcon);
        });

        if (layout == Layout.GRID) {
            buildGrid(iconPlate);
        } else {
            buildList();
        }

        // Clicking the card opens the instance; the buttons keep their own actions.
        setCursor(javafx.scene.Cursor.HAND);
        setFocusTraversable(true);
        setAccessibleRole(AccessibleRole.BUTTON);
        setAccessibleText(Localization.text("instanceCard.optionsForPrefix") + instance.getName());
        setOnMouseClicked(event -> {
            if (!fromButton(event.getTarget())) {
                onSettings.run();
            }
        });
        setOnKeyPressed(event -> {
            if (event.getTarget() == this
                    && (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)) {
                onSettings.run();
                event.consume();
            }
        });

        update();
        updateIcon();
    }

    private static boolean fromButton(Object target) {
        Node node = target instanceof Node n ? n : null;
        while (node != null) {
            if (node instanceof ButtonBase) {
                return true;
            }
            node = node.getParent();
        }
        return false;
    }

    // ================================================================ layouts

    private void buildGrid(StackPane iconPlate) {
        CoverArt cover = new CoverArt(instance.getName(), instance.getLoader()).rounded(8);
        cover.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        HBox topActions = new HBox(6, settingsButton, menuButton);
        topActions.setAlignment(Pos.TOP_RIGHT);
        topActions.setPickOnBounds(false);
        StackPane.setAlignment(topActions, Pos.TOP_RIGHT);
        StackPane.setMargin(topActions, new Insets(8));

        StackPane.setAlignment(statusBadge, Pos.TOP_LEFT);
        StackPane.setMargin(statusBadge, new Insets(8));

        StackPane.setAlignment(iconPlate, Pos.BOTTOM_LEFT);
        StackPane.setMargin(iconPlate, new Insets(8));

        StackPane.setAlignment(playButton, Pos.BOTTOM_RIGHT);
        StackPane.setMargin(playButton, new Insets(8));

        StackPane art = new StackPane(cover, iconPlate, statusBadge, topActions, playButton);
        art.getStyleClass().add("instance-card-art");
        art.setMinHeight(112);
        art.setPrefHeight(112);
        art.setMaxHeight(112);

        HBox badges = new HBox(6, loaderBadge, versionBadge);
        badges.setAlignment(Pos.CENTER_LEFT);

        Region fill = new Region();
        HBox.setHgrow(fill, Priority.ALWAYS);
        HBox meta = new HBox(8, badges, fill, playtimeLabel);
        meta.setAlignment(Pos.CENTER_LEFT);

        VBox body = new VBox(8, nameLabel, meta);
        body.setPadding(new Insets(10, 6, 6, 6));

        VBox column = new VBox(art, body);
        column.setPadding(new Insets(6));
        getChildren().add(column);

        reveal(topActions, playButton);
    }

    private void buildList() {
        CoverArt thumb = new CoverArt(instance.getName(), instance.getLoader())
                .rounded(6).sized(72, 40);

        HBox identity = new HBox(14, thumb, nameLabel);
        identity.setAlignment(Pos.CENTER_LEFT);
        identity.setMinWidth(0);
        HBox.setHgrow(identity, Priority.ALWAYS);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        StackPane loaderCell = cell(loaderBadge, 150);
        StackPane versionCell = cell(versionBadge, 88);
        playtimeLabel.setAlignment(Pos.CENTER_RIGHT);
        StackPane playtimeCell = cell(playtimeLabel, 84);
        playtimeCell.setAlignment(Pos.CENTER_RIGHT);
        StackPane statusCell = cell(statusBadge, 96);

        HBox actions = new HBox(6, playButton, settingsButton, menuButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox row = new HBox(12, identity, loaderCell, versionCell, playtimeCell, statusCell, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 12, 8, 8));
        getChildren().add(row);

        widthProperty().addListener((observable, previous, next) -> {
            double width = next.doubleValue();
            boolean wide = width >= 760;
            boolean medium = width >= 600;
            statusCell.setVisible(wide);
            statusCell.setManaged(wide);
            playtimeCell.setVisible(medium && LauncherSettings.isShowPlaytimeEnabled());
            playtimeCell.setManaged(playtimeCell.isVisible());
            loaderCell.setVisible(medium);
            loaderCell.setManaged(medium);
        });
    }

    private static StackPane cell(Node node, double width) {
        StackPane cell = new StackPane(node);
        cell.setAlignment(Pos.CENTER_LEFT);
        cell.setMinWidth(width);
        cell.setPrefWidth(width);
        cell.setMaxWidth(width);
        return cell;
    }

    /** Fades the given nodes in while the card is hovered or holds focus. */
    private void reveal(Node... nodes) {
        for (Node node : nodes) {
            node.setOpacity(0);
        }
        Runnable sync = () -> {
            boolean show = isHover() || isFocused() || playButton.isFocused()
                    || settingsButton.isFocused() || menuButton.isFocused() || getStyleClass().contains("instance-card-live");
            for (Node node : nodes) {
                FadeTransition fade = new FadeTransition(Duration.millis(show ? 120 : 160), node);
                fade.setToValue(show ? 1 : 0);
                Object previous = node.getProperties().put("reveal.fade", fade);
                if (previous instanceof FadeTransition active) {
                    active.stop();
                }
                fade.play();
            }
        };
        hoverProperty().addListener((observable, was, is) -> sync.run());
        focusedProperty().addListener((observable, was, is) -> sync.run());
        playButton.focusedProperty().addListener((observable, was, is) -> sync.run());
        settingsButton.focusedProperty().addListener((observable, was, is) -> sync.run());
        menuButton.focusedProperty().addListener((observable, was, is) -> sync.run());
        getProperties().put("reveal.sync", sync);
    }

    private void refreshReveal() {
        if (getProperties().get("reveal.sync") instanceof Runnable sync) {
            sync.run();
        }
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

        MenuItem renameItem = new MenuItem(Localization.text("instanceCard.rename"));
        MenuItem duplicateItem = new MenuItem(Localization.text("instanceCard.duplicate"));
        MenuItem iconItem = new MenuItem(Localization.text("instanceCard.changeIcon"));

        MenuItem exportItem =
                new MenuItem(
                        Localization.text("instanceCard.export")
                );

        MenuItem deleteItem =
                new MenuItem(
                        Localization.text("instanceCard.delete")
                );

        deleteItem.getStyleClass().add(
                "danger-item"
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

        ThemeManager.apply(menu);
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
                        Localization.text("instanceCard.deleteQuestion")
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
                        Localization.text("common.cancelTitleCase")
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
                        Localization.text("common.deleteTitleCase")
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

        dialogRoot.getStyleClass().add("delete-dialog-root");

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

        dev.itsdropper.vanta.ui.Stylesheets.addTo(scene);

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
                Localization.text("instanceCard.exportTitle")
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

        UiExecutor.shared().execute(() -> {

                            try {

                                MrpackExporter.exportMrpack(
                                        instance,
                                        finalSelected.toPath()
                                );

                                UiExecutor.shared().dispatchToUi(() -> {

                                    menuButton.setDisable(
                                            false
                                    );

                                    showExportSuccess(
                                            finalSelected
                                    );
                                });

                            } catch (Exception e) {

                                e.printStackTrace();

                                UiExecutor.shared().dispatchToUi(() -> {

                                    menuButton.setDisable(
                                            false
                                    );

                                    showExportError(
                                            e
                                    );
                                });
                            }
        });


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
                Localization.text("instanceCard.exportComplete")
        );

        alert.setHeaderText(
                Localization.text("instanceCard.exportSuccess")
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
                Localization.text("instanceCard.exportFailed")
        );

        alert.setHeaderText(
                Localization.text("instanceCard.exportError")
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
                customIconView.setImage(new Image(file.toURI().toString(), 18, 18, true, true));
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

    private void update() {
        nameLabel.setText(instance.getName());
        versionBadge.setText(instance.getMinecraftVersion());
        loaderBadge.setText(instance.getDisplayLoader());
        playtimeLabel.setText(
                InstanceUsageManager.formatPlaytime(
                        InstanceUsageManager.getPlaytimeSeconds(instance)));
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
        g.setStroke(ICON_INK);
        g.setFill(ICON_INK);
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
        UiExecutor.shared().dispatchToUi(() -> {
            if (state == LaunchService.LaunchState.IDLE || !isThisInstance) {
                resetToReady();
                return;
            }

            switch (state) {
                case PREPARING -> {
                    playButton.setDisable(true);
                    setPlayButtonAction("Preparing…", "Preparing to launch " + instance.getName(), false);
                    setPlayingStyle(false);
                    showStatus(Localization.text("instanceCard.preparing"), Badge.Tone.ACCENT);
                }
                case STARTING -> {
                    playButton.setDisable(true);
                    setPlayButtonAction("Starting…", "Starting Minecraft instance " + instance.getName(), false);
                    setPlayingStyle(false);
                    showStatus(Localization.text("instanceCard.starting"), Badge.Tone.ACCENT);
                }
                case RUNNING -> {
                    playButton.setDisable(false);
                    setPlayButtonAction(Localization.text("instanceCard.close"), "Close Minecraft instance " + instance.getName(), false);
                    setPlayingStyle(true);
                    showStatus(Localization.text("instanceCard.running"), Badge.Tone.SUCCESS);
                }
                case CLOSING -> {
                    playButton.setDisable(true);
                    setPlayButtonAction("Closing…", "Closing Minecraft instance " + instance.getName(), false);
                    setPlayingStyle(true);
                    showStatus(Localization.text("instanceCard.closing"), Badge.Tone.WARNING);
                }
                case ERROR -> {
                    playButton.setDisable(false);
                    setPlayButtonAction(Localization.text("common.play"),
                            Localization.text("instanceCard.launchPrefix") + instance.getName(), true);
                    setPlayingStyle(false);
                    showStatus(Localization.text("instanceCard.error"), Badge.Tone.DANGER);
                }
                default -> resetToReady();
            }
        });
    }

    private void setPlayButtonAction(String text, String accessibleText, boolean withIcon) {
        playButton.setText(text);
        playButton.setGraphic(withIcon || text.equals(Localization.text("common.play"))
                ? IconView.create(IconView.Type.PLAY, 14) : null);
        playButton.setAccessibleText(accessibleText);
        playButton.setTooltip(new Tooltip(accessibleText));
    }

    private void resetToReady() {
        playButton.setDisable(false);
        setPlayingStyle(false);
        if (dev.itsdropper.vanta.launcher.instance.InstanceManager.isInstallationInProgress(instance)) {
            setPlayButtonAction("Repair & Play",
                    "Repair interrupted installation and launch " + instance.getName(), true);
            showStatus("Repair required", Badge.Tone.WARNING);
            return;
        }
        setPlayButtonAction(Localization.text("common.play"),
                Localization.text("instanceCard.launchPrefix") + instance.getName(), true);
        hideStatus();
    }

    private void showStatus(String text, Badge.Tone tone) {
        statusBadge.setText(text.replaceFirst("^\\W+", ""));
        statusBadge.getStyleClass().removeIf(style -> style.startsWith("badge-")
                && !style.equals("badge-mono") && !style.equals("badge-dot"));
        statusBadge.getStyleClass().add("badge-" + tone.name().toLowerCase(java.util.Locale.ROOT));
        statusBadge.setVisible(true);
        statusBadge.setManaged(true);
    }

    private void hideStatus() {
        statusBadge.setText("");
        statusBadge.setVisible(false);
        statusBadge.setManaged(false);
    }

    private void setPlayingStyle(boolean playing) {
        playButton.getStyleClass().remove("instance-playing-button");
        playButton.getStyleClass().removeAll("vanta-button-primary", "vanta-button-secondary");
        if (playing) {
            playButton.getStyleClass().addAll("vanta-button-secondary", "instance-playing-button");
            if (!getStyleClass().contains("instance-card-live")) {
                getStyleClass().add("instance-card-live");
            }
        } else {
            playButton.getStyleClass().add("vanta-button-primary");
            getStyleClass().remove("instance-card-live");
        }
        refreshReveal();
    }

    // =============================================================
    // GET / SET INSTANCE
    // =============================================================

    public Instance getInstance() {
        return instance;
    }

    public void setInstance(Instance instance) {
        if (instance == null) {
            return;
        }
        this.instance = instance;
        update();
    }

}
