package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.example.launcher.instance.InstanceManager;
import org.example.launcher.model.Instance;
import org.example.launcher.service.LaunchService;
import org.example.ui.components.InstanceCard;

import java.util.List;
import java.util.function.Consumer;

public class InstancesView extends VBox {

    private final LaunchService launchService;

    private final VBox instanceList;
    private final Label statusLabel;
    private final Label countLabel;

    private final Consumer<Instance> onInstanceSelected;
    private final Consumer<Instance> onInstanceSettings;

    private Instance selectedInstance;

    public InstancesView(
            LaunchService launchService,
            Runnable onCreateInstance,
            Consumer<Instance> onInstanceSelected,
            Consumer<Instance> onInstanceSettings
    ) {

        this.launchService =
                launchService;

        this.onInstanceSelected =
                onInstanceSelected;

        this.onInstanceSettings =
                onInstanceSettings;

        // ---------------------------------------------------------
        // PAGE
        // ---------------------------------------------------------

        getStyleClass().add(
                "instances-page"
        );

        setPadding(
                new Insets(36, 36, 36, 36)
        );

        setSpacing(
                24
        );

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        Label title =
                new Label("Instances");

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
                        "+  CREATE INSTANCE"
                );

        createButton.getStyleClass().add(
                "instance-create-button"
        );

        createButton.setOnAction(
                event ->
                        onCreateInstance.run()
        );

        HBox header =
                new HBox(
                        18,
                        headerText,
                        countLabel,
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
        // LIST CONTAINER
        // ---------------------------------------------------------

        instanceList =
                new VBox(
                        12
                );

        instanceList.getStyleClass().add(
                "instance-list"
        );

        // ---------------------------------------------------------
        // SCROLL
        // ---------------------------------------------------------

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

                        countLabel.setText(
                                instances.size()
                                        + " INSTANCE"
                                        + (instances.size() == 1
                                        ? ""
                                        : "S")
                        );

                        // -------------------------------------------------
                        // EMPTY STATE
                        // -------------------------------------------------

                        if (instances.isEmpty()) {

                            VBox emptyState =
                                    createEmptyState();

                            instanceList
                                    .getChildren()
                                    .add(
                                            emptyState
                                    );

                            statusLabel.setText(
                                    "No Minecraft instances installed."
                            );

                            return;
                        }

                        // -------------------------------------------------
                        // INSTANCE CARDS
                        // -------------------------------------------------

                        for (Instance instance : instances) {

                            InstanceCard card =
                                    new InstanceCard(
                                            instance,
                                            () -> launch(instance),
                                            () -> selectInstance(instance),
                                            () -> onInstanceSettings.accept(instance)
                                    );

                            card.setSelected(
                                    selectedInstance != null
                                            && selectedInstance
                                            .getId()
                                            .equals(
                                                    instance.getId()
                                            )
                            );

                            instanceList
                                    .getChildren()
                                    .add(
                                            card
                                    );
                        }

                        statusLabel.setText(
                                "Select an instance to use it on the home screen."
                        );
                    });
                });

        thread.setDaemon(true);
        thread.start();
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
                new Insets(70)
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

        if (launchService.isRunning()) {

            statusLabel.setText(
                    "Minecraft is already running."
            );

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

                        Platform.runLater(() ->
                                statusLabel.setText(
                                        "Minecraft is running."
                                )
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

        thread.setDaemon(true);
        thread.start();
    }
}