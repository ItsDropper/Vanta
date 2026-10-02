package org.example.ui.views;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import org.example.launcher.account.Account;
import org.example.launcher.instance.InstanceManager;
import org.example.launcher.model.Instance;
import org.example.launcher.model.ServerHistoryEntry;
import org.example.launcher.service.AccountService;
import org.example.launcher.service.InstanceUsageManager;
import org.example.launcher.service.LaunchFailure;
import org.example.launcher.service.MultiLaunchService;
import org.example.launcher.service.ServerHistoryManager;
import org.example.launcher.service.ServerTarget;
import org.example.launcher.service.LaunchService;
import org.example.ui.components.IconView;
import org.example.ui.components.MinecraftBackdrop;

import java.util.List;
import java.util.function.Consumer;

public class HomeView extends StackPane {

    private final AccountService accountService;
    private final MultiLaunchService launchService;
    private final Consumer<LaunchFailure> onLaunchFailure;
    private final Consumer<Instance> onLaunchInstance;

    private VBox recentServersList;
    private Label recentServersStatus;
    private final Label accountLabel;
    private VBox recentList;
    private Label recentStatus;
    private final java.util.Map<String, Label> playtimeLabels =
            new java.util.HashMap<>();
    private final java.util.Map<String, Instance> displayedInstances =
            new java.util.HashMap<>();
    private final Timeline playtimeTimer;

    public HomeView(
            AccountService accountService,
            MultiLaunchService launchService,
            Consumer<LaunchFailure> onLaunchFailure,
            Consumer<Instance> onLaunchInstance
    ) {
        this.accountService = accountService;
        this.launchService = launchService;
        this.onLaunchFailure = onLaunchFailure;
        this.onLaunchInstance = onLaunchInstance;

        getStyleClass().addAll("page", "home-page");

        MinecraftBackdrop backdrop = new MinecraftBackdrop();
        backdrop.setManaged(false);
        backdrop.prefWidthProperty().bind(widthProperty());
        backdrop.prefHeightProperty().bind(heightProperty());
        backdrop.setMinSize(0, 0);
        backdrop.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Label title = new Label("Home");
        title.getStyleClass().add("home-title");

        accountLabel = new Label("Checking account...");
        accountLabel.getStyleClass().add("home-account-label");

        VBox header = new VBox(4, title, accountLabel);
        header.getStyleClass().add("home-header");

        VBox recentSection = createRecentInstancesSection();
        VBox serverCard = createRecentServersCard();

        VBox contentBox = new VBox(
                18,
                header,
                recentSection,
                serverCard
        );
        contentBox.setFillWidth(true);
        contentBox.setPadding(new Insets(24, 36, 28, 36));
        contentBox.setMaxWidth(Double.MAX_VALUE);
        contentBox.setMaxHeight(Double.MAX_VALUE);

        ScrollPane homeScroll = new ScrollPane(contentBox);
        homeScroll.setFitToWidth(true);
        homeScroll.setFitToHeight(false);
        homeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        homeScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        homeScroll.setPannable(false);
        homeScroll.getStyleClass().add("home-content-scroll");
        homeScroll.setManaged(true);

        getChildren().addAll(backdrop, homeScroll);
        StackPane.setAlignment(homeScroll, Pos.TOP_LEFT);

        // Home content is a single interactive hierarchy.
        // Keeping the header inside the ScrollPane prevents an overlay node
        // from ever sitting above the recent-instance controls.

        accountService.addListener(this::onAccountChanged);
        launchService.addStateListener(this::onLaunchStateChanged);
        ServerHistoryManager.addListener(() ->
                Platform.runLater(this::refreshRecentServers)
        );

        playtimeTimer = new Timeline(
                new KeyFrame(
                        Duration.seconds(1),
                        event -> updatePlaytimeLabels()
                )
        );
        playtimeTimer.setCycleCount(Timeline.INDEFINITE);
        playtimeTimer.play();

        refreshAccount();
        refreshRecentInstances();
        refreshRecentServers();
    }

    private VBox createRecentInstancesSection() {
        Label recentTitle = new Label("RECENTLY PLAYED");
        recentTitle.getStyleClass().add("home-section-title");

        Label recentSubtitle = new Label(
                "Jump back into the instances you played most recently."
        );
        recentSubtitle.getStyleClass().add("home-section-subtitle");

        recentList = new VBox(10);
        recentList.getStyleClass().add("home-recent-list");

        recentStatus = new Label("Loading instances...");
        recentStatus.getStyleClass().add("home-recent-status");

        VBox section = new VBox(
                8,
                recentTitle,
                recentSubtitle,
                recentList,
                recentStatus
        );
        recentList.setMinHeight(0);
        recentList.setMaxHeight(Double.MAX_VALUE);
        section.setFillWidth(true);
        section.getStyleClass().add("home-recent-section");
        return section;
    }

