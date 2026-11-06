package dev.itsdropper.vanta.ui.components;

import dev.itsdropper.vanta.ui.LauncherSettings;
import dev.itsdropper.vanta.ui.Localization;
import javafx.animation.PauseTransition;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.util.Duration;

import java.util.function.Consumer;

/**
 * Shared search, sorting, result-count, layout and pagination controls for
 * both Modrinth browsers. Search state is immutable at the callback boundary.
 */
public final class ModrinthBrowserToolbar extends VBox {
    public record SearchState(
            String query,
            String index,
            int limit,
            int offset
    ) {}

    private final TextField searchField = new TextField();
    private final ComboBox<String> sortBox = new ComboBox<>();
    private final ComboBox<Integer> limitBox = new ComboBox<>();
    private final Button layoutButton = new Button();
    private final Button previousButton = new Button("‹");
    private final Button currentPageButton = new Button();
    private final Button nextPageButton = new Button();
    private final Button nextArrowButton = new Button("›");
    private final Button ellipsisButton = new Button("…");
    private final Button lastPageButton = new Button();
    private final TextField jumpField = new TextField();
    private final HBox pagination = new HBox(5);
    private final PauseTransition debounce = new PauseTransition(Duration.millis(350));
    private final Consumer<SearchState> searchListener;
    private final Consumer<Boolean> layoutListener;

    private int offset;
    private int totalHits;
    private boolean gridLayout;
    private boolean updatingControls;

