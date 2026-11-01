package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.async.UiExecutor;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.Cursor;
import javafx.scene.AccessibleRole;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
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
import dev.itsdropper.vanta.ui.components.MinecraftBackdrop;

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
            () -> UiExecutor.shared().dispatchToUi(this::refreshRecentServers);
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
        setStyle("-fx-background-color: #080d0b;");

        Label kicker = new Label("VANTA  /  YOUR MINECRAFT LIBRARY");
        kicker.setStyle("-fx-text-fill: #87a994; -fx-font-size: 9px; -fx-font-weight: 900; -fx-letter-spacing: 2.1px;");

        Label title = new Label("Your worlds, ready.");
        title.getStyleClass().add("home-title");
        title.setStyle("-fx-text-fill: #f0f4ef; -fx-font-size: 34px; -fx-font-weight: 800;");

        accountLabel = new Label("Checking account...");
        accountLabel.getStyleClass().add("home-account-label");
        accountLabel.setStyle("-fx-text-fill: #9aa99f; -fx-font-size: 11px;");

        VBox headerText = new VBox(7, kicker, title, accountLabel);
        headerText.setAlignment(Pos.CENTER_LEFT);

        launchStatusLabel = new Label("READY TO PLAY");
        launchStatusLabel.getStyleClass().addAll("home-launch-indicator", "home-status-ready");
        launchStatusLabel.setStyle("-fx-background-color: rgba(112,169,132,.13); -fx-border-color: rgba(139,190,153,.28); -fx-border-radius: 20px; -fx-background-radius: 20px; -fx-padding: 8px 12px; -fx-text-fill: #b8d8c0; -fx-font-size: 9px; -fx-font-weight: 900; -fx-letter-spacing: 1px;");
        launchStatusLabel.setAccessibleText("Minecraft launch status: ready to play");

        Button createInstanceButton = new Button("＋   CREATE INSTANCE");
        createInstanceButton.getStyleClass().add("primary-button");
        createInstanceButton.setStyle("-fx-background-color: #d7e7d9; -fx-text-fill: #102018; -fx-font-size: 10px; -fx-font-weight: 900; -fx-background-radius: 6px; -fx-padding: 0 18px; -fx-min-height: 40px; -fx-cursor: hand;");
        AnimationUtils.installInteractiveAnimations(createInstanceButton);
        createInstanceButton.setMinHeight(40);
        createInstanceButton.setAccessibleText("Create a new Minecraft instance");
        createInstanceButton.setTooltip(new Tooltip("Create, import, or install a modpack"));
        createInstanceButton.setOnAction(event -> {
            if (this.onCreateInstance != null) this.onCreateInstance.run();
        });

        HBox headerActions = new HBox(12, launchStatusLabel, createInstanceButton);
        headerActions.setAlignment(Pos.CENTER_RIGHT);
        Region headerSpacer = new Region();
        HBox.setHgrow(headerSpacer, Priority.ALWAYS);
        HBox header = new HBox(20, headerText, headerSpacer, headerActions);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(2, 0, 8, 0));
        header.getStyleClass().add("home-header");

        VBox featuredSection = createFeaturedSection();
        VBox recentSection = createRecentInstancesSection();
        VBox serverCard = createRecentServersCard();
        HBox lowerWorkspace = new HBox(16, recentSection, serverCard);
        lowerWorkspace.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(recentSection, Priority.ALWAYS);
        HBox.setHgrow(serverCard, Priority.ALWAYS);
        recentSection.setMinWidth(0);
        serverCard.setMinWidth(0);
        recentSection.prefWidthProperty().bind(lowerWorkspace.widthProperty().multiply(0.56));
        serverCard.prefWidthProperty().bind(lowerWorkspace.widthProperty().multiply(0.44));

        VBox contentBox = new VBox(24, header, featuredSection, lowerWorkspace);
        contentBox.setStyle("-fx-background-color: transparent;");
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
        Label eyebrow = new Label("THE WORLD YOU LEFT BEHIND");
        eyebrow.setStyle("-fx-text-fill: #9dbba5; -fx-font-size: 9px; -fx-font-weight: 900; -fx-letter-spacing: 2px;");
        Label subtitle = new Label("Your last session is always one click away.");
        subtitle.setStyle("-fx-text-fill: #819087; -fx-font-size: 11px;");

        Label loading = new Label("Preparing your worlds...");
        loading.setStyle("-fx-text-fill: #a9b8ad; -fx-font-size: 12px; -fx-padding: 22px;");
        featuredContainer.getStyleClass().add("home-featured-container");
        featuredContainer.getChildren().setAll(loading);
        featuredContainer.setMinHeight(292);
        featuredContainer.setMaxWidth(Double.MAX_VALUE);

        VBox section = new VBox(11, eyebrow, subtitle, featuredContainer);
        section.getStyleClass().add("home-featured-section");
        section.setFillWidth(true);
        return section;
    }

    private VBox createRecentInstancesSection() {
        Label recentTitle = new Label("YOUR INSTANCES");
        recentTitle.setStyle("-fx-text-fill: #edf2ed; -fx-font-size: 15px; -fx-font-weight: 900; -fx-letter-spacing: .4px;");
        Label recentSubtitle = new Label("Jump back into a world or close a running session.");
        recentSubtitle.setStyle("-fx-text-fill: #849389; -fx-font-size: 10px;");

        recentList = new FlowPane();
        recentList.setHgap(10);
        recentList.setVgap(10);
        recentList.prefWrapLengthProperty().bind(recentList.widthProperty());
        recentList.setMaxWidth(Double.MAX_VALUE);
        recentList.setMinHeight(0);
        recentList.getStyleClass().add("home-recent-list");

        recentStatus = new Label("SYNCING LOCAL INSTANCES");
        recentStatus.setStyle("-fx-text-fill: #728176; -fx-font-size: 8px; -fx-font-weight: 900; -fx-letter-spacing: 1.2px;");

        VBox section = new VBox(12, recentTitle, recentSubtitle, recentList, recentStatus);
        section.setFillWidth(true);
        section.setPadding(new Insets(19));
        section.setStyle("-fx-background-color: #0d1511; -fx-background-radius: 10px; -fx-border-color: #263a2e; -fx-border-radius: 10px;");
        section.getStyleClass().add("home-recent-section");
        return section;
    }

    private VBox createRecentServersCard() {
        VBox card = new VBox(12);
        card.setPadding(new Insets(19));
        card.setStyle("-fx-background-color: #0d1511; -fx-background-radius: 10px; -fx-border-color: #263a2e; -fx-border-radius: 10px;");
        card.getStyleClass().add("home-recent-servers-card");

        HBox heading = new HBox(9);
        Label marker = new Label("↗");
        marker.setStyle("-fx-text-fill: #a6c5ad; -fx-font-size: 16px; -fx-font-weight: 900;");
        Label title = new Label("SERVER HISTORY");
        title.setStyle("-fx-text-fill: #edf2ed; -fx-font-size: 13px; -fx-font-weight: 900; -fx-letter-spacing: .8px;");
        heading.setAlignment(Pos.CENTER_LEFT);
        heading.getChildren().addAll(marker, title);

        Label subtitle = new Label("Rejoin a multiplayer world with its original instance.");
        subtitle.setWrapText(true);
        subtitle.setStyle("-fx-text-fill: #849389; -fx-font-size: 10px;");

        recentServersList = new FlowPane();
        recentServersList.setHgap(8);
        recentServersList.setVgap(8);
        recentServersList.prefWrapLengthProperty().bind(recentServersList.widthProperty());
        recentServersList.setMaxWidth(Double.MAX_VALUE);
        recentServersList.setMinHeight(0);
        recentServersList.getStyleClass().add("home-server-list");

        recentServersStatus = new Label("Your recently joined servers will appear here.");
        recentServersStatus.setWrapText(true);
        recentServersStatus.setStyle("-fx-text-fill: #728176; -fx-font-size: 9px;");

        card.getChildren().addAll(heading, subtitle, recentServersList, recentServersStatus);
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
        UiExecutor.shared().execute(() -> {
            try {
                List<Instance> instances =
                        InstanceUsageManager.sortByLastPlayed(
                                InstanceManager.discoverInstances()
                        );

                UiExecutor.shared().dispatchToUi(() -> {
                    if (request != instanceRefreshSequence.get()) return;
                    renderRecentInstances(instances);
                });

            } catch (Throwable ex) {
                ex.printStackTrace();
                UiExecutor.shared().dispatchToUi(() -> {
                    if (request != instanceRefreshSequence.get()) return;
                    recentList.getChildren().clear();
                    recentStatus.setText("Failed to load recent instances.");
                    featuredContainer.getChildren().setAll(createInstanceLoadErrorCard());
                });
            }
        });

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

    private VBox createInstanceLoadErrorCard() {
        Label eyebrow = new Label("LIBRARY UNAVAILABLE");
        eyebrow.getStyleClass().add("home-empty-eyebrow");

        Label title = new Label("Couldn't load your instances.");
        title.getStyleClass().add("home-load-error-title");

        Label description = new Label(
                "Vanta couldn't read the local instance library. Your installations have not been changed."
        );
        description.setWrapText(true);
        description.setMaxWidth(480);
        description.getStyleClass().add("home-load-error-description");

        Button retry = new Button("TRY AGAIN");
        retry.getStyleClass().add("secondary-button");
        retry.setMinHeight(38);
        retry.setAccessibleText("Retry loading Minecraft instances");
        retry.setOnAction(event -> refreshRecentInstances());
        AnimationUtils.installInteractiveAnimations(retry);

        VBox card = new VBox(12, eyebrow, title, description, retry);
        card.setPadding(new Insets(24));
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.prefWidthProperty().bind(
                Bindings.createDoubleBinding(
                        () -> Math.max(0, featuredContainer.getWidth() - 2),
                        featuredContainer.widthProperty()
                )
        );
        card.getStyleClass().add("home-load-error-card");
        return card;
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
        card.setMinHeight(292);
        card.setPrefHeight(318);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setStyle("-fx-background-color: #122019; -fx-background-radius: 13px; -fx-border-color: #3a5745; -fx-border-radius: 13px; -fx-border-width: 1px;");

        MinecraftBackdrop landscape = new MinecraftBackdrop();
        landscape.setOpacity(1);

        StackPane scrim = new StackPane();
        scrim.setMouseTransparent(true);
        scrim.setStyle("-fx-background-color: linear-gradient(to right, rgba(5,12,10,.97) 0%, rgba(7,15,12,.88) 38%, rgba(7,15,12,.40) 72%, rgba(7,15,12,.22) 100%), linear-gradient(to top, rgba(5,12,10,.78), transparent 62%); -fx-background-radius: 13px;");

        Label eyebrow = new Label("CONTINUE YOUR ADVENTURE");
        eyebrow.setStyle("-fx-text-fill: #b5d5bd; -fx-font-size: 9px; -fx-font-weight: 900; -fx-letter-spacing: 2px;");

        Label state = new Label(launchService.isRunning(instance) ? "●  CURRENTLY RUNNING" : "●  LAST PLAYED");
        state.setStyle("-fx-text-fill: #d0e5d4; -fx-font-size: 9px; -fx-font-weight: 800; -fx-background-color: rgba(14,34,23,.72); -fx-border-color: rgba(160,205,170,.30); -fx-border-radius: 20px; -fx-background-radius: 20px; -fx-padding: 7px 10px;");

        Region topSpacer = new Region();
        HBox.setHgrow(topSpacer, Priority.ALWAYS);
        HBox topLine = new HBox(12, eyebrow, topSpacer, state);
        topLine.setAlignment(Pos.CENTER_LEFT);

        StackPane iconFrame = new StackPane();
        iconFrame.setMinSize(76, 76);
        iconFrame.setPrefSize(76, 76);
        iconFrame.setMaxSize(76, 76);
        iconFrame.setStyle("-fx-background-color: rgba(12,27,20,.78); -fx-background-radius: 11px; -fx-border-color: rgba(195,224,201,.28); -fx-border-radius: 11px; -fx-border-width: 1px;");
        if ("CUSTOM".equalsIgnoreCase(instance.getIcon())
                && instance.getDirectory().resolve("icon.png").toFile().isFile()) {
            ImageView customIcon = new ImageView(new Image(
                    instance.getDirectory().resolve("icon.png").toUri().toString(),
                    64, 64, true, true
            ));
            customIcon.setFitWidth(62);
            customIcon.setFitHeight(62);
            customIcon.setPreserveRatio(true);
            iconFrame.getChildren().add(customIcon);
        } else {
            iconFrame.getChildren().add(InstanceCard.createIconGraphic(instance.getIcon(), 54));
        }

        Label name = new Label(instance.getName());
        name.setWrapText(true);
        name.setMinWidth(0);
        name.setStyle("-fx-text-fill: #f3f7f1; -fx-font-size: 32px; -fx-font-weight: 900;");

        Label version = new Label("MINECRAFT  " + instance.getMinecraftVersion());
        version.setStyle("-fx-text-fill: #d5e0d7; -fx-font-size: 10px; -fx-font-weight: 800; -fx-letter-spacing: .8px;");

        Label loader = new Label(instance.getDisplayLoader());
        loader.setStyle("-fx-text-fill: #c2d8c8; -fx-font-size: 10px; -fx-background-color: rgba(133,181,145,.16); -fx-background-radius: 4px; -fx-padding: 5px 8px;");

        Label playtime = new Label(
                InstanceUsageManager.formatPlaytime(
                        InstanceUsageManager.getPlaytimeSeconds(instance)
                                + launchService.getSessionPlaytimeSeconds(instance)
                ) + " IN GAME"
        );
        playtime.setStyle("-fx-text-fill: #a6b9aa; -fx-font-size: 9px; -fx-font-weight: 800; -fx-letter-spacing: 1px;");
        playtimeLabels.put(instance.getId(), playtime);
        displayedInstances.put(instance.getId(), instance);

        HBox metadata = new HBox(9, version, loader);
        metadata.setAlignment(Pos.CENTER_LEFT);
        VBox identity = new VBox(9, name, metadata, playtime);
        identity.setAlignment(Pos.CENTER_LEFT);
        identity.setMinWidth(0);
        HBox.setHgrow(identity, Priority.ALWAYS);

        boolean running = launchService.isRunning(instance);
        Button play = new Button(running ? "CLOSE GAME   →" : "PLAY NOW   →");
        play.getStyleClass().add("home-featured-play-button");
        play.setStyle("-fx-background-color: #dcebdd; -fx-text-fill: #102018; -fx-font-size: 10px; -fx-font-weight: 900; -fx-background-radius: 6px; -fx-padding: 0 22px; -fx-min-height: 48px; -fx-cursor: hand;");
        play.setMinWidth(158);
        play.setMinHeight(48);
        play.setAccessibleText((running ? "Close " : "Launch ") + instance.getName());
        play.setTooltip(new Tooltip((running ? "Close " : "Launch ") + instance.getName()));
        play.setOnAction(event -> launchOrCloseInstance(instance));
        AnimationUtils.installInteractiveAnimations(play);

        VBox launchPanel = new VBox(8, play);
        launchPanel.setAlignment(Pos.CENTER_RIGHT);
        launchPanel.setMinWidth(158);

        HBox bottomLine = new HBox(20, iconFrame, identity, launchPanel);
        bottomLine.setAlignment(Pos.CENTER_LEFT);

        VBox foreground = new VBox(0, topLine, new Region(), bottomLine);
        foreground.setPadding(new Insets(25, 28, 25, 28));
        foreground.setFillWidth(true);
        VBox.setVgrow(foreground.getChildren().get(1), Priority.ALWAYS);

        card.getChildren().setAll(landscape, scrim, foreground);
        StackPane.setAlignment(foreground, Pos.CENTER_LEFT);

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
        card.setAccessibleRole(AccessibleRole.BUTTON);
        card.setAccessibleText("Launch or close " + instance.getName());
        card.setFocusTraversable(true);
        card.setOnMouseClicked(event -> {
            if (event.getTarget() == play || play.isHover()) {
                return;
            }
            launchOrCloseInstance(instance);
        });
        card.setOnKeyPressed(event -> {
            if (event.getTarget() == card
                    && (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)) {
                launchOrCloseInstance(instance);
                event.consume();
            }
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

        UiExecutor.shared().execute(() -> {
            try {
                // Server quick-play is handled by the separate server history section.
                launchService.launch(instance);
            } catch (Throwable ex) {
                ex.printStackTrace();
                LaunchFailure failure = launchService.getLastFailure();

                UiExecutor.shared().dispatchToUi(() -> {
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
        });
    }

    public void refreshRecentServers() {
        long request = serverRefreshSequence.incrementAndGet();
        UiExecutor.shared().execute(() -> {
            try {
                List<Instance> instances =
                        InstanceManager.discoverInstances();

                List<ServerHistoryEntry> history =
                        ServerHistoryManager.getRecent();

                UiExecutor.shared().dispatchToUi(() -> {
                    if (request != serverRefreshSequence.get()) return;
                    renderRecentServers(instances, history);
                });
            } catch (Throwable ex) {
                ex.printStackTrace();
                UiExecutor.shared().dispatchToUi(() -> {
                    if (request != serverRefreshSequence.get()) return;
                    recentServersList.getChildren().clear();
                    recentServersStatus.setText(
                            "Failed to load recent servers."
                    );
                });
            }
        });

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

        play.setOnAction(event -> launchServer(instance, server));

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

        card.setAccessibleRole(AccessibleRole.BUTTON);
        card.setAccessibleText("Join " + server.getDisplayName()
                + (instanceAvailable ? " using " + instance.getName() : ", unavailable"));
        card.setFocusTraversable(instanceAvailable);
        card.setOnMouseClicked(event -> {
            if (event.getTarget() == play || instance == null) {
                return;
            }
            launchServer(instance, server);
        });
        card.setOnKeyPressed(event -> {
            if (event.getTarget() == card && instanceAvailable
                    && (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)) {
                launchServer(instance, server);
                event.consume();
            }
        });

        return card;
    }

    private void launchServer(Instance instance, ServerHistoryEntry server) {
        if (instance == null) {
            return;
        }
        if (launchService.isRunning(instance)) {
            launchService.close(instance);
            return;
        }

        UiExecutor.shared().execute(() -> {
            try {
                launchService.launch(
                        instance,
                        new ServerTarget(server.getHost(), server.getPort())
                );
            } catch (Exception ex) {
                LaunchFailure failure = launchService.getLastFailure();
                if (failure != null) {
                    UiExecutor.shared().dispatchToUi(() -> onLaunchFailure.accept(failure));
                } else {
                    ex.printStackTrace();
                }
            }
        });
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
        UiExecutor.shared().dispatchToUi(() -> setAccount(account));
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
        UiExecutor.shared().dispatchToUi(() -> {
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