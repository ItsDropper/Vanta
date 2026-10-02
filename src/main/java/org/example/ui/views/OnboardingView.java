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

    private final StackPane pageContainer = new StackPane();
    private final Label stepLabel = new Label();
    private final Label accountStatus = new Label();
    private final Button backButton = new Button("BACK");
    private final Button nextButton = new Button("NEXT");
    private final Button connectButton = new Button("CONNECT MICROSOFT ACCOUNT");

    private int step = 0;

    public OnboardingView(
            AccountService accountService,
            Runnable onFinished
    ) {
        this.accountService = accountService;
        this.onFinished = onFinished;

        getStyleClass().add("onboarding-page");
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
                "Vanta keeps each Minecraft instance separate, so mods, settings, worlds, screenshots, and logs stay organized."
        );
        line.getStyleClass().add("onboarding-body");
        line.setWrapText(true);
        line.setMaxWidth(680);

        box.getChildren().add(line);
        return box;
    }

    private Node howItWorksPage() {
        VBox box = page("The basic workflow", 
                "Vanta is designed so the common path stays simple.");

        addFeature(box, "1", "Create an instance", "Choose Minecraft and a loader, or start from a preset.");
        addFeature(box, "2", "Install content", "Browse Modrinth and manage mods, resource packs, and shaders per instance.");
        addFeature(box, "3", "Play", "Launch the selected instance. Vanta keeps its files and configuration isolated.");
        addFeature(box, "4", "Repair when needed", "Use Vanta's repair and diagnostics tools when an instance needs attention.");

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
        VBox box = page("You're ready to use Vanta", 
                "Start by creating an instance or connecting an account later from the sidebar.");

        Label checklist = new Label(
                "• Home — launch and monitor your selected instance\n"
                        + "• Instances — create and manage isolated Minecraft installations\n"
                        + "• Mods — manage installed content and browse Modrinth\n"
                        + "• Accounts — manage Microsoft accounts\n"
                        + "• Settings — customize Vanta and inspect its data"
        );
        checklist.getStyleClass().add("onboarding-body");
        checklist.setWrapText(true);
        checklist.setMaxWidth(680);

        box.getChildren().add(checklist);
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
