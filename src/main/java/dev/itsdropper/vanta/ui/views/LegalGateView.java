package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.kit.VantaLogo;
import dev.itsdropper.vanta.ui.legal.LegalAcceptance;
import dev.itsdropper.vanta.ui.legal.LegalPane;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * Shown to existing users when the Terms of Service or Privacy Policy changed since they last accepted. Blocks the
 * launcher until the new versions are accepted, or the user exits.
 */
public final class LegalGateView extends VBox {

    public LegalGateView(Runnable onAccepted) {
        super(16);
        getStyleClass().addAll("onboarding-page", "legal-gate");
        setPadding(new Insets(32, 36, 28, 36));
        setMaxWidth(760);
        setMaxHeight(600);
        setAlignment(Pos.TOP_LEFT);

        boolean updated = LegalAcceptance.wasAcceptedBefore();
        Label title = new Label(Localization.text(updated ? "legal.updatedTitle" : "onboarding.termsTitle"));
        title.getStyleClass().add("onboarding-title");
        Label subtitle = new Label(Localization.text(updated ? "legal.updatedSubtitle" : "onboarding.termsSubtitle"));
        subtitle.getStyleClass().add("onboarding-subtitle");
        subtitle.setWrapText(true);

        LegalPane pane = new LegalPane(300);

        Button decline = new Button(Localization.text("legal.decline"));
        decline.getStyleClass().add("vanta-button-secondary");
        decline.setOnAction(event -> Platform.exit());

        Button accept = new Button(Localization.text("legal.continue"));
        accept.getStyleClass().addAll("vanta-button-primary", "button-large");
        accept.disableProperty().bind(pane.acceptedProperty().not());
        accept.setOnAction(event -> {
            LegalAcceptance.accept();
            onAccepted.run();
        });

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(10, decline, spacer, accept);
        actions.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(VantaLogo.full(26, 18), title, subtitle, pane, actions);
    }
}
