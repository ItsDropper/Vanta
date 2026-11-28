package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.launcher.diagnostics.BisectEngine;
import dev.itsdropper.vanta.launcher.diagnostics.BisectEngine.Outcome;
import dev.itsdropper.vanta.launcher.diagnostics.ModBisectService;
import dev.itsdropper.vanta.launcher.diagnostics.ModBisectSession;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.ui.VantaUI;
import dev.itsdropper.vanta.ui.async.UiExecutor;
import dev.itsdropper.vanta.ui.components.PageHeader;
import dev.itsdropper.vanta.ui.kit.Callout;
import dev.itsdropper.vanta.ui.kit.Card;
import dev.itsdropper.vanta.ui.kit.StatTile;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.BooleanSupplier;

/**
 * Guides the user through finding the mod that causes a crash or bug: Vanta switches mods off in halves, the user
 * launches the game and reports whether the problem happened, and the search narrows down until one mod is left.
 */
public final class ModBisectView extends VBox {

    private final Instance instance;
    private final Callable<Process> launcher;
    private final BooleanSupplier gameRunning;
    private final Runnable onBack;
    private final ModBisectService service = new ModBisectService();
    private final VBox body = new VBox(16);

    private ModBisectSession session;
    private boolean launching;
    private boolean waitingForExit;
    private String error;

