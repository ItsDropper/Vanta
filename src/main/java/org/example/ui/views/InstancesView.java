package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.stage.Popup;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.stage.FileChooser;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.example.launcher.instance.InstanceManager;
import org.example.launcher.model.Instance;
import org.example.launcher.service.LaunchService;
import org.example.launcher.service.MultiLaunchService;
import org.example.ui.components.InstanceCard;
import org.example.ui.LauncherSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class InstancesView extends VBox {

    private final MultiLaunchService launchService;

    private final VBox instanceList;
    private final Label statusLabel;
    private final Label countLabel;

    private final Consumer<Instance> onInstanceSettings;

    private final TextField searchField;

    private List<Instance> loadedInstances =
            List.of();


    public InstancesView(
            MultiLaunchService launchService,
            Runnable onCreateInstance,
            Consumer<Instance> onInstanceSettings
    ) {

        this.launchService =
                launchService;

        this.onInstanceSettings =
                onInstanceSettings;

        getStyleClass().add(
                "instances-page"
        );

        setPadding(
                new Insets(
                        36,
                        36,
                        36,
                        36
                )
        );

        setSpacing(
                24
        );

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        Label title =
                new Label(
                        "Instances"
                );

        title.getStyleClass().add(
                "page-title"
        );

        Label subtitle =
                new Label(
                        "Manage your Minecraft installations."
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

        countLabel =
                new Label(
                        "0 INSTANCES"
                );

        countLabel.getStyleClass().add(
                "instances-count"
        );

        Button createButton =
                new Button(
                        "CREATE"
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
                "Search instances..."
        );

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

        HBox header =
                new HBox(
                        18,
                        headerText,
                        countLabel,
                        searchField,
                        createButton
                );

        header.setAlignment(
                Pos.CENTER_LEFT
        );

        HBox.setHgrow(
                headerText,
                Priority.ALWAYS
        );

        // ---------------------------------------------------------
        // LIST
        // ---------------------------------------------------------

        instanceList =
                new VBox(
                        12
                );

        instanceList.getStyleClass().add(
                "instance-list"
        );

        ScrollPane scrollPane =
                new ScrollPane(
                        instanceList
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
                        "Scanning installed instances..."
                );

        statusLabel.getStyleClass().add(
                "instances-status"
        );

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

        refresh();
    }

    // =============================================================
    // REFRESH
    // =============================================================

    public void refresh() {

        statusLabel.setText(
                "Scanning installed instances..."
        );

        Thread thread =
                new Thread(() -> {

                    List<Instance> instances;

                    try {

                        instances =
                                InstanceManager
                                        .discoverInstances();

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() -> {

                            instanceList
                                    .getChildren()
                                    .clear();

                            countLabel.setText(
                                    "ERROR"
                            );

                            statusLabel.setText(
                                    "Failed to scan instances."
                            );
                        });

                        return;
                    }

                    Platform.runLater(() -> {

                        instanceList
                                .getChildren()
                                .clear();

                        loadedInstances =
                                List.copyOf(instances);

                        updateCount(
                                loadedInstances.size()
                        );

                        if (loadedInstances.isEmpty()) {

                            instanceList
                                    .getChildren()
                                    .add(
                                            createEmptyState()
                                    );

                            statusLabel.setText(
                                    "No Minecraft instances installed."
                            );

                            return;
                        }

                        renderInstances(
                                loadedInstances
                        );

                        statusLabel.setText(
                                "Select an instance to use it on the home screen."
                        );
                    });
                });

        thread.setDaemon(
                true
        );

        thread.setName(
                "Vanta-Instance-Refresh"
        );

        thread.start();
    }

    private void filterInstances(String query) {

        if (loadedInstances.isEmpty()) {
            return;
        }

        String normalized =
                query == null
                        ? ""
                        : query.trim().toLowerCase();

        List<Instance> filtered =
                loadedInstances.stream()
                        .filter(instance ->
                                normalized.isBlank()
                                        || instance.getName()
                                        .toLowerCase()
                                        .contains(normalized)
                                        || instance.getMinecraftVersion()
                                        .toLowerCase()
                                        .contains(normalized)
                                        || instance.getDisplayLoader()
                                        .toLowerCase()
                                        .contains(normalized)
                        )
                        .toList();

        renderInstances(filtered);

        if (filtered.isEmpty()) {
            statusLabel.setText(
                    "No instances match your search."
            );
        } else {
            statusLabel.setText(
                    filtered.size()
                            + " matching instance"
                            + (filtered.size() == 1 ? "" : "s")
            );
        }
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
        VBox root = new VBox(12);
        root.getStyleClass().add("instance-action-popup");
        Label title = new Label(duplicate ? "DUPLICATE INSTANCE" : "RENAME INSTANCE");
        title.getStyleClass().add("instance-action-title");
        Label subtitle = new Label(duplicate ? "Create a complete copy with its own instance ID." : "Change the display name without moving the instance.");
        subtitle.getStyleClass().add("instance-action-subtitle");
        subtitle.setWrapText(true);
        TextField field = new TextField(duplicate ? "Copy of " + instance.getName() : instance.getName());
        field.getStyleClass().add("instance-action-field");
        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);
        Button cancel = new Button("CANCEL");
        cancel.getStyleClass().add("secondary-button");
        Button apply = new Button(duplicate ? "DUPLICATE" : "SAVE");
        apply.getStyleClass().add("primary-button");
        cancel.setOnAction(e -> popup.hide());
        apply.setOnAction(e -> {
            String name = field.getText() == null ? "" : field.getText().trim();
            if (name.isBlank()) return;
            popup.hide();
            statusLabel.setText((duplicate ? "Duplicating " : "Renaming ") + instance.getName() + "...");
            Thread thread = new Thread(() -> {
                try {
                    if (duplicate) InstanceManager.duplicateInstance(instance, name);
                    else InstanceManager.renameInstance(instance, name);
                    Platform.runLater(this::refresh);
                } catch (Throwable ex) {
                    Platform.runLater(() -> statusLabel.setText(ex.getMessage() == null ? "Operation failed." : ex.getMessage()));
                }
            }, duplicate ? "Vanta-Instance-Duplicate" : "Vanta-Instance-Rename");
            thread.setDaemon(true);
            thread.start();
        });
        actions.getChildren().addAll(cancel, apply);
        root.getChildren().addAll(title, subtitle, field, actions);
        popup.getContent().add(root);
        showPopupCentered(popup, 430, duplicate ? 190 : 175);
        Platform.runLater(() -> { field.requestFocus(); field.selectAll(); });
    }

    private void showIconPopup(Instance instance) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        VBox root = new VBox(12);
        root.getStyleClass().add("instance-action-popup");
        Label title = new Label("INSTANCE ICON");
        title.getStyleClass().add("instance-action-title");
        Label subtitle = new Label("Choose the identity shown on the Instances page.");
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
                Thread thread = new Thread(() -> {
                    try { InstanceManager.setInstanceIcon(instance, icon[0]); Platform.runLater(this::refresh); }
                    catch (Throwable ex) { Platform.runLater(() -> statusLabel.setText(ex.getMessage() == null ? "Could not change icon." : ex.getMessage())); }
                }, "Vanta-Instance-Icon");
                thread.setDaemon(true);
                thread.start();
            });
            choices.getChildren().add(button);
        }
        Button upload = new Button("UPLOAD PNG");
        upload.getStyleClass().add("secondary-button");
        upload.setOnAction(e -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose instance icon");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("PNG images", "*.png"));
            java.io.File selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (selected == null) return;
            popup.hide();
            Thread thread = new Thread(() -> {
                try {
                    java.nio.file.Files.copy(selected.toPath(), instance.getDirectory().resolve("icon.png"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    InstanceManager.setInstanceIcon(instance, "CUSTOM");
                    Platform.runLater(this::refresh);
                } catch (Throwable ex) {
                    Platform.runLater(() -> statusLabel.setText(ex.getMessage() == null ? "Could not upload icon." : ex.getMessage()));
                }
            }, "Vanta-Instance-Icon-Upload");
            thread.setDaemon(true);
            thread.start();
        });
        root.getChildren().addAll(title, subtitle, choices, upload);
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
                    "You cannot delete a running instance."
            );
            return;
        }

        statusLabel.setText(
                "Deleting "
                        + instance.getName()
                        + "..."
        );

        Thread thread =
                new Thread(() -> {

                    try {

                        InstanceManager.deleteInstance(
                                instance
                        );

                        Platform.runLater(() -> {

                            statusLabel.setText(
                                    "Deleted "
                                            + instance.getName()
                            );

                            refresh();
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() ->
                                statusLabel.setText(
                                        ex.getMessage() != null
                                                ? ex.getMessage()
                                                : "Failed to delete instance."
                                )
                        );
                    }
                });

        thread.setDaemon(
                true
        );

        thread.setName(
                "Vanta-Instance-Delete"
        );

        thread.start();
    }

    // =============================================================
    // LAUNCH STATE
    // =============================================================

    private void onLaunchStateChanged(
            LaunchService.LaunchState state
    ) {

        Platform.runLater(
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
                        "No instances"
                );

        title.getStyleClass().add(
                "instance-empty-title"
        );

        Label description =
                new Label(
                        "Create an instance to start playing Minecraft."
                );

        description.getStyleClass().add(
                "instance-empty-description"
        );

        VBox empty =
                new VBox(
                        10,
                        icon,
                        title,
                        description
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
                "Launching "
                        + instance.getName()
                        + "..."
        );

        Thread thread =
                new Thread(() -> {

                    try {

                        launchService.launch(
                                instance
                        );

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() ->
                                statusLabel.setText(
                                        ex.getMessage() != null
                                                ? ex.getMessage()
                                                : "Failed to launch Minecraft."
                                )
                        );
                    }
                });

        thread.setDaemon(
                true
        );

        thread.setName(
                "Vanta-Instance-Launch"
        );

        thread.start();
    }
}