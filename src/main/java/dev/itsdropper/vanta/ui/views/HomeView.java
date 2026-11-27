package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.async.UiExecutor;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.beans.binding.Bindings;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.AccessibleRole;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
import javafx.scene.shape.Rectangle;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
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
import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.components.IconView;
import dev.itsdropper.vanta.ui.kit.ActionTile;
import dev.itsdropper.vanta.ui.kit.Badge;
import dev.itsdropper.vanta.ui.kit.Callout;
import dev.itsdropper.vanta.ui.kit.CoverArt;
import dev.itsdropper.vanta.ui.kit.IconButton;
import dev.itsdropper.vanta.ui.kit.SectionHeader;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Home: a pixel-art hero for the last played instance with one Play action,
 * a compact list of recent instances, and quick actions.
 */
public class HomeView extends StackPane implements dev.itsdropper.vanta.ui.LauncherViewLifecycle {

    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("MMM d");

    private final AccountService accountService;
    private final MultiLaunchService launchService;
    private final Consumer<LaunchFailure> onLaunchFailure;
    private final Consumer<Instance> onLaunchInstance;
    private final Runnable onCreateInstance;

    private final StackPane featuredContainer = new StackPane();
    private VBox recentServersList;
    private VBox serversColumn;
    private Label recentServersStatus;
    private final Label accountLabel;
    private final Label launchStatusLabel;
    private FlowPane recentList;
    private String featuredId;
    private final java.util.Map<String, Node> recentTiles = new java.util.LinkedHashMap<>();
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

        Label title = new Label(Localization.text("home.title"));
        title.getStyleClass().add("page-header-title");

        accountLabel = new Label(Localization.text("home.accountChecking"));
        accountLabel.getStyleClass().add("page-header-subtitle");

        launchStatusLabel = Badge.status(Localization.text("home.readyToPlay"), Badge.Tone.SUCCESS);
        launchStatusLabel.getStyleClass().add("home-status");
        launchStatusLabel.setAccessibleText(Localization.text("home.launchStatusReady"));

        VBox featuredSection = createFeaturedSection();
        VBox recentSection = createRecentInstancesSection();
        VBox sideColumn = createSideColumn();

        VBox contentBox = new VBox(28, featuredSection, recentSection, sideColumn);
        contentBox.setFillWidth(true);
        contentBox.paddingProperty().bind(
                Bindings.createObjectBinding(() -> {
                    double gutter = dev.itsdropper.vanta.ui.ResponsiveLayout.gutter(getWidth());
                    return new Insets(20, gutter, 24, gutter);
                }, widthProperty())
        );
        contentBox.setMaxWidth(Double.MAX_VALUE);

        ScrollPane homeScroll = new ScrollPane(contentBox);
        homeScroll.setFitToWidth(true);
        homeScroll.setFitToHeight(false);
        homeScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        homeScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        homeScroll.setPannable(false);
        homeScroll.getStyleClass().add("home-content-scroll");

        getChildren().add(homeScroll);
        StackPane.setAlignment(homeScroll, Pos.TOP_LEFT);

