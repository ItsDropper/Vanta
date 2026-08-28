package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import org.example.launcher.account.Account;
import org.example.launcher.service.AccountService;

public class AccountsView extends VBox {

    private final AccountService accountService;

    private final Label usernameLabel;
    private final Label uuidLabel;
    private final Label statusLabel;

    private final Button loginButton;
    private final Button logoutButton;
    private final Button refreshButton;

    public AccountsView(AccountService accountService) {

        this.accountService = accountService;

        getStyleClass().add("page");

        setPadding(new Insets(40));
        setSpacing(28);

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        Label title = new Label("Accounts");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label(
                "Manage the Microsoft account used by Vanta."
        );
        subtitle.getStyleClass().add("page-subtitle");

        VBox header = new VBox(
                6,
                title,
                subtitle
        );

        // ---------------------------------------------------------
        // ACCOUNT PANEL
        // ---------------------------------------------------------

        VBox accountPanel = new VBox(18);
        accountPanel.getStyleClass().add("account-panel");
        accountPanel.setPadding(new Insets(28));

        Label panelTitle = new Label("MICROSOFT ACCOUNT");
        panelTitle.getStyleClass().add("card-title");

        usernameLabel = new Label("No account connected");
        usernameLabel.getStyleClass().add("account-name");

        statusLabel = new Label("● Disconnected");
        statusLabel.getStyleClass().add("disconnected-label");

        uuidLabel = new Label("UUID unavailable");
        uuidLabel.getStyleClass().add("account-detail");

        HBox actions = new HBox(10);

        loginButton = new Button("CONNECT ACCOUNT");
        loginButton.getStyleClass().add("primary-button");

        refreshButton = new Button("REFRESH");
        refreshButton.getStyleClass().add("secondary-button");

        logoutButton = new Button("SIGN OUT");
        logoutButton.getStyleClass().add("danger-button");

        loginButton.setOnAction(event -> login());
        refreshButton.setOnAction(event -> refresh());
        logoutButton.setOnAction(event -> logout());

        actions.getChildren().addAll(
                loginButton,
                refreshButton,
                logoutButton
        );

        accountPanel.getChildren().addAll(
                panelTitle,
                usernameLabel,
                statusLabel,
                uuidLabel,
                actions
        );

        getChildren().addAll(
                header,
                accountPanel
        );

        updateAccount(
                accountService.getCurrentAccount()
        );
    }

    private void updateAccount(Account account) {

        if (account == null) {

            usernameLabel.setText("No account connected");
            statusLabel.setText("● Disconnected");

            statusLabel.getStyleClass().remove("connected-label");
            statusLabel.getStyleClass().add("disconnected-label");

            uuidLabel.setText("UUID unavailable");

            loginButton.setVisible(true);
            loginButton.setManaged(true);

            logoutButton.setVisible(false);
            logoutButton.setManaged(false);

            refreshButton.setDisable(true);

            return;
        }

        usernameLabel.setText(
                account.getUsername()
        );

        statusLabel.setText("● Connected");

        statusLabel.getStyleClass().remove("disconnected-label");
        statusLabel.getStyleClass().add("connected-label");

        uuidLabel.setText(
                account.getUuid() != null
                        ? "UUID  " + account.getUuid()
                        : "UUID unavailable"
        );

        loginButton.setVisible(false);
        loginButton.setManaged(false);

        logoutButton.setVisible(true);
        logoutButton.setManaged(true);

        refreshButton.setDisable(false);
    }

    private void login() {

        loginButton.setDisable(true);
        loginButton.setText("CONNECTING...");

        new Thread(() -> {

            try {

                Account account =
                        accountService.login();

                Platform.runLater(() -> {

                    updateAccount(account);

                    loginButton.setDisable(false);
                    loginButton.setText("CONNECT ACCOUNT");
                });

            } catch (Throwable ex) {

                ex.printStackTrace();

                Platform.runLater(() -> {

                    loginButton.setDisable(false);
                    loginButton.setText("CONNECT ACCOUNT");

                    showError(ex);
                });
            }

        }).start();
    }

    public void refresh() {

        refreshButton.setDisable(true);
        refreshButton.setText("REFRESHING...");

        new Thread(() -> {

            try {

                Account account =
                        accountService.loadAccount();

                Platform.runLater(() -> {

                    updateAccount(account);

                    refreshButton.setDisable(false);
                    refreshButton.setText("REFRESH");
                });

            } catch (Throwable ex) {

                ex.printStackTrace();

                Platform.runLater(() -> {

                    refreshButton.setDisable(false);
                    refreshButton.setText("REFRESH");

                    showError(ex);
                });
            }

        }).start();
    }

    private void logout() {

        try {

            accountService.logout();

            updateAccount(null);

        } catch (Throwable ex) {

            ex.printStackTrace();

            showError(ex);
        }
    }

    private void showError(Throwable ex) {

        Alert alert = new Alert(
                Alert.AlertType.ERROR
        );

        alert.setTitle("Vanta");
        alert.setHeaderText("Authentication error");

        alert.setContentText(
                ex.getMessage() != null
                        ? ex.getMessage()
                        : ex.toString()
        );

        alert.showAndWait();
    }
}