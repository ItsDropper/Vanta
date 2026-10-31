package dev.itsdropper.vanta.ui.components;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Searchable keyboard-first command surface for launcher navigation and actions.
 */
public final class CommandPalette extends StackPane {

    public record Command(String title, String description, String shortcut, Runnable action) {
        public Command {
            Objects.requireNonNull(title, "title");
            Objects.requireNonNull(description, "description");
            Objects.requireNonNull(shortcut, "shortcut");
            Objects.requireNonNull(action, "action");
        }
    }

    private final List<Command> commands;
    private final TextField searchField = new TextField();
    private final VBox resultList = new VBox(4);
    private final ScrollPane resultScroll = new ScrollPane(resultList);
    private final Label emptyState = new Label("No matching commands");
    private final List<Command> visibleCommands = new ArrayList<>();
    private int selectedIndex;

    public CommandPalette(List<Command> commands) {
        this.commands = List.copyOf(Objects.requireNonNull(commands, "commands"));

        getStyleClass().add("command-palette-overlay");
        setVisible(false);
        setManaged(false);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setPickOnBounds(true);

        Rectangle scrim = new Rectangle();
        scrim.widthProperty().bind(widthProperty());
        scrim.heightProperty().bind(heightProperty());
        scrim.getStyleClass().add("command-palette-scrim");
        scrim.setOnMouseClicked(event -> hide());

        Label eyebrow = new Label("VANTA COMMAND");
        eyebrow.getStyleClass().add("command-palette-eyebrow");

        Label title = new Label("Where do you want to go?");
        title.getStyleClass().add("command-palette-title");

        searchField.setPromptText("Search pages and actions...");
        searchField.setAccessibleText("Search Vanta commands");
        searchField.getStyleClass().add("command-palette-search");
        searchField.textProperty().addListener((observable, oldValue, newValue) -> refreshResults());
        searchField.addEventFilter(KeyEvent.KEY_PRESSED, this::handleSearchKeyPressed);
        searchField.setOnAction(event -> executeSelected());

        resultList.getStyleClass().add("command-palette-results");
        emptyState.getStyleClass().add("command-palette-empty");
        resultList.setFillWidth(true);

        resultScroll.setFitToWidth(true);
        resultScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        resultScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        resultScroll.setMaxHeight(360);
        resultScroll.getStyleClass().add("command-palette-scroll");
        resultScroll.setAccessibleText("Command search results");

        HBox footer = new HBox(12,
                hint("↑↓", "navigate"),
                hint("Enter", "open"),
                hint("Esc", "close"));
        footer.setAlignment(Pos.CENTER_LEFT);
        footer.getStyleClass().add("command-palette-footer");

        VBox panel = new VBox(14, eyebrow, title, searchField, resultScroll, footer);
        panel.getStyleClass().add("command-palette-panel");
        panel.setPadding(new Insets(22));
        panel.setMaxWidth(620);
        panel.setPrefWidth(620);
        panel.setMaxHeight(560);
        StackPane.setAlignment(panel, Pos.TOP_CENTER);
        StackPane.setMargin(panel, new Insets(88, 24, 24, 24));

        getChildren().addAll(scrim, panel);
        setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                hide();
                event.consume();
            } else if (event.getCode() == KeyCode.DOWN) {
                moveSelection(1);
                event.consume();
            } else if (event.getCode() == KeyCode.UP) {
                moveSelection(-1);
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                executeSelected();
                event.consume();
            }
        });

        refreshResults();
    }

    private void handleSearchKeyPressed(KeyEvent event) {
        if (event.getCode() == KeyCode.ESCAPE) {
            hide();
            event.consume();
        } else if (event.getCode() == KeyCode.DOWN) {
            moveSelection(1);
            event.consume();
        } else if (event.getCode() == KeyCode.UP) {
            moveSelection(-1);
            event.consume();
        } else if (event.getCode() == KeyCode.ENTER) {
            executeSelected();
            event.consume();
        }
    }

    private HBox hint(String key, String label) {
        Label keyLabel = new Label(key);
        keyLabel.getStyleClass().add("command-palette-key");
        Label textLabel = new Label(label);
        textLabel.getStyleClass().add("command-palette-hint");
        return new HBox(6, keyLabel, textLabel);
    }

    public void show() {
        setManaged(true);
        setVisible(true);
        setOpacity(1.0);
        searchField.clear();
        refreshResults();
        Platform.runLater(searchField::requestFocus);
    }

    public void hide() {
        setVisible(false);
        setManaged(false);
    }

    public boolean isShowing() {
        return isVisible() && isManaged();
    }

    private void refreshResults() {
        String query = searchField.getText() == null
                ? ""
                : searchField.getText().strip().toLowerCase(Locale.ROOT);

        visibleCommands.clear();
        for (Command command : commands) {
            String searchable = (command.title() + " " + command.description())
                    .toLowerCase(Locale.ROOT);
            if (query.isEmpty() || searchable.contains(query)) {
                visibleCommands.add(command);
            }
        }

        selectedIndex = 0;
        resultList.getChildren().clear();

        if (visibleCommands.isEmpty()) {
            resultList.getChildren().add(emptyState);
            return;
        }

        for (int i = 0; i < visibleCommands.size(); i++) {
            Command command = visibleCommands.get(i);
            Button row = createResultRow(command, i);
            resultList.getChildren().add(row);
        }
        updateSelection();
    }

    private Button createResultRow(Command command, int index) {
        Label name = new Label(command.title());
        name.getStyleClass().add("command-result-title");
        Label description = new Label(command.description());
        description.getStyleClass().add("command-result-description");

        VBox text = new VBox(3, name, description);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label shortcut = new Label(command.shortcut().isBlank() ? " " : command.shortcut());
        shortcut.getStyleClass().add("command-result-shortcut");

        Button row = new Button();
        row.setAccessibleText(command.title() + ". " + command.description()
                + (command.shortcut().isBlank() ? "" : ". Shortcut: " + command.shortcut()));
        row.setGraphic(new HBox(12, text, shortcut));
        row.setMaxWidth(Double.MAX_VALUE);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("command-result");
        row.setOnAction(event -> execute(command));
        row.setOnMouseEntered(event -> {
            selectedIndex = index;
            updateSelection();
        });
        return row;
    }

    private void moveSelection(int offset) {
        if (visibleCommands.isEmpty()) {
            return;
        }
        selectedIndex = Math.floorMod(selectedIndex + offset, visibleCommands.size());
        updateSelection();
    }

    private void updateSelection() {
        if (visibleCommands.size() > 1) {
            resultScroll.setVvalue((double) selectedIndex / (visibleCommands.size() - 1));
        }
        for (int i = 0; i < resultList.getChildren().size(); i++) {
            resultList.getChildren().get(i).getStyleClass().remove("command-result-selected");
            if (i == selectedIndex && resultList.getChildren().get(i) instanceof Button button) {
                button.getStyleClass().add("command-result-selected");
            }
        }
    }

    private void executeSelected() {
        if (selectedIndex >= 0 && selectedIndex < visibleCommands.size()) {
            execute(visibleCommands.get(selectedIndex));
        }
    }

    private void execute(Command command) {
        hide();
        command.action().run();
    }
}
