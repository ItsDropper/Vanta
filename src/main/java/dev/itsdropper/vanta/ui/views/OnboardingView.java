package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.async.UiExecutor;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import dev.itsdropper.vanta.launcher.account.Account;
import dev.itsdropper.vanta.launcher.service.AccountService;
import dev.itsdropper.vanta.ui.AnimationUtils;
import dev.itsdropper.vanta.ui.components.IconView;
import dev.itsdropper.vanta.ui.kit.CoverArt;
import dev.itsdropper.vanta.ui.kit.VantaLogo;
import dev.itsdropper.vanta.ui.OnboardingManager;
import dev.itsdropper.vanta.ui.LauncherSettings;

public class OnboardingView extends BorderPane {

    private final AccountService accountService;
    private final Runnable onFinished;
    private final Runnable onCreateInstance;

    private final StackPane pageContainer = new StackPane();
    private final Label stepLabel = new Label();
    private final Label accountStatus = new Label();
    private final Button backButton = new Button(Localization.text("common.back"));
    private final Button nextButton = new Button(Localization.text("onboarding.next"));
    private final Button connectButton = new Button(Localization.text("onboarding.connectMicrosoft"));
    private final CheckBox usageTelemetry = new CheckBox(Localization.text("onboarding.usageOptIn"));
    private final CheckBox crashTelemetry = new CheckBox(Localization.text("onboarding.crashOptIn"));

    private final VBox stepList = new VBox();
    private int step = 0;

    public OnboardingView(
            AccountService accountService,
            Runnable onFinished,
            Runnable onCreateInstance
    ) {
        this.accountService = accountService;
        this.onFinished = onFinished;
        this.onCreateInstance = onCreateInstance;

        usageTelemetry.setSelected(LauncherSettings.isUsageTelemetryEnabled());
        usageTelemetry.getStyleClass().add("settings-checkbox");
        crashTelemetry.setSelected(LauncherSettings.isCrashTelemetryEnabled());
        crashTelemetry.getStyleClass().add("settings-checkbox");

        getStyleClass().add("onboarding-page");
        setMinWidth(0);
        setPrefWidth(940);
        setMaxWidth(980);
        setMinHeight(0);
        setPrefHeight(580);
        setMaxHeight(640);

        // Left rail: the world photo, the wordmark and the four steps.
        CoverArt backdrop = new CoverArt("Vanta", "vanilla");
        backdrop.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        Region railScrim = new Region();
        railScrim.getStyleClass().add("onboarding-rail-scrim");
        railScrim.setMouseTransparent(true);

        Node logo = VantaLogo.full(30, 20);

        stepList.setSpacing(6);
        VBox railContent = new VBox(32, logo, stepList);
        railContent.setPadding(new Insets(32, 28, 28, 28));
        railContent.setAlignment(Pos.TOP_LEFT);

        StackPane rail = new StackPane(backdrop, railScrim, railContent);
        rail.getStyleClass().add("onboarding-rail");
        rail.setPrefWidth(236);
        rail.setMinWidth(236);
        rail.setMaxWidth(236);
        StackPane.setAlignment(railContent, Pos.TOP_LEFT);
        setLeft(rail);

        stepLabel.getStyleClass().add("onboarding-step");

        pageContainer.getStyleClass().add("onboarding-content");
        pageContainer.setMinWidth(0);
        pageContainer.setMaxWidth(Double.MAX_VALUE);
        pageContainer.setAlignment(Pos.TOP_LEFT);
        ScrollPane onboardingScroll = new ScrollPane(pageContainer);
        onboardingScroll.setFitToWidth(true);
        onboardingScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        onboardingScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        onboardingScroll.getStyleClass().add("onboarding-scroll");
        VBox.setVgrow(onboardingScroll, Priority.ALWAYS);

        Label hint = new Label(
                Localization.text("onboarding.changeLater")
        );
        hint.getStyleClass().add("onboarding-hint");
        hint.setWrapText(true);
        HBox.setHgrow(hint, Priority.ALWAYS);
        hint.setMaxWidth(Double.MAX_VALUE);

        backButton.getStyleClass().add("vanta-button-secondary");
        nextButton.getStyleClass().addAll("vanta-button-primary", "button-large");

        backButton.setOnAction(event -> previous());
        nextButton.setOnAction(event -> next());

        HBox footer = new HBox(10, hint, backButton, nextButton);
        footer.setAlignment(Pos.CENTER_RIGHT);
        footer.getStyleClass().add("onboarding-footer");

        VBox main = new VBox(onboardingScroll, footer);
        main.getStyleClass().add("onboarding-main");
        setCenter(main);

        javafx.scene.shape.Rectangle clip = new javafx.scene.shape.Rectangle();
        clip.setArcWidth(32);
        clip.setArcHeight(32);
        clip.widthProperty().bind(widthProperty());
        clip.heightProperty().bind(heightProperty());
        setClip(clip);

        showStep();
    }