    private VBox createRecentServersCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("home-recent-servers-card");

        Label title = new Label("RECENTLY PLAYED SERVERS");
        title.getStyleClass().add("home-section-title");

        Label subtitle = new Label(
                "Join a server directly with the instance you last used."
        );
        subtitle.getStyleClass().add("home-section-subtitle");

        recentServersList = new VBox(8);
        recentServersList.getStyleClass().add("home-server-list");

        recentServersStatus = new Label("No server history yet.");
        recentServersStatus.getStyleClass().add("home-recent-status");

        recentServersList.setMinHeight(Region.USE_PREF_SIZE);
        recentServersList.setPrefHeight(Region.USE_COMPUTED_SIZE);
        recentServersList.setMaxHeight(Double.MAX_VALUE);

        card.getChildren().addAll(
                title,
                subtitle,
                recentServersList,
                recentServersStatus
        );

        return card;
    }

    public void refreshRecentInstances() {
        Thread thread = new Thread(() -> {
            try {
                List<Instance> instances =
                        InstanceUsageManager.sortByLastPlayed(
                                InstanceManager.discoverInstances()
                        );

                Platform.runLater(() -> renderRecentInstances(instances));

            } catch (Throwable ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    recentList.getChildren().clear();
                    recentStatus.setText("Failed to load recent instances.");
                });
            }
        });

        thread.setDaemon(true);
        thread.setName("Vanta-Home-Instances");
        thread.start();
    }

    private void renderRecentInstances(List<Instance> instances) {
        recentList.getChildren().clear();
        playtimeLabels.clear();
        displayedInstances.clear();

        List<Instance> recent = instances.stream()
                .filter(instance ->
                        InstanceUsageManager.getLastPlayed(instance) > 0
                )
                .limit(6)
                .toList();

        // Keep the Home layout useful even when play history is empty.
        if (recent.isEmpty() && !instances.isEmpty()) {
            recent = List.of(instances.get(0));
            recentStatus.setText("1 instance available");
        } else if (recent.isEmpty()) {
            recentStatus.setText(
                    "No instances yet. Create an instance to get started."
            );
            return;
        } else {
            recentStatus.setText(
                    recent.size() + " recent instance"
                            + (recent.size() == 1 ? "" : "s")
            );
        }

        for (Instance instance : recent) {
            recentList.getChildren().add(createRecentCard(instance));
        }
    }

    private void updatePlaytimeLabels() {
        for (java.util.Map.Entry<String, Label> entry
                : playtimeLabels.entrySet()) {

            Instance instance = displayedInstances.get(entry.getKey());
            if (instance == null) {
                continue;
            }

            long seconds =
                    InstanceUsageManager.getPlaytimeSeconds(instance)
                            + launchService.getSessionPlaytimeSeconds(instance);

            entry.getValue().setText(
                    InstanceUsageManager.formatPlaytime(seconds)
                            + " played"
            );
        }
    }

    private HBox createRecentCard(Instance instance) {
        Label icon = new Label();
        icon.setGraphic(
                IconView.create(IconView.Type.PLAY, 16)
        );
        icon.getStyleClass().add("home-recent-icon");

        Label name = new Label(instance.getName());
        name.getStyleClass().add("home-recent-name");

        Label metadata = new Label(
                instance.getMinecraftVersion()
                        + " • "
                        + instance.getDisplayLoader()
        );
        metadata.getStyleClass().add("home-recent-meta");

        Label playtime = new Label(
                InstanceUsageManager.formatPlaytime(
                        InstanceUsageManager.getPlaytimeSeconds(instance)
                                + launchService.getSessionPlaytimeSeconds(instance)
                )
                        + " played"
        );
        playtimeLabels.put(instance.getId(), playtime);
        displayedInstances.put(instance.getId(), instance);
        playtime.getStyleClass().add("home-recent-playtime");

        VBox info = new VBox(4, name, metadata, playtime);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button play = new Button(
                instance != null && launchService.isRunning(instance)
                        ? "CLOSE"
                        : "PLAY"
        );
        play.getStyleClass().add(
                launchService.isRunning(instance)
                        ? "home-recent-stop"
                        : "home-recent-play"
        );
        play.setMinWidth(92);
        play.setPrefWidth(92);
        play.setMinHeight(36);
        play.setPrefHeight(36);
        play.setMaxHeight(36);
        play.setCursor(Cursor.HAND);
        play.setPickOnBounds(true);
        play.setFocusTraversable(true);
        play.setDisable(false);
        play.setMouseTransparent(false);

        play.setOnMousePressed(event -> {
            play.getProperties().put("vanta-home-pressed", true);
            play.setScaleX(0.96);
            play.setScaleY(0.96);
            event.consume();
        });

        play.setOnMouseReleased(event -> {
            play.getProperties().put("vanta-home-pressed", false);
            play.setScaleX(1.0);
            play.setScaleY(1.0);
            event.consume();
        });

        play.setOnMouseEntered(event -> {
            play.setScaleX(1.025);
            play.setScaleY(1.025);
        });

        play.setOnMouseExited(event -> {
            play.setScaleX(1.0);
            play.setScaleY(1.0);
            play.getProperties().put("vanta-home-pressed", false);
        });

        play.setDisable(instance == null);

        play.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                return;
            }

            event.consume();

            if (instance == null) {
                return;
            }

            if (launchService.isRunning(instance)) {
                launchService.close(instance);
                return;
            }

            Thread thread = new Thread(() -> {
                try {
                    ServerTarget recentServer =
                            ServerHistoryManager.getRecent().stream()
                                    .filter(server ->
                                            instance.getId().equals(
                                                    server.getInstanceId()
                                            ))
                                    .findFirst()
                                    .map(server ->
                                            new ServerTarget(
                                                    server.getHost(),
                                                    server.getPort()
                                            )
                                    )
                                    .orElse(null);

                    launchService.launch(instance, recentServer);
                } catch (Throwable ex) {
                    ex.printStackTrace();

                    LaunchFailure failure = launchService.getLastFailure();

                    Platform.runLater(() -> {
                        if (failure != null) {
                            onLaunchFailure.accept(failure);
                        } else {
                            onLaunchFailure.accept(
                                    new LaunchFailure(
                                            "Minecraft failed to launch",
                                            ex.getMessage() != null
                                                    ? ex.getMessage()
                                                    : "Vanta could not start this instance.",
                                            ""
                                    )
                            );
                        }
                    });
                }
            });

            thread.setDaemon(true);
            thread.setName("Vanta-Home-Launch");
            thread.start();
        });

        HBox card = new HBox(14, icon, info, play);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPickOnBounds(true);
        card.setMouseTransparent(false);
        card.getStyleClass().add("home-recent-card");

        // The whole card is a fallback hit target. The visible PLAY button
        // remains the primary control, but clicking its area always launches.
        card.addEventFilter(MouseEvent.MOUSE_PRESSED, event -> {
            if (event.getTarget() == play || play.isHover()) {
                return;
            }
        });

        card.setOnMouseClicked(event -> {
            if (event.getTarget() == play) {
                return;
            }

            if (launchService.isRunning(instance)) {
                launchService.close(instance);
                return;
            }

            Thread thread = new Thread(() -> {
                try {
                    launchService.launch(instance);
                } catch (Throwable ex) {
                    ex.printStackTrace();
                    LaunchFailure failure = launchService.getLastFailure();

                    Platform.runLater(() -> {
                        if (failure != null) {
                            onLaunchFailure.accept(failure);
                        } else {
                            onLaunchFailure.accept(
                                    new LaunchFailure(
                                            "Minecraft failed to launch",
                                            ex.getMessage() != null
                                                    ? ex.getMessage()
                                                    : "Vanta could not start this instance.",
                                            ""
                                    )
                            );
                        }
                    });
                }
            });

            thread.setDaemon(true);
            thread.setName("Vanta-Home-Launch-Fallback");
            thread.start();
        });

        return card;
    }

    public void refreshRecentServers() {
        Thread thread = new Thread(() -> {
            try {
                List<Instance> instances =
                        InstanceManager.discoverInstances();

                List<ServerHistoryEntry> history =
                        ServerHistoryManager.getRecent();

                Platform.runLater(() ->
                        renderRecentServers(instances, history)
                );
            } catch (Throwable ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    recentServersList.getChildren().clear();
                    recentServersStatus.setText(
                            "Failed to load recent servers."
                    );
                });
            }
        });

        thread.setDaemon(true);
        thread.setName("Vanta-Home-Servers");
        thread.start();
    }

    private void renderRecentServers(
            List<Instance> instances,
            List<ServerHistoryEntry> history
    ) {
        recentServersList.getChildren().clear();

        List<ServerHistoryEntry> recent = history.stream()
                .limit(5)
                .toList();

        if (recent.isEmpty()) {
            recentServersStatus.setText(
                    "Play on a server from Minecraft and it will appear here."
            );
            return;
        }

        int rendered = 0;

        for (ServerHistoryEntry entry : recent) {
            Instance instance = instances.stream()
                    .filter(candidate ->
                            candidate.getId().equals(entry.getInstanceId())
                    )
                    .findFirst()
                    .orElseGet(() -> instances.stream()
                            .filter(candidate ->
                                    candidate.getName().equalsIgnoreCase(
                                            entry.getInstanceName()
                                    )
                            )
                            .findFirst()
                            .orElse(null));

            recentServersList.getChildren().add(
                    createRecentServerCard(instance, entry)
            );
            rendered++;
        }

        if (rendered == 0) {
            recentServersStatus.setText(
                    "Recent server history is unavailable for the installed instances."
            );
            return;
        }

        recentServersStatus.setText(
                rendered + " recent server"
                        + (rendered == 1 ? "" : "s")
        );
    }
    private HBox createRecentServerCard(
            Instance instance,
            ServerHistoryEntry server
    ) {
        Label name = new Label(server.getDisplayName());
        name.getStyleClass().add("home-server-name");

        Label metadata = new Label(
                instance != null
                        ? "Using " + instance.getName()
                        : "Instance no longer installed"
        );
        metadata.getStyleClass().add("home-server-meta");

        VBox info = new VBox(3, name, metadata);
        HBox.setHgrow(info, Priority.ALWAYS);

        boolean instanceAvailable = instance != null;
        boolean instanceRunning =
                instanceAvailable && launchService.isRunning(instance);

        Button play = new Button(
                instanceRunning ? "CLOSE" : "PLAY"
        );
        play.getStyleClass().add(
                instanceRunning
                        ? "home-recent-stop"
                        : "home-recent-play"
        );
        play.setMinWidth(84);
        play.setPickOnBounds(true);
        play.setMouseTransparent(false);
        play.setCursor(Cursor.HAND);
        play.setDisable(!instanceAvailable);

        play.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getButton() != javafx.scene.input.MouseButton.PRIMARY) {
                return;
            }

            event.consume();

            if (instance == null) {
                return;
            }

            if (launchService.isRunning(instance)) {
                launchService.close(instance);
                return;
            }

            Thread thread = new Thread(() -> {
                try {
                    launchService.launch(
                            instance,
                            new ServerTarget(
                                    server.getHost(),
                                    server.getPort()
                            )
                    );
                } catch (Exception ex) {
                    LaunchFailure failure = launchService.getLastFailure();

                    if (failure != null) {
                        Platform.runLater(() ->
                                onLaunchFailure.accept(failure)
                        );
                    } else {
                        ex.printStackTrace();
                    }
                }
            });

            thread.setDaemon(true);
            thread.setName("Vanta-Server-Launch");
            thread.start();
        });

        HBox card = new HBox(14, info, play);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("home-server-card");

        card.setPickOnBounds(true);
        card.setMouseTransparent(false);

        card.setOnMouseClicked(event -> {
            if (event.getTarget() == play
                    || instance == null) {
                return;
            }

            if (launchService.isRunning(instance)) {
                launchService.close(instance);
                return;
            }

            Thread thread = new Thread(() -> {
                try {
                    launchService.launch(
                            instance,
                            new ServerTarget(
                                    server.getHost(),
                                    server.getPort()
                            )
                    );
                } catch (Exception ex) {
                    LaunchFailure failure = launchService.getLastFailure();

                    if (failure != null) {
                        Platform.runLater(() ->
                                onLaunchFailure.accept(failure)
                        );
                    } else {
                        ex.printStackTrace();
                    }
                }
            });

            thread.setDaemon(true);
            thread.setName("Vanta-Server-Launch-Card");
            thread.start();
        });

        return card;
    }

    public void refreshAccount() {
        setAccount(accountService.getCurrentAccount());
    }

    public void setAccount(Account account) {
        if (account == null) {
            accountLabel.setText("No Microsoft account connected");
            return;
        }

        accountLabel.setText("Signed in as " + account.getUsername());
    }

    private void onAccountChanged(Account account) {
        Platform.runLater(() -> setAccount(account));
    }

    private void onLaunchStateChanged(LaunchService.LaunchState state) {
        Platform.runLater(() -> {
            refreshRecentInstances();
            refreshRecentServers();

            if (state == LaunchService.LaunchState.ERROR) {
                LaunchFailure failure = launchService.getLastFailure();
                if (failure != null) {
                    onLaunchFailure.accept(failure);
                }
            }
        });
    }
}
