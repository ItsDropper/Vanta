package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.launcher.server.ServerManagerService;
import dev.itsdropper.vanta.launcher.server.ServerManagerService.Profile;
import dev.itsdropper.vanta.launcher.server.ServerManagerService.Snapshot;
import dev.itsdropper.vanta.launcher.server.ServerManagerService.State;
import dev.itsdropper.vanta.ui.components.IconView;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Window;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import java.util.Properties;

/** Local-first manager for vanilla/Paper/Fabric/Forge-compatible Java server jars. */
public final class ServerManagerView extends BorderPane implements AutoCloseable {
    private final ServerManagerService service = new ServerManagerService();
    private final VBox profileList = new VBox(8);
    private final VBox detail = new VBox(14);
    private final Label status = new Label("Select a server");
    private final Label statusDot = new Label("●");
    private final Label serverTitle = new Label("No server selected");
    private final Label serverMeta = new Label("Add an existing Java server directory to get started.");
    private final TextArea console = new TextArea();
    private final TextField commandInput = new TextField();
    private final TextField javaInput = new TextField();
    private final Spinner<Integer> minRam = new Spinner<>(256, 262144, 1024, 256);
    private final Spinner<Integer> maxRam = new Spinner<>(256, 262144, 4096, 256);
    private final CheckBox eula = new CheckBox("I have read and agree to the Minecraft EULA");
    private final Button startButton = actionButton("Start", IconView.Type.PLAY, "server-primary");
    private final Button stopButton = actionButton("Stop", IconView.Type.PAUSE, "server-secondary");
    private final Button forceButton = actionButton("Force stop", IconView.Type.CLOSE, "server-danger");
    private final Button restartButton = actionButton("Restart", IconView.Type.REFRESH, "server-secondary");
    private String selectedId;
    private boolean suppressEulaChange;

