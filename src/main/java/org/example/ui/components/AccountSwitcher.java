package org.example.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;

import org.example.launcher.account.Account;
import org.example.launcher.service.AccountService;
import org.example.launcher.service.AccountSkinService;

public class AccountSwitcher extends HBox {

    private final AccountService accountService;

    private final ImageView playerHead;
    private final Label usernameLabel;
    private final Label arrowLabel;

    private final Popup popup;
    private final VBox accountList;

    public AccountSwitcher(
            AccountService accountService
    ) {

        this.accountService = accountService;

        getStyleClass().add(
                "account-switcher"
        );

        setAlignment(
                Pos.CENTER_LEFT
        );

        setSpacing(8);

        setPadding(
                new Insets(
                        0,
                        9,
                        0,
                        7
                )
        );

        setMinHeight(30);
        setPrefHeight(30);
        setMaxHeight(30);

        // -----------------------------------------------------
        // PLAYER HEAD
        // -----------------------------------------------------

        playerHead =
                new ImageView();

        playerHead.setFitWidth(22);
        playerHead.setFitHeight(22);

        playerHead.setPreserveRatio(false);

        playerHead.setSmooth(false);

        playerHead.getStyleClass().add(
                "account-switcher-head"
        );

        // -----------------------------------------------------
        // USERNAME
        // -----------------------------------------------------

        usernameLabel =
                new Label("Not signed in");

        usernameLabel.getStyleClass().add(
                "account-switcher-name"
        );

        // -----------------------------------------------------
        // ARROW
        // -----------------------------------------------------

        arrowLabel =
                new Label("▾");

        arrowLabel.getStyleClass().add(
                "account-switcher-arrow"
        );

        getChildren().addAll(
                playerHead,
                usernameLabel,
                arrowLabel
        );

        // -----------------------------------------------------
        // POPUP
        // -----------------------------------------------------

        popup = new Popup();

        popup.setAutoHide(true);
        popup.setHideOnEscape(true);

        accountList =
                new VBox();

        accountList.getStyleClass().add(
                "account-popup"
        );

        accountList.setSpacing(4);

        accountList.setPadding(
                new Insets(10)
        );

        popup.getContent().add(
                accountList
        );

        // -----------------------------------------------------
        // CLICK
        // -----------------------------------------------------

        setOnMouseClicked(event -> {

            if (popup.isShowing()) {
                popup.hide();
            } else {
                updatePopup();
                showPopup();
            }
        });

        // -----------------------------------------------------
        // ACCOUNT LISTENER
        // -----------------------------------------------------

        accountService.addListener(
                account ->
                        Platform.runLater(
                                () -> updateAccountDisplay(
                                        account
                                )
                        )
        );

        updateAccountDisplay(
                accountService.getCurrentAccount()
        );
    }

    private void updateAccountDisplay(
            Account account
    ) {

        if (account == null) {

            usernameLabel.setText(
                    "Not signed in"
            );

            playerHead.setImage(null);

            return;
        }

        usernameLabel.setText(
                account.getUsername()
        );

        AccountSkinService.loadHead(
                account,
                image ->
                        playerHead.setImage(image)
        );
    }

    private void updatePopup() {

        accountList.getChildren().clear();

        Label header =
                new Label("ACCOUNTS");

        header.getStyleClass().add(
                "account-popup-header"
        );

        accountList.getChildren().add(
                header
        );

        Account currentAccount =
                accountService.getCurrentAccount();

        for (Account account :
                accountService.getAccounts()) {

            accountList.getChildren().add(
                    createAccountButton(
                            account,
                            currentAccount
                    )
            );
        }

        if (!accountService
                .getAccounts()
                .isEmpty()) {

            HBox separator =
                    new HBox();

            separator.getStyleClass().add(
                    "account-popup-separator"
            );

            accountList.getChildren().add(
                    separator
            );
        }

        Button addAccount =
                new Button();

        HBox addContent =
                new HBox(8);

        addContent.setAlignment(
                Pos.CENTER_LEFT
        );

        Label plus =
                new Label("+");

        plus.getStyleClass().add(
                "account-popup-plus"
        );

        Label text =
                new Label(
                        "Add Microsoft account"
                );

        text.getStyleClass().add(
                "account-popup-add-text"
        );

        addContent.getChildren().addAll(
                plus,
                text
        );

        addAccount.setGraphic(
                addContent
        );

        addAccount.getStyleClass().add(
                "account-popup-add"
        );

        addAccount.setMaxWidth(
                Double.MAX_VALUE
        );

        addAccount.setOnAction(
                event -> {

                    popup.hide();
                    loginNewAccount();
                }
        );

        accountList.getChildren().add(
                addAccount
        );
    }

    private Button createAccountButton(
            Account account,
            Account currentAccount
    ) {

        boolean active =
                currentAccount != null
                        && currentAccount
                        .getUuid()
                        .equals(
                                account.getUuid()
                        );

        Button button =
                new Button();

        HBox content =
                new HBox(9);

        content.setAlignment(
                Pos.CENTER_LEFT
        );

        ImageView head =
                new ImageView();

        head.setFitWidth(28);
        head.setFitHeight(28);
        head.setSmooth(false);

        head.getStyleClass().add(
                "account-popup-head"
        );

        Label username =
                new Label(
                        account.getUsername()
                );

        username.getStyleClass().add(
                "account-popup-username"
        );

        VBox text =
                new VBox(1);

        text.setAlignment(
                Pos.CENTER_LEFT
        );

        text.getChildren().add(
                username
        );

        if (active) {

            Label activeLabel =
                    new Label("Active");

            activeLabel.getStyleClass().add(
                    "account-popup-active"
            );

            text.getChildren().add(
                    activeLabel
            );
        }

        content.getChildren().addAll(
                head,
                text
        );

        button.setGraphic(
                content
        );

        button.getStyleClass().add(
                "account-popup-account"
        );

        if (active) {
            button.getStyleClass().add(
                    "active"
            );
        }

        button.setMaxWidth(
                Double.MAX_VALUE
        );

        button.setAlignment(
                Pos.CENTER_LEFT
        );

        AccountSkinService.loadHead(
                account,
                head::setImage
        );

        button.setOnAction(
                event -> {

                    popup.hide();

                    if (!active) {
                        switchAccount(
                                account
                        );
                    }
                }
        );

        return button;
    }

    private void showPopup() {

        if (getScene() == null ||
                getScene().getWindow() == null) {

            return;
        }

        double width = 270;

        double x =
                localToScreen(
                        getBoundsInLocal()
                ).getMaxX()
                        - width;

        double y =
                localToScreen(
                        getBoundsInLocal()
                ).getMaxY()
                        + 6;

        popup.show(
                getScene().getWindow(),
                x,
                y
        );
    }

    private void switchAccount(
            Account account
    ) {

        setDisable(true);

        Thread thread =
                new Thread(() -> {

                    try {

                        accountService.switchAccount(
                                account.getUuid()
                        );

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                    } finally {

                        Platform.runLater(
                                () -> setDisable(false)
                        );
                    }
                });

        thread.setDaemon(true);
        thread.start();
    }

    private void loginNewAccount() {

        setDisable(true);

        Thread thread =
                new Thread(() -> {

                    try {

                        accountService.login();

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                    } finally {

                        Platform.runLater(
                                () -> setDisable(false)
                        );
                    }
                });

        thread.setDaemon(true);
        thread.start();
    }
}