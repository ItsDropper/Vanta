package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.ThemeManager;
import dev.itsdropper.vanta.ui.async.UiExecutor;
import dev.itsdropper.vanta.ui.components.PageHeader;
import dev.itsdropper.vanta.ui.components.EmptyState;
import dev.itsdropper.vanta.ui.components.ModrinthBrowserToolbar;
import dev.itsdropper.vanta.ui.components.ModrinthTagIcon;
import javafx.scene.paint.Color;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Separator;
import javafx.stage.Popup;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import dev.itsdropper.vanta.launcher.modrinth.ModrinthClient;
import dev.itsdropper.vanta.launcher.modrinth.ModrinthSearchHit;
import dev.itsdropper.vanta.launcher.instance.InstanceManager;
import dev.itsdropper.vanta.launcher.instance.MinecraftVersionResolver;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.modrinth.ModrinthContentType;
import dev.itsdropper.vanta.launcher.modrinth.ModrinthProject;
import dev.itsdropper.vanta.launcher.service.ModrinthContentService;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import javax.imageio.ImageIO;

public class GlobalModsView extends VBox {

    private final ModrinthClient modrinthClient;
    private final HttpClient httpClient;

    private final TextField searchField;
    private final ModrinthBrowserToolbar browserToolbar;
    private final ComboBox<String> loaderBox;
    private final ComboBox<String> versionBox;
    private final TextField versionSearchField;
    private final ComboBox<ModrinthContentType> contentTypeBox;
    private final List<String> allMinecraftVersions = new ArrayList<>();
    private final List<String> stableMinecraftVersions = new ArrayList<>();
    private CheckBox showAllVersions;

    private final VBox popularResults;
    private final Label popularStatusLabel;
    private final Label popularTitle;
    private final ScrollPane popularScrollPane;

    private final FlowPane results;
    private final Label statusLabel;
    private Button retryButton;
    private final ScrollPane resultsScrollPane;

    private final BiConsumer<String, ModrinthContentType> onModSelected;
    private final boolean screenshotPreview;
    private long searchRequestId;
    private long popularRequestId;
    private long loaderTagRequestId;
    private long categoryTagRequestId;
    private List<dev.itsdropper.vanta.launcher.modrinth.ModrinthTag> categoryTags = List.of();
    private List<dev.itsdropper.vanta.launcher.modrinth.ModrinthTag> loaderTags = List.of();
    private final java.util.Set<String> selectedCategories = new java.util.LinkedHashSet<>();
    private Button categoriesButton;
    private Button loadersButton;
    private Button versionsButton;
    private final java.util.Set<String> selectedLoaders = new java.util.LinkedHashSet<>();
    private final java.util.Set<String> selectedVersions = new java.util.LinkedHashSet<>();
    private final java.util.Map<ModrinthContentType, Button> contentTypeButtons = new java.util.EnumMap<>(ModrinthContentType.class);
    private VBox versionFilterOptions;
    private VBox loaderFilterOptions;
    private VBox categoryFilterOptions;
    private TextField loaderSearchField;
    private TextField categorySearchField;

    public GlobalModsView(BiConsumer<String, ModrinthContentType> onModSelected) {
        this(onModSelected, false);
    }