    public ServerManagerView() {
        getStyleClass().add("page");
        getStyleClass().add("server-manager");
        setPadding(new Insets(22, 24, 24, 24));

        Label eyebrow = new Label("LOCAL HOSTING");
        eyebrow.getStyleClass().add("server-eyebrow");
        Label heading = new Label("Server Manager");
        heading.getStyleClass().add("server-heading");
        Label subtitle = new Label("Run, inspect and maintain your own Minecraft servers from Vanta.");
        subtitle.getStyleClass().add("server-subtitle");

        Button add = actionButton("Import existing", IconView.Type.FOLDER, "server-secondary");
        add.setOnAction(event -> addProfile());
        Button create = actionButton("Create from JAR", IconView.Type.PLUS, "server-primary");
        create.setOnAction(event -> createProfileFromJar());
        HBox actions = new HBox(8, add, create);
        HBox headingRow = new HBox(14, new VBox(5, eyebrow, heading, subtitle), spacer(), actions);
        headingRow.setAlignment(Pos.CENTER_LEFT);
        VBox.setVgrow(headingRow.getChildren().get(0), Priority.NEVER);

        profileList.getStyleClass().add("server-profile-list");
        profileList.setPadding(new Insets(10));
        ScrollPane profileScroll = new ScrollPane(profileList);
        profileScroll.setFitToWidth(true);
        profileScroll.getStyleClass().add("server-scroll");
        profileScroll.setPrefWidth(245);
        profileScroll.setMinWidth(220);

        VBox left = new VBox(12);
        HBox listHeader = new HBox(8, new Label("YOUR SERVERS"), spacer(), new Label("LOCAL"));
        listHeader.getStyleClass().add("server-list-header");
        left.getChildren().addAll(listHeader, profileScroll);
        VBox.setVgrow(profileScroll, Priority.ALWAYS);
        left.getStyleClass().add("server-sidebar");

        serverTitle.getStyleClass().add("server-title");
        serverMeta.getStyleClass().add("server-subtitle");
        status.getStyleClass().add("server-status-label");
        statusDot.getStyleClass().add("server-status-dot");
        HBox statusRow = new HBox(8, statusDot, status, spacer(), restartButton, stopButton, forceButton, startButton);
        statusRow.setAlignment(Pos.CENTER_LEFT);
        statusRow.getStyleClass().add("server-status-row");

        javaInput.setPromptText("java (or absolute path to java.exe)");
        javaInput.getStyleClass().add("server-input");
        minRam.setEditable(true);
        maxRam.setEditable(true);
        minRam.getStyleClass().add("server-spinner");
        maxRam.getStyleClass().add("server-spinner");
        eula.getStyleClass().add("server-eula");
        Button saveSettings = actionButton("Save runtime settings", IconView.Type.SAVE, "server-secondary");
        saveSettings.setOnAction(event -> saveRuntimeSettings(true));

        GridPane runtimeGrid = new GridPane();
        runtimeGrid.setHgap(12);
        runtimeGrid.setVgap(10);
        runtimeGrid.add(field("Java executable", javaInput), 0, 0);
        runtimeGrid.add(field("Minimum RAM (MB)", minRam), 1, 0);
        runtimeGrid.add(field("Maximum RAM (MB)", maxRam), 2, 0);
        ColumnConstraints col = new ColumnConstraints();
        col.setHgrow(Priority.ALWAYS); col.setPercentWidth(46);
        ColumnConstraints col2 = new ColumnConstraints();
        col2.setHgrow(Priority.ALWAYS); col2.setPercentWidth(27);
        runtimeGrid.getColumnConstraints().addAll(col, col2, col2);

        FlowPane tools = new FlowPane(8, 8);
        Button properties = actionButton("Server properties", IconView.Type.SETTINGS, "server-secondary");
        Button backup = actionButton("Create backup", IconView.Type.PACKAGE, "server-secondary");
        Button restore = actionButton("Restore backup", IconView.Type.REFRESH, "server-secondary");
        Button players = actionButton("List players", IconView.Type.ACCOUNTS, "server-secondary");
        Button crashReport = actionButton("Crash diagnostics", IconView.Type.SHIELD, "server-secondary");
        Button remove = actionButton("Remove profile", IconView.Type.TRASH, "server-danger");
        properties.setOnAction(event -> editProperties());
        backup.setOnAction(event -> runBackground("Creating server backup", () -> service.createBackup(requireSelectedId()),
                path -> info("Backup created", "Consistent offline backup saved to:\n" + path)));
        restore.setOnAction(event -> restoreBackupDialog());
        players.setOnAction(event -> runCommand("list"));
        crashReport.setOnAction(event -> showCrashDiagnostics());
        remove.setOnAction(event -> removeProfile());
        tools.getChildren().addAll(properties, backup, restore, players, crashReport, remove);

        console.setEditable(false);
        console.setWrapText(false);
        console.setPromptText("Server output will appear here when the process starts.");
        console.getStyleClass().add("server-console");
        console.setPrefRowCount(16);
        VBox consolePanel = new VBox(8, sectionHeader("LIVE CONSOLE", "Process output and server commands"), console);
        consolePanel.getStyleClass().add("server-panel");
        VBox.setVgrow(console, Priority.ALWAYS);
        HBox commandRow = new HBox(8);
        commandInput.setPromptText("Enter a command, e.g. say Server restarting in 5 minutes");
        commandInput.getStyleClass().add("server-input");
        Button send = actionButton("Send", IconView.Type.ARROW_RIGHT, "server-primary");
        send.setOnAction(event -> submitCommand());
        commandInput.setOnAction(event -> submitCommand());
        commandRow.getChildren().addAll(commandInput, send);
        HBox.setHgrow(commandInput, Priority.ALWAYS);
        consolePanel.getChildren().add(commandRow);

        VBox runtimePanel = new VBox(10, sectionHeader("RUNTIME", "Java process configuration"), runtimeGrid, eula, saveSettings);
        runtimePanel.getStyleClass().add("server-panel");
        detail.getChildren().setAll(
                new VBox(5, serverTitle, serverMeta),
                statusRow,
                tools,
                runtimePanel,
                consolePanel
        );
        detail.getStyleClass().add("server-detail");
        ScrollPane detailScroll = new ScrollPane(detail);
        detailScroll.setFitToWidth(true);
        detailScroll.getStyleClass().add("server-scroll");
        VBox.setVgrow(detailScroll, Priority.ALWAYS);

        SplitPane workspace = new SplitPane(left, detailScroll);
        workspace.setDividerPositions(0.27);
        workspace.getStyleClass().add("server-workspace");
        VBox main = new VBox(18, headingRow, workspace);
        VBox.setVgrow(workspace, Priority.ALWAYS);
        setCenter(main);

        startButton.setOnAction(event -> startServer());
        stopButton.setOnAction(event -> stopServer());
        forceButton.setOnAction(event -> forceStopServer());
        restartButton.setOnAction(event -> restartServer());
        eula.setOnAction(event -> acceptEulaChanged());
        commandInput.setDisable(true);
        setActionsEnabled(false);
        detail.setDisable(true);

        service.setConsoleListener((id, line) -> Platform.runLater(() -> {
            if (!id.equals(selectedId)) return;
            console.appendText(line + System.lineSeparator());
            if (console.getLength() > 140_000) console.deleteText(0, console.getLength() - 100_000);
        }));
        service.setStateListener((id, snapshot) -> Platform.runLater(() -> {
            if (id.equals(selectedId)) refreshSelected(snapshot);
            refreshProfileList();
        }));
        refreshProfileList();
        if (!service.profiles().isEmpty()) selectProfile(service.profiles().get(0).id);
    }

