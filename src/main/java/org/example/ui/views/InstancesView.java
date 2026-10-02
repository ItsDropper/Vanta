package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.example.launcher.instance.InstanceManager;
import org.example.launcher.model.Instance;
import org.example.launcher.service.LaunchService;
import org.example.ui.components.InstanceCard;

import java.util.ArrayList;
import java.util.List;

public class InstancesView extends VBox {

    private final LaunchService launchService;

    private final VBox instanceList;
    private final Label statusLabel;
    private final Label countLabel;

    private final Consumer<Instance> onInstanceSettings;

    private final TextField searchField;

    private List<Instance> loadedInstances =
            List.of();


    public InstancesView(
            LaunchService launchService,
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
                            () -> deleteInstance(instance)
                    );

            instanceList.getChildren().add(card);
        }

        updateCards();
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
    // SELECT
    // =============================================================

    private void selectInstance(
            Instance instance
    ) {

        selectedInstance =
                instance;

        for (
                javafx.scene.Node node
                : instanceList.getChildren()
        ) {

            if (node instanceof InstanceCard card) {

                card.setSelected(
                        card.getInstance()
                                .getId()
                                .equals(
                                        instance.getId()
                                )
                );
            }
        }

        onInstanceSelected.accept(
                instance
        );

        statusLabel.setText(
                "Selected "
                        + instance.getName()
        );
    }

    // =============================================================
    // LAUNCH
    // =============================================================

    private void launch(
            Instance instance
    ) {

        LaunchService.LaunchState state =
                launchService.getState();

        if (state == LaunchService.LaunchState.RUNNING) {

            Instance running =
                    launchService.getRunningInstance();

            if (running != null
                    && running.getId()
                    .equals(
                            instance.getId()
                    )) {

                launchService.close();

            } else {

                statusLabel.setText(
                        "Minecraft is already running."
                );
            }

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