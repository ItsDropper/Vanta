/*
 * Copyright (c) 2026 ItsDropper
 * Licensed under the MIT License.
 */
package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.launcher.server.RemoteRconClient;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.io.IOException;

/** UI for connecting to a remote Minecraft Java server through its RCON interface. */
public final class RemoteServerConnectionDialog {
    private RemoteServerConnectionDialog() {}

    public static void open(Window owner) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Connect to remote server");
        if (owner != null) dialog.initOwner(owner);
        dialog.initModality(Modality.WINDOW_MODAL);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.CANCEL, new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE));

        TextField host = new TextField();
        host.setPromptText("server.example.com");
        TextField port = new TextField("25575");
        PasswordField password = new PasswordField();
        password.setPromptText("RCON password");
        Label help = new Label("Connects through Minecraft Java RCON. The host must enable RCON and expose its RCON port. This is not the normal player port.");
        help.setWrapText(true);
        help.setMaxWidth(430);
        GridPane fields = new GridPane();
        fields.setHgap(10); fields.setVgap(10); fields.setPadding(new Insets(14));
        fields.add(new Label("Host / IP"), 0, 0); fields.add(host, 1, 0);
        fields.add(new Label("RCON port"), 0, 1); fields.add(port, 1, 1);
        fields.add(new Label("RCON password"), 0, 2); fields.add(password, 1, 2);
        fields.add(help, 0, 3, 2, 1);
        fields.getColumnConstraints().addAll(new ColumnConstraints(), growColumn());
        dialog.getDialogPane().setContent(fields);
        dialog.getDialogPane().setPrefWidth(500);
        Button connectButton = (Button) dialog.getDialogPane().lookupButton(dialog.getDialogPane().getButtonTypes().get(1));
        connectButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            if (host.getText().isBlank() || password.getText().isEmpty()) {
                event.consume();
                showError("Host and RCON password are required.");
                return;
            }
            try {
                int value = Integer.parseInt(port.getText().trim());
                if (value < 1 || value > 65535) throw new NumberFormatException();
            } catch (NumberFormatException e) {
                event.consume();
                showError("Enter a valid RCON port from 1 to 65535.");
            }
        });

        dialog.showAndWait().filter(result -> result.getButtonData() == ButtonBar.ButtonData.OK_DONE).ifPresent(result -> {
            String address = host.getText().trim();
            int remotePort = Integer.parseInt(port.getText().trim());
            char[] secret = password.getText().toCharArray();
            password.clear();
            connectButton.setDisable(true);
            UiExecutor.shared().execute(() -> {
                RemoteRconClient client = null;
                Exception failure = null;
                try {
                    client = new RemoteRconClient(address, remotePort, new String(secret));
                } catch (Exception e) { failure = e; }
                java.util.Arrays.fill(secret, '\0');
                RemoteRconClient connected = client;
                Exception error = failure;
                UiExecutor.shared().dispatchToUi(() -> {
                    if (error != null) showError("Could not connect: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
                    else showConsole(owner, address, remotePort, connected);
                });
            });
        });
    }

    private static void showConsole(Window owner, String host, int port, RemoteRconClient client) {
        Stage stage = new Stage();
        stage.setTitle("Remote Console — " + host + ":" + port);
        if (owner != null) stage.initOwner(owner);
        TextArea output = new TextArea();
        output.setEditable(false);
        output.setWrapText(true);
        output.getStyleClass().add("server-console");
        output.appendText("Connected to " + host + ":" + port + " using RCON.\n");
        TextField command = new TextField();
        command.setPromptText("Enter a remote server command");
        Button send = new Button("Send");
        Button close = new Button("Disconnect");
        Runnable sendCommand = () -> {
            String value = command.getText().trim();
            if (value.isEmpty()) return;
            command.clear();
            send.setDisable(true);
            UiExecutor.shared().execute(() -> {
                String response = null;
                Exception failure = null;
                try { response = client.command(value); } catch (Exception e) { failure = e; }
                String result = response;
                Exception error = failure;
                UiExecutor.shared().dispatchToUi(() -> {
                    send.setDisable(false);
                    output.appendText("> " + value + "\n");
                    if (error != null) output.appendText("Connection error: " + error.getMessage() + "\n");
                    else if (result == null || result.isBlank()) output.appendText("(No response)\n");
                    else output.appendText(result + "\n");
                    output.positionCaret(output.getLength());
                    if (error != null) { client.close(); command.setDisable(true); send.setDisable(true); }
                });
            });
        };
        send.setOnAction(event -> sendCommand.run());
        command.setOnAction(event -> sendCommand.run());
        close.setOnAction(event -> stage.close());
        HBox controls = new HBox(8, command, send, close);
        controls.setAlignment(Pos.CENTER);
        HBox.setHgrow(command, Priority.ALWAYS);
        VBox root = new VBox(10, output, controls);
        root.setPadding(new Insets(14));
        VBox.setVgrow(output, Priority.ALWAYS);
        stage.setScene(new Scene(root, 720, 460));
        stage.setMinWidth(520);
        stage.setMinHeight(340);
        stage.setOnHidden(event -> client.close());
        stage.show();
    }

    private static ColumnConstraints growColumn() {
        ColumnConstraints constraints = new ColumnConstraints();
        constraints.setHgrow(Priority.ALWAYS);
        return constraints;
    }

    private static void showError(String message) {
        UiExecutor.shared().dispatchToUi(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR, message, ButtonType.OK);
            alert.setHeaderText("Remote server connection");
            alert.showAndWait();
        });
    }
}