    private void showStep() {
        stepLabel.setText(Localization.text("onboarding.stepPrefix") + (step + 1) + Localization.text("onboarding.stepSuffix"));
        backButton.setDisable(step == 0);
        nextButton.setVisible(step != 3);
        nextButton.setManaged(step != 3);
        nextButton.setText(Localization.text("onboarding.next"));

        Node page = switch (step) {
            case 0 -> welcomePage();
            case 1 -> howItWorksPage();
            case 2 -> accountPage();
            default -> readyPage();
        };

        renderSteps();
        pageContainer.getChildren().setAll(page);
        AnimationUtils.slideFadeIn(page, step == 0 ? 24 : 30);
    }

    private void renderSteps() {
        String[] names = {
                Localization.text("onboarding.rail.welcome"),
                Localization.text("onboarding.rail.how"),
                Localization.text("onboarding.rail.account"),
                Localization.text("onboarding.rail.ready")
        };
        stepList.getChildren().clear();
        for (int i = 0; i < names.length; i++) {
            Label name = new Label(names[i]);
            name.getStyleClass().add("onboarding-rail-label");
            Node marker;
            if (i < step) {
                marker = IconView.create(IconView.Type.CHECK, 14);
            } else {
                Region dot = new Region();
                dot.getStyleClass().add("onboarding-rail-dot");
                marker = dot;
            }
            StackPane markerBox = new StackPane(marker);
            markerBox.setMinSize(18, 18);
            markerBox.setPrefSize(18, 18);
            HBox row = new HBox(10, markerBox, name);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("onboarding-rail-step");
            row.getStyleClass().add(i < step ? "done" : i == step ? "active" : "todo");
            row.setAccessibleText(names[i] + (i == step ? ", current step" : i < step ? ", done" : ""));
            stepList.getChildren().add(row);
        }
    }

    private Node welcomePage() {
        VBox box = page(Localization.text("onboarding.welcomeTitle"), 
                Localization.text("onboarding.welcomeSubtitle"));

        Label line = new Label(
                Localization.text("onboarding.overview")
        );
        line.getStyleClass().add("onboarding-body");
        line.setWrapText(true);
        line.setMaxWidth(680);

        CoverArt banner = new CoverArt("Vanta welcome", "fabric").rounded(14).sized(540, 220);
        box.getChildren().addAll(line, banner);
        return box;
    }

    private Node howItWorksPage() {
        VBox box = page(
                Localization.text("onboarding.organizationTitle"),
                Localization.text("onboarding.organizationBody")
        );

        addFeature(box, IconView.Type.INSTANCES, Localization.text("onboarding.feature.instancesTitle"), Localization.text("onboarding.feature.instancesDescription"));
        addFeature(box, IconView.Type.MODS, Localization.text("onboarding.feature.contentTitle"), Localization.text("onboarding.feature.contentDescription"));
        addFeature(box, IconView.Type.PLAY, Localization.text("onboarding.feature.launchTitle"), Localization.text("onboarding.feature.launchDescription"));
        addFeature(box, IconView.Type.SHIELD, Localization.text("onboarding.feature.repairTitle"), Localization.text("onboarding.feature.repairDescription"));

        Label path = new Label(
                Localization.text("onboarding.instanceTip")
        );
        path.getStyleClass().add("onboarding-hint");
        path.setWrapText(true);
        box.getChildren().add(path);

        return box;
    }

    private Node accountPage() {
        VBox box = page(Localization.text("onboarding.accountTitle"), 
                Localization.text("onboarding.accountBody"));

        Account account = accountService.getCurrentAccount();

        accountStatus.setText(
                account == null
                        ? Localization.text("onboarding.noAccount")
                        : Localization.text("onboarding.connectedAs") + account.getUsername()
        );
        accountStatus.getStyleClass().removeAll("connected-label", "onboarding-account-status");
        accountStatus.getStyleClass().add(
                account == null
                        ? "onboarding-account-status"
                        : "connected-label"
        );

        connectButton.getStyleClass().add("vanta-button-primary");
        connectButton.setAccessibleText(Localization.text("onboarding.connectPrompt"));
        connectButton.setGraphic(null);
        connectButton.setDisable(account != null);
        connectButton.setText(account == null ? Localization.text("onboarding.connectMicrosoft") : Localization.text("onboarding.accountConnected"));
        connectButton.setOnAction(event -> connectAccount());

        Label note = new Label(
                Localization.text("onboarding.skipAccount")
        );
        note.getStyleClass().add("onboarding-body");
        note.setWrapText(true);

        box.getChildren().addAll(
                accountStatus,
                connectButton,
                note
        );

        return box;
    }