    private void addProfile() {
        Window owner = getScene() == null ? null : getScene().getWindow();
        DirectoryChooser directories = new DirectoryChooser();
        directories.setTitle("Choose server directory");
        var directory = directories.showDialog(owner);
        if (directory == null) return;
        FileChooser jars = new FileChooser();
        jars.setTitle("Choose server JAR");
        jars.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java server JAR", "*.jar"));
        jars.setInitialDirectory(directory);
        var jar = jars.showOpenDialog(owner);
        if (jar == null) return;
        TextInputDialog nameDialog = new TextInputDialog(directory.getName());
        nameDialog.setTitle("Add server");
        nameDialog.setHeaderText("Name this server profile");
        nameDialog.setContentText("Display name:");
        Optional<String> name = nameDialog.showAndWait();
        if (name.isEmpty() || name.get().isBlank()) return;
        try {
            Profile p = service.addProfile(name.get(), directory.toPath(), jar.toPath());
            selectProfile(p.id);
            refreshProfileList();
            console.appendText("Vanta: profile added. Review RAM settings and EULA acceptance before starting.\n");
        } catch (Exception e) {
            error("Could not add server", e.getMessage());
        }
    }

    private void createProfileFromJar() {
        Window owner = getScene() == null ? null : getScene().getWindow();
        TextInputDialog nameDialog = new TextInputDialog("My Minecraft Server");
        nameDialog.setTitle("Create server");
        nameDialog.setHeaderText("Create a new server directory");
        nameDialog.setContentText("Server name:");
        Optional<String> name = nameDialog.showAndWait();
        if (name.isEmpty() || name.get().isBlank()) return;

        DirectoryChooser parentChooser = new DirectoryChooser();
        parentChooser.setTitle("Choose where the server folder should be created");
        var parent = parentChooser.showDialog(owner);
        if (parent == null) return;

        FileChooser jarChooser = new FileChooser();
        jarChooser.setTitle("Choose a server JAR (vanilla, Paper, Fabric, or compatible)");
        jarChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Java server JAR", "*.jar"));
        var jar = jarChooser.showOpenDialog(owner);
        if (jar == null) return;

        String slug = name.get().trim().toLowerCase(java.util.Locale.ROOT)
                .replaceAll("[^a-z0-9._-]+", "-").replaceAll("^-+|-+$", "");
        if (slug.isBlank()) slug = "minecraft-server";
        Path directory = parent.toPath().resolve(slug).normalize();
        Path targetJar = directory.resolve(jar.getName()).normalize();
        try {
            if (Files.exists(directory)) throw new IOException("That server folder already exists. Choose a different name or parent directory.");
            Files.createDirectory(directory);
            Files.copy(jar.toPath(), targetJar, StandardCopyOption.COPY_ATTRIBUTES);
            Profile p = service.addProfile(name.get().trim(), directory, targetJar);
            selectProfile(p.id);
            refreshProfileList();
            console.appendText("Vanta: created a new server folder from the selected JAR. First startup may generate eula.txt and configuration files.\\n");
        } catch (Exception e) {
            try { Files.deleteIfExists(targetJar); } catch (IOException ignored) {}
            try { Files.deleteIfExists(directory); } catch (IOException ignored) {}
            error("Could not create server", e.getMessage());
        }
    }