    public GlobalModsView(
            BiConsumer<String, ModrinthContentType> onModSelected,
            boolean screenshotPreview
    ) {
        this.onModSelected = onModSelected;
        this.screenshotPreview = screenshotPreview;

        modrinthClient =
                new ModrinthClient();

        httpClient =
                HttpClient.newHttpClient();

        getStyleClass().addAll("page", "global-mods-page");

        dev.itsdropper.vanta.ui.ResponsiveLayout.bindPagePadding(this);

        setSpacing(
                20
        );

        // =========================================================
        // HEADER
        // =========================================================

        PageHeader header = new PageHeader(
                "Modrinth",
                Localization.text("modrinth.subtitle"),
                "page-title",
                "page-subtitle"
        );

        // =========================================================
        // GLOBAL CONTENT FILTERS
        // =========================================================

        contentTypeBox = new ComboBox<>();
        contentTypeBox.getItems().addAll(
                ModrinthContentType.MOD,
                ModrinthContentType.RESOURCE_PACK,
                ModrinthContentType.DATA_PACK,
                ModrinthContentType.SHADER,
                ModrinthContentType.MODPACK
        );
        contentTypeBox.setValue(ModrinthContentType.MOD);
        contentTypeBox.setAccessibleText(Localization.text("globalMods.typeFilter"));
        contentTypeBox.setPrefWidth(170);
        contentTypeBox.setPrefHeight(42);
        contentTypeBox.getStyleClass().add("create-combo");

        loaderBox = new ComboBox<>();
        loaderBox.getItems().addAll("Any", "Fabric", "Quilt", "Forge", "NeoForge", "Ornithe", "Legacy Fabric", "Babric", "NilLoader", "LiteLoader", "Rift", "ModLoader");
        loaderBox.setValue("Any");
        loaderBox.setVisible(false);
        loaderBox.setManaged(false);
        loaderBox.setAccessibleText(Localization.text("globalMods.loaderFilter"));
        loaderBox.setPrefWidth(140);
        loaderBox.setPrefHeight(42);
        loaderBox.getStyleClass().add("create-combo");

        versionBox = new ComboBox<>();
        versionBox.getItems().add("All versions");
        versionBox.setValue("All versions");
        versionBox.setVisible(false);
        versionBox.setManaged(false);
        versionBox.setAccessibleText(Localization.text("globalMods.versionFilter"));
        versionBox.setPrefWidth(170);
        versionBox.setPrefHeight(42);
        versionBox.getStyleClass().add("create-combo");

        versionSearchField = new TextField();
        versionSearchField.setPromptText(Localization.text("globalMods.versionPrompt"));
        versionSearchField.setAccessibleText(Localization.text("globalMods.versionSearchAccessible"));
        versionSearchField.setPrefWidth(210);
        versionSearchField.setPrefHeight(42);
        versionSearchField.getStyleClass().add("create-field");
        showAllVersions = new CheckBox(Localization.text("modrinth.showAllVersions"));
        showAllVersions.setSelected(false);
        showAllVersions.getStyleClass().add("dialog-instance-checkbox");
        showAllVersions.selectedProperty().addListener((obs, oldValue, newValue) -> {
            filterVersionChoices(versionSearchField.getText());
            rebuildVersionFilterOptions();
            browserToolbar.refresh();
        });

        categoriesButton = new Button(Localization.text("modrinth.categories"));
        categoriesButton.getStyleClass().add("secondary-button");
        categoriesButton.setOnAction(event -> showCategoryPicker());
        loadersButton = new Button(Localization.text("modrinth.anyLoader"));
        loadersButton.getStyleClass().add("secondary-button");
        loadersButton.setMaxWidth(Double.MAX_VALUE);
        loadersButton.setOnAction(event -> showLoaderPicker());
        versionsButton = new Button(Localization.text("modrinth.allVersions"));
        versionsButton.getStyleClass().add("secondary-button");
        versionsButton.setMaxWidth(Double.MAX_VALUE);
        versionsButton.setOnAction(event -> showVersionPicker());
        contentTypeBox.setVisible(false);
        contentTypeBox.setManaged(false);
        HBox contentTypeSegments = new HBox(6);
        contentTypeSegments.getStyleClass().add("modrinth-content-segments");
        for (ModrinthContentType type : List.of(
                ModrinthContentType.MOD,
                ModrinthContentType.RESOURCE_PACK,
                ModrinthContentType.DATA_PACK,
                ModrinthContentType.SHADER,
                ModrinthContentType.MODPACK
        )) {
            String label = switch (type) {
                case MOD -> Localization.text("modrinth.mods");
                case RESOURCE_PACK -> Localization.text("modrinth.resourcePacks");
                case DATA_PACK -> Localization.text("modrinth.dataPacks");
                case SHADER -> Localization.text("modrinth.shaders");
                case MODPACK -> Localization.text("modrinth.modpacks");
            };
            Button segment = new Button(label);
            segment.getStyleClass().add("modrinth-content-segment");
            segment.setOnAction(event -> contentTypeBox.setValue(type));
            contentTypeButtons.put(type, segment);
            contentTypeSegments.getChildren().add(segment);
        }
        updateContentTypeSegments();

        Label filtersTitle = new Label(Localization.text("modrinth.filters"));
        filtersTitle.getStyleClass().add("settings-section-title");

        versionFilterOptions = new VBox(4);
        versionFilterOptions.getStyleClass().add("modrinth-filter-options");
        ScrollPane versionScroll = createFilterScroll(versionFilterOptions, 224);
        versionSearchField.setPromptText(Localization.text("modrinth.search"));
        versionSearchField.setPrefHeight(34);
        versionSearchField.setMaxWidth(Double.MAX_VALUE);
        HBox versionSearch = createFilterSearch(versionSearchField);
        VBox versionFilters = new VBox(8, versionSearch, versionScroll, showAllVersions);
        versionFilters.getStyleClass().add("modrinth-filter-content");

        loaderFilterOptions = new VBox(4);
        loaderFilterOptions.getStyleClass().add("modrinth-filter-options");
        ScrollPane loaderScroll = createFilterScroll(loaderFilterOptions, 224);
        loaderSearchField = new TextField();
        loaderSearchField.setPromptText(Localization.text("modrinth.search"));
        loaderSearchField.setPrefHeight(34);
        loaderSearchField.setMaxWidth(Double.MAX_VALUE);
        VBox loaderFilters = new VBox(8, createFilterSearch(loaderSearchField), loaderScroll);
        loaderFilters.getStyleClass().add("modrinth-filter-content");

        categoryFilterOptions = new VBox(4);
        categoryFilterOptions.getStyleClass().add("modrinth-filter-options");
        ScrollPane categoryScroll = createFilterScroll(categoryFilterOptions, 224);
        categorySearchField = new TextField();
        categorySearchField.setPromptText(Localization.text("modrinth.search"));
        categorySearchField.setPrefHeight(34);
        categorySearchField.setMaxWidth(Double.MAX_VALUE);
        VBox categoryFilters = new VBox(8, createFilterSearch(categorySearchField), categoryScroll);
        categoryFilters.getStyleClass().add("modrinth-filter-content");

        TitledPane versionPanel = new TitledPane(Localization.text("modrinth.gameVersion"), versionFilters);
        TitledPane loaderPanel = new TitledPane(Localization.text("modrinth.loader"), loaderFilters);
        TitledPane categoryPanel = new TitledPane(Localization.text("modrinth.category"), categoryFilters);
        versionPanel.setExpanded(true);
        loaderPanel.setExpanded(true);
        categoryPanel.setExpanded(true);
        versionPanel.setAnimated(true);
        loaderPanel.setAnimated(true);
        categoryPanel.setAnimated(true);
        VBox filterSidebar = new VBox(9, filtersTitle, versionPanel, loaderPanel, categoryPanel);
        filterSidebar.getStyleClass().add("modrinth-filter-sidebar");
        filterSidebar.setPrefWidth(250);
        filterSidebar.setMinWidth(220);
        filterSidebar.setMaxWidth(280);

        loaderSearchField.textProperty().addListener((obs, oldValue, newValue) -> rebuildLoaderFilterOptions());
        categorySearchField.textProperty().addListener((obs, oldValue, newValue) -> rebuildCategoryFilterOptions());

        contentTypeBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (newValue != ModrinthContentType.MOD) selectedLoaders.clear();
            selectedCategories.clear();
            updateLoaderButton();
            updateVersionButton();
            updateCategoriesButton();
            updateContentTypeSegments();
            browserToolbar.setSearchPrompt(searchPromptFor(newValue));
            loadLoaderTags(newValue);
            loadCategoryTags(newValue);
            browserToolbar.refresh();
        });

        // The hidden legacy combo boxes remain as compatibility adapters for
        // existing install/popular logic; the visible pickers are multi-select.
        versionSearchField.textProperty().addListener((obs, oldValue, newValue) -> {
            filterVersionChoices(newValue);
            rebuildVersionFilterOptions();
        });

        if (!screenshotPreview) {
            loadLoaderTags(ModrinthContentType.MOD);
            loadCategoryTags(ModrinthContentType.MOD);
            UiExecutor.shared().execute(() -> {
                try {
                    List<String> versions = MinecraftVersionResolver.getAllVersions();
                    List<String> releases = MinecraftVersionResolver.getReleaseVersions();
                    UiExecutor.shared().dispatchToUi(() -> {
                        allMinecraftVersions.clear();
                        allMinecraftVersions.addAll(versions);
                        stableMinecraftVersions.clear();
                        stableMinecraftVersions.addAll(releases);
                        filterVersionChoices(versionSearchField.getText());
                        rebuildVersionFilterOptions();
                    });
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            });
        }

        // =========================================================
        // SEARCH AND SHARED BROWSE CONTROLS
        // =========================================================

        browserToolbar = new ModrinthBrowserToolbar(
                searchPromptFor(ModrinthContentType.MOD),
                this::search,
                this::applyGlobalResultLayout
        );
        searchField = browserToolbar.getSearchField();

        // =========================================================

        // MOST DOWNLOADED TITLE
        // =========================================================

        popularTitle =
                new Label(
                        Localization.text("common.mostDownloaded")
                );

        popularTitle.getStyleClass().add(
                "settings-section-title"
        );

        // =========================================================
        // MOST DOWNLOADED RESULTS
        // =========================================================

        popularResults =
                new VBox(
                        12
                );

        popularResults.setAlignment(
                Pos.CENTER
        );

        popularScrollPane =
                new ScrollPane(
                        popularResults
                );

        popularScrollPane.setFitToWidth(
                true
        );

        popularScrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        popularScrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        popularScrollPane.setPrefHeight(320);
        popularScrollPane.setMaxHeight(320);
        popularScrollPane.setMaxWidth(Double.MAX_VALUE);
        popularResults.setMaxWidth(Double.MAX_VALUE);
        popularResults.setFillWidth(true);

        popularScrollPane.getStyleClass().add(
                "instances-scroll"
        );

        popularStatusLabel =
                new Label(
                        Localization.text("browseMods.loadingPopular")
                );

        popularStatusLabel.getStyleClass().add(
                "instances-status"
        );

        // =========================================================
        // SEARCH RESULTS
        // =========================================================

        results = new FlowPane();
        results.setHgap(12);
        results.setVgap(12);
        results.setAlignment(Pos.TOP_LEFT);
        results.getStyleClass().addAll("instance-list", "modrinth-list-results");

        resultsScrollPane =
                new ScrollPane(
                        results
                );

        resultsScrollPane.setFitToWidth(true);
        resultsScrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) ->
                applyGlobalResultLayout(browserToolbar.isGridLayout()));

        resultsScrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        resultsScrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        resultsScrollPane.getStyleClass().add(
                "instances-scroll"
        );

        VBox.setVgrow(
                resultsScrollPane,
                Priority.ALWAYS
        );

        // =========================================================
        // STATUS
        // =========================================================

        statusLabel =
                new Label(
                        Localization.text("globalMods.searchHelp")
                );

        statusLabel.getStyleClass().add(
                "instances-status"
        );

        // =========================================================
        // BUILD
        // =========================================================

        Button retryButton = new Button(Localization.text("common.retry"));
        retryButton.getStyleClass().add("secondary-button");
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        retryButton.setOnAction(event -> browserToolbar.refresh());
        this.retryButton = retryButton;
        VBox contentPane = new VBox(8, resultsScrollPane, statusLabel, retryButton);
        contentPane.setFillWidth(true);
        HBox browseContent = new HBox(18, filterSidebar, contentPane);
        HBox.setHgrow(contentPane, Priority.ALWAYS);
        VBox.setVgrow(browseContent, Priority.ALWAYS);

        getChildren().addAll(
                header,
                contentTypeSegments,
                browserToolbar,
                browseContent
        );

        // =========================================================
        // INITIAL STATE
        // =========================================================

        resultsScrollPane.setVisible(
                false
        );

        resultsScrollPane.setManaged(
                false
        );

        // =========================================================
        // LOAD POPULAR MODS
        // =========================================================

        if (screenshotPreview) {
            popularStatusLabel.setText("");
            popularStatusLabel.setManaged(false);
            popularResults.getChildren().setAll(new EmptyState(
                    "✦",
                    "MODRINTH DISCOVERY",
                    "Find your next favourite project",
                    "Popular mods, resource packs and shaders appear here when Modrinth browsing is active."
            ));
            results.getChildren().setAll(new EmptyState(
                    "⌕",
                    "SEARCH MODRINTH",
                    "Search across Minecraft content",
                    "Filter by content type, loader and Minecraft version to find compatible projects."
            ));
            statusLabel.setText(Localization.text("globalMods.previewNotice"));
            statusLabel.setVisible(false);
            statusLabel.setManaged(false);
        } else {
            hidePopularSection();
            search();
        }
    }

    private ScrollPane createFilterScroll(VBox content, double height) {
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scroll.setPrefViewportHeight(height);
        scroll.setMaxHeight(height);
        scroll.getStyleClass().add("modrinth-filter-scroll");
        return scroll;
    }

    private HBox createFilterSearch(TextField field) {
        Label icon = new Label();
        icon.setGraphic(dev.itsdropper.vanta.ui.components.IconView.create(
                dev.itsdropper.vanta.ui.components.IconView.Type.SEARCH, 14));
        icon.getStyleClass().add("modrinth-filter-search-icon");
        HBox row = new HBox(7, icon, field);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("modrinth-filter-search");
        HBox.setHgrow(field, Priority.ALWAYS);
        return row;
    }

    private Button createFilterOption(String label, boolean selected, javafx.scene.Node leadingGraphic,
            Color labelColor, java.util.function.Consumer<Boolean> selectionChanged) {
        Button option = new Button();
        Label name = new Label(label);
        name.getStyleClass().add("modrinth-filter-option-label");
        if (labelColor != null) name.setTextFill(labelColor);
        Label checkmark = new Label("✓");
        checkmark.getStyleClass().add("modrinth-filter-check");
        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox row = new HBox(8);
        if (leadingGraphic != null) row.getChildren().add(leadingGraphic);
        row.getChildren().addAll(name, spacer, checkmark);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPrefWidth(205);
        row.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(spacer, Priority.ALWAYS);
        option.setGraphic(row);
        option.setContentDisplay(javafx.scene.control.ContentDisplay.GRAPHIC_ONLY);
        option.setAlignment(Pos.CENTER_LEFT);
        option.setMaxWidth(Double.MAX_VALUE);
        option.getStyleClass().add("modrinth-filter-option");
        if (selected) {
            option.getStyleClass().add("modrinth-filter-option-selected");
            name.setTextFill(Color.WHITE);
        }
        option.setOnAction(event -> {
            boolean next = !option.getStyleClass().contains("modrinth-filter-option-selected");
            option.getStyleClass().remove("modrinth-filter-option-selected");
            if (next) option.getStyleClass().add("modrinth-filter-option-selected");
            if (labelColor != null) name.setTextFill(next ? Color.WHITE : labelColor);
            selectionChanged.accept(next);
        });
        return option;
    }

    private void rebuildVersionFilterOptions() {
        if (versionFilterOptions == null) return;
        versionFilterOptions.getChildren().clear();
        List<String> source = showAllVersions.isSelected()
                ? allMinecraftVersions : stableMinecraftVersions;
        String query = versionSearchField.getText() == null ? ""
                : versionSearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        source.stream().filter(v -> v != null && v.toLowerCase(java.util.Locale.ROOT).contains(query))
                .limit(500).forEach(version -> versionFilterOptions.getChildren().add(
                        createFilterOption(version, selectedVersions.contains(version), null, null, selected -> {
                            if (selected) selectedVersions.add(version); else selectedVersions.remove(version);
                            browserToolbar.refresh();
                        })));
        if (versionFilterOptions.getChildren().isEmpty()) {
            Label empty = new Label(Localization.text("modrinth.noVersions"));
            empty.getStyleClass().add("modrinth-filter-empty");
            versionFilterOptions.getChildren().add(empty);
        }
    }

    private void rebuildLoaderFilterOptions() {
        if (loaderFilterOptions == null) return;
        loaderFilterOptions.getChildren().clear();
        String query = loaderSearchField.getText() == null ? ""
                : loaderSearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        loaderTags.stream().filter(tag -> tag != null && tag.getName() != null)
                .filter(tag -> tag.getName().toLowerCase(java.util.Locale.ROOT).contains(query))
                .forEach(tag -> {
                    String name = tag.getName();
                    String id = dev.itsdropper.vanta.launcher.modrinth.ModrinthLoaderIds.normalize(name);
                    Button option = createFilterOption(formatTagName(name), selectedLoaders.contains(id),
                            new ModrinthTagIcon(tag, ModrinthTagIcon.loaderColor(name), 16),
                            ModrinthTagIcon.loaderColor(name), selected -> {
                                if (selected) selectedLoaders.add(id); else selectedLoaders.remove(id);
                                browserToolbar.refresh();
                            });
                     loaderFilterOptions.getChildren().add(option);
                });
        if (loaderFilterOptions.getChildren().isEmpty()) {
            Label empty = new Label(Localization.text("modrinth.noLoaders"));
            empty.getStyleClass().add("modrinth-filter-empty");
            loaderFilterOptions.getChildren().add(empty);
        }
    }

    private void rebuildCategoryFilterOptions() {
        if (categoryFilterOptions == null) return;
        categoryFilterOptions.getChildren().clear();
        String query = categorySearchField.getText() == null ? ""
                : categorySearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        categoryTags.stream().filter(tag -> tag != null && tag.getName() != null)
                .filter(tag -> tag.getName().toLowerCase(java.util.Locale.ROOT).contains(query))
                .forEach(tag -> {
                    String name = tag.getName();
                    Button option = createFilterOption(formatTagName(name), selectedCategories.contains(name),
                            new ModrinthTagIcon(tag, Color.web("#9299A6"), 15), Color.web("#9299A6"), selected -> {
                                if (selected) selectedCategories.add(name); else selectedCategories.remove(name);
                                browserToolbar.refresh();
                            });
                     categoryFilterOptions.getChildren().add(option);
                });
        if (categoryFilterOptions.getChildren().isEmpty()) {
            Label empty = new Label(Localization.text("modrinth.noCategories"));
            empty.getStyleClass().add("modrinth-filter-empty");
            categoryFilterOptions.getChildren().add(empty);
        }
    }

    private String colorToCss(Color color) {
        return String.format(java.util.Locale.ROOT, "#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }

    private String searchPromptFor(ModrinthContentType type) {
        if (type == null) type = ModrinthContentType.MOD;
        return switch (type) {
            case MOD -> Localization.text("modrinth.searchMods");
            case RESOURCE_PACK -> Localization.text("modrinth.searchResourcePacks");
            case DATA_PACK -> Localization.text("modrinth.searchDataPacks");
            case SHADER -> Localization.text("modrinth.searchShaders");
            case MODPACK -> Localization.text("modrinth.searchModpacks");
        };
    }

    private void updateContentTypeSegments() {
        for (java.util.Map.Entry<ModrinthContentType, Button> entry : contentTypeButtons.entrySet()) {
            boolean selected = entry.getKey() == contentTypeBox.getValue();
            entry.getValue().getStyleClass().remove("modrinth-content-segment-selected");
            if (selected) entry.getValue().getStyleClass().add("modrinth-content-segment-selected");
        }
    }

    private void loadCategoryTags(ModrinthContentType contentType) {
        long requestId = ++categoryTagRequestId;
        UiExecutor.shared().execute(() -> {
            try {
                List<dev.itsdropper.vanta.launcher.modrinth.ModrinthTag> tags =
                        modrinthClient.getCategoryTags(contentType);
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != categoryTagRequestId) return;
                    categoryTags = tags == null ? List.of() : List.copyOf(tags);
                    rebuildCategoryFilterOptions();
                });
            } catch (Throwable failure) {
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId == categoryTagRequestId) {
                        statusLabel.setText(Localization.text("modrinth.categoriesUnavailable")
                                .replace("{error}", failure.getMessage() == null
                                        ? Localization.text("modrinth.requestFailed") : failure.getMessage()));
                    }
                });
            }
        });
    }

    private void updateCategoriesButton() {
        categoriesButton.setText(selectedCategories.isEmpty()
                ? Localization.text("modrinth.categories")
                : Localization.text("modrinth.categoriesSelected")
                        .replace("{count}", Integer.toString(selectedCategories.size())));
    }

    private void showCategoryPicker() {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);
        VBox root = new VBox(10);
        root.getStyleClass().add("modrinth-popup");
        root.setPrefWidth(340);
        Label title = new Label(Localization.text("modrinth.projectCategories"));
        title.getStyleClass().add("modrinth-popup-title");
        VBox options = new VBox(6);
        List<CheckBox> boxes = new ArrayList<>();
        for (dev.itsdropper.vanta.launcher.modrinth.ModrinthTag tag : categoryTags) {
            if (tag == null || tag.getName() == null || tag.getName().isBlank()) continue;
            String id = tag.getName();
            String label = java.util.Arrays.stream(id.split("[_-]"))
                    .filter(part -> !part.isBlank())
                    .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                    .collect(java.util.stream.Collectors.joining(" "));
            CheckBox box = new CheckBox(label);
            box.setSelected(selectedCategories.contains(id));
            box.setGraphic(new ModrinthTagIcon(tag, Color.web("#9299A6"), 15));
            box.getStyleClass().add("dialog-instance-checkbox");
            box.setUserData(id);
            boxes.add(box);
            options.getChildren().add(box);
        }
        if (boxes.isEmpty()) {
            options.getChildren().add(new Label(Localization.text("modrinth.noCategories")));
        }
        ScrollPane scroll = new ScrollPane(options);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(300);
        Button clear = new Button(Localization.text("modrinth.clear"));
        clear.getStyleClass().add("secondary-button");
        Button apply = new Button(Localization.text("modrinth.applyFilters"));
        apply.getStyleClass().add("primary-button");
        HBox actions = new HBox(8, clear, apply);
        actions.setAlignment(Pos.CENTER_RIGHT);
        clear.setOnAction(event -> boxes.forEach(box -> box.setSelected(false)));
        apply.setOnAction(event -> {
            selectedCategories.clear();
            for (CheckBox box : boxes) {
                if (box.isSelected()) selectedCategories.add((String) box.getUserData());
            }
            updateCategoriesButton();

            popup.hide();
            refreshForCurrentFilters();
        });
        root.getChildren().addAll(title, scroll, actions);
        ThemeManager.apply(root, ThemeManager.loadAccent());
        popup.getContent().add(root);
        javafx.geometry.Bounds bounds = categoriesButton.localToScreen(categoriesButton.getBoundsInLocal());
        if (bounds != null) popup.show(categoriesButton, bounds.getMinX(), bounds.getMaxY() + 6);
    }

    private void showLoaderPicker() {
        if (contentTypeBox.getValue() != ModrinthContentType.MOD) return;
        Popup popup = createFilterPopup(Localization.text("modrinth.selectLoaders"), loadersButton);
        VBox options = (VBox) popup.getProperties().get("options");
        List<CheckBox> boxes = new ArrayList<>();
        for (dev.itsdropper.vanta.launcher.modrinth.ModrinthTag tag : loaderTags) {
            if (tag == null || tag.getName() == null || tag.getName().isBlank()) continue;
            String id = dev.itsdropper.vanta.launcher.modrinth.ModrinthLoaderIds.normalize(tag.getName());
            CheckBox box = new CheckBox(formatTagName(tag.getName()));
            box.setSelected(selectedLoaders.contains(id));
            box.setGraphic(new ModrinthTagIcon(tag,
                    ModrinthTagIcon.loaderColor(tag.getName()), 17));
            box.getStyleClass().add("dialog-instance-checkbox");
            box.setUserData(id);
            boxes.add(box);
            options.getChildren().add(box);
        }
        if (boxes.isEmpty()) options.getChildren().add(new Label(Localization.text("modrinth.noLoaders")));
        finishFilterPopup(popup, boxes, selectedLoaders, loadersButton, this::updateLoaderButton);
    }

    private void showVersionPicker() {
        Popup popup = createFilterPopup(Localization.text("modrinth.selectVersions"), versionsButton);
        VBox options = (VBox) popup.getProperties().get("options");
        List<CheckBox> boxes = new ArrayList<>();
        List<String> source = showAllVersions.isSelected()
                ? allMinecraftVersions : stableMinecraftVersions;
        String query = versionSearchField.getText() == null ? ""
                : versionSearchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
        for (String version : source) {
            if (version == null || !version.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            CheckBox box = new CheckBox(version);
            box.setSelected(selectedVersions.contains(version));
            box.getStyleClass().add("dialog-instance-checkbox");
            box.setUserData(version);
            boxes.add(box);
            options.getChildren().add(box);
        }
        if (boxes.isEmpty()) options.getChildren().add(new Label(Localization.text("modrinth.noVersions")));
        finishFilterPopup(popup, boxes, selectedVersions, versionsButton, this::updateVersionButton);
    }

    private Popup createFilterPopup(String titleText, Button owner) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);
        VBox root = new VBox(10);
        root.getStyleClass().add("modrinth-popup");
        root.setPrefWidth(320);
        Label title = new Label(titleText);
        title.getStyleClass().add("modrinth-popup-title");
        VBox options = new VBox(7);
        ScrollPane scroll = new ScrollPane(options);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(280);
        root.getChildren().addAll(title, scroll);
        ThemeManager.apply(root, ThemeManager.loadAccent());
        popup.getContent().add(root);
        popup.getProperties().put("options", options);
        javafx.geometry.Bounds bounds = owner.localToScreen(owner.getBoundsInLocal());
        if (bounds != null) popup.show(owner, bounds.getMinX(), bounds.getMaxY() + 6);
        return popup;
    }

    private void finishFilterPopup(
            Popup popup,
            List<CheckBox> boxes,
            java.util.Set<String> selected,
            Button owner,
            Runnable updateButton
    ) {
        VBox root = (VBox) popup.getContent().get(0);
        Button clear = new Button(Localization.text("modrinth.clear"));
        clear.getStyleClass().add("secondary-button");
        Button apply = new Button(Localization.text("modrinth.applyFilters"));
        apply.getStyleClass().add("primary-button");
        HBox actions = new HBox(8, clear, apply);
        actions.setAlignment(Pos.CENTER_RIGHT);
        clear.setOnAction(event -> boxes.forEach(box -> box.setSelected(false)));
        apply.setOnAction(event -> {
            selected.clear();
            for (CheckBox box : boxes) {
                if (box.isSelected()) selected.add((String) box.getUserData());
            }
            updateButton.run();

            popup.hide();
            refreshForCurrentFilters();
        });
        root.getChildren().add(actions);
    }

    private String formatTagName(String id) {
        if (id == null || id.isBlank()) return "";
        String compact = id.toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9]", "");
        String known = switch (compact) {
            case "btababric" -> "BTA (Babric)";
            case "javaagent" -> "Java Agent";
            case "legacyfabric" -> "Legacy Fabric";
            case "liteloader" -> "LiteLoader";
            case "modloader", "risugamismodloader" -> "Risugami's ModLoader";
            case "neoforge" -> "NeoForge";
            case "nilloader" -> "NilLoader";
            default -> null;
        };
        if (known != null) return known;
        return java.util.Arrays.stream(id.split("[_-]"))
                .filter(part -> !part.isBlank())
                .map(part -> Character.toUpperCase(part.charAt(0)) + part.substring(1))
                .collect(java.util.stream.Collectors.joining(" "));
    }

    private void updateLoaderButton() {
        loadersButton.setText(selectedLoaders.isEmpty()
                ? Localization.text("modrinth.anyLoader")
                : Localization.text("modrinth.loadersSelected").replace("{count}", Integer.toString(selectedLoaders.size())));
    }

    private void updateVersionButton() {
        versionsButton.setText(selectedVersions.isEmpty()
                ? Localization.text("modrinth.allVersions")
                : Localization.text("modrinth.versionsSelected").replace("{count}", Integer.toString(selectedVersions.size())));
    }

    private List<String> selectedLoaderFacets() {
        return selectedLoaders.stream().map(dev.itsdropper.vanta.launcher.modrinth.ModrinthLoaderIds::normalize).toList();
    }

    private List<String> selectedVersionFacets() {
        return List.copyOf(selectedVersions);
    }

    private void loadLoaderTags(ModrinthContentType contentType) {
        long requestId = ++loaderTagRequestId;
        UiExecutor.shared().execute(() -> {
            try {
                List<dev.itsdropper.vanta.launcher.modrinth.ModrinthTag> response =
                        modrinthClient.getLoaderTags(contentType);
                List<dev.itsdropper.vanta.launcher.modrinth.ModrinthTag> loadedTags =
                        response == null ? List.of() : List.copyOf(response);
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != loaderTagRequestId) return;
                    loaderTags = loadedTags;
                    java.util.Set<String> available = loadedTags.stream()
                            .map(dev.itsdropper.vanta.launcher.modrinth.ModrinthTag::getName)
                            .filter(java.util.Objects::nonNull)
                            .map(dev.itsdropper.vanta.launcher.modrinth.ModrinthLoaderIds::normalize)
                            .collect(java.util.stream.Collectors.toSet());
                    selectedLoaders.retainAll(available);
                    rebuildLoaderFilterOptions();
                    updateLoaderButton();
                });
            } catch (Throwable failure) {
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != loaderTagRequestId) return;
                    loaderTags = List.of();
                    rebuildLoaderFilterOptions();
                    statusLabel.setText(Localization.text("modrinth.loadersUnavailable"));
                });
            }
        });
    }

    private void filterVersionChoices(String query) {
        String selected = versionBox.getValue();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);

        List<String> sourceVersions = showAllVersions.isSelected()
                ? allMinecraftVersions : stableMinecraftVersions;
        selectedVersions.retainAll(sourceVersions);
        updateVersionButton();
        List<String> filtered = sourceVersions.stream()
                .filter(version -> normalizedQuery.isEmpty()
                        || version.toLowerCase(java.util.Locale.ROOT).contains(normalizedQuery))
                .toList();

        versionBox.getItems().setAll("All versions");
        versionBox.getItems().addAll(filtered);

        if (selected != null && (selected.equals("All versions") || filtered.contains(selected))) {
            versionBox.getSelectionModel().select(selected);
        } else {
            versionBox.getSelectionModel().select("All versions");
        }
    }

    private void refreshForCurrentFilters() {
        if (screenshotPreview) {
            statusLabel.setText(Localization.text("globalMods.previewResultsNotice"));
            return;
        }
        browserToolbar.refresh();
    }

    private String selectedVersion() {
        return selectedVersions.stream().findFirst().orElse(null);
    }

    private String selectedLoader() {
        return selectedLoaders.stream().findFirst().orElse(null);
    }

    private List<Instance> compatibleInstances(
            ModrinthProject project,
            List<dev.itsdropper.vanta.launcher.modrinth.ModrinthVersion> versions
    ) {
        List<Instance> compatible = new ArrayList<>();

        for (Instance instance : InstanceManager.discoverInstances()) {
            if (instance == null
                    || instance.getMinecraftVersion() == null
                    || instance.getMinecraftVersion().isBlank()
                    || instance.getLoader() == null) {
                continue;
            }

            String minecraftVersion = instance.getMinecraftVersion();
            String loaderId = dev.itsdropper.vanta.launcher.modrinth.ModrinthLoaderIds
                    .normalize(instance.getLoader());

            if (contentTypeBox.getValue() == ModrinthContentType.MOD) {
                String selected = selectedLoader();
                if (selected != null && !selected.isBlank()
                        && !selected.equalsIgnoreCase("all")
                        && !loaderId.equalsIgnoreCase(selected)) {
                    continue;
                }
            }

            boolean compatibleVersion = false;

            if (versions != null) {
                for (dev.itsdropper.vanta.launcher.modrinth.ModrinthVersion version : versions) {
                    if (!dev.itsdropper.vanta.launcher.modrinth.ModrinthLoaderIds
                            .supportsGameVersion(version.getGameVersions(), minecraftVersion)) {
                        continue;
                    }

                    if (contentTypeBox.getValue() == ModrinthContentType.MOD) {
                        if (version.getLoaders() == null
                                || version.getLoaders().stream()
                                .noneMatch(value -> loaderId.equalsIgnoreCase(
                                        dev.itsdropper.vanta.launcher.modrinth.ModrinthLoaderIds.normalize(value)))) {
                            continue;
                        }
                    }

                    compatibleVersion = true;
                    break;
                }
            }

            if (compatibleVersion) {
                compatible.add(instance);
            }
        }

        return compatible;
    }

    private void handleProjectInstall(String projectId, Button sourceButton) {
        ModrinthContentType selectedType = contentTypeBox.getValue();
        if (selectedType == ModrinthContentType.MODPACK) {
            sourceButton.setDisable(true);
            sourceButton.setText(Localization.text("common.installing"));
            UiExecutor.shared().execute(() -> {
                try {
                    Instance imported = new ModrinthContentService().installModpack(
                            modrinthClient.getProject(projectId)
                    );
                    UiExecutor.shared().dispatchToUi(() -> {
                        sourceButton.setDisable(false);
                        sourceButton.setText(Localization.text("common.install"));
                        statusLabel.setText(Localization.text("modrinth.modpackImported")
                                .replace("{name}", imported.getName()));
                    });
                } catch (Throwable failure) {
                    UiExecutor.shared().dispatchToUi(() -> {
                        sourceButton.setDisable(false);
                        sourceButton.setText(Localization.text("common.install"));
                        statusLabel.setText(failure.getMessage() == null
                                ? Localization.text("modrinth.modpackImportFailed") : failure.getMessage());
                    });
                }
            });
        } else if (selectedType == ModrinthContentType.DATA_PACK) {
            chooseDataPackWorld(projectId, sourceButton);
        } else {
            loadProjectForInstall(projectId, sourceButton);
        }
    }

    private void loadProjectForInstall(String projectId, Button sourceButton) {
        UiExecutor.shared().execute(() -> {
            try {
                ModrinthProject project = modrinthClient.getProject(projectId);
                List<dev.itsdropper.vanta.launcher.modrinth.ModrinthVersion> versions =
                        modrinthClient.getVersions(projectId);
                UiExecutor.shared().dispatchToUi(() -> chooseInstances(project, versions, sourceButton));
            } catch (Throwable ex) {
                ex.printStackTrace();
                UiExecutor.shared().dispatchToUi(() -> statusLabel.setText(Localization.text("globalMods.compatibilityFailed")));
            }
        });
    }

    private record DataPackWorld(Instance instance, java.nio.file.Path directory) {}

    private void chooseDataPackWorld(String projectId, Button sourceButton) {
        sourceButton.setDisable(true);
        sourceButton.setText(Localization.text("common.installing"));
        UiExecutor.shared().execute(() -> {
            try {
                ModrinthProject project = modrinthClient.getProject(projectId);
                List<DataPackWorld> worlds = new ArrayList<>();
                for (Instance instance : InstanceManager.discoverInstances()) {
                    if (instance == null || instance.getDirectory() == null) continue;
                    java.nio.file.Path saves = instance.getDirectory().resolve("saves");
                    if (!java.nio.file.Files.isDirectory(saves)) continue;
                    try (java.util.stream.Stream<java.nio.file.Path> entries = java.nio.file.Files.list(saves)) {
                        entries.filter(java.nio.file.Files::isDirectory)
                                .filter(world -> java.nio.file.Files.isRegularFile(world.resolve("level.dat")))
                                .forEach(world -> worlds.add(new DataPackWorld(instance, world)));
                    }
                }
                UiExecutor.shared().dispatchToUi(() -> {
                    sourceButton.setDisable(false);
                    sourceButton.setText(Localization.text("common.install"));
                    if (worlds.isEmpty()) {
                        statusLabel.setText(Localization.text("modrinth.noWorldsFound"));
                        return;
                    }
                    Popup popup = new Popup();
                    popup.setAutoHide(true);
                    popup.setAutoFix(true);
                    popup.setHideOnEscape(true);
                    VBox root = new VBox(10);
                    root.getStyleClass().add("modrinth-popup");
                    root.setPrefWidth(520);
                    Label title = new Label(Localization.text("modrinth.dataPackInstallPrompt"));
                    title.getStyleClass().add("modrinth-popup-title");
                    Label subtitle = new Label(Localization.text("modrinth.dataPackInstallDescription"));
                    subtitle.getStyleClass().add("modrinth-popup-subtitle");
                    VBox choices = new VBox(7);
                    for (DataPackWorld world : worlds) {
                        Button choice = new Button(world.instance().getName() + "  /  "
                                + world.directory().getFileName());
                        choice.getStyleClass().add("modrinth-version-row");
                        choice.setMaxWidth(Double.MAX_VALUE);
                        choice.setOnAction(event -> {
                            popup.hide();
                            choice.setDisable(true);
                            UiExecutor.shared().execute(() -> {
                                try {
                                    new dev.itsdropper.vanta.launcher.service.ModrinthService()
                                            .installDataPack(world.instance(), world.directory(), project);
                                    UiExecutor.shared().dispatchToUi(() ->
                                            statusLabel.setText(Localization.text("modrinth.dataPackInstalled")
                                                    .replace("{name}", project.getTitle())
                                                    .replace("{world}", world.directory().getFileName().toString())));
                                } catch (Throwable failure) {
                                    UiExecutor.shared().dispatchToUi(() ->
                                            statusLabel.setText(failure.getMessage() == null
                                                    ? Localization.text("modrinth.dataPackInstallFailed") : failure.getMessage()));
                                }
                            });
                        });
                        choices.getChildren().add(choice);
                    }
                    ScrollPane scroll = new ScrollPane(choices);
                    scroll.setFitToWidth(true);
                    scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                    scroll.setPrefViewportHeight(Math.min(420, 80 + worlds.size() * 42.0));
                    root.getChildren().addAll(title, subtitle, scroll);
                    ThemeManager.apply(root, ThemeManager.loadAccent());
                    popup.getContent().add(root);
                    javafx.geometry.Bounds bounds = sourceButton.localToScreen(sourceButton.getBoundsInLocal());
                    if (bounds != null) popup.show(sourceButton, bounds.getMinX() - 520 + sourceButton.getWidth(),
                            bounds.getMaxY() + 8);
                });
            } catch (Throwable failure) {
                UiExecutor.shared().dispatchToUi(() -> {
                    sourceButton.setDisable(false);
                    sourceButton.setText(Localization.text("common.install"));
                    statusLabel.setText(failure.getMessage() == null
                            ? Localization.text("modrinth.worldLoadFailed") : failure.getMessage());
                });
            }
        });
    }

    private void chooseInstances(
            ModrinthProject project,
            List<dev.itsdropper.vanta.launcher.modrinth.ModrinthVersion> versions,
            Button sourceButton
    ) {
        List<Instance> compatible = compatibleInstances(project, versions);
        if (compatible.isEmpty()) {
            statusLabel.setText(
                    Localization.text("globalMods.noCompatibleInstancesPrefix") + project.getTitle()
                            + ". Check that you have a matching Minecraft version and loader."
            );
            return;
        }

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);

        VBox root = new VBox(14);
        root.getStyleClass().add("modrinth-popup");
        root.setPrefWidth(560);

        Label title = new Label(Localization.text("globalMods.installPrefix") + project.getTitle());
        title.getStyleClass().add("modrinth-popup-title");
        Label subtitle = new Label(Localization.text("globalMods.chooseInstances"));
        subtitle.getStyleClass().add("modrinth-popup-subtitle");

        VBox choices = new VBox(8);
        List<CheckBox> boxes = new ArrayList<>();
        for (Instance instance : compatible) {
            CheckBox box = new CheckBox(
                    instance.getName() + "   •   Minecraft " + instance.getMinecraftVersion()
                            + "   •   " + instance.getDisplayLoader()
            );
            box.getStyleClass().add("dialog-instance-checkbox");
            boxes.add(box);
            choices.getChildren().add(box);
        }

        ScrollPane scroll = new ScrollPane(choices);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(Math.min(420, 90 + compatible.size() * 48.0));
        scroll.getStyleClass().add("modrinth-popup-scroll");

        Button cancel = new Button(Localization.text("common.cancel"));
        cancel.getStyleClass().add("modrinth-popup-secondary");
        Button install = new Button(Localization.text("common.install"));
        install.getStyleClass().add("modrinth-popup-primary");
        install.setDisable(true);

        for (CheckBox box : boxes) {
            box.selectedProperty().addListener((obs, oldValue, selected) ->
                    install.setDisable(boxes.stream().noneMatch(CheckBox::isSelected))
            );
        }

        HBox actions = new HBox(8, cancel, install);
        actions.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().addAll(title, subtitle, scroll, actions);

        cancel.setOnAction(event -> popup.hide());
        install.setOnAction(event -> {
            List<Instance> selected = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).isSelected()) selected.add(compatible.get(i));
            }
            popup.hide();
            installToInstances(project, selected, sourceButton);
        });

        ThemeManager.apply(root, ThemeManager.loadAccent());

        popup.getContent().add(root);
        popup.show(sourceButton,
                sourceButton.localToScreen(sourceButton.getBoundsInLocal()).getMinX() - 560 + sourceButton.getWidth(),
                sourceButton.localToScreen(sourceButton.getBoundsInLocal()).getMaxY() + 8);
    }

    private void installToInstances(ModrinthProject project, List<Instance> instances, Button sourceButton) {
        sourceButton.setDisable(true);
        sourceButton.setText(Localization.text("common.installing"));
        UiExecutor.shared().execute(() -> {
            int success = 0;
            int failed = 0;
            for (Instance instance : instances) {
                try {
                    ModrinthContentType type = contentTypeBox.getValue();
                    if (type == ModrinthContentType.MOD) {
                        new dev.itsdropper.vanta.launcher.service.ModrinthService().installMod(instance, project);
                    } else if (type == ModrinthContentType.RESOURCE_PACK) {
                        new ModrinthContentService().installResourcePack(instance, project);
                    } else if (type == ModrinthContentType.SHADER) {
                        new ModrinthContentService().installShader(instance, project);
                    }
                    success++;
                } catch (Throwable ex) {
                    failed++;
                    ex.printStackTrace();
                }
            }
            int installed = success;
            int failures = failed;
            UiExecutor.shared().dispatchToUi(() -> {
                sourceButton.setDisable(false);
                sourceButton.setText(Localization.text("common.install"));
                statusLabel.setText(Localization.text("modrinth.installedIntoInstances")
                        .replace("{name}", project.getTitle())
                        .replace("{count}", Integer.toString(installed))
                        .replace("{failed}", Integer.toString(failures)));
            });
        });
    }

    // =============================================================
    // MOST DOWNLOADED
    // =============================================================

    private void loadMostDownloadedMods() {
        long requestId = ++popularRequestId;

        UiExecutor.shared().execute(() -> {

                    try {

                        List<ModrinthSearchHit> projects =
                                modrinthClient
                                        .getMostDownloaded(
                                                contentTypeBox.getValue(),
                                                selectedLoader(),
                                                selectedVersion()
                                        )
                                        .getHits();

                        UiExecutor.shared().dispatchToUi(() -> {
                            if (requestId != popularRequestId) return;
                            showMostDownloadedMods(projects);
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        UiExecutor.shared().dispatchToUi(() -> {
                            if (requestId != popularRequestId) return;
                            popularStatusLabel.setText(Localization.text("browseMods.popularFailed"));
                        });
                    }
        });


    }

    // =============================================================
    // SHOW MOST DOWNLOADED
    // =============================================================

    private void showMostDownloadedMods(
            List<ModrinthSearchHit> projects
    ) {

        popularResults
                .getChildren()
                .clear();

        if (projects == null
                || projects.isEmpty()) {

            popularStatusLabel.setText(
                    Localization.text("browseMods.noPopular")
            );

            return;
        }

        for (
                ModrinthSearchHit project
                : projects
        ) {

            popularResults
                    .getChildren()
                    .add(
                            createPopularCard(
                                    project
                            )
                    );
        }

        popularStatusLabel.setText(
                projects.size()
                        + " popular mods."
        );
    }

    // =============================================================
    // POPULAR CARD
    // =============================================================

    private VBox createPopularCard(
            ModrinthSearchHit project
    ) {

        ImageView icon =
                createIcon(
                        project
                );

        Label title =
                new Label(
                        safe(
                                project.getTitle(),
                                "Unknown Mod"
                        )
                );

        title.getStyleClass().add(
                "instance-name"
        );

        Label description =
                new Label(
                        safe(
                                project.getDescription(),
                                Localization.text("modrinth.noDescription")
                        )
                );

        description.setWrapText(
                true
        );

        description.setMaxWidth(
                650
        );

        description.getStyleClass().add(
                "global-mod-description"
        );

        Label downloads =
                new Label(
                        formatNumber(
                                project.getDownloads()
                        )
                                + " downloads"
                );

        downloads.getStyleClass().add(
                "instance-loader"
        );

        VBox information =
                new VBox(
                        5,
                        title,
                        description,
                        downloads
                );

        HBox.setHgrow(
                information,
                Priority.ALWAYS
        );

        Button versionsButton = createVersionsButton(project.getProjectId());

        Button installButton = new Button(Localization.text("common.install"));
        installButton.getStyleClass().add("primary-button");
        installButton.setOnAction(event ->
                handleProjectInstall(project.getProjectId(), installButton)
        );

        HBox actions = new HBox(
                8,
                versionsButton,
                installButton
        );
        actions.setAlignment(Pos.CENTER_RIGHT);

        HBox row =
                new HBox(
                        16,
                        createIconBox(
                                icon,
                                52
                        ),
                        information,
                        actions
                );

        row.setAlignment(
                Pos.CENTER_LEFT
        );

        VBox card =
                new VBox(
                        row
                );

        card.setMaxWidth(
                Double.MAX_VALUE
        );

        card.setPadding(
                new Insets(
                        16,
                        18,
                        16,
                        18
                )
        );

        card.getStyleClass().add(
                "instance-card"
        );

        card.setCursor(
                javafx.scene.Cursor.HAND
        );

        card.setOnMouseClicked(
                event -> {

                    if (event.getButton() ==
                            javafx.scene.input.MouseButton.PRIMARY) {

                        String projectId =
                                project.getProjectId();

                        if (projectId != null
                                && !projectId.isBlank()) {

                            onModSelected.accept(
                                    projectId,
                                    contentTypeBox.getValue()
                            );
                        }
                    }
                }
        );

        return card;
    }

    // =============================================================
    // SEARCH
    // =============================================================

    private void search() {
        browserToolbar.refresh();
    }

    private void search(ModrinthBrowserToolbar.SearchState state) {
        long requestId = ++searchRequestId;
        String query = state.query();
        boolean hasActiveFacets = contentTypeBox.getValue() != ModrinthContentType.MOD
                || !selectedLoaders.isEmpty()
                || !selectedVersions.isEmpty()
                || !selectedCategories.isEmpty();

        hidePopularSection();
        results.getChildren().clear();
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        statusLabel.setText(Localization.text("common.searching"));
        for (int i = 0; i < Math.min(state.limit(), 6); i++) {
            VBox skeleton = new VBox(8);
            skeleton.setMinHeight(112);
            skeleton.getStyleClass().add("modrinth-result-skeleton");
            results.getChildren().add(skeleton);
        }

        ModrinthContentType requestedType = contentTypeBox.getValue();
        List<String> requestedLoaders = selectedLoaderFacets();
        List<String> requestedVersions = selectedVersionFacets();
        List<String> requestedCategories = List.copyOf(selectedCategories);

        UiExecutor.shared().execute(() -> {
            try {
                dev.itsdropper.vanta.launcher.modrinth.ModrinthSearchResult response =
                        modrinthClient.searchPage(
                                query,
                                requestedType,
                                requestedLoaders,
                                requestedVersions,
                                requestedCategories,
                                state.index(),
                                state.limit(),
                                state.offset()
                        );
                List<ModrinthSearchHit> projects = response.getHits();
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != searchRequestId) return;
                    browserToolbar.setTotalHits(response.getTotalHits());
                    showResults(projects);
                });
            } catch (Throwable failure) {
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != searchRequestId) return;
                    results.getChildren().clear();
                    statusLabel.setText(failure.getMessage() == null
                            ? Localization.text("modrinth.searchFailed") : failure.getMessage());
                    retryButton.setVisible(true);
                    retryButton.setManaged(true);
                });
            }
        });
    }

    // =============================================================
    // SHOW POPULAR SECTION
    // =============================================================

    private void showPopularSection() {

        popularTitle.setVisible(
                true
        );

        popularTitle.setManaged(
                true
        );

        popularScrollPane.setVisible(
                true
        );

        popularScrollPane.setManaged(
                true
        );

        popularStatusLabel.setVisible(
                true
        );

        popularStatusLabel.setManaged(
                true
        );

        resultsScrollPane.setVisible(
                false
        );

        resultsScrollPane.setManaged(
                false
        );
    }

    // =============================================================
    // HIDE POPULAR SECTION
    // =============================================================

    private void hidePopularSection() {

        popularTitle.setVisible(
                false
        );

        popularTitle.setManaged(
                false
        );

        popularScrollPane.setVisible(
                false
        );

        popularScrollPane.setManaged(
                false
        );

        popularStatusLabel.setVisible(
                false
        );

        popularStatusLabel.setManaged(
                false
        );

        resultsScrollPane.setVisible(
                true
        );

        resultsScrollPane.setManaged(
                true
        );
    }

    // =============================================================
    // RESULTS
    // =============================================================

    private void showResults(
            List<ModrinthSearchHit> projects
    ) {

        results.getChildren().clear();
        retryButton.setVisible(false);
        retryButton.setManaged(false);

        if (projects == null || projects.isEmpty()) {
            results.getChildren().add(new EmptyState(
                    "⌕",
                    Localization.text("modrinth.emptyTitle"),
                    Localization.text("modrinth.emptyDescription"),
                    Localization.text("modrinth.emptyHint")
            ));
            statusLabel.setText(Localization.text("modrinth.showingResults")
                    .replace("{start}", "0").replace("{end}", "0")
                    .replace("{total}", "0"));
            return;
        }

        for (
                ModrinthSearchHit project
                : projects
        ) {

            results.getChildren().add(createSizedGlobalProjectCard(project));
        }

        ModrinthBrowserToolbar.SearchState page = browserToolbar.getState();
        int start = page.offset() + 1;
        int end = page.offset() + projects.size();
        statusLabel.setText(Localization.text("modrinth.showingResults")
                .replace("{start}", Integer.toString(start))
                .replace("{end}", Integer.toString(end))
                .replace("{total}", formatNumber(browserToolbar.getTotalHits())));
    }

    private VBox createSizedGlobalProjectCard(ModrinthSearchHit project) {
        VBox card = createProjectCard(project);
        double availableWidth = resultsScrollPane.getViewportBounds().getWidth();
        boolean grid = browserToolbar.isGridLayout();
        card.setPrefWidth(grid ? 340 : Math.max(280, availableWidth - 28));
        card.setMinWidth(grid ? 260 : 0);
        return card;
    }

    private void applyGlobalResultLayout(boolean grid) {
        results.getStyleClass().removeAll("modrinth-grid-results", "modrinth-list-results");
        results.getStyleClass().add(grid ? "modrinth-grid-results" : "modrinth-list-results");
        double availableWidth = resultsScrollPane.getViewportBounds().getWidth();
        for (javafx.scene.Node node : results.getChildren()) {
            if (node instanceof VBox card) {
                card.setPrefWidth(grid ? 340 : Math.max(280, availableWidth - 28));
                card.setMinWidth(grid ? 260 : 0);
            }
        }
    }

    // =============================================================
    // PROJECT CARD
    // =============================================================

    private Button createVersionsButton(String projectId) {
        Button button = new Button("⋮");
        button.getStyleClass().add("modrinth-versions-button");
        button.setAccessibleText(Localization.text("globalMods.versions"));
        button.setOnAction(event -> showVersionsPopup(projectId, button));
        return button;
    }

    private void showVersionsPopup(String projectId, Button anchor) {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);

        VBox root = new VBox(10);
        root.getStyleClass().add("modrinth-popup");
        root.setPrefWidth(520);

        Label title = new Label(Localization.text("globalMods.versionsUpper"));
        title.getStyleClass().add("modrinth-popup-title");
        Label subtitle = new Label(Localization.text("globalMods.loadingVersions"));
        subtitle.getStyleClass().add("modrinth-popup-subtitle");

        VBox list = new VBox(7);
        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(420);
        scroll.getStyleClass().add("modrinth-popup-scroll");

        root.getChildren().addAll(title, subtitle, new Separator(), scroll);
        ThemeManager.apply(root, ThemeManager.loadAccent());

        popup.getContent().add(root);

        UiExecutor.shared().execute(() -> {
            try {
                List<dev.itsdropper.vanta.launcher.modrinth.ModrinthVersion> versions =
                        modrinthClient.getVersions(projectId);
                UiExecutor.shared().dispatchToUi(() -> {
                    list.getChildren().clear();
                    subtitle.setText((versions == null ? 0 : versions.size()) + " versions");
                    if (versions == null || versions.isEmpty()) {
                        list.getChildren().add(new Label(Localization.text("globalMods.noVersions")));
                        return;
                    }
                    for (dev.itsdropper.vanta.launcher.modrinth.ModrinthVersion version : versions) {
                        list.getChildren().add(createVersionRow(projectId, version, popup));
                    }
                });
            } catch (Throwable ex) {
                UiExecutor.shared().dispatchToUi(() -> {
                    list.getChildren().clear();
                    subtitle.setText(Localization.text("globalMods.loadVersionsFailed"));
                });
            }
        });

        popup.show(anchor, anchor.localToScreen(anchor.getBoundsInLocal()).getMaxX() - 520,
                anchor.localToScreen(anchor.getBoundsInLocal()).getMaxY() + 6);
    }

    private HBox createVersionRow(
            String projectId,
            dev.itsdropper.vanta.launcher.modrinth.ModrinthVersion version,
            Popup versionsPopup
    ) {
        Label name = new Label(safe(version.getVersionNumber(), safe(version.getName(), "Unknown")));
        name.getStyleClass().add("modrinth-version-name");
        Label type = new Label(safe(version.getVersionType(), "release").toUpperCase());
        type.getStyleClass().add("modrinth-version-type");
        String games = version.getGameVersions() == null ? "" : String.join(", ", version.getGameVersions());
        Label meta = new Label(games + (version.getLoaders() == null ? "" : "  •  " + String.join(", ", version.getLoaders())));
        meta.getStyleClass().add("modrinth-version-meta");
        VBox info = new VBox(4, name, meta);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label downloads = new Label(formatNumber(version.getDownloads()));
        downloads.getStyleClass().add("modrinth-version-downloads");

        HBox row = new HBox(10, info, type, downloads);

        if (contentTypeBox.getValue() == ModrinthContentType.MOD) {
            Button installButton = new Button(Localization.text("common.install"));
            installButton.getStyleClass().add("modrinth-version-install");
            installButton.setOnAction(event -> {
                versionsPopup.hide();
                chooseInstancesForVersion(projectId, version);
            });
            row.getChildren().add(installButton);
        }
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("modrinth-version-row");
        return row;
    }

    private void chooseInstancesForVersion(
            String projectId,
            dev.itsdropper.vanta.launcher.modrinth.ModrinthVersion version
    ) {
        List<Instance> compatible = new ArrayList<>();

        for (Instance instance : InstanceManager.discoverInstances()) {
            if (instance == null || instance.getMinecraftVersion() == null
                    || instance.getMinecraftVersion().isBlank()) continue;

            if (contentTypeBox.getValue() != ModrinthContentType.MOD) continue;

            String instanceLoader = instance.getLoader() == null
                    ? ""
                    : instance.getLoader().trim();

            if (!"fabric".equalsIgnoreCase(instanceLoader)
                    && !"forge".equalsIgnoreCase(instanceLoader)) continue;

            boolean gameMatch = version.getGameVersions() != null
                    && version.getGameVersions().contains(instance.getMinecraftVersion());
            boolean loaderMatch = version.getLoaders() != null
                    && version.getLoaders().stream().anyMatch(v -> instanceLoader.equalsIgnoreCase(v));

            if (gameMatch && loaderMatch) compatible.add(instance);
        }

        if (compatible.isEmpty()) {
            statusLabel.setText(Localization.text("globalMods.noCompatibleVersionInstances"));
            return;
        }

        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);

        VBox root = new VBox(14);
        root.getStyleClass().add("modrinth-popup");
        root.setPrefWidth(560);

        Label title = new Label(Localization.text("globalMods.installPrefix") + safe(version.getVersionNumber(), "VERSION"));
        title.getStyleClass().add("modrinth-popup-title");
        Label subtitle = new Label(Localization.text("globalMods.chooseExactVersionInstances"));
        subtitle.getStyleClass().add("modrinth-popup-subtitle");

        VBox choices = new VBox(8);
        List<CheckBox> boxes = new ArrayList<>();
        for (Instance instance : compatible) {
            CheckBox box = new CheckBox(instance.getName() + "   •   Minecraft "
                    + instance.getMinecraftVersion() + "   •   " + instance.getDisplayLoader());
            box.getStyleClass().add("dialog-instance-checkbox");
            boxes.add(box);
            choices.getChildren().add(box);
        }

        ScrollPane scroll = new ScrollPane(choices);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(Math.min(420, 90 + compatible.size() * 48.0));
        scroll.getStyleClass().add("modrinth-popup-scroll");

        Button cancel = new Button(Localization.text("common.cancel"));
        cancel.getStyleClass().add("modrinth-popup-secondary");
        Button install = new Button(Localization.text("common.install"));
        install.getStyleClass().add("modrinth-popup-primary");
        install.setDisable(true);

        for (CheckBox box : boxes) {
            box.selectedProperty().addListener((obs, oldValue, selected) ->
                    install.setDisable(boxes.stream().noneMatch(CheckBox::isSelected)));
        }

        HBox actions = new HBox(8, cancel, install);
        actions.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().addAll(title, subtitle, scroll, actions);
        ThemeManager.apply(root, ThemeManager.loadAccent());

        popup.getContent().add(root);

        cancel.setOnAction(event -> popup.hide());
        install.setOnAction(event -> {
            List<Instance> selected = new ArrayList<>();
            for (int i = 0; i < boxes.size(); i++) {
                if (boxes.get(i).isSelected()) selected.add(compatible.get(i));
            }
            popup.hide();
            installExactVersion(projectId, version, selected);
        });

        if (getScene() != null) {
            javafx.geometry.Bounds bounds = localToScreen(getBoundsInLocal());
            if (bounds != null) {
                popup.show(this,
                        Math.max(bounds.getMinX() + 80, bounds.getCenterX() - 280),
                        Math.max(bounds.getMinY() + 80, bounds.getCenterY() - 220));
            }
        }
    }

    private void installExactVersion(
            String projectId,
            dev.itsdropper.vanta.launcher.modrinth.ModrinthVersion version,
            List<Instance> instances
    ) {
        UiExecutor.shared().execute(() -> {
            int success = 0;
            int failed = 0;
            for (Instance instance : instances) {
                try {
                    new dev.itsdropper.vanta.launcher.service.ModrinthService()
                            .installModVersion(instance, projectId, version);
                    success++;
                } catch (Throwable ex) {
                    failed++;
                    ex.printStackTrace();
                }
            }
            int installed = success;
            int failures = failed;
            UiExecutor.shared().dispatchToUi(() -> statusLabel.setText(
                    Localization.text("modrinth.versionInstalledIntoInstances")
                            .replace("{version}", safe(version.getVersionNumber(), "Version"))
                            .replace("{count}", Integer.toString(installed))
                            .replace("{failed}", Integer.toString(failures))
            ));
        });
    }

    private String formatEnvironment(String client, String server) {
        boolean clientSupported = supportsEnvironment(client);
        boolean serverSupported = supportsEnvironment(server);
        if (clientSupported && serverSupported) {
            return "◎  " + Localization.text("modrinth.environmentClientOrServer");
        }
        if (clientSupported) return "▣  " + Localization.text("modrinth.environmentClientOnly");
        if (serverSupported) return "▤  " + Localization.text("modrinth.environmentServerOnly");
        return "";
    }

    private boolean supportsEnvironment(String value) {
        return value != null && !value.isBlank()
                && !"unknown".equalsIgnoreCase(value)
                && !"unsupported".equalsIgnoreCase(value);
    }

    private String relativeUpdateTime(String value) {
        if (value == null || value.isBlank()) return Localization.text("modrinth.updateUnavailable");
        try {
            java.time.Duration age = java.time.Duration.between(
                    java.time.Instant.parse(value), java.time.Instant.now());
            long days = Math.max(0, age.toDays());
            if (days == 0) return Localization.text("modrinth.updatedToday");
            if (days == 1) return Localization.text("modrinth.updatedYesterday");
            if (days < 30) return Localization.text("modrinth.updatedDaysAgo").replace("{count}", Long.toString(days));
            if (days < 365) return Localization.text("modrinth.updatedMonthsAgo").replace("{count}", Long.toString(days / 30));
            return Localization.text("modrinth.updatedYearsAgo").replace("{count}", Long.toString(days / 365));
        } catch (java.time.DateTimeException ignored) {
            return Localization.text("modrinth.updateUnavailable");
        }
    }

    private VBox createProjectCard(ModrinthSearchHit project) {
        ImageView icon = createIcon(project);
        VBox iconBox = createIconBox(icon, 58);

        Label title = new Label(safe(project.getTitle(), Localization.text("modrinth.unknownAuthor")));
        title.getStyleClass().add("modrinth-project-title");
        Label author = new Label(Localization.text("modrinth.byAuthor")
                .replace("{author}", safe(project.getAuthor(), Localization.text("modrinth.unknownAuthor"))));
        author.getStyleClass().add("modrinth-project-author");

        HBox titleLine = new HBox(7, title, author);
        titleLine.setAlignment(Pos.CENTER_LEFT);

        Label description = new Label(safe(project.getDescription(), Localization.text("modrinth.noDescription")));
        description.setWrapText(true);
        description.setMaxHeight(42);
        description.getStyleClass().add("modrinth-project-description");

        FlowPane tags = new FlowPane(5, 4);
        tags.setAlignment(Pos.CENTER_LEFT);
        String environment = formatEnvironment(project.getClientSide(), project.getServerSide());
        if (!environment.isBlank()) {
            Label env = new Label(environment);
            env.getStyleClass().addAll("modrinth-tag-chip", "modrinth-environment-chip");
            tags.getChildren().add(env);
        }

        java.util.Map<String, dev.itsdropper.vanta.launcher.modrinth.ModrinthTag> loaderById =
                loaderTags.stream().filter(tag -> tag != null && tag.getName() != null)
                        .collect(java.util.stream.Collectors.toMap(
                                tag -> dev.itsdropper.vanta.launcher.modrinth.ModrinthLoaderIds.normalize(tag.getName()),
                                tag -> tag, (first, second) -> first, java.util.LinkedHashMap::new));
        java.util.Set<String> categoryIds = categoryTags.stream()
                .map(dev.itsdropper.vanta.launcher.modrinth.ModrinthTag::getName)
                .filter(java.util.Objects::nonNull)
                .collect(java.util.stream.Collectors.toSet());

        List<String> cardCategories = project.getCategories() == null ? List.of() : project.getCategories();
        List<String> orderedTags = new ArrayList<>();
        for (String value : cardCategories) {
            if (value != null && !value.isBlank()
                    && !loaderById.containsKey(dev.itsdropper.vanta.launcher.modrinth.ModrinthLoaderIds.normalize(value))) {
                orderedTags.add(value);
            }
        }
        for (String value : cardCategories) {
            if (value != null && loaderById.containsKey(dev.itsdropper.vanta.launcher.modrinth.ModrinthLoaderIds.normalize(value))) {
                orderedTags.add(value);
            }
        }
        int maxVisibleTags = browserToolbar.isGridLayout() ? 3 : 7;
        int visible = 0;
        for (String value : orderedTags) {
            if (visible >= maxVisibleTags) break;
            String normalized = dev.itsdropper.vanta.launcher.modrinth.ModrinthLoaderIds.normalize(value);
            dev.itsdropper.vanta.launcher.modrinth.ModrinthTag loaderTag = loaderById.get(normalized);
            if (loaderTag != null) {
                Label loaderChip = new Label(formatTagName(value));
                loaderChip.setGraphic(new ModrinthTagIcon(loaderTag,
                        ModrinthTagIcon.loaderColor(value), 12));
                loaderChip.getStyleClass().addAll("modrinth-tag-chip", "modrinth-loader-chip");
                loaderChip.setStyle("-fx-text-fill: " + colorToCss(ModrinthTagIcon.loaderColor(value)) + ";");
                tags.getChildren().add(loaderChip);
            } else {
                String display = categoryTags.stream()
                        .filter(tag -> tag != null && value.equals(tag.getName()))
                        .map(dev.itsdropper.vanta.launcher.modrinth.ModrinthTag::getName)
                        .findFirst().orElse(value);
                Label categoryChip = new Label(formatTagName(display));
                categoryChip.getStyleClass().add("modrinth-tag-chip");
                tags.getChildren().add(categoryChip);
            }
            visible++;
        }
        if (orderedTags.size() > visible) {
            Label more = new Label("+" + (orderedTags.size() - visible));
            more.getStyleClass().add("modrinth-tag-chip");
            tags.getChildren().add(more);
        }

        Button menuButton = createVersionsButton(project.getProjectId());
        Button installButton = new Button(Localization.text("common.install"));
        installButton.getStyleClass().add("primary-button");
        installButton.setOnAction(event -> handleProjectInstall(project.getProjectId(), installButton));

        return new dev.itsdropper.vanta.ui.components.ModrinthProjectCard(
                iconBox, title, author, description, tags,
                project.getDownloads(), project.getFollows(),
                relativeUpdateTime(project.getDateModified()),
                menuButton, installButton, browserToolbar.isGridLayout(),
                () -> {
                    if (project.getProjectId() != null && !project.getProjectId().isBlank()) {
                        onModSelected.accept(project.getProjectId(), contentTypeBox.getValue());
                    }
                });
    }

    private HBox statLine(String symbol, String value) {
        Label icon = new Label(symbol);
        icon.getStyleClass().add("modrinth-stat-icon");
        Label text = new Label(value);
        text.getStyleClass().add("modrinth-stat-value");
        HBox line = new HBox(6, icon, text);
        line.setAlignment(Pos.CENTER_RIGHT);
        return line;
    }

    private String compactNumber(long value) {
        if (value >= 1_000_000_000L) return String.format(java.util.Locale.ROOT, "%.1fB", value / 1_000_000_000.0);
        if (value >= 1_000_000L) return String.format(java.util.Locale.ROOT, "%.1fM", value / 1_000_000.0);
        if (value >= 10_000L) return String.format(java.util.Locale.ROOT, "%.1fK", value / 1_000.0);
        return String.format(java.util.Locale.ROOT, "%,d", value);
    }
    // =============================================================
    // ICON
    // =============================================================

    private ImageView createIcon(
            ModrinthSearchHit project
    ) {

        ImageView icon =
                new ImageView();

        icon.setFitWidth(
                52
        );

        icon.setFitHeight(
                52
        );

        icon.setPreserveRatio(
                true
        );

        icon.setSmooth(
                true
        );

        Rectangle clip =
                new Rectangle(
                        52,
                        52
                );

        clip.setArcWidth(
                14
        );

        clip.setArcHeight(
                14
        );

        icon.setClip(
                clip
        );

        loadIcon(
                project,
                icon
        );

        return icon;
    }

    private VBox createIconBox(
            ImageView icon,
            double size
    ) {

        VBox iconBox =
                new VBox(
                        icon
                );

        iconBox.setAlignment(
                Pos.CENTER
        );

        iconBox.setMinSize(
                size,
                size
        );

        iconBox.setPrefSize(
                size,
                size
        );

        iconBox.setMaxSize(
                size,
                size
        );

        iconBox.getStyleClass().add(
                "mod-icon-box"
        );

        return iconBox;
    }

    // =============================================================
    // LOAD MODRINTH ICON
    // =============================================================

    private void loadIcon(
            ModrinthSearchHit project,
            ImageView imageView
    ) {

        String iconUrl =
                project.getIconUrl();

        if (iconUrl == null
                || iconUrl.isBlank()) {

            return;
        }

        UiExecutor.shared().execute(() -> {

                    try {

                        HttpRequest request =
                                HttpRequest.newBuilder(
                                                URI.create(
                                                        iconUrl
                                                )
                                        )
                                        .GET()
                                        .header(
                                                "User-Agent",
                                                "VantaLauncher/1.0"
                                        )
                                        .build();

                        HttpResponse<InputStream> response =
                                httpClient.send(
                                        request,
                                        HttpResponse.BodyHandlers
                                                .ofInputStream()
                                );

                        if (response.statusCode() < 200
                                || response.statusCode() >= 300) {

                            response.body().close();

                            return;
                        }

                        BufferedImage bufferedImage;

                        try (InputStream input =
                                     response.body()) {

                            bufferedImage =
                                    ImageIO.read(
                                            input
                                    );
                        }

                        if (bufferedImage == null) {

                            return;
                        }

                        ByteArrayOutputStream output =
                                new ByteArrayOutputStream();

                        ImageIO.write(
                                bufferedImage,
                                "png",
                                output
                        );

                        Image image =
                                new Image(
                                        new ByteArrayInputStream(
                                                output.toByteArray()
                                        )
                                );

                        UiExecutor.shared().dispatchToUi(() -> {

                            if (!image.isError()) {

                                imageView.setImage(
                                        image
                                );
                            }
                        });

                    } catch (Throwable ex) {


                        ex.printStackTrace();
                    }
        });


    }

    // =============================================================
    // SAFE
    // =============================================================

    private String safe(
            String value,
            String fallback
    ) {

        if (value == null
                || value.isBlank()) {

            return fallback;
        }

        return value;
    }

    // =============================================================
    // FORMAT NUMBER
    // =============================================================

    private String formatNumber(
            int number
    ) {

        if (number >= 1_000_000) {

            return String.format(
                    "%.1fM",
                    number / 1_000_000.0
            );
        }

        if (number >= 1_000) {

            return String.format(
                    "%.1fK",
                    number / 1_000.0
            );
        }

        return String.valueOf(
                number
        );
    }
}

