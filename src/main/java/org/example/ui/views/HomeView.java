package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.example.launcher.MinecraftLocator;
import org.example.launcher.account.Account;
import org.example.launcher.instance.InstanceManager;
import org.example.launcher.model.Instance;
import org.example.launcher.service.AccountService;
import org.example.launcher.service.InstanceUsageManager;
import org.example.launcher.service.LaunchFailure;
import org.example.launcher.service.MultiLaunchService;
import org.example.launcher.service.LaunchService;
import org.example.ui.components.AccountCard;
import org.example.ui.components.IconView;
import org.example.ui.components.MinecraftBackdrop;

import java.util.List;
import java.util.function.Consumer;

public class HomeView extends StackPane {

    private final AccountService accountService;
    private final MultiLaunchService launchService;
    private final Consumer<LaunchFailure> onLaunchFailure;
    private final Consumer<Instance> onLaunchInstance;

    private final AccountCard accountCard;
    private final Label accountLabel;
    private final VBox recentList;
    private final Label recentStatus;

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

        VBox contentBox = new VBox(22);
        contentBox.setPadding(new Insets(32, 36, 36, 36));

        Label title = new Label("Home");
        title.getStyleClass().add("home-title");

        accountLabel = new Label("Checking account...");
        accountLabel.getStyleClass().add("home-account-label");

        VBox header = new VBox(4, title, accountLabel);
        header.getStyleClass().add("home-header");

        MinecraftBackdrop backdrop = new MinecraftBackdrop();
        StackPane hero = new StackPane();
        hero.setMinHeight(190);
        hero.setPrefHeight(190);
        hero.setMaxHeight(190);
        hero.getStyleClass().add("home-hero");
        hero.getChildren().addAll(backdrop, header);
        StackPane.setAlignment(header, Pos.TOP_LEFT);
        StackPane.setMargin(header, new Insets(24));

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

        ScrollPane recentScroll = new ScrollPane(recentList);
        recentScroll.setFitToWidth(true);
        recentScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        recentScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        recentScroll.setPrefViewportHeight(270);
        recentScroll.setMaxHeight(330);
        recentScroll.getStyleClass().add("home-recent-scroll");
        VBox.setVgrow(recentScroll, Priority.ALWAYS);

        VBox recentSection = new VBox(
                8,
                recentTitle,
                recentSubtitle,
                recentScroll,
                recentStatus
        );
        recentSection.getStyleClass().add("home-recent-section");

        accountCard = new AccountCard();
        accountCard.getStyleClass().add("home-account-card");

        VBox launcherCard = createLauncherStatusCard();

        HBox footerCards = new HBox(14, accountCard, launcherCard);
        HBox.setHgrow(accountCard, Priority.ALWAYS);
        HBox.setHgrow(launcherCard, Priority.ALWAYS);

        contentBox.getChildren().addAll(
                hero,
                recentSection,
                footerCards
        );

        getChildren().add(contentBox);

        accountService.addListener(this::onAccountChanged);
        launchService.addStateListener(this::onLaunchStateChanged);

        refreshAccount();
        refreshRecentInstances();
    }

    private VBox createLauncherStatusCard() {
        VBox card = new VBox(8);
        card.getStyleClass().add("home-status-card");

        Label title = new Label("Vanta Launcher");
        title.getStyleClass().add("home-status-title");

        Label status = new Label("READY");
        status.getStyleClass().add("home-status-value");

        Label java = new Label("Java " + System.getProperty("java.version"));
        java.getStyleClass().add("home-status-meta");

        Label data = new Label(
                MinecraftLocator.getVantaDirectory().toString()
        );
        data.getStyleClass().add("home-status-meta");
        data.setWrapText(true);

        card.getChildren().addAll(title, status, java, data);
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

        List<Instance> recent = instances.stream()
                .filter(instance ->
                        InstanceUsageManager.getLastPlayed(instance) > 0
                )
                .limit(6)
                .toList();

        if (recent.isEmpty()) {
            recentStatus.setText(
                    "No play history yet. Launch an instance and it will appear here."
            );
            return;
        }

        recentStatus.setText(
                recent.size() + " recent instance"
                        + (recent.size() == 1 ? "" : "s")
        );

        for (Instance instance : recent) {
            recentList.getChildren().add(createRecentCard(instance));
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
                )
                        + " played"
        );
        playtime.getStyleClass().add("home-recent-playtime");

        VBox info = new VBox(4, name, metadata, playtime);
        HBox.setHgrow(info, Priority.ALWAYS);

        Button play = new Button(
                launchService.isRunning(instance) ? "CLOSE" : "PLAY"
        );
        play.getStyleClass().add(
                launchService.isRunning(instance)
                        ? "home-recent-stop"
                        : "home-recent-play"
        );
        play.setMinWidth(84);

        play.setOnAction(event -> {
            if (launchService.isRunning(instance)) {
                launchService.close(instance);
            } else {
                onLaunchInstance.accept(instance);
            }
        });

        HBox card = new HBox(14, icon, info, play);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPadding(new Insets(14, 16, 14, 16));
        card.setMaxWidth(Double.MAX_VALUE);
        card.getStyleClass().add("home-recent-card");

        org.example.ui.AnimationUtils.installInteractiveAnimations(card);
        org.example.ui.AnimationUtils.slideFadeVertical(card, 10);

        return card;
    }

    public void refreshAccount() {
        setAccount(accountService.getCurrentAccount());
    }

    public void setAccount(Account account) {
        if (account == null) {
            accountLabel.setText("No Microsoft account connected");
            accountCard.setDisconnected();
            return;
        }

        accountLabel.setText("Signed in as " + account.getUsername());
        accountCard.setAccount(account);
    }

    private void onAccountChanged(Account account) {
        Platform.runLater(() -> setAccount(account));
    }

    private void onLaunchStateChanged(LaunchService.LaunchState state) {
        Platform.runLater(() -> {
            refreshRecentInstances();

            if (state == LaunchService.LaunchState.ERROR) {
                LaunchFailure failure = launchService.getLastFailure();
                if (failure != null) {
                    onLaunchFailure.accept(failure);
                }
            }
        });
    }
}
