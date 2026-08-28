package org.example.ui.components;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import org.example.launcher.account.Account;

public class AccountCard extends VBox {


    private final Label nameLabel;
    private final Label statusLabel;

    public AccountCard() {

        setSpacing(10);
        setPadding(new Insets(22));

        getStyleClass().add("info-card");

        Label title = new Label("PROFILE");
        title.getStyleClass().add("card-title");

        nameLabel = new Label("No account connected");
        nameLabel.getStyleClass().add("card-main");

        statusLabel = new Label("● Disconnected");
        statusLabel.getStyleClass().add("disconnected-label");

        getChildren().addAll(
                title,
                nameLabel,
                statusLabel
        );
    }

    public void setAccount(Account account) {

        if (account == null) {

            setDisconnected();

            return;
        }

        nameLabel.setText(
                account.getUsername()
        );

        statusLabel.setText(
                "● Connected"
        );

        statusLabel.getStyleClass().remove(
                "disconnected-label"
        );

        if (!statusLabel.getStyleClass().contains(
                "connected-label"
        )) {

            statusLabel.getStyleClass().add(
                    "connected-label"
            );
        }
    }

    public void setDisconnected() {

        nameLabel.setText(
                "No account connected"
        );

        statusLabel.setText(
                "● Disconnected"
        );

        statusLabel.getStyleClass().remove(
                "connected-label"
        );

        if (!statusLabel.getStyleClass().contains(
                "disconnected-label"
        )) {

            statusLabel.getStyleClass().add(
                    "disconnected-label"
            );
        }
    }

    public void setError() {

        nameLabel.setText(
                "Account unavailable"
        );

        statusLabel.setText(
                "● Authentication error"
        );

        statusLabel.getStyleClass().remove(
                "connected-label"
        );

        if (!statusLabel.getStyleClass().contains(
                "disconnected-label"
        )) {

            statusLabel.getStyleClass().add(
                    "disconnected-label"
            );
        }
    }


}
