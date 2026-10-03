package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import org.example.launcher.account.Account;
import org.example.launcher.service.AccountService;
import org.example.launcher.service.AccountSkinService;

import java.util.List;

public class AccountsView extends VBox {

    private final AccountService accountService;

    private final ImageView heroHead = new ImageView();
    private final Label heroFallback = new Label("?");
    private final Label heroUsername = new Label();
    private final Label heroStatus = new Label();
    private final Label heroUuid = new Label();

    private final Button addAccountButton = new Button("ADD ACCOUNT");
    private final Button refreshButton = new Button("REFRESH");
    private final Button signOutButton = new Button("SIGN OUT");

    private final FlowPane accountList = new FlowPane();
    private final Label accountCount = new Label();
    private final Label errorLabel = new Label();

    public AccountsView(AccountService accountService) {

        this.accountService = accountService;

        getStyleClass().addAll("page", "accounts-page");
        setFillWidth(true);

        Label title = new Label("Accounts");
        title.getStyleClass().add("accounts-title");

        Label subtitle = new Label(
                "Manage the Minecraft accounts Vanta uses to launch your game."
        );
        subtitle.getStyleClass().add("accounts-subtitle");

        VBox header = new VBox(5, title, subtitle);
        header.getStyleClass().add("accounts-header");

        StackPane hero = createHero();
        VBox accountsSection = createAccountsSection();

        VBox content = new VBox(
                22,
                header,
                hero,
                accountsSection
        );
        content.setFillWidth(true);
        content.setPadding(new Insets(30, 38, 36, 38));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("accounts-scroll");

        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);

        accountService.addListener(account ->
                Platform.runLater(() -> renderAccounts())
        );

