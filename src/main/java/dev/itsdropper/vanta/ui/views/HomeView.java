package dev.itsdropper.vanta.ui.views;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.Cursor;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import dev.itsdropper.vanta.launcher.account.Account;
import dev.itsdropper.vanta.launcher.instance.InstanceManager;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.model.ServerHistoryEntry;
import dev.itsdropper.vanta.launcher.service.AccountService;
import dev.itsdropper.vanta.launcher.service.InstanceUsageManager;
import dev.itsdropper.vanta.launcher.service.LaunchFailure;
import dev.itsdropper.vanta.launcher.service.MultiLaunchService;
import dev.itsdropper.vanta.launcher.service.ServerHistoryManager;
import dev.itsdropper.vanta.launcher.service.ServerTarget;
import dev.itsdropper.vanta.launcher.service.LaunchService;
import dev.itsdropper.vanta.ui.AnimationUtils;
import dev.itsdropper.vanta.ui.components.IconView;
import dev.itsdropper.vanta.ui.components.InstanceCard;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

public class HomeView extends StackPane implements dev.itsdropper.vanta.ui.LauncherViewLifecycle {

    private final AccountService accountService;
    private final MultiLaunchService launchService;
    private final Consumer<LaunchFailure> onLaunchFailure;
    private final Consumer<Instance> onLaunchInstance;
    private final Runnable onCreateInstance;

    private final StackPane featuredContainer = new StackPane();
    private FlowPane recentServersList;
    private Label recentServersStatus;
    private final Label accountLabel;
    private final Label launchStatusLabel;
    private FlowPane recentList;
    private Label recentStatus;
    private final java.util.Map<String, Label> playtimeLabels =
            new java.util.HashMap<>();
    private final java.util.Map<String, Instance> displayedInstances =
            new java.util.HashMap<>();
    private final Timeline playtimeTimer;
    private final AccountService.AccountListener accountListener = this::onAccountChanged;
    private final Consumer<LaunchService.LaunchState> launchStateListener =
            this::onLaunchStateChanged;
    private final Runnable serverHistoryListener =
            () -> Platform.runLater(this::refreshRecentServers);
    private boolean listenersRegistered;
    private final AtomicLong instanceRefreshSequence = new AtomicLong();
    private final AtomicLong serverRefreshSequence = new AtomicLong();

    public HomeView(
            AccountService accountService,
            MultiLaunchService launchService,
            Consumer<LaunchFailure> onLaunchFailure,
            Consumer<Instance> onLaunchInstance,
            Runnable onCreateInstance
    ) {
        this.accountService = accountService;
        this.launchService = launchService;
        this.onLaunchFailure = onLaunchFailure;
        this.onLaunchInstance = onLaunchInstance;
        this.onCreateInstance = onCreateInstance;

        getStyleClass().addAll("page", "home-page");

        Label title = new Label("Home");
        title.getStyleClass().add("home-title");

        accountLabel = new Label("Checking account...");
        accountLabel.getStyleClass().add("home-account-label");

        VBox headerText = new VBox(4, title, accountLabel);
        FlowPane header = new FlowPane(16, 10);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setRowValignment(javafx.geometry.VPos.CENTER);
        header.getStyleClass().add("home-header");

        launchStatusLabel = new Label("READY TO PLAY");
        launchStatusLabel.getStyleClass().addAll("home-launch-indicator", "home-status-ready");
        launchStatusLabel.setAccessibleText("Minecraft launch status: ready to play");

        Button createInstanceButton = new Button("＋  NEW INSTANCE");
        createInstanceButton.getStyleClass().add("primary-button");
        AnimationUtils.installInteractiveAnimations(createInstanceButton);
        createInstanceButton.setMinHeight(40);
        createInstanceButton.setAccessibleText("Create a new Minecraft instance");
        createInstanceButton.setTooltip(new Tooltip("Create, import, or install a modpack"));
        createInstanceButton.setOnAction(event -> {
            if (this.onCreateInstance != null) this.onCreateInstance.run();
        });
        header.getChildren().addAll(headerText, launchStatusLabel, createInstanceButton);

        VBox featuredSection = createFeaturedSection();
        VBox recentSection = createRecentInstancesSection();
        VBox serverCard = createRecentServersCard();

        VBox contentBox = new VBox(
                22,
                header,
                featuredSection,
                recentSection,
                serverCard
        );
        contentBox.setFillWidth(true);
        contentBox.paddingProperty().bind(
                Bindings.createObjectBinding(() -> {
                    double width = getWidth();
                    double horizontalPadding = width < 900
                            ? 20
                            : width < 1200
                                    ? 28
                                    : 36;
                    return new Insets(
                            24,
                            horizontalPadding,
                            28,
                            horizontalPadding
                    );
                }, widthProperty())
        );
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

        getChildren().add(homeScroll);
        StackPane.setAlignment(homeScroll, Pos.TOP_LEFT);

        // Home content is a single interactive hierarchy.
        // Keeping the header inside the ScrollPane prevents an overlay node
        // from ever sitting above the recent-instance controls.

         playtimeTimer = new Timeline(
                new KeyFrame(
                        Duration.seconds(1),
                        event -> updatePlaytimeLabels()
                )
        );
        playtimeTimer.setCycleCount(Timeline.INDEFINITE);
        sceneProperty().addListener((observable, previousScene, nextScene) -> {
            if (nextScene == null) {
                playtimeTimer.stop();
            } else if (isVisible() && playtimeTimer.getStatus() != javafx.animation.Animation.Status.RUNNING) {
                playtimeTimer.play();
            }
        });

    }