        playtimeTimer = new Timeline(
                new KeyFrame(Duration.seconds(1), event -> updatePlaytimeLabels())
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

    // ------------------------------------------------------------ sections

    private VBox createFeaturedSection() {
        Label loading = new Label(Localization.text("home.libraryLoading"));
        loading.getStyleClass().add("home-muted");
        featuredContainer.getStyleClass().add("home-featured-container");
        featuredContainer.getChildren().setAll(loading);
        featuredContainer.setMinHeight(260);

        VBox section = new VBox(featuredContainer);
        section.setFillWidth(true);
        return section;
    }

    private VBox createRecentInstancesSection() {
        recentList = new dev.itsdropper.vanta.ui.kit.CardGrid(14, 210, 4);
        recentList.setMinHeight(0);
        recentList.getStyleClass().add("home-tiles");

        recentStatus = new Label(Localization.text("home.instancesLoading"));
        recentStatus.getStyleClass().add("home-muted");
        recentStatus.setWrapText(true);

        VBox section = new VBox(12, new SectionHeader(Localization.text("home.recentTitle")), recentList, recentStatus);
        section.setFillWidth(true);
        return section;
    }

    private VBox createSideColumn() {
        recentServersList = new VBox(2);
        recentServersList.setFillWidth(true);
        recentServersList.setMinHeight(0);
        recentServersList.getStyleClass().add("home-list");

        recentServersStatus = new Label(Localization.text("home.noServerHistory"));
        recentServersStatus.getStyleClass().add("home-muted");
        recentServersStatus.setWrapText(true);

        VBox serverCard = new VBox(8, recentServersList, recentServersStatus);
        serverCard.getStyleClass().add("home-panel");
        serverCard.setPadding(new Insets(8));

        VBox column = new VBox(12,
                new SectionHeader(Localization.text("home.serversTitle")),
                serverCard);
        column.setFillWidth(true);
        column.setPadding(Insets.EMPTY);
        serversColumn = column;
        return column;
    }

    /** Fills the dashboard with stable explanatory content for CI screenshots. */
    public void prepareScreenshotPreview() {
        accountLabel.setText(Localization.text("home.accountFallback"));
        setLaunchBadge(Localization.text("home.preview"), Badge.Tone.NEUTRAL);
        recentList.getChildren().clear();
        recentStatus.setManaged(true);
        recentStatus.setVisible(true);
        recentStatus.setText(Localization.text("home.previewRecentInstances"));
        recentServersList.getChildren().clear();
        recentServersStatus.setText(Localization.text("home.previewRecentServers"));

        // Same actionable first-run state a real empty library shows; screenshot
        // mode must not invent a Minecraft installation.
        featuredContainer.getChildren().setAll(createFirstInstanceCard());
    }

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

    // ------------------------------------------------------------ instances

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
                    recentStatus.setManaged(true);
                    recentStatus.setVisible(true);
                    recentStatus.setText(Localization.text("home.recentLoadFailed"));
                    featuredContainer.getChildren().setAll(createInstanceLoadErrorCard());
                });
            }
        });
    }

    private void renderRecentInstances(List<Instance> instances) {
        recentList.getChildren().clear();
        recentStatus.setManaged(true);
        recentStatus.setVisible(true);
        featuredContainer.getChildren().clear();
        playtimeLabels.clear();
        displayedInstances.clear();

        List<Instance> recent = instances.stream()
                .filter(instance -> InstanceUsageManager.getLastPlayed(instance) > 0)
                .limit(6)
                .toList();

        // A newly installed library still gets a useful featured launch surface.
        if (recent.isEmpty() && !instances.isEmpty()) {
            recent = List.of(instances.get(0));
        }

        if (recent.isEmpty()) {
            recentStatus.setText(Localization.text("home.noAdditionalInstances"));
            featuredContainer.getChildren().setAll(createFirstInstanceCard());
            return;
        }

        recentTiles.clear();
        for (Instance instance : recent) {
            Node tile = createRecentTile(instance);
            recentTiles.put(instance.getId(), tile);
            recentList.getChildren().add(tile);
        }
        recentList.getChildren().add(createNewTile());
        selectFeatured(recent.get(0));

        recentStatus.setText("");
        recentStatus.setVisible(false);
        recentStatus.setManaged(false);
    }

    private void updatePlaytimeLabels() {
        for (java.util.Map.Entry<String, Label> entry : playtimeLabels.entrySet()) {
            Instance instance = displayedInstances.get(entry.getKey());
            if (instance == null) {
                continue;
            }
            entry.getValue().setText(playtimeText(instance));
        }
    }

    private String playtimeText(Instance instance) {
        return InstanceUsageManager.formatPlaytime(
                InstanceUsageManager.getPlaytimeSeconds(instance)
                        + launchService.getSessionPlaytimeSeconds(instance));
    }

    private String lastPlayedText(Instance instance) {
        long millis = InstanceUsageManager.getLastPlayed(instance);
        if (millis <= 0) {
            return Localization.text("home.neverPlayed");
        }
        LocalDate day = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate();
        LocalDate today = LocalDate.now();
        if (day.equals(today)) {
            return Localization.text("home.today");
        }
        if (day.equals(today.minusDays(1))) {
            return Localization.text("home.yesterday");
        }
        return SHORT_DATE.format(day);
    }

    // ------------------------------------------------------------ hero

    /** Hero shell: the instance photo fills the panel, copy sits on a scrim at the bottom left. */
    private StackPane heroShell(CoverArt art, VBox copy) {
        art.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Region scrim = new Region();
        scrim.getStyleClass().add("home-hero-scrim");
        scrim.setMouseTransparent(true);

        StackPane hero = new StackPane(art, scrim, copy, launchStatusLabel);
        hero.getStyleClass().add("home-hero");
        hero.setMinHeight(340);
        hero.setPrefHeight(340);
        hero.setMaxHeight(340);
        hero.setMaxWidth(Double.MAX_VALUE);

        copy.setMaxWidth(640);
        copy.setMaxHeight(Region.USE_PREF_SIZE);
        StackPane.setAlignment(copy, Pos.BOTTOM_LEFT);
        StackPane.setAlignment(launchStatusLabel, Pos.TOP_RIGHT);
        StackPane.setMargin(launchStatusLabel, new Insets(16));

        Rectangle clip = new Rectangle();
        clip.setArcWidth(28);
        clip.setArcHeight(28);
        clip.widthProperty().bind(hero.widthProperty());
        clip.heightProperty().bind(hero.heightProperty());
        hero.setClip(clip);
        return hero;
    }

    private void selectFeatured(Instance instance) {
        featuredId = instance.getId();
        playtimeLabels.clear();
        displayedInstances.clear();
        featuredContainer.getChildren().setAll(createFeaturedInstanceCard(instance));
        recentTiles.forEach((id, tile) -> {
            tile.getStyleClass().remove("home-tile-selected");
            if (id.equals(featuredId)) {
                tile.getStyleClass().add("home-tile-selected");
            }
        });
    }

    private Node createNewTile() {
        Label plus = new Label("+");
        plus.getStyleClass().add("home-tile-new-plus");
        Label text = new Label(Localization.text("common.createInstance"));
        text.getStyleClass().add("home-tile-name");
        VBox box = new VBox(6, plus, text);
        box.setAlignment(Pos.CENTER);
        StackPane tile = new StackPane(box);
        tile.getStyleClass().addAll("home-tile", "home-tile-new");
        tile.setMinSize(232, 130);
        tile.setPrefSize(232, 130);
        tile.setMaxSize(232, 130);
        tile.setFocusTraversable(true);
        tile.setAccessibleRole(AccessibleRole.BUTTON);
        tile.setAccessibleText(Localization.text("home.createInstanceAccessible"));
        Runnable open = () -> {
            if (onCreateInstance != null) onCreateInstance.run();
        };
        tile.setOnMouseClicked(event -> open.run());
        tile.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                open.run();
                event.consume();
            }
        });
        return tile;
    }

    private StackPane createFeaturedInstanceCard(Instance instance) {
        CoverArt art = new CoverArt(instance.getName(), instance.getLoader());

        HBox badges = new HBox(6,
                Badge.loader(instance.getLoader()),
                Badge.mono(instance.getMinecraftVersion()));
        badges.setAlignment(Pos.CENTER_LEFT);

        Label name = new Label(instance.getName());
        name.getStyleClass().add("home-hero-name");
        name.setWrapText(true);
        name.setMinWidth(0);
        name.setMaxHeight(64);

        Label playtime = new Label(playtimeText(instance));
        playtime.getStyleClass().add("home-stat-value");
        playtimeLabels.put(instance.getId(), playtime);
        displayedInstances.put(instance.getId(), instance);

        Label lastPlayed = new Label(lastPlayedText(instance));
        lastPlayed.getStyleClass().add("home-stat-value");

        HBox stats = new HBox(32,
                stat(Localization.text("home.statPlaytime"), playtime),
                stat(Localization.text("home.statLastPlayed"), lastPlayed));
        stats.setAlignment(Pos.CENTER_LEFT);

        boolean running = launchService.isRunning(instance);
        Button play = new Button(running ? Localization.text("home.closeGame") : Localization.text("common.play"));
        if (running) {
            play.getStyleClass().addAll("vanta-button-secondary", "button-large", "home-play");
        } else {
            play.setGraphic(IconView.create(IconView.Type.PLAY, 16));
            play.getStyleClass().addAll("vanta-button-primary", "button-large", "home-play");
        }
        String verb = running ? "Close " : "Launch ";
        play.setAccessibleText(verb + instance.getName());
        play.setTooltip(new Tooltip(verb + instance.getName()));
        play.setOnAction(event -> launchOrCloseInstance(instance));
        AnimationUtils.installInteractiveAnimations(play);

        Region fill = new Region();
        VBox.setVgrow(fill, Priority.ALWAYS);
        VBox copy = new VBox(14, badges, name, stats, fill, play);
        copy.setAlignment(Pos.TOP_LEFT);
        copy.setPadding(new Insets(28));
        copy.getStyleClass().add("home-hero-copy");
        play.setMinWidth(150);
        play.setMaxWidth(Region.USE_PREF_SIZE);
        return heroShell(art, copy);
    }

    private VBox stat(String label, Label value) {
        Label key = new Label(label);
        key.getStyleClass().add("home-stat-label");
        VBox box = new VBox(2, key, value);
        box.setAlignment(Pos.CENTER_LEFT);
        return box;
    }

    private Node createInstanceLoadErrorCard() {
        Button retry = new Button(Localization.text("common.retry"));
        retry.getStyleClass().add("vanta-button-secondary");
        retry.setAccessibleText(Localization.text("home.retryInstancesAccessible"));
        retry.setOnAction(event -> refreshRecentInstances());
        AnimationUtils.installInteractiveAnimations(retry);

        Callout callout = new Callout(
                Callout.Tone.DANGER,
                Localization.text("home.libraryLoadFailedTitle"),
                Localization.text("home.libraryLoadFailedBody"),
                retry);
        callout.setMaxWidth(Double.MAX_VALUE);
        StackPane.setAlignment(callout, Pos.TOP_LEFT);
        return callout;
    }

    private StackPane createFirstInstanceCard() {
        CoverArt art = new CoverArt("Vanta", "vanilla");

        Label title = new Label(Localization.text("home.emptyTitle"));
        title.getStyleClass().add("home-hero-name");
        title.setWrapText(true);

        Label description = new Label(Localization.text("home.emptyDescription"));
        description.setWrapText(true);
        description.getStyleClass().add("home-muted");

        Button create = new Button(Localization.text("common.createInstance"));
        create.setGraphic(IconView.create(IconView.Type.PLUS, 16));
        create.getStyleClass().addAll("vanta-button-primary", "button-large", "home-play");
        create.setAccessibleText(Localization.text("home.createInstanceAccessible"));
        create.setTooltip(new Tooltip(Localization.text("home.createInstanceTooltip")));
        create.setOnAction(event -> {
            if (onCreateInstance != null) onCreateInstance.run();
        });
        AnimationUtils.installInteractiveAnimations(create);

        Region fill = new Region();
        VBox.setVgrow(fill, Priority.ALWAYS);
        VBox copy = new VBox(12, title, description, fill, create);
        copy.setPadding(new Insets(24));
        create.setMaxWidth(Region.USE_PREF_SIZE);
        description.setMaxWidth(380);
        return heroShell(art, copy);
    }

    // ------------------------------------------------------------ rows

    private StackPane createRecentTile(Instance instance) {
        CoverArt art = new CoverArt(instance.getName(), instance.getLoader()).rounded(12);
        art.setMinSize(0, 130);
        art.setPrefSize(232, 130);
        art.setMaxSize(Double.MAX_VALUE, 130);

        Region shade = new Region();
        shade.getStyleClass().add("home-tile-shade");
        shade.setMouseTransparent(true);
        shade.setMaxHeight(84);

        Label name = new Label(instance.getName());
        name.getStyleClass().add("home-tile-name");
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        name.setMaxWidth(200);
        Label version = new Label(instance.getMinecraftVersion());
        version.getStyleClass().add("home-tile-version");
        VBox caption = new VBox(1, name, version);
        caption.setPadding(new Insets(0, 12, 11, 12));
        caption.setMouseTransparent(true);
        caption.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);

        boolean running = launchService.isRunning(instance);
        String verb = running ? "Close " : "Launch ";
        IconButton play = new IconButton(
                running ? IconView.Type.CLOSE : IconView.Type.PLAY,
                verb + instance.getName(),
                running ? IconButton.Style.DANGER : IconButton.Style.SECONDARY);
        play.getStyleClass().add("home-tile-play");
        play.setOnAction(event -> launchOrCloseInstance(instance));
        AnimationUtils.installInteractiveAnimations(play);

        Region ring = new Region();
        ring.getStyleClass().add("home-tile-ring");
        ring.setMouseTransparent(true);

        StackPane tile = new StackPane(art, shade, caption, play, ring);
        tile.getStyleClass().add("home-tile");
        tile.setMinSize(232, 130);
        tile.setPrefSize(232, 130);
        tile.setMaxSize(232, 130);
        StackPane.setAlignment(shade, Pos.BOTTOM_CENTER);
        StackPane.setAlignment(caption, Pos.BOTTOM_LEFT);
        StackPane.setAlignment(play, Pos.TOP_RIGHT);
        StackPane.setMargin(play, new Insets(8));

        tile.setAccessibleRole(AccessibleRole.BUTTON);
        tile.setAccessibleText("Show " + instance.getName());
        tile.setFocusTraversable(true);
        tile.setOnMouseClicked(event -> {
            if (event.getTarget() == play || play.isHover()) {
                return;
            }
            if (event.getClickCount() >= 2) {
                launchOrCloseInstance(instance);
            } else {
                selectFeatured(instance);
            }
        });
        tile.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                selectFeatured(instance);
                event.consume();
            }
        });
        return tile;
    }

    // ------------------------------------------------------------ actions

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
                                Localization.text("home.launchFailedTitle"),
                                ex.getMessage() != null
                                        ? ex.getMessage()
                                        : Localization.text("home.launchFailedBody"),
                                ""
                        ));
                    }
                });
            }
        });
    }

    // ------------------------------------------------------------ servers

    public void refreshRecentServers() {
        long request = serverRefreshSequence.incrementAndGet();
        UiExecutor.shared().execute(() -> {
            try {
                List<Instance> instances = InstanceManager.discoverInstances();
                List<ServerHistoryEntry> history = ServerHistoryManager.getRecent();

                UiExecutor.shared().dispatchToUi(() -> {
                    if (request != serverRefreshSequence.get()) return;
                    renderRecentServers(instances, history);
                });
            } catch (Throwable ex) {
                ex.printStackTrace();
                UiExecutor.shared().dispatchToUi(() -> {
                    if (request != serverRefreshSequence.get()) return;
                    recentServersList.getChildren().clear();
                    recentServersStatus.setVisible(true);
                    recentServersStatus.setManaged(true);
                    recentServersStatus.setText(Localization.text("home.serversLoadFailed"));
                });
            }
        });
    }

    private void renderRecentServers(
            List<Instance> instances,
            List<ServerHistoryEntry> history
    ) {
        recentServersList.getChildren().clear();

        List<ServerHistoryEntry> recent = history.stream().limit(5).toList();

        // Nothing to show means no section, rather than an empty box.
        serversColumn.setVisible(!recent.isEmpty());
        serversColumn.setManaged(!recent.isEmpty());

        if (recent.isEmpty()) {
            recentServersStatus.setVisible(true);
            recentServersStatus.setManaged(true);
            recentServersStatus.setText(Localization.text("home.serversEmpty"));
            return;
        }

        int rendered = 0;

        for (ServerHistoryEntry entry : recent) {
            Instance instance = instances.stream()
                    .filter(candidate ->
                            entry.getInstanceId() != null
                                    && candidate.getId().equals(entry.getInstanceId()))
                    .findFirst()
                    .orElseGet(() -> instances.stream()
                            .filter(candidate ->
                                    candidate.getName().equalsIgnoreCase(entry.getInstanceName()))
                            .findFirst()
                            .orElse(null));

            recentServersList.getChildren().add(createRecentServerRow(instance, entry));
            rendered++;
        }

        if (rendered == 0) {
            recentServersStatus.setVisible(true);
            recentServersStatus.setManaged(true);
            recentServersStatus.setText(Localization.text("home.serversUnavailable"));
            return;
        }

        recentServersStatus.setText("");
        recentServersStatus.setVisible(false);
        recentServersStatus.setManaged(false);
    }

    private HBox createRecentServerRow(
            Instance instance,
            ServerHistoryEntry server
    ) {
        Label name = new Label(server.getDisplayName());
        name.getStyleClass().add("home-row-name");
        name.setTextOverrun(OverrunStyle.ELLIPSIS);
        name.setMinWidth(0);

        Label metadata = new Label(
                instance != null
                        ? "Using " + instance.getName()
                        : Localization.text("home.instanceMissingTitle"));
        metadata.getStyleClass().add("home-muted");
        metadata.setTextOverrun(OverrunStyle.ELLIPSIS);
        metadata.setMinWidth(0);

        VBox info = new VBox(2, name, metadata);
        info.setAlignment(Pos.CENTER_LEFT);
        info.setMinWidth(0);
        HBox.setHgrow(info, Priority.ALWAYS);

        boolean instanceAvailable = instance != null;
        ServerTarget runningTarget = launchService.getRunningServerTarget();
        boolean serverRunning =
                instanceAvailable
                        && launchService.isRunning(instance)
                        && runningTarget != null
                        && runningTarget.host().equalsIgnoreCase(server.getHost())
                        && runningTarget.port() == server.getPort();

        String serverActionDescription = !instanceAvailable
                ? Localization.text("home.instanceMissingPrefix") + server.getDisplayName() + Localization.text("home.instanceMissingSuffix")
                : (serverRunning ? Localization.text("home.closeMinecraftPrefix") : Localization.text("home.joinPrefix"))
                        + server.getDisplayName()
                        + (serverRunning ? "" : Localization.text("home.usingPrefix") + instance.getName());

        IconButton play = new IconButton(
                serverRunning ? IconView.Type.CLOSE : IconView.Type.PLAY,
                serverActionDescription,
                serverRunning ? IconButton.Style.DANGER : IconButton.Style.SECONDARY);
        play.getStyleClass().add("home-row-play");
        play.setDisable(!instanceAvailable);
        play.setOnAction(event -> launchServer(instance, server));
        AnimationUtils.installInteractiveAnimations(play);

        HBox row = new HBox(12, info, play);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 10, 8, 12));
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add("home-row");

        row.setAccessibleRole(AccessibleRole.BUTTON);
        row.setAccessibleText(Localization.text("home.joinPrefix") + server.getDisplayName()
                + (instanceAvailable ? Localization.text("home.usingPrefix") + instance.getName() : Localization.text("home.unavailableSuffix")));
        row.setFocusTraversable(instanceAvailable);
        row.setOnMouseClicked(event -> {
            if (event.getTarget() == play || play.isHover() || instance == null) {
                return;
            }
            launchServer(instance, server);
        });
        row.setOnKeyPressed(event -> {
            if (event.getTarget() == row && instanceAvailable
                    && (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE)) {
                launchServer(instance, server);
                event.consume();
            }
        });
        return row;
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

    // ------------------------------------------------------------ account and status

    public void refreshAccount() {
        setAccount(accountService.getCurrentAccount());
    }

    public void setAccount(Account account) {
        if (account == null) {
            accountLabel.setText(Localization.text("home.noAccount"));
            return;
        }

        accountLabel.setText(Localization.text("home.signedInAsPrefix") + account.getUsername());
    }

    private void onAccountChanged(Account account) {
        UiExecutor.shared().dispatchToUi(() -> setAccount(account));
    }

    private void setLaunchBadge(String text, Badge.Tone tone) {
        launchStatusLabel.setText(text);
        launchStatusLabel.getStyleClass().removeIf(style -> style.startsWith("badge-")
                && !style.equals("badge-mono") && !style.equals("badge-dot"));
        launchStatusLabel.getStyleClass().add("badge-" + tone.name().toLowerCase(java.util.Locale.ROOT));
    }

    private void updateLaunchStatus(LaunchService.LaunchState state) {
        String label;
        Badge.Tone tone;
        switch (state) {
            case PREPARING, STARTING -> {
                label = Localization.text("home.starting");
                tone = Badge.Tone.ACCENT;
            }
            case RUNNING -> {
                label = Localization.text("home.running");
                tone = Badge.Tone.SUCCESS;
            }
            case CLOSING -> {
                label = Localization.text("home.closing");
                tone = Badge.Tone.WARNING;
            }
            case ERROR -> {
                label = Localization.text("home.launchFailed");
                tone = Badge.Tone.DANGER;
            }
            case IDLE -> {
                label = Localization.text("home.readyToPlay");
                tone = Badge.Tone.SUCCESS;
            }
            default -> {
                label = Localization.text("home.vantaReady");
                tone = Badge.Tone.SUCCESS;
            }
        }
        setLaunchBadge(label, tone);
        launchStatusLabel.setAccessibleText(Localization.text("home.launchStatusPrefix") + label.toLowerCase());
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
            // Preparation/closing events only affect the status badge.
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