        renderAccounts();
    }

    private StackPane createHero() {

        StackPane hero = new StackPane();
        hero.getStyleClass().add("accounts-hero");
        hero.setMaxWidth(Double.MAX_VALUE);

        HBox content = new HBox(22);
        content.setAlignment(Pos.CENTER_LEFT);

        StackPane headFrame = new StackPane();
        headFrame.getStyleClass().add("accounts-hero-head-frame");
        headFrame.setMinSize(104, 104);
        headFrame.setPrefSize(104, 104);
        headFrame.setMaxSize(104, 104);

        heroHead.setFitWidth(88);
        heroHead.setFitHeight(88);
        heroHead.setPreserveRatio(false);
        heroHead.setSmooth(false);
        heroHead.getStyleClass().add("accounts-hero-head");

        heroFallback.getStyleClass().add("accounts-head-fallback");
        heroFallback.setVisible(false);
        heroFallback.setManaged(false);

        headFrame.getChildren().addAll(heroHead, heroFallback);

        VBox identity = new VBox(7);

        Label eyebrow = new Label("ACTIVE MINECRAFT ACCOUNT");
        eyebrow.getStyleClass().add("accounts-eyebrow");

        heroUsername.getStyleClass().add("accounts-hero-name");

        HBox statusRow = new HBox(8);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        heroStatus.getStyleClass().add("accounts-status-pill");

        heroUuid.getStyleClass().add("accounts-hero-uuid");

        statusRow.getChildren().addAll(heroStatus, heroUuid);
        identity.getChildren().addAll(eyebrow, heroUsername, statusRow);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        VBox actions = new VBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);

        addAccountButton.getStyleClass().add("accounts-primary-button");
        refreshButton.getStyleClass().add("accounts-secondary-button");
        signOutButton.getStyleClass().add("accounts-danger-button");

        addAccountButton.setOnAction(event -> login());
        refreshButton.setOnAction(event -> refresh());
        signOutButton.setOnAction(event -> logout());

        HBox topActions = new HBox(8, addAccountButton, refreshButton);
        topActions.setAlignment(Pos.CENTER_RIGHT);
        actions.getChildren().addAll(topActions, signOutButton);

        content.getChildren().addAll(headFrame, identity, spacer, actions);

        hero.getChildren().add(content);
        StackPane.setMargin(content, new Insets(24));

        return hero;
    }

    private VBox createAccountsSection() {

        HBox sectionHeader = new HBox();
        sectionHeader.setAlignment(Pos.CENTER_LEFT);

        VBox titles = new VBox(3);

        Label title = new Label("Your accounts");
        title.getStyleClass().add("accounts-section-title");

        accountCount.getStyleClass().add("accounts-section-count");
        titles.getChildren().addAll(title, accountCount);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label privacy = new Label("Stored locally • No account telemetry");
        privacy.getStyleClass().add("accounts-privacy");

        sectionHeader.getChildren().addAll(titles, spacer, privacy);

        errorLabel.getStyleClass().add("accounts-error");
        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        accountList.setHgap(12);
        accountList.setVgap(12);
        accountList.setPrefWrapLength(760);
        accountList.setMaxWidth(Double.MAX_VALUE);
        accountList.getStyleClass().add("accounts-grid");

        VBox section = new VBox(
                12,
                sectionHeader,
                errorLabel,
                accountList
        );
        section.getStyleClass().add("accounts-section");
        section.setFillWidth(true);

        return section;
    }

    private void renderAccounts() {

        Account current = accountService.getCurrentAccount();
        List<Account> accounts = accountService.getAccounts();

        renderHero(current);
        accountList.getChildren().clear();

        accountCount.setText(
                accounts.size() + " saved account"
                        + (accounts.size() == 1 ? "" : "s")
        );

        if (accounts.isEmpty()) {
            accountList.getChildren().add(createEmptyCard());
            return;
        }

        for (Account account : accounts) {
            accountList.getChildren().add(
                    createAccountCard(
                            account,
                            current != null
                                    && account.getUuid().equals(current.getUuid())
                    )
            );
        }
    }

    private void renderHero(Account account) {

        if (account == null) {

            heroUsername.setText("No account connected");
            heroStatus.setText("NOT CONNECTED");
            heroStatus.getStyleClass().remove("accounts-status-connected");
            heroStatus.getStyleClass().add("accounts-status-disconnected");
            heroUuid.setText("Connect a Microsoft account to get started.");

            heroHead.setImage(null);
            heroHead.setVisible(false);
            heroHead.setManaged(false);
            heroFallback.setVisible(true);
            heroFallback.setManaged(true);

            signOutButton.setDisable(true);
            refreshButton.setDisable(true);
            return;
        }

        heroUsername.setText(account.getUsername());
        heroStatus.setText("CONNECTED");
        heroStatus.getStyleClass().remove("accounts-status-disconnected");
        heroStatus.getStyleClass().add("accounts-status-connected");

        heroUuid.setText(
                account.getUuid() != null
                        ? "UUID  " + account.getUuid()
                        : "UUID unavailable"
        );

        signOutButton.setDisable(false);
        refreshButton.setDisable(false);

        heroFallback.setVisible(false);
        heroFallback.setManaged(false);
        heroHead.setManaged(true);

        AccountSkinService.loadHead(account, image -> {
            heroHead.setImage(image);
            heroHead.setVisible(image != null);
            heroFallback.setVisible(image == null);
            heroFallback.setManaged(image == null);
        });
    }


    private VBox createAccountCard(Account account, boolean active) {

        VBox card = new VBox(14);
        card.getStyleClass().add("accounts-card");

        if (active) {
            card.getStyleClass().add("accounts-card-active");
        }

        HBox top = new HBox(13);
        top.setAlignment(Pos.CENTER_LEFT);

        StackPane headFrame = new StackPane();
        headFrame.getStyleClass().add("accounts-card-head-frame");
        headFrame.setMinSize(54, 54);
        headFrame.setPrefSize(54, 54);
        headFrame.setMaxSize(54, 54);

        ImageView head = new ImageView();
        head.setFitWidth(44);
        head.setFitHeight(44);
        head.setPreserveRatio(false);
        head.setSmooth(false);

        Label placeholder = new Label("?");
        placeholder.getStyleClass().add("accounts-card-head-fallback");

        headFrame.getChildren().addAll(head, placeholder);

        VBox identity = new VBox(3);

        Label name = new Label(account.getUsername());
        name.getStyleClass().add("accounts-card-name");

        Label uuid = new Label(account.getUuid());
        uuid.getStyleClass().add("accounts-card-uuid");

        identity.getChildren().addAll(name, uuid);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label state = new Label(active ? "ACTIVE" : "SAVED");
        state.getStyleClass().add(
                active
                        ? "accounts-card-active-label"
                        : "accounts-card-saved-label"
        );

        top.getChildren().addAll(headFrame, identity, spacer, state);

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_RIGHT);

        if (!active) {
            Button use = new Button("USE ACCOUNT");
            use.getStyleClass().add("accounts-card-use");
            use.setOnAction(event -> switchAccount(account));
            actions.getChildren().add(use);
        } else {
            Label activeHint = new Label("Currently selected for launches");
            activeHint.getStyleClass().add("accounts-card-hint");
            actions.getChildren().add(activeHint);
        }

        card.getChildren().addAll(top, actions);

        AccountSkinService.loadHead(account, image -> {
            head.setImage(image);
            placeholder.setVisible(image == null);
        });

        return card;
    }

    private VBox createEmptyCard() {

        VBox empty = new VBox(8);
        empty.setAlignment(Pos.CENTER);
        empty.setPrefHeight(180);
        empty.getStyleClass().add("accounts-empty");

        Label icon = new Label("+");
        icon.getStyleClass().add("accounts-empty-icon");

        Label title = new Label("No accounts yet");
        title.getStyleClass().add("accounts-empty-title");

        Label text = new Label(
                "Connect a Microsoft account to start launching Minecraft."
        );
        text.getStyleClass().add("accounts-empty-text");

        Button add = new Button("CONNECT ACCOUNT");
        add.getStyleClass().add("accounts-primary-button");
        add.setOnAction(event -> login());

        empty.getChildren().addAll(icon, title, text, add);
        return empty;
    }

    private void login() {

        setBusy(true, "Connecting to Microsoft...");

        Thread thread = new Thread(() -> {
            try {
                accountService.login();
                Platform.runLater(() -> setBusy(false, null));
            } catch (Throwable ex) {
                ex.printStackTrace();
                Platform.runLater(() -> showError(
                        ex.getMessage() != null
                                ? ex.getMessage()
                                : ex.toString()
                ));
            }
        });

        thread.setDaemon(true);
        thread.setName("Vanta-Accounts-Login");
        thread.start();
    }

    public void refresh() {

        setBusy(true, "Refreshing account...");

        Thread thread = new Thread(() -> {
            try {
                accountService.loadAccount();
                Platform.runLater(() -> setBusy(false, null));
            } catch (Throwable ex) {
                ex.printStackTrace();
                Platform.runLater(() -> showError(
                        ex.getMessage() != null
                                ? ex.getMessage()
                                : ex.toString()
                ));
            }
        });

        thread.setDaemon(true);
        thread.setName("Vanta-Accounts-Refresh");
        thread.start();
    }

    private void switchAccount(Account account) {

        setBusy(true, "Switching to " + account.getUsername() + "...");

        Thread thread = new Thread(() -> {
            try {
                accountService.switchAccount(account.getUuid());
                Platform.runLater(() -> setBusy(false, null));
            } catch (Throwable ex) {
                ex.printStackTrace();
                Platform.runLater(() -> showError(
                        ex.getMessage() != null
                                ? ex.getMessage()
                                : ex.toString()
                ));
            }
        });

        thread.setDaemon(true);
        thread.setName("Vanta-Accounts-Switch");
        thread.start();
    }

    private void logout() {

        setBusy(true, "Signing out...");

        Thread thread = new Thread(() -> {
            try {
                accountService.logout();
                Platform.runLater(() -> setBusy(false, null));
            } catch (Throwable ex) {
                ex.printStackTrace();
                Platform.runLater(() -> showError(
                        ex.getMessage() != null
                                ? ex.getMessage()
                                : ex.toString()
                ));
            }
        });

        thread.setDaemon(true);
        thread.setName("Vanta-Accounts-Logout");
        thread.start();
    }

    private void setBusy(boolean busy, String message) {

        addAccountButton.setDisable(busy);
        refreshButton.setDisable(busy || accountService.getCurrentAccount() == null);
        signOutButton.setDisable(busy || accountService.getCurrentAccount() == null);

        if (busy) {
            errorLabel.setText(message == null ? "Working..." : message);
            errorLabel.getStyleClass().remove("accounts-error");
            if (!errorLabel.getStyleClass().contains("accounts-busy")) {
                errorLabel.getStyleClass().add("accounts-busy");
            }
            errorLabel.setVisible(true);
            errorLabel.setManaged(true);
        } else {
            errorLabel.getStyleClass().remove("accounts-busy");
            if (!errorLabel.getStyleClass().contains("accounts-error")) {
                errorLabel.getStyleClass().add("accounts-error");
            }
            errorLabel.setVisible(false);
            errorLabel.setManaged(false);
        }
    }

    private void showError(String message) {

        errorLabel.setText("Could not complete account action: " + message);
        errorLabel.getStyleClass().remove("accounts-busy");
        if (!errorLabel.getStyleClass().contains("accounts-error")) {
            errorLabel.getStyleClass().add("accounts-error");
        }
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);

        addAccountButton.setDisable(false);
        refreshButton.setDisable(accountService.getCurrentAccount() == null);
        signOutButton.setDisable(accountService.getCurrentAccount() == null);
    }

    public void updateAccountDisplay() {
        renderAccounts();
    }
}