    private VBox createFeaturedSection() {
        Label eyebrow = new Label("YOUR WORLD, ONE CLICK AWAY");
        eyebrow.getStyleClass().add("home-featured-section-eyebrow");

        Label subtitle = new Label("Pick up where you left off.");
        subtitle.getStyleClass().add("home-featured-section-subtitle");

        Label loading = new Label("Loading your Minecraft library...");
        loading.getStyleClass().add("home-featured-loading");
        featuredContainer.getStyleClass().add("home-featured-container");
        featuredContainer.getChildren().setAll(loading);
        featuredContainer.setMinHeight(220);

        VBox section = new VBox(10, eyebrow, subtitle, featuredContainer);
        section.getStyleClass().add("home-featured-section");
        section.setFillWidth(true);
        return section;
    }

    private VBox createRecentInstancesSection() {
        Label recentTitle = new Label("RECENTLY PLAYED");
        recentTitle.getStyleClass().add("home-section-title");

        Label recentSubtitle = new Label(
                "Jump back into the instances you played most recently."
        );
        recentSubtitle.getStyleClass().add("home-section-subtitle");

        recentList = new FlowPane();
        recentList.setHgap(10);
        recentList.setVgap(10);
        recentList.prefWrapLengthProperty().bind(recentList.widthProperty());
        recentList.setMaxWidth(Double.MAX_VALUE);
        recentList.setMinHeight(0);
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

        recentServersList = new FlowPane();
        recentServersList.setHgap(10);
        recentServersList.setVgap(10);
        recentServersList.prefWrapLengthProperty().bind(recentServersList.widthProperty());
        recentServersList.setMaxWidth(Double.MAX_VALUE);
        recentServersList.setMinHeight(0);
        recentServersList.getStyleClass().add("home-server-list");

        recentServersStatus = new Label("No server history yet.");
        recentServersStatus.getStyleClass().add("home-recent-status");

        card.getChildren().addAll(
                title,
                subtitle,
                recentServersList,
                recentServersStatus
        );

        return card;
    }

    /**
     * Refresh dashboard data whenever the user returns to Home. Instance
     * creation, imports and repairs can change the library without a launch
     * state transition, so relying only on launch listeners leaves stale UI.
     */
    public void onShown() {
        if (!listenersRegistered) {
            accountService.addListener(accountListener);
            launchService.addStateListener(launchStateListener);
            ServerHistoryManager.addListener(serverHistoryListener);
            listenersRegistered = true;
        }

        if (playtimeTimer.getStatus() != javafx.animation.Animation.Status.RUNNING) {
            playtimeTimer.play();
        }

        refreshAccount();
        updateLaunchStatus(launchService.getState());
        refreshRecentInstances();
        refreshRecentServers();
    }

    @Override
    public void onHidden() {
        if (listenersRegistered) {
            accountService.removeListener(accountListener);
            launchService.removeStateListener(launchStateListener);
            ServerHistoryManager.removeListener(serverHistoryListener);
            listenersRegistered = false;
        }

        playtimeTimer.stop();
        instanceRefreshSequence.incrementAndGet();
        serverRefreshSequence.incrementAndGet();
    }