    public ModBisectView(Instance instance, Callable<Process> launcher, BooleanSupplier gameRunning,
                         Runnable onBack) {
        super(16);
        this.instance = instance;
        this.launcher = launcher;
        this.gameRunning = gameRunning;
        this.onBack = onBack;
        getStyleClass().add("mod-bisect-view");
        setPadding(new Insets(28, 32, 24, 32));

        Button back = VantaUI.backButton();
        back.setOnAction(event -> onBack.run());
        PageHeader header = new PageHeader(
                "Find the problem mod",
                "Narrow down which mod crashes the game or causes a bug, one launch at a time.",
                "page-title",
                "page-subtitle");

        ScrollPane scroll = new ScrollPane(body);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.getStyleClass().add("page-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        getChildren().addAll(back, header, scroll);
        try {
            session = service.resume(instance).orElse(null);
        } catch (IOException failure) {
            error = "The saved search could not be read: " + failure.getMessage();
        }
        render();
    }

    // ---- rendering ----

    private void render() {
        body.getChildren().clear();
        if (error != null) {
            Button dismiss = new Button("Dismiss");
            dismiss.getStyleClass().add("secondary-button");
            dismiss.setOnAction(event -> {
                error = null;
                render();
            });
            body.getChildren().add(new Callout(Callout.Tone.DANGER, "Something went wrong", error, dismiss));
        }
        if (session == null) {
            body.getChildren().add(introduction());
        } else if (session.engine().isFinished()) {
            body.getChildren().add(result());
        } else {
            body.getChildren().add(running());
        }
    }

    private Node introduction() {
        int enabled;
        try {
            enabled = service.inspect(instance).size();
        } catch (IOException failure) {
            enabled = 0;
        }
        Label explanation = text("Vanta turns off half of your mods and you launch the game. Tell it whether the "
                + "problem still happens, and it keeps narrowing down. Mods that need each other are always "
                + "switched together, nothing is deleted, and every mod is switched back on when you finish.");

        Button start = new Button("Start search");
        start.getStyleClass().add("primary-button");
        start.setDisable(enabled < 2 || gameRunning.getAsBoolean());
        start.setOnAction(event -> begin());

        List<Node> parts = new java.util.ArrayList<>();
        parts.add(new HBox(12,
                new StatTile("Enabled mods", Integer.toString(enabled), "in this instance"),
                new StatTile("Launches needed", enabled < 2 ? "None" : "About " + estimate(enabled), "usually")));
        parts.add(explanation);
        if (gameRunning.getAsBoolean()) {
            parts.add(new Callout(Callout.Tone.WARNING, "Minecraft is running",
                    "Close the game first. Mods can't be switched while it is open."));
        } else if (enabled < 2) {
            parts.add(new Callout(Callout.Tone.INFO, "Not enough mods",
                    "A search needs at least two enabled mods."));
        }
        parts.add(start);
        return new Card(parts.toArray(new Node[0])).padded(22).spaced(16);
    }

    private Node running() {
        BisectEngine engine = session.engine();
        int total = engine.testsDone() + engine.estimatedRemaining();
        int on = engine.currentTest().size();
        int all = session.unitFilenames().size();

        String title = switch (engine.status()) {
            case BASELINE -> "Test 1: no mods";
            case VERIFYING -> "Final check";
            default -> "Test " + (engine.testsDone() + 1) + " of about " + total;
        };
        String detail = switch (engine.status()) {
            case BASELINE -> "All mods are off. This rules out the game itself.";
            case VERIFYING -> on + " suspected " + (on == 1 ? "mod is" : "mods are")
                    + " on, with what they need. This confirms they cause the problem alone.";
            default -> on + " of " + all + " mods are on for this launch.";
        };

        VBox content = new VBox(14);
        content.getChildren().addAll(heading(title), text(detail));

        if (waitingForExit) {
            content.getChildren().addAll(
                    new Callout(Callout.Tone.INFO, "Minecraft is running",
                            "Play until the problem shows up, or until you are sure it won't. Close the game when "
                                    + "you are done and Vanta will ask what happened."));
        } else if (launching) {
            content.getChildren().add(text("Starting Minecraft…"));
        } else if (askedToLaunch()) {
            content.getChildren().add(question());
        } else {
            Button launch = new Button("Launch Minecraft");
            launch.getStyleClass().add("primary-button");
            launch.setDisable(gameRunning.getAsBoolean());
            launch.setOnAction(event -> launch());
            content.getChildren().add(launch);
        }

        HBox secondary = new HBox(8);
        Button undo = new Button("Undo last answer");
        undo.getStyleClass().add("secondary-button");
        undo.setDisable(!engine.canUndo() || launching || waitingForExit);
        undo.setOnAction(event -> run(() -> session.undo()));
        Button stop = new Button("Stop and restore mods");
        stop.getStyleClass().add("secondary-button");
        stop.setDisable(launching || waitingForExit);
        stop.setOnAction(event -> run(() -> {
            session.stop();
            session = null;
        }));
        secondary.getChildren().addAll(undo, stop);
        content.getChildren().add(secondary);

        return new Card(content).padded(22).spaced(16);
    }

    /** True once a launch for the current test has finished and the answer is still owed. */
    private boolean launched;

    private boolean askedToLaunch() {
        return launched;
    }

    private Node question() {
        Button problem = answerButton("Yes, the problem happened", Outcome.PROBLEM, true);
        Button fine = answerButton("No, it worked fine", Outcome.FINE, false);
        Button unsure = answerButton("I couldn't tell", Outcome.UNSURE, false);
        VBox box = new VBox(10, heading("Did the problem happen?"),
                text("Pick what you saw during that launch. If something else went wrong, choose \"I couldn't tell\"."),
                new HBox(8, problem, fine, unsure));
        return box;
    }

    private Node result() {
        BisectEngine engine = session.engine();
        List<String> files = session.culpritFilenames();
        VBox box = new VBox(14);
        switch (engine.status()) {
            case FOUND -> {
                String names = String.join(", ", files);
                String message = files.size() == 1
                        ? "The problem happens with this mod on, and goes away with it off."
                        : "The problem only happens when these mods are on together. Turning off any one fixes it.";
                if (engine.isLessCertain()) {
                    message += " You answered \"couldn't tell\" at least once, so double check it.";
                }
                Button disable = new Button(files.size() == 1 ? "Turn it off" : "Turn them off");
                disable.getStyleClass().add("primary-button");
                disable.setOnAction(event -> run(() -> {
                    session.disableSuspects();
                    session = null;
                }));
                Button keep = new Button("Keep everything on");
                keep.getStyleClass().add("secondary-button");
                keep.setOnAction(event -> run(() -> {
                    session.stop();
                    session = null;
                }));
                box.getChildren().addAll(
                        new Callout(Callout.Tone.SUCCESS, "Found it: " + names, message),
                        text("All your mods are switched back on. Turning off the suspects leaves the rest as they were."),
                        new HBox(8, disable, keep));
            }
            case NOT_A_MOD -> box.getChildren().addAll(
                    new Callout(Callout.Tone.INFO, "No mod is causing this",
                            "The problem happened with every mod off, so it comes from the game, Java or your "
                                    + "settings. All your mods are back on."),
                    closeButton());
            default -> box.getChildren().addAll(
                    new Callout(Callout.Tone.WARNING, "Couldn't reproduce it",
                            "The suspects didn't cause the problem on their own, so it may only happen "
                                    + "sometimes. Suspects: " + String.join(", ", files)
                                    + ". All your mods are back on."),
                    closeButton());
        }
        return new Card(box).padded(22).spaced(16);
    }

    private Button closeButton() {
        Button done = new Button("Done");
        done.getStyleClass().add("primary-button");
        done.setOnAction(event -> run(() -> {
            session.stop();
            session = null;
        }));
        return done;
    }

    private Button answerButton(String label, Outcome outcome, boolean primary) {
        Button button = new Button(label);
        button.getStyleClass().add(primary ? "primary-button" : "secondary-button");
        button.setOnAction(event -> {
            launched = false;
            run(() -> session.answer(outcome));
        });
        return button;
    }

    // ---- actions ----

    private void begin() {
        run(() -> session = service.start(instance));
    }

    private void launch() {
        launching = true;
        launched = false;
        render();
        UiExecutor.shared().execute(() -> {
            try {
                Process process = launcher.call();
                UiExecutor.shared().dispatchToUi(() -> {
                    launching = false;
                    waitingForExit = true;
                    render();
                });
                process.onExit().thenRun(() -> UiExecutor.shared().dispatchToUi(() -> {
                    waitingForExit = false;
                    launched = true;
                    render();
                }));
            } catch (Exception failure) {
                UiExecutor.shared().dispatchToUi(() -> {
                    launching = false;
                    error = "Minecraft could not start: "
                            + (failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage());
                    render();
                });
            }
        });
    }

    @FunctionalInterface
    private interface FileAction {
        void run() throws IOException;
    }

    /** Runs a file operation off the UI thread and shows any failure without losing the saved search. */
    private void run(FileAction action) {
        UiExecutor.shared().execute(() -> {
            String failureMessage = null;
            try {
                action.run();
            } catch (IOException | RuntimeException failure) {
                failureMessage = failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage();
            }
            String message = failureMessage;
            UiExecutor.shared().dispatchToUi(() -> {
                error = message;
                render();
            });
        });
    }

    // ---- small helpers ----

    private static int estimate(int mods) {
        return 2 + 2 * (int) Math.ceil(Math.log(mods + 1) / Math.log(2));
    }

    private static Node heading(String value) {
        return new dev.itsdropper.vanta.ui.kit.SectionHeader(value);
    }

    private static Label text(String value) {
        Label label = new Label(value);
        label.getStyleClass().add("settings-card-description");
        label.setWrapText(true);
        label.setAlignment(Pos.TOP_LEFT);
        return label;
    }
}
