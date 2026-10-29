package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.launcher.instance.InstanceManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

public final class CreateHorizonInstanceView extends VBox {

    private final TextField nameField = new TextField("Horizon Experimental");
    private final Button createButton = new Button("CREATE EXPERIMENTAL INSTANCE");
    private final Button backButton = new Button("Back");
    private final Label status = new Label();
    private final Runnable onBack;
    private final Runnable onCreated;

    public CreateHorizonInstanceView(Runnable onBack, Runnable onCreated) {
        this.onBack = onBack;
        this.onCreated = onCreated;

        getStyleClass().add("page");
        setPadding(new Insets(32, 36, 36, 36));
        setSpacing(18);

        Label eyebrow = new Label("EXPERIMENTAL · RUST");
        eyebrow.getStyleClass().add("settings-nav-group");

        Label title = new Label("Create a Horizon instance");
        title.getStyleClass().add("page-title");

        Label subtitle = new Label(
                "Creates an isolated Vanta profile for the native Horizon launcher."
        );
        subtitle.getStyleClass().add("page-subtitle");

        VBox header = new VBox(5, eyebrow, title, subtitle);

        VBox panel = new VBox(12);
        panel.getStyleClass().add("create-instance-panel");
        panel.setPadding(new Insets(24));
        panel.setMaxWidth(680);

        Label nameLabel = new Label("Instance name");
        nameField.setPromptText("Horizon Experimental");
        nameField.setMaxWidth(Double.MAX_VALUE);

        Label compatibility = new Label(
                "Experimental limitations: Windows x64 only; Minecraft 26.3 only. " +
                "Vanta downloads and verifies Horizon's launcher, then opens it. " +
                "You must select/play the game inside Horizon for now. Java mods, Fabric and Forge are not supported."
        );
        compatibility.setWrapText(true);
        compatibility.getStyleClass().add("page-subtitle");

        panel.getChildren().addAll(nameLabel, nameField, compatibility, status);

        createButton.getStyleClass().add("primary-button");
        createButton.setOnAction(event -> create());

        backButton.getStyleClass().add("secondary-button");
        backButton.setOnAction(event -> onBack.run());

        HBox actions = new HBox(10, backButton, createButton);
        actions.setAlignment(Pos.CENTER_RIGHT);

        getChildren().addAll(header, panel, actions);
    }

    private void create() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (name.isBlank()) {
            status.setText("Enter an instance name.");
            nameField.requestFocus();
            return;
        }

        createButton.setDisable(true);
        backButton.setDisable(true);
        nameField.setDisable(true);
        status.setText("Creating isolated instance metadata...");

        Thread worker = new Thread(() -> {
            try {
                InstanceManager.createHorizonInstance(name);
                Platform.runLater(() -> {
                    status.setText("Experimental Horizon instance created.");
                    onCreated.run();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    createButton.setDisable(false);
                    backButton.setDisable(false);
                    nameField.setDisable(false);
                    status.setText(ex.getMessage() == null
                            ? "Could not create Horizon instance."
                            : ex.getMessage());
                });
            }
        }, "Vanta-Horizon-Instance-Creation");
        worker.setDaemon(true);
        worker.start();
    }
}