    public void refreshRecentInstances() {
        long request = instanceRefreshSequence.incrementAndGet();
        Thread thread = new Thread(() -> {
            try {
                List<Instance> instances =
                        InstanceUsageManager.sortByLastPlayed(
                                InstanceManager.discoverInstances()
                        );

                Platform.runLater(() -> {
                    if (request != instanceRefreshSequence.get()) return;
                    renderRecentInstances(instances);
                });

            } catch (Throwable ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    if (request != instanceRefreshSequence.get()) return;
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
        featuredContainer.getChildren().clear();
        playtimeLabels.clear();
        displayedInstances.clear();

        List<Instance> recent = instances.stream()
                .filter(instance ->
                        InstanceUsageManager.getLastPlayed(instance) > 0
                )
                .limit(3)
                .toList();

        // A newly installed library still gets a useful featured launch surface.
        if (recent.isEmpty() && !instances.isEmpty()) {
            recent = List.of(instances.get(0));
        }

        if (recent.isEmpty()) {
            recentStatus.setText("No additional instances yet.");
            featuredContainer.getChildren().setAll(createFirstInstanceCard());
            return;
        }

        Instance featured = recent.get(0);
        featuredContainer.getChildren().setAll(createFeaturedInstanceCard(featured));

        List<Instance> secondary = recent.stream().skip(1).toList();
        for (Instance instance : secondary) {
            recentList.getChildren().add(createRecentCard(instance));
        }

        recentStatus.setText(
                recent.size() + " recently played instance"
                        + (recent.size() == 1 ? "" : "s")
        );
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

    private VBox createFirstInstanceCard() {
        Label eyebrow = new Label("YOUR NEXT ADVENTURE");
        eyebrow.getStyleClass().add("home-empty-eyebrow");

        Label title = new Label("Nothing installed. Yet.");
        title.getStyleClass().add("home-empty-title");

        Label description = new Label(
                "Create a clean Minecraft instance, import an existing profile, or start from a modpack. "
                        + "Each instance keeps its own mods, settings and worlds."
        );
        description.setWrapText(true);
        description.setMaxWidth(430);
        description.getStyleClass().add("home-empty-description");

        Button create = new Button("CREATE AN INSTANCE");
        create.getStyleClass().add("primary-button");
        AnimationUtils.installInteractiveAnimations(create);
        create.setMinHeight(40);
        create.setAccessibleText("Create a Minecraft instance");
        create.setTooltip(new Tooltip("Create, import, or install a modpack"));
        create.setOnAction(event -> {
            if (onCreateInstance != null) onCreateInstance.run();
        });

        VBox card = new VBox(12, eyebrow, title, description, create);
        card.setPadding(new Insets(24));
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.prefWidthProperty().bind(
                Bindings.createDoubleBinding(
                        () -> Math.max(0, featuredContainer.getWidth() - 2),
                        featuredContainer.widthProperty()
                )
        );
        card.getStyleClass().add("home-empty-card");
        return card;
    }

    private double responsiveCardWidth(FlowPane list) {
        double width = list.getWidth();

        if (width <= 1) {
            return 420;
        }

        if (width < 680) {
            return Math.max(0, width - 2);
        }

        return Math.max(0, (width - list.getHgap()) / 2.0);
    }

    private StackPane createFeaturedInstanceCard(Instance instance) {
        StackPane card = new StackPane();
        card.getStyleClass().add("home-featured-card");
        card.setMinHeight(220);
        card.setMaxWidth(Double.MAX_VALUE);

        HBox layout = new HBox(24);
        layout.setAlignment(Pos.CENTER_LEFT);
        layout.setPadding(new Insets(26, 30, 26, 30));

        StackPane iconFrame = new StackPane();
        iconFrame.getStyleClass().add("home-featured-icon-frame");
        iconFrame.setMinSize(104, 104);
        iconFrame.setPrefSize(104, 104);
        iconFrame.setMaxSize(104, 104);

        if ("CUSTOM".equalsIgnoreCase(instance.getIcon())
                && instance.getDirectory().resolve("icon.png").toFile().isFile()) {
            ImageView customIcon = new ImageView(new Image(
                    instance.getDirectory().resolve("icon.png").toUri().toString(),
                    88, 88, true, true
            ));
            customIcon.setFitWidth(88);
            customIcon.setFitHeight(88);
            customIcon.setPreserveRatio(true);
            iconFrame.getChildren().add(customIcon);
        } else {
            iconFrame.getChildren().add(InstanceCard.createIconGraphic(instance.getIcon(), 76));
        }

        VBox information = new VBox(10);
        information.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(information, Priority.ALWAYS);

        Label eyebrow = new Label("FEATURED INSTANCE");
        eyebrow.getStyleClass().add("home-featured-eyebrow");

        Label name = new Label(instance.getName());
        name.getStyleClass().add("home-featured-title");
        name.setWrapText(true);

        Label metadata = new Label(
                "MINECRAFT " + instance.getMinecraftVersion()
                        + "   /   " + instance.getDisplayLoader()
        );
        metadata.getStyleClass().add("home-featured-meta");

        Label playtime = new Label(
                InstanceUsageManager.formatPlaytime(
                        InstanceUsageManager.getPlaytimeSeconds(instance)
                                + launchService.getSessionPlaytimeSeconds(instance)
                ) + " PLAYED"
        );
        playtime.getStyleClass().add("home-featured-playtime");
        playtimeLabels.put(instance.getId(), playtime);
        displayedInstances.put(instance.getId(), instance);

        information.getChildren().addAll(eyebrow, name, metadata, playtime);

        boolean running = launchService.isRunning(instance);
        Button play = new Button(running ? "CLOSE GAME" : "PLAY NOW");
        play.getStyleClass().add("home-featured-play-button");
        play.setMinWidth(142);
        play.setMinHeight(48);
        play.setAccessibleText((running ? "Close " : "Launch ") + instance.getName());
        play.setTooltip(new Tooltip((running ? "Close " : "Launch ") + instance.getName()));
        play.setOnAction(event -> launchOrCloseInstance(instance));
        AnimationUtils.installInteractiveAnimations(play);

        VBox action = new VBox(8, play);
        action.setAlignment(Pos.CENTER_RIGHT);

        layout.getChildren().addAll(iconFrame, information, action);
        card.getChildren().add(layout);
        return card;
    }

    private HBox createRecentCard(Instance instance) {
        StackPane icon = new StackPane();
        icon.getStyleClass().add("home-recent-icon");
        ImageView customIcon = new ImageView();
        customIcon.setFitWidth(30);
        customIcon.setFitHeight(30);
        customIcon.setPreserveRatio(true);
        if ("CUSTOM".equalsIgnoreCase(instance.getIcon())
                && instance.getDirectory().resolve("icon.png").toFile().isFile()) {
            customIcon.setImage(new Image(instance.getDirectory().resolve("icon.png").toUri().toString(), 30, 30, true, true));
            icon.getChildren().add(customIcon);
        } else {
            icon.getChildren().add(InstanceCard.createIconGraphic(instance.getIcon(), 30));
        }

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
        play.setAccessibleText(
                (launchService.isRunning(instance) ? "Close " : "Launch ")
                        + instance.getName()
        );
        play.setTooltip(new Tooltip(
                (launchService.isRunning(instance) ? "Close " : "Launch ")
                        + instance.getName()
        ));
        play.setDisable(false);
        play.setMouseTransparent(false);

        play.setDisable(instance == null);

        play.setOnAction(event -> launchOrCloseInstance(instance));

        AnimationUtils.installInteractiveAnimations(play);
        HBox card = new HBox(14, icon, info, play);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setMinWidth(0);
        card.prefWidthProperty().bind(
                Bindings.createDoubleBinding(
                        () -> responsiveCardWidth(recentList),
                        recentList.widthProperty()
                )
        );
        card.setMaxWidth(Double.MAX_VALUE);
        card.setPickOnBounds(true);
        card.setMouseTransparent(false);
        card.getStyleClass().add("home-recent-card");

        // The card is a secondary hit target; the explicit button remains primary.
        card.setOnMouseClicked(event -> {
            if (event.getTarget() == play || play.isHover()) {
                return;
            }
            launchOrCloseInstance(instance);
        });

        return card;
    }

    private void launchOrCloseInstance(Instance instance) {
        if (instance == null) {
            return;
        }

        if (launchService.isRunning(instance)) {
            launchService.close(instance);
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                // Server quick-play is handled by the separate server history section.
                launchService.launch(instance);
            } catch (Throwable ex) {
                ex.printStackTrace();
                LaunchFailure failure = launchService.getLastFailure();

                Platform.runLater(() -> {
                    if (failure != null) {
                        onLaunchFailure.accept(failure);
                    } else {
                        onLaunchFailure.accept(new LaunchFailure(
                                "Minecraft failed to launch",
                                ex.getMessage() != null
                                        ? ex.getMessage()
                                        : "Vanta could not start this instance.",
                                ""
                        ));
                    }
                });
            }
        }, "Vanta-Home-Launch");
        thread.setDaemon(true);
        thread.start();
    }

    public void refreshRecentServers() {
        long request = serverRefreshSequence.incrementAndGet();
        Thread thread = new Thread(() -> {
            try {
                List<Instance> instances =
                        InstanceManager.discoverInstances();

                List<ServerHistoryEntry> history =
                        ServerHistoryManager.getRecent();

                Platform.runLater(() -> {
                    if (request != serverRefreshSequence.get()) return;
                    renderRecentServers(instances, history);
                });
            } catch (Throwable ex) {
                ex.printStackTrace();
                Platform.runLater(() -> {
                    if (request != serverRefreshSequence.get()) return;
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
                            entry.getInstanceId() != null
                                    && candidate.getId().equals(entry.getInstanceId())
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
        ServerTarget runningTarget = launchService.getRunningServerTarget();
        boolean serverRunning =
                instanceAvailable
                        && launchService.isRunning(instance)
                        && runningTarget != null
                        && runningTarget.host().equalsIgnoreCase(server.getHost())
                        && runningTarget.port() == server.getPort();

        Button play = new Button(
                serverRunning ? "CLOSE" : "PLAY"
        );
        play.getStyleClass().add(
                serverRunning
                        ? "home-recent-stop"
                        : "home-recent-play"
        );
        play.setMinWidth(84);
        play.setPickOnBounds(true);
        play.setMouseTransparent(false);
        play.setCursor(Cursor.HAND);
        play.setDisable(!instanceAvailable);
        String serverActionDescription = !instanceAvailable
                ? "Unavailable: the instance for " + server.getDisplayName() + " is not installed"
                : (serverRunning ? "Close Minecraft on " : "Join ")
                        + server.getDisplayName()
                        + (serverRunning ? "" : " using " + instance.getName());
        play.setAccessibleText(serverActionDescription);
        play.setTooltip(new Tooltip(serverActionDescription));

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
                    // Server cards are intentionally different from instance
                    // cards: they always launch the selected server target.
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

        AnimationUtils.installInteractiveAnimations(play);
        HBox card = new HBox(14, info, play);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(12, 14, 12, 14));
        card.setMinWidth(0);
        card.prefWidthProperty().bind(
                Bindings.createDoubleBinding(
                        () -> responsiveCardWidth(recentServersList),
                        recentServersList.widthProperty()
                )
        );
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

    private void updateLaunchStatus(LaunchService.LaunchState state) {
        String label;
        String style;
        switch (state) {
            case PREPARING, STARTING -> {
                label = "STARTING MINECRAFT";
                style = "home-status-progress";
            }
            case RUNNING -> {
                label = "MINECRAFT RUNNING";
                style = "home-status-running";
            }
            case CLOSING -> {
                label = "CLOSING MINECRAFT";
                style = "home-status-progress";
            }
            case ERROR -> {
                label = "LAUNCH FAILED";
                style = "home-status-error";
            }
            case IDLE -> {
                label = "READY TO PLAY";
                style = "home-status-ready";
            }
            default -> {
                label = "VANTA READY";
                style = "home-status-ready";
            }
        }
        launchStatusLabel.setText(label);
        launchStatusLabel.getStyleClass().removeAll(
                "home-status-ready", "home-status-progress",
                "home-status-running", "home-status-error"
        );
        launchStatusLabel.getStyleClass().add(style);
        launchStatusLabel.setAccessibleText("Minecraft launch status: " + label.toLowerCase());
    }

    private void onLaunchStateChanged(LaunchService.LaunchState state) {
        Platform.runLater(() -> {
            LaunchService.LaunchState aggregateState = launchService.getState();
            updateLaunchStatus(
                    aggregateState == LaunchService.LaunchState.IDLE
                            && state == LaunchService.LaunchState.ERROR
                            ? LaunchService.LaunchState.ERROR
                            : aggregateState
            );
            // Preparation/closing events only affect the status chip.
            // Avoid rescanning the instance directory for every transition.
            if (state == LaunchService.LaunchState.RUNNING
                    || state == LaunchService.LaunchState.IDLE
                    || state == LaunchService.LaunchState.ERROR) {
                refreshRecentInstances();
                refreshRecentServers();
            }

            if (state == LaunchService.LaunchState.ERROR) {
                LaunchFailure failure = launchService.getLastFailure();
                if (failure != null) {
                    onLaunchFailure.accept(failure);
                }
            }
        });
    }
}