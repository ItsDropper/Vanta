package dev.itsdropper.vanta.ui.components;

import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import dev.itsdropper.vanta.launcher.account.Account;
import dev.itsdropper.vanta.ui.VantaUI;
import dev.itsdropper.vanta.ui.Localization;

public class AccountCard extends VBox {


    private final Label nameLabel;
    private final Label statusLabel;

    public AccountCard() {

        setSpacing(VantaUI.spacing(2));
        setPadding(new Insets(VantaUI.spacing(6)));
        setMaxWidth(Double.MAX_VALUE);

        getStyleClass().add("info-card");
        VantaUI.applySurface(this, VantaUI.SurfaceRole.CARD);

        Label title = new Label(Localization.text("accountCard.profile"));
        title.getStyleClass().add("card-title");

        nameLabel = new Label(Localization.text("accountCard.none"));
        nameLabel.getStyleClass().add("card-main");

        statusLabel = new Label(Localization.text("accountCard.disconnected"));
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
                Localization.text("accountCard.connected")
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
                Localization.text("accountCard.none")
        );

        statusLabel.setText(
                Localization.text("accountCard.disconnected")
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
                Localization.text("accountCard.unavailable")
        );

        statusLabel.setText(
                Localization.text("accountCard.authError")
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