    private Node readyPage() {
        VBox box = page(
                Localization.text("onboarding.readyTitle"),
                Localization.text("onboarding.readyBody")
        );

        addFeature(box, IconView.Type.PLUS, Localization.text("onboarding.feature.firstInstanceTitle"), Localization.text("onboarding.feature.firstInstanceDescription"));
        addFeature(box, IconView.Type.SEARCH, Localization.text("onboarding.feature.browseTitle"), Localization.text("onboarding.feature.browseDescription"));
        addFeature(box, IconView.Type.SETTINGS, Localization.text("onboarding.feature.settingsTitle"), Localization.text("onboarding.feature.settingsDescription"));

        Label telemetryTitle = new Label(Localization.text("onboarding.privacyTitle"));
        telemetryTitle.getStyleClass().add("onboarding-feature-title");

        Label telemetryDetails = new Label(
                Localization.text("onboarding.privacyUsage")
                        + Localization.text("onboarding.privacyCrash")
                        + Localization.text("onboarding.privacyWarning")
                        + Localization.text("onboarding.privacyNote")
        );
        telemetryDetails.getStyleClass().add("onboarding-feature-description");
        telemetryDetails.setWrapText(true);
        telemetryDetails.setMaxWidth(680);

        VBox telemetryOptions = new VBox(9, telemetryTitle, usageTelemetry, crashTelemetry, telemetryDetails);
        telemetryOptions.getStyleClass().add("onboarding-feature");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button create = new Button(Localization.text("onboarding.createInstance"));
        create.getStyleClass().add("vanta-button-primary");
        create.setOnAction(event -> {
            saveTelemetryConsent();
            OnboardingManager.markCompleted();
            onFinished.run();
            onCreateInstance.run();
        });

        Button finish = new Button(Localization.text("onboarding.explore"));
        finish.getStyleClass().add("vanta-button-secondary");
        finish.setOnAction(event -> {
            saveTelemetryConsent();
            OnboardingManager.markCompleted();
            onFinished.run();
        });

        actions.getChildren().addAll(create, finish);
        box.getChildren().addAll(telemetryOptions, actions);

        return box;
    }

    private VBox page(String title, String subtitle) {
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("onboarding-title");

        Label subtitleLabel = new Label(subtitle);
        subtitleLabel.getStyleClass().add("onboarding-subtitle");
        subtitleLabel.setWrapText(true);
        subtitleLabel.setMaxWidth(700);

        VBox box = new VBox(16, titleLabel, subtitleLabel);
        box.setPadding(new Insets(36, 40, 12, 40));
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(620);
        return box;
    }

    private void addFeature(VBox parent, IconView.Type icon, String title, String description) {
        StackPane numberLabel = new StackPane(IconView.create(icon, 18));
        numberLabel.getStyleClass().add("onboarding-feature-icon");
        numberLabel.setMinSize(40, 40);
        numberLabel.setPrefSize(40, 40);
        numberLabel.setMaxSize(40, 40);

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("onboarding-feature-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("onboarding-feature-description");
        descriptionLabel.setWrapText(true);

        VBox text = new VBox(3, titleLabel, descriptionLabel);
        HBox row = new HBox(14, numberLabel, text);
        HBox.setHgrow(text, Priority.ALWAYS);
        row.getStyleClass().add("onboarding-feature");
        row.setAlignment(Pos.CENTER_LEFT);
        parent.getChildren().add(row);
    }

    private void previous() {
        if (step <= 0) {
            return;
        }
        step--;
        showStep();
    }

    private void next() {
        if (step < 3) {
            step++;
            showStep();
            return;
        }

        saveTelemetryConsent();
        OnboardingManager.markCompleted();
        onFinished.run();
    }

    private void saveTelemetryConsent() {
        LauncherSettings.setUsageTelemetryEnabled(usageTelemetry.isSelected());
        LauncherSettings.setCrashTelemetryEnabled(crashTelemetry.isSelected());
    }

    private void connectAccount() {
        connectButton.setDisable(true);
        connectButton.setText(Localization.text("onboarding.waiting"));

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(18, 18);
        connectButton.setGraphic(progress);

        UiExecutor.shared().execute(() -> {
            try {
                Account account = accountService.login();

                UiExecutor.shared().dispatchToUi(() -> {
                    accountStatus.setText(Localization.text("onboarding.connectedAs") + account.getUsername());
                    accountStatus.getStyleClass().remove("onboarding-account-status");
                    accountStatus.getStyleClass().add("connected-label");
                    connectButton.setGraphic(null);
                    connectButton.setText(Localization.text("onboarding.accountConnected"));
                    connectButton.setDisable(false);
                });
            } catch (Throwable ex) {
                UiExecutor.shared().dispatchToUi(() -> {
                    accountStatus.setText(
                            Localization.text("onboarding.connectErrorPrefix")
                                    + (ex.getMessage() == null ? ex.toString() : ex.getMessage())
                    );
                    accountStatus.getStyleClass().remove("connected-label");
                    accountStatus.getStyleClass().add("onboarding-account-status");
                    connectButton.setGraphic(null);
                    connectButton.setText(Localization.text("common.retry"));
                    connectButton.setDisable(false);
                });
            }
        });
    }
}