    private void restoreBackupDialog() {
        try {
            String id = requireSelectedId();
            Profile p = service.profile(id).orElseThrow();
            Path backupDirectory = p.directoryPath().resolve("backups");
            if (!Files.isDirectory(backupDirectory)) {
                info("No backups found", "Create a backup first. Backups are stored in this server's backups folder.");
                return;
            }
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose a server backup to restore");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Vanta server backups", "*.zip"));
            chooser.setInitialDirectory(backupDirectory.toFile());
            var selected = chooser.showOpenDialog(getScene() == null ? null : getScene().getWindow());
            if (selected == null) return;
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Restore server backup");
            confirm.setHeaderText("Restore " + selected.getName() + "?");
            confirm.setContentText("The server must be stopped. Vanta will create a safety backup of the current server before replacing files. Existing files not present in the selected backup will be left in place.");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            Path backup = selected.toPath();
            runBackground("Restoring server backup", () -> service.restoreBackup(id, backup),
                    safety -> info("Backup restored", "The selected backup was restored. Safety backup of the previous state:\\n" + safety));
        } catch (Exception e) {
            error("Could not restore backup", e.getMessage());
        }
    }

    private void removeProfile() {
        try {
            String id = requireSelectedId();
            Profile p = service.profile(id).orElseThrow();
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Remove server profile");
            confirm.setHeaderText("Remove " + p.name + " from Vanta?");
            confirm.setContentText("This removes only the saved profile from Vanta. The server directory, world files, JAR and backups will not be deleted.");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            service.removeProfile(id);
            selectedId = null;
            console.clear();
            serverTitle.setText("No server selected");
            serverMeta.setText("Select a profile or create a server.");
            setActionsEnabled(false);
            detail.setDisable(true);
            refreshProfileList();
        } catch (Exception e) {
            error("Could not remove profile", e.getMessage());
        }
    }

    private void refreshProfileList() {
        String keep = selectedId;
        profileList.getChildren().clear();
        List<Profile> profiles = service.profiles();
        if (profiles.isEmpty()) {
            Label empty = new Label("No server profiles yet.\n\nAdd an existing server folder containing a server JAR.");
            empty.getStyleClass().add("server-empty");
            empty.setWrapText(true);
            profileList.getChildren().add(empty);
            return;
        }
        for (Profile profile : profiles) {
            Snapshot snapshot = service.snapshot(profile.id);
            Label name = new Label(profile.name);
            name.getStyleClass().add("server-profile-name");
            Label meta = new Label(snapshot.state().name().toLowerCase().replace('_', ' ') + " · port " + profile.port);
            meta.getStyleClass().add("server-profile-meta");
            Label dot = new Label("●");
            dot.getStyleClass().add("server-status-dot");
            dot.getStyleClass().add("server-state-" + snapshot.state().name().toLowerCase());
            HBox row = new HBox(9, dot, new VBox(4, name, meta));
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(12));
            row.getStyleClass().add("server-profile-card");
            if (profile.id.equals(keep)) row.getStyleClass().add("server-profile-selected");
            row.setOnMouseClicked(event -> selectProfile(profile.id));
            profileList.getChildren().add(row);
        }
    }

    private void selectProfile(String id) {
        selectedId = id;
        detail.setDisable(false);
        console.clear();
        Profile profile = service.profile(id).orElse(null);
        if (profile == null) {
            setActionsEnabled(false);
            return;
        }
        javaInput.setText(profile.javaExecutable == null ? "java" : profile.javaExecutable);
        minRam.getValueFactory().setValue(profile.minRamMb);
        maxRam.getValueFactory().setValue(profile.maxRamMb);
        suppressEulaChange = true;
        eula.setSelected(profile.eulaAccepted);
        suppressEulaChange = false;
        commandInput.setDisable(false);
        setActionsEnabled(true);
        refreshSelected(service.snapshot(id));
        refreshProfileList();
        console.appendText("Vanta: selected " + profile.name + "\n");
        console.appendText("Directory: " + profile.directory + "\n");
        console.appendText("JAR: " + profile.jar + "\n");
    }

    private void refreshSelected(Snapshot snapshot) {
        Profile p = snapshot.profile();
        serverTitle.setText(p.name);
        serverMeta.setText(p.directory + "   ·   " + Path.of(p.jar).getFileName() + "   ·   Port " + p.port);
        status.setText(switch (snapshot.state()) {
            case STOPPED -> "Stopped";
            case STARTING -> "Starting — waiting for server readiness";
            case RUNNING -> "Online";
            case STOPPING -> "Stopping gracefully…";
            case CRASHED -> "Exited unexpectedly";
        });
        statusDot.getStyleClass().removeIf(s -> s.startsWith("server-state-"));
        statusDot.getStyleClass().add("server-state-" + snapshot.state().name().toLowerCase());
        boolean active = snapshot.state() == State.RUNNING || snapshot.state() == State.STARTING || snapshot.state() == State.STOPPING;
        startButton.setDisable(active);
        stopButton.setDisable(!active || snapshot.state() == State.STOPPING);
        forceButton.setDisable(!active);
        restartButton.setDisable(!active && snapshot.state() != State.CRASHED && snapshot.state() != State.STOPPED);
        if (snapshot.state() == State.CRASHED && snapshot.lastExitReason() != null && !snapshot.lastExitReason().isBlank()) {
            status.setTooltip(new Tooltip(snapshot.lastExitReason()));
        } else {
            status.setTooltip(null);
        }
    }

    private boolean saveRuntimeSettings(boolean notify) {
        try {
            Profile p = service.profile(requireSelectedId()).orElseThrow();
            p.javaExecutable = javaInput.getText().isBlank() ? "java" : javaInput.getText().trim();
            p.minRamMb = minRam.getValue();
            p.maxRamMb = maxRam.getValue();
            service.updateProfile(p);
            if (notify) info("Settings saved", "Java and memory settings saved for " + p.name + ".");
            return true;
        } catch (Exception e) {
            error("Could not save settings", e.getMessage());
            return false;
        }
    }

    private void acceptEulaChanged() {
        if (suppressEulaChange || selectedId == null) return;
        if (!eula.isSelected()) {
            try {
                Profile p = service.profile(selectedId).orElseThrow();
                p.eulaAccepted = false;
                service.updateProfile(p);
                console.appendText("Vanta: EULA acceptance disabled for this profile. The server cannot start until you accept it again.\\n");
            } catch (Exception e) {
                error("Could not update EULA setting", e.getMessage());
            }
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Accept Minecraft EULA");
        confirm.setHeaderText("Confirm that you reviewed the Minecraft EULA");
        confirm.setContentText("You must review the terms at https://aka.ms/MinecraftEULA before accepting. Confirm only if you agree to them.");
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            suppressEulaChange = true; eula.setSelected(false); suppressEulaChange = false;
            return;
        }
        try {
            Profile p = service.profile(selectedId).orElseThrow();
            p.eulaAccepted = true;
            service.updateProfile(p);
            console.appendText("Vanta: EULA acceptance saved to the server folder.\n");
        } catch (Exception e) {
            suppressEulaChange = true; eula.setSelected(false); suppressEulaChange = false;
            error("Could not save EULA acceptance", e.getMessage());
        }
    }

    private void startServer() {
        try {
            if (!saveRuntimeSettings(false)) return;
            service.start(requireSelectedId());
        } catch (Exception e) { error("Could not start server", e.getMessage()); }
    }

    private void stopServer() {
        try { service.stop(requireSelectedId()); }
        catch (Exception e) { error("Could not stop server", e.getMessage()); }
    }

    private void forceStopServer() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Force stop server");
        alert.setHeaderText("Forcefully terminate this Java process?");
        alert.setContentText("Unsaved world changes may be lost. Use this only if the server does not respond to the normal Stop command.");
        if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) service.forceStop(requireSelectedId());
    }

    private void restartServer() {
        Snapshot snapshot = service.snapshot(requireSelectedId());
        if (snapshot.state() == State.RUNNING || snapshot.state() == State.STARTING) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Restart server");
            alert.setHeaderText("Restart " + snapshot.profile().name + "?");
            alert.setContentText("Vanta will send the normal stop command, wait for the process to exit, then start it again.");
            if (alert.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            try {
                service.stop(selectedId);
                String id = selectedId;
                Thread waiter = new Thread(() -> {
                    try {
                        for (int i = 0; i < 60; i++) {
                            Thread.sleep(500);
                            Snapshot s = service.snapshot(id);
                            if (s.state() == State.STOPPED || s.state() == State.CRASHED) {
                                Platform.runLater(() -> {
                                    try { service.start(id); } catch (Exception e) { error("Restart failed", e.getMessage()); }
                                });
                                return;
                            }
                        }
                        Platform.runLater(() -> error("Restart timed out", "The server did not exit within 30 seconds. Stop it manually before restarting."));
                    } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                }, "vanta-server-restart");
                waiter.setDaemon(true);
                waiter.start();
            } catch (Exception e) { error("Restart failed", e.getMessage()); }
        } else {
            startServer();
        }
    }

    private void submitCommand() {
        String value = commandInput.getText();
        if (value == null || value.isBlank()) return;
        commandInput.clear();
        runCommand(value);
    }

    private void runCommand(String command) {
        try { service.sendCommand(requireSelectedId(), command); }
        catch (Exception e) { error("Command failed", e.getMessage()); }
    }

    private void editProperties() {
        try {
            String id = requireSelectedId();
            Properties current = service.readServerProperties(id);
            TextField port = new TextField(current.getProperty("server-port", "25565"));
            TextField players = new TextField(current.getProperty("max-players", "20"));
            TextField motd = new TextField(current.getProperty("motd", "A Minecraft Server"));
            port.getStyleClass().add("server-input");
            players.getStyleClass().add("server-input");
            motd.getStyleClass().add("server-input");
            VBox form = new VBox(10, field("Server port", port), field("Maximum players", players), field("Message of the day", motd));
            form.setPrefWidth(420);
            Alert dialog = new Alert(Alert.AlertType.CONFIRMATION);
            dialog.setTitle("Server properties");
            dialog.setHeaderText("Basic server configuration");
            dialog.getDialogPane().setContent(form);
            dialog.getButtonTypes().setAll(ButtonType.OK, ButtonType.CANCEL);
            if (dialog.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) return;
            int parsedPort = Integer.parseInt(port.getText().trim());
            int parsedPlayers = Integer.parseInt(players.getText().trim());
            if (parsedPort < 1 || parsedPort > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535.");
            if (parsedPlayers < 1 || parsedPlayers > 1000) throw new IllegalArgumentException("Maximum players must be between 1 and 1000.");
            Properties updates = new Properties();
            updates.setProperty("server-port", Integer.toString(parsedPort));
            updates.setProperty("max-players", Integer.toString(parsedPlayers));
            updates.setProperty("motd", motd.getText());
            service.saveServerProperties(id, updates);
            refreshProfileList();
            info("Properties saved", "Changes were written to server.properties. Restart the server for them to take effect.");
        } catch (Exception e) { error("Could not edit properties", e.getMessage()); }
    }

    private void showCrashDiagnostics() {
        try {
            String summary = service.latestCrashSummary(requireSelectedId());
            TextArea text = new TextArea(summary);
            text.setEditable(false);
            text.setWrapText(false);
            text.setPrefColumnCount(90);
            text.setPrefRowCount(26);
            Alert dialog = new Alert(Alert.AlertType.INFORMATION);
            dialog.setTitle("Crash diagnostics");
            dialog.setHeaderText("Latest crash report");
            dialog.getDialogPane().setContent(text);
            dialog.getDialogPane().setPrefSize(760, 560);
            dialog.showAndWait();
        } catch (Exception e) { error("Diagnostics unavailable", e.getMessage()); }
    }

    private <T> void runBackground(String title, IOAction<T> action, java.util.function.Consumer<T> success) {
        try {
            String id = requireSelectedId();
            console.appendText("Vanta: " + title + "…\n");
            Thread worker = new Thread(() -> {
                try {
                    T result = action.run();
                    Platform.runLater(() -> success.accept(result));
                } catch (Exception e) {
                    Platform.runLater(() -> error(title + " failed", e.getMessage()));
                }
            }, "vanta-server-task");
            worker.setDaemon(true);
            worker.start();
        } catch (Exception e) { error(title + " failed", e.getMessage()); }
    }

    @FunctionalInterface private interface IOAction<T> { T run() throws Exception; }

    private String requireSelectedId() {
        if (selectedId == null) throw new IllegalStateException("Select a server first.");
        return selectedId;
    }

    private void setActionsEnabled(boolean enabled) {
        startButton.setDisable(!enabled);
        stopButton.setDisable(true);
        forceButton.setDisable(true);
        restartButton.setDisable(!enabled);
        commandInput.setDisable(!enabled);
        eula.setDisable(!enabled);
        javaInput.setDisable(!enabled);
        minRam.setDisable(!enabled);
        maxRam.setDisable(!enabled);
    }

    private static Button actionButton(String text, IconView.Type icon, String style) {
        Button button = new Button(text, IconView.create(icon, 15));
        button.getStyleClass().addAll("server-action", style);
        button.setGraphicTextGap(7);
        return button;
    }

    private static Node field(String label, Node input) {
        Label title = new Label(label);
        title.getStyleClass().add("server-field-label");
        VBox box = new VBox(6, title, input);
        box.getStyleClass().add("server-field");
        return box;
    }

    private static Node sectionHeader(String title, String subtitle) {
        Label heading = new Label(title);
        heading.getStyleClass().add("server-section-title");
        Label sub = new Label(subtitle);
        sub.getStyleClass().add("server-section-subtitle");
        return new VBox(4, heading, sub);
    }

    private static Region spacer() {
        Region region = new Region();
        HBox.setHgrow(region, Priority.ALWAYS);
        return region;
    }

    private void info(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "Completed." : message);
        alert.show();
    }

    private void error(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(title);
        alert.setContentText(message == null ? "Unknown error." : message);
        alert.show();
    }

    @Override public void close() { service.close(); }
}
