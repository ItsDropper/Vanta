package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.example.launcher.account.Account;
import org.example.launcher.service.AccountService;
import org.example.ui.AnimationUtils;
import org.example.ui.OnboardingManager;

public class OnboardingView extends BorderPane {

    private final AccountService accountService;
    private final Runnable onFinished;
    private final Runnable onCreateInstance;

    private final StackPane pageContainer = new StackPane();
    private final Label stepLabel = new Label();
    private final Label accountStatus = new Label();
    private final Button backButton = new Button("BACK");
    private final Button nextButton = new Button("NEXT");
    private final Button connectButton = new Button("CONNECT MICROSOFT ACCOUNT");

    private int step = 0;

    public OnboardingView(
            AccountService accountService,
            Runnable onFinished,
            Runnable onCreateInstance
    ) {
        this.accountService = accountService;
        this.onFinished = onFinished;
        this.onCreateInstance = onCreateInstance;

        getStyleClass().add("onboarding-page");
        setMinWidth(700);
        setPrefWidth(820);
        setMaxWidth(900);
        setMinHeight(500);
        setPrefHeight(560);
        setMaxHeight(620);
        setPadding(new Insets(36));

        Label logo = new Label("VANTA");
        logo.getStyleClass().add("onboarding-logo");

        stepLabel.getStyleClass().add("onboarding-step");

        HBox top = new HBox(14, logo, stepLabel);
        top.setAlignment(Pos.CENTER_LEFT);

        setTop(top);

        pageContainer.getStyleClass().add("onboarding-content");
        setCenter(pageContainer);

        Label hint = new Label(
                "You can change everything later in Settings."
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
        stepLabel.setText("STEP " + (step + 1) + " OF 4");
        backButton.setDisable(step == 0);
        nextButton.setText(step == 3 ? "FINISH" : "NEXT");

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
        VBox box = page("Welcome to Vanta", 
                "A focused Minecraft launcher built around clean instances, reliable downloads, and control without unnecessary clutter.");

        Label line = new Label(
                "Vanta keeps each Minecraft instance separate, gives you a Modrinth-based content workflow, and keeps launcher configuration local."
        );
        line.getStyleClass().add("onboarding-body");
        line.setWrapText(true);
        line.setMaxWidth(680);

        box.getChildren().add(line);
        return box;
    }

    private Node howItWorksPage() {
        VBox box = page(
                "How Vanta is organized",
                "An instance is a complete Minecraft environment. Keeping environments separate prevents one setup from accidentally changing another."
        );

        addFeature(box, "1", "Instances", "Each instance has its own mods, resource packs, shaders, worlds, screenshots, logs and settings.");
        addFeature(box, "2", "Content", "Use Browse Content to install from Modrinth. Installed content can then be enabled, disabled or removed per instance.");
        addFeature(box, "3", "Launch", "Vanta resolves the selected instance's Minecraft version, loader, libraries and settings before starting it.");
        addFeature(box, "4", "Repair", "If a dependency is missing or damaged, use the instance repair flow instead of manually deleting random files.");

        Label path = new Label(
                "Tip: use one instance for each distinct modded setup. For example, keep a Fabric performance setup separate from a large modpack."
        );
        path.getStyleClass().add("onboarding-hint");
        path.setWrapText(true);
        box.getChildren().add(path);

        return box;
    }

    private Node accountPage() {
        VBox box = page("Connect your Minecraft account", 
                "Vanta uses your Microsoft account for Minecraft authentication. Your password is entered through Microsoft's authentication flow, not stored by this screen.");

        Account account = accountService.getCurrentAccount();

        accountStatus.setText(
                account == null
                        ? "No Microsoft account connected"
                        : "Connected as " + account.getUsername()
        );
        accountStatus.getStyleClass().removeAll("connected-label", "onboarding-account-status");
        accountStatus.getStyleClass().add(
                account == null
                        ? "onboarding-account-status"
                        : "connected-label"
        );

        connectButton.getStyleClass().add("primary-button");
        connectButton.setDisable(false);
        connectButton.setText(
                account == null
                        ? "CONNECT MICROSOFT ACCOUNT"
                        : "CONNECT ANOTHER ACCOUNT"
        );
        connectButton.setOnAction(event -> connectAccount());

        Label note = new Label(
                "You can skip this and connect an account later from Accounts."
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
                "You're ready to use Vanta",
                "The first useful action is to create an instance. You can always change launcher defaults later in Settings."
        );

        addFeature(box, "1", "Create your first instance", "Pick a Minecraft version and loader. Vanta creates the isolated folder structure for you.");
        addFeature(box, "2", "Open Browse Content", "Install compatible mods, resource packs or shaders from Modrinth for that instance.");
        addFeature(box, "3", "Tune it in Settings", "Set RAM, resolution, fullscreen, Java arguments and Minecraft arguments for an individual instance.");

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);

        Button create = new Button("CREATE FIRST INSTANCE");
        create.getStyleClass().add("primary-button");
        create.setOnAction(event -> {
            OnboardingManager.markCompleted();
            onFinished.run();
            onCreateInstance.run();
        });

        Button finish = new Button("EXPLORE VANTA");
        finish.getStyleClass().add("secondary-button");
        finish.setOnAction(event -> {
            OnboardingManager.markCompleted();
            onFinished.run();
        });

        actions.getChildren().addAll(create, finish);
        box.getChildren().add(actions);

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

        OnboardingManager.markCompleted();
        onFinished.run();
    }

    private void connectAccount() {
        connectButton.setDisable(true);
        connectButton.setText("WAITING FOR MICROSOFT SIGN-IN...");

        ProgressIndicator progress = new ProgressIndicator();
        progress.setPrefSize(18, 18);
        connectButton.setGraphic(progress);

        new Thread(() -> {
            try {
                Account account = accountService.login();

                Platform.runLater(() -> {
                    accountStatus.setText("Connected as " + account.getUsername());
                    accountStatus.getStyleClass().remove("onboarding-account-status");
                    accountStatus.getStyleClass().add("connected-label");
                    connectButton.setGraphic(null);
                    connectButton.setText("ACCOUNT CONNECTED");
                    connectButton.setDisable(false);
                });
            } catch (Throwable ex) {
                Platform.runLater(() -> {
                    accountStatus.setText(
                            "Could not connect: "
                                    + (ex.getMessage() == null ? ex.toString() : ex.getMessage())
                    );
                    accountStatus.getStyleClass().remove("connected-label");
                    accountStatus.getStyleClass().add("onboarding-account-status");
                    connectButton.setGraphic(null);
                    connectButton.setText("TRY AGAIN");
                    connectButton.setDisable(false);
                });
            }
        }, "Vanta-Onboarding-Account").start();
    }
}
