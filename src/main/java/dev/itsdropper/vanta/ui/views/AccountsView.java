package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.async.UiExecutor;

import javafx.beans.binding.Bindings;
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

import dev.itsdropper.vanta.launcher.account.Account;
import dev.itsdropper.vanta.launcher.service.AccountService;
import dev.itsdropper.vanta.ui.components.PageHeader;
import dev.itsdropper.vanta.launcher.service.AccountSkinService;

import java.util.List;

public class AccountsView extends VBox implements dev.itsdropper.vanta.ui.LauncherViewLifecycle {

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
    private boolean listenersRegistered;
    private final AccountService.AccountListener accountListener = account ->
            UiExecutor.shared().dispatchToUi(() -> {
                if (listenersRegistered) {
                    renderAccounts();
                }
            });

    public AccountsView(AccountService accountService) {

        this.accountService = accountService;

        getStyleClass().addAll("page", "accounts-page");
        setFillWidth(true);

        PageHeader header = new PageHeader(
                "Accounts",
                "Manage the Minecraft accounts Vanta uses to launch your game.",
                "accounts-title",
                "accounts-subtitle"
        );
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
        content.paddingProperty().bind(
                Bindings.createObjectBinding(() -> {
                    double width = getWidth();
                    double horizontal = width < 620 ? 16 : width < 900 ? 24 : 38;
                    return new Insets(24, horizontal, 28, horizontal);
                }, widthProperty())
        );

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.getStyleClass().add("accounts-scroll");

        VBox.setVgrow(scroll, Priority.ALWAYS);
        getChildren().add(scroll);

        renderAccounts();
    }


    @Override
    public void onShown() {
        if (!listenersRegistered) {
            listenersRegistered = true;
            accountService.addListener(accountListener);
        }
        renderAccounts();
    }

    @Override
    public void onHidden() {
        if (listenersRegistered) {
            accountService.removeListener(accountListener);
            listenersRegistered = false;
        }
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
        identity.setMinWidth(0);
        HBox identityRow = new HBox(22, headFrame, identity);
        identityRow.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(identity, Priority.ALWAYS);

        Label eyebrow = new Label("ACTIVE MINECRAFT ACCOUNT");
        eyebrow.getStyleClass().add("accounts-eyebrow");

        heroUsername.getStyleClass().add("accounts-hero-name");
        heroUsername.setMinWidth(0);
        heroUsername.setWrapText(true);

        HBox statusRow = new HBox(8);
        statusRow.setMinWidth(0);
        statusRow.setAlignment(Pos.CENTER_LEFT);

        heroStatus.getStyleClass().add("accounts-status-pill");

        heroUuid.getStyleClass().add("accounts-hero-uuid");
        heroUuid.setMinWidth(0);
        heroUuid.setWrapText(true);

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

        content.getChildren().addAll(identityRow, spacer, actions);

        hero.getChildren().add(content);
        StackPane.setMargin(content, new Insets(24));

        hero.widthProperty().addListener((observable, previousWidth, nextWidth) -> {
            boolean compact = nextWidth.doubleValue() < 700;
            if (compact && content.getChildren().contains(spacer)) {
                content.getChildren().setAll(identityRow, actions);
                content.setSpacing(16);
                actions.setAlignment(Pos.CENTER_LEFT);
                topActions.setAlignment(Pos.CENTER_LEFT);
                StackPane.setMargin(content, new Insets(16));
            } else if (!compact && !content.getChildren().contains(spacer)) {
                content.getChildren().setAll(identityRow, spacer, actions);
                content.setSpacing(0);
                actions.setAlignment(Pos.CENTER_RIGHT);
                topActions.setAlignment(Pos.CENTER_RIGHT);
                StackPane.setMargin(content, new Insets(24));
            }
        });

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
        accountList.prefWrapLengthProperty().bind(accountList.widthProperty());
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
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.prefWidthProperty().bind(
                Bindings.createDoubleBinding(() -> {
                    double width = accountList.getWidth();
                    if (width <= 1) return 360.0;
                    if (width < 700) return Math.max(0, width - 2);
                    return Math.min(520, (width - accountList.getHgap()) / 2.0);
                }, accountList.widthProperty())
        );
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
        identity.setMinWidth(0);
        HBox.setHgrow(identity, Priority.ALWAYS);

        Label name = new Label(account.getUsername());
        name.getStyleClass().add("accounts-card-name");
        name.setMinWidth(0);
        name.setWrapText(true);

        Label uuid = new Label(account.getUuid());
        uuid.getStyleClass().add("accounts-card-uuid");
        uuid.setMinWidth(0);
        uuid.setWrapText(true);

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

        UiExecutor.shared().execute(() -> {
            try {
                accountService.login();
                UiExecutor.shared().dispatchToUi(() -> setBusy(false, null));
            } catch (Throwable ex) {
                ex.printStackTrace();
                UiExecutor.shared().dispatchToUi(() -> showError(
                        ex.getMessage() != null
                                ? ex.getMessage()
                                : ex.toString()
                ));
            }
        });

    }

    public void refresh() {

        setBusy(true, "Refreshing account...");

        UiExecutor.shared().execute(() -> {
            try {
                accountService.loadAccount();
                UiExecutor.shared().dispatchToUi(() -> setBusy(false, null));
            } catch (Throwable ex) {
                ex.printStackTrace();
                UiExecutor.shared().dispatchToUi(() -> showError(
                        ex.getMessage() != null
                                ? ex.getMessage()
                                : ex.toString()
                ));
            }
        });

    }

    private void switchAccount(Account account) {

        setBusy(true, "Switching to " + account.getUsername() + "...");

        UiExecutor.shared().execute(() -> {
            try {
                accountService.switchAccount(account.getUuid());
                UiExecutor.shared().dispatchToUi(() -> setBusy(false, null));
            } catch (Throwable ex) {
                ex.printStackTrace();
                UiExecutor.shared().dispatchToUi(() -> showError(
                        ex.getMessage() != null
                                ? ex.getMessage()
                                : ex.toString()
                ));
            }
        });

    }

    private void logout() {

        setBusy(true, "Signing out...");

        UiExecutor.shared().execute(() -> {
            try {
                accountService.logout();
                UiExecutor.shared().dispatchToUi(() -> setBusy(false, null));
            } catch (Throwable ex) {
                ex.printStackTrace();
                UiExecutor.shared().dispatchToUi(() -> showError(
                        ex.getMessage() != null
                                ? ex.getMessage()
                                : ex.toString()
                ));
            }
        });

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