    public ModrinthBrowserToolbar(
            String searchPrompt,
            Consumer<SearchState> searchListener,
            Consumer<Boolean> layoutListener
    ) {
        this.searchListener = searchListener;
        this.layoutListener = layoutListener;
        this.gridLayout = LauncherSettings.isModrinthGridLayoutEnabled();

        getStyleClass().add("modrinth-browser-toolbar");
        setSpacing(9);

        Label searchIcon = new Label();
        searchIcon.setGraphic(IconView.create(IconView.Type.SEARCH, 17));
        searchIcon.getStyleClass().add("modrinth-search-icon");

        searchField.setPromptText(searchPrompt);
        searchField.setAccessibleText(searchPrompt);
        searchField.getStyleClass().addAll("create-field", "modrinth-search-field");
        searchField.setPrefHeight(42);
        HBox.setHgrow(searchField, Priority.ALWAYS);

        HBox searchRow = new HBox(10, searchIcon, searchField);
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchRow.getStyleClass().add("modrinth-search-row");

        Label sortLabel = new Label(Localization.text("modrinth.sortBy"));
        sortLabel.getStyleClass().add("modrinth-control-label");
        sortBox.getItems().setAll(
                Localization.text("modrinth.sortRelevance"),
                Localization.text("modrinth.sortDownloads"),
                Localization.text("modrinth.sortFollowers"),
                Localization.text("modrinth.sortDatePublished"),
                Localization.text("modrinth.sortDateUpdated")
        );
        sortBox.setValue(Localization.text("modrinth.sortRelevance"));
        sortBox.getStyleClass().addAll("create-combo", "modrinth-small-combo");
        sortBox.setPrefWidth(145);

        Label viewLabel = new Label(Localization.text("modrinth.resultsLabel"));
        viewLabel.getStyleClass().add("modrinth-control-label");
        limitBox.getItems().setAll(5, 10, 15, 20, 50, 100);
        limitBox.setValue(20);
        limitBox.getStyleClass().addAll("create-combo", "modrinth-small-combo");
        limitBox.setPrefWidth(82);

        layoutButton.getStyleClass().addAll("secondary-button", "modrinth-layout-button");
        layoutButton.setAccessibleText(Localization.text("modrinth.layoutAccessible"));
        updateLayoutButton();

        jumpField.setPromptText(Localization.text("modrinth.pageNumber"));
        jumpField.setPrefWidth(76);
        jumpField.setVisible(false);
        jumpField.setManaged(false);
        jumpField.getStyleClass().add("create-field");
        jumpField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                jumpToEnteredPage();
            } else if (event.getCode() == KeyCode.ESCAPE) {
                hideJumpField();
            }
        });
        jumpField.focusedProperty().addListener((obs, wasFocused, focused) -> {
            if (!focused && jumpField.isVisible()) hideJumpField();
        });

        for (Button button : new Button[]{
                previousButton, currentPageButton, nextPageButton,
                ellipsisButton, lastPageButton, nextArrowButton
        }) {
            button.getStyleClass().addAll("secondary-button", "modrinth-page-button");
            button.setMinWidth(30);
            button.setPrefHeight(30);
        }
        currentPageButton.getStyleClass().add("modrinth-current-page");
        previousButton.setAccessibleText(Localization.text("modrinth.previousPage"));
        currentPageButton.setAccessibleText(Localization.text("modrinth.currentPage"));
        nextPageButton.setAccessibleText(Localization.text("modrinth.nextPage"));
        nextArrowButton.setAccessibleText(Localization.text("modrinth.nextPage"));
        ellipsisButton.setAccessibleText(Localization.text("modrinth.jumpToPage"));
        lastPageButton.setAccessibleText(Localization.text("modrinth.lastPage"));

        pagination.setAlignment(Pos.CENTER_RIGHT);
        pagination.getChildren().setAll(
                previousButton, currentPageButton, nextPageButton,
                ellipsisButton, jumpField, lastPageButton, nextArrowButton
        );

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox controls = new HBox(8,
                sortLabel, sortBox, viewLabel, limitBox, layoutButton,
                spacer, pagination
        );
        controls.setAlignment(Pos.CENTER_LEFT);
        controls.getStyleClass().add("modrinth-controls-row");

        getChildren().addAll(searchRow, controls);

        debounce.setOnFinished(event -> submitSearch(true));
        searchField.textProperty().addListener((obs, oldValue, newValue) -> {
            offset = 0;
            debounce.playFromStart();
        });
        searchField.setOnAction(event -> {
            debounce.stop();
            submitSearch(true);
        });
        sortBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingControls && newValue != null) {
                offset = 0;
                submitSearch(true);
            }
        });
        limitBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (!updatingControls && newValue != null) {
                offset = 0;
                submitSearch(true);
            }
        });
        layoutButton.setOnAction(event -> {
            gridLayout = !gridLayout;
            LauncherSettings.setModrinthGridLayoutEnabled(gridLayout);
            updateLayoutButton();
            if (this.layoutListener != null) this.layoutListener.accept(gridLayout);
        });
        previousButton.setOnAction(event -> moveToPage(currentPage() - 1));
        currentPageButton.setOnAction(event -> moveToPage(currentPage()));
        nextPageButton.setOnAction(event -> moveToPage(currentPage() + 1));
        nextArrowButton.setOnAction(event -> moveToPage(currentPage() + 1));
        ellipsisButton.setOnAction(event -> showJumpField());
        lastPageButton.setOnAction(event -> moveToPage(pageCount()));
        updatePagination();
    }

    public TextField getSearchField() {
        return searchField;
    }

    public SearchState getState() {
        return new SearchState(
                searchField.getText() == null ? "" : searchField.getText().trim(),
                selectedIndex(),
                limitBox.getValue() == null ? 20 : limitBox.getValue(),
                offset
        );
    }

    public boolean isGridLayout() {
        return gridLayout;
    }

    public void setTotalHits(int totalHits) {
        this.totalHits = Math.max(0, totalHits);
        int maxOffset = Math.max(0, (pageCount() - 1) * getState().limit());
        if (offset > maxOffset) offset = maxOffset;
        updatePagination();
    }

    public void refresh() {
        debounce.stop();
        offset = 0;
        submitSearch(true);
    }

    private String selectedIndex() {
        String selected = sortBox.getValue();
        if (Localization.text("modrinth.sortDownloads").equals(selected)) return "downloads";
        if (Localization.text("modrinth.sortFollowers").equals(selected)) return "follows";
        if (Localization.text("modrinth.sortDatePublished").equals(selected)) return "newest";
        if (Localization.text("modrinth.sortDateUpdated").equals(selected)) return "updated";
        return "relevance";
    }

    private int currentPage() {
        int limit = getState().limit();
        return offset / limit + 1;
    }

    private int pageCount() {
        int limit = getState().limit();
        return Math.max(1, (int) Math.ceil(totalHits / (double) limit));
    }

    private void moveToPage(int page) {
        int bounded = Math.max(1, Math.min(page, pageCount()));
        int nextOffset = (bounded - 1) * getState().limit();
        if (nextOffset == offset) return;
        offset = nextOffset;
        updatePagination();
        submitSearch(false);
    }

    private void showJumpField() {
        jumpField.clear();
        jumpField.setVisible(true);
        jumpField.setManaged(true);
        ellipsisButton.setVisible(false);
        ellipsisButton.setManaged(false);
        jumpField.requestFocus();
    }

    private void hideJumpField() {
        jumpField.setVisible(false);
        jumpField.setManaged(false);
        ellipsisButton.setVisible(true);
        ellipsisButton.setManaged(true);
    }

    private void jumpToEnteredPage() {
        try {
            int requested = Integer.parseInt(jumpField.getText().trim());
            if (requested < 1 || requested > pageCount()) {
                jumpField.setStyle("-fx-border-color: -vantadanger;");
                return;
            }
            jumpField.setStyle("");
            hideJumpField();
            moveToPage(requested);
        } catch (NumberFormatException ignored) {
            jumpField.setStyle("-fx-border-color: -vantadanger;");
        }
    }

    private void updateLayoutButton() {
        layoutButton.setText(gridLayout ? "▦" : "☷");
        layoutButton.setTooltip(new javafx.scene.control.Tooltip(
                gridLayout ? Localization.text("modrinth.switchToList") : Localization.text("modrinth.switchToGrid")
        ));
    }

    private void updatePagination() {
        int page = currentPage();
        int pages = pageCount();
        currentPageButton.setText(Integer.toString(page));
        nextPageButton.setText(page < pages ? Integer.toString(page + 1) : "›");
        currentPageButton.setText(Integer.toString(page));
        previousButton.setDisable(page <= 1);
        currentPageButton.setDisable(true);
        nextPageButton.setDisable(page >= pages);
        nextArrowButton.setDisable(page >= pages);
        ellipsisButton.setDisable(pages <= 3);
        lastPageButton.setText(Integer.toString(pages));
        lastPageButton.setDisable(page >= pages || pages <= 2);
        if (pages <= 2) {
            ellipsisButton.setVisible(false);
            ellipsisButton.setManaged(false);
            lastPageButton.setVisible(false);
            lastPageButton.setManaged(false);
        } else {
            ellipsisButton.setVisible(true);
            ellipsisButton.setManaged(true);
            lastPageButton.setVisible(true);
            lastPageButton.setManaged(true);
        }
    }

    private void submitSearch(boolean resetTotal) {
        if (resetTotal) {
            totalHits = 0;
            updatePagination();
        }
        if (searchListener != null) searchListener.accept(getState());
    }
}
