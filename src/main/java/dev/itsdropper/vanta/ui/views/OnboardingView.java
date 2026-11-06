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
        setPrefWidth(820);
        setMaxWidth(900);
        setMinHeight(0);
        setPrefHeight(560);
        setMaxHeight(620);
        dev.itsdropper.vanta.ui.ResponsiveLayout.bindPagePadding(this);

        Label logo = new Label("VANTA");
        logo.getStyleClass().add("onboarding-logo");

        stepLabel.getStyleClass().add("onboarding-step");

        HBox top = new HBox(14, logo, stepLabel);
        top.setAlignment(Pos.CENTER_LEFT);

        setTop(top);

        pageContainer.getStyleClass().add("onboarding-content");
        pageContainer.setMinWidth(0);
        pageContainer.setMaxWidth(Double.MAX_VALUE);
        ScrollPane onboardingScroll = new ScrollPane(pageContainer);
        onboardingScroll.setFitToWidth(true);
        onboardingScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        onboardingScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        onboardingScroll.getStyleClass().add("onboarding-scroll");
        setCenter(onboardingScroll);

        Label hint = new Label(
                Localization.text("onboarding.changeLater")
        );
        hint.getStyleClass().add("onboarding-hint");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        backButton.getStyleClass().add("secondary-button");
        nextButton.getStyleClass().add("primary-button");

        backButton.setOnAction(event -> previous());
        nextButton.setOnAction(event -> next());

        HBox footer = new HBox(
                12,
                backButton,
                spacer,
                hint,
                nextButton
        );
        footer.setAlignment(Pos.CENTER);
        footer.setPadding(new Insets(24, 0, 0, 0));

        setBottom(footer);

        showStep();
    }

    private void showStep() {
        stepLabel.setText(Localization.text("onboarding.stepPrefix") + (step + 1) + Localization.text("onboarding.stepSuffix"));
        backButton.setDisable(step == 0);
        nextButton.setText(step == 3 ? Localization.text("onboarding.finish") : Localization.text("onboarding.next"));

        Node page = switch (step) {
            case 0 -> welcomePage();
            case 1 -> howItWorksPage();
            case 2 -> accountPage();
            default -> readyPage();
        };

        pageContainer.getChildren().setAll(page);
        AnimationUtils.slideFadeIn(page, step == 0 ? 24 : 30);
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

        box.getChildren().add(line);
        return box;
    }

    private Node howItWorksPage() {
        VBox box = page(
                Localization.text("onboarding.organizationTitle"),
                Localization.text("onboarding.organizationBody")
        );

        addFeature(box, "1", "Instances", "Each instance has its own mods, resource packs, shaders, worlds, screenshots, logs and settings.");
        addFeature(box, "2", "Content", "Use Browse Content to install from Modrinth. Installed content can then be enabled, disabled or removed per instance.");
        addFeature(box, "3", "Launch", "Vanta resolves the selected instance's Minecraft version, loader, libraries and settings before starting it.");
        addFeature(box, "4", "Repair", "If a dependency is missing or damaged, use the instance repair flow instead of manually deleting random files.");

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

        connectButton.getStyleClass().add("primary-button");
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

        addFeature(box, "1", "Create your first instance", "Pick a Minecraft version and loader. Vanta creates the isolated folder structure for you.");
        addFeature(box, "2", "Open Browse Content", "Install compatible mods, resource packs or shaders from Modrinth for that instance.");
        addFeature(box, "3", "Tune it in Settings", "Set RAM, resolution, fullscreen, Java arguments and Minecraft arguments for an individual instance.");

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
        create.getStyleClass().add("primary-button");
        create.setOnAction(event -> {
            saveTelemetryConsent();
            OnboardingManager.markCompleted();
            onFinished.run();
            onCreateInstance.run();
        });

        Button finish = new Button(Localization.text("onboarding.explore"));
        finish.getStyleClass().add("secondary-button");
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

        VBox box = new VBox(18, titleLabel, subtitleLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setMaxWidth(760);
        return box;
    }

    private void addFeature(VBox parent, String number, String title, String description) {
        Label numberLabel = new Label(number);
        numberLabel.getStyleClass().add("onboarding-feature-number");

        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("onboarding-feature-title");

        Label descriptionLabel = new Label(description);
        descriptionLabel.getStyleClass().add("onboarding-feature-description");
        descriptionLabel.setWrapText(true);

        VBox text = new VBox(3, titleLabel, descriptionLabel);
        HBox row = new HBox(14, numberLabel, text);
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
