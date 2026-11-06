package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.ThemeManager;
import dev.itsdropper.vanta.ui.async.UiExecutor;
import dev.itsdropper.vanta.ui.components.PageHeader;
import dev.itsdropper.vanta.ui.components.EmptyState;
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
    private int searchOffset;
    private int searchTotalHits;
    private final ComboBox<String> sortBox;
    private final ComboBox<Integer> resultLimitBox;
    private final Button previousPageButton;
    private final Button nextPageButton;
    private final Label pageIndicator;
    private final Button layoutButton;
    private final java.util.Map<ModrinthContentType, Button> contentTypeButtons = new java.util.EnumMap<>(ModrinthContentType.class);

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
                "Browse mods, resource packs, and shaders on Modrinth.",
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
        loaderBox.setAccessibleText(Localization.text("globalMods.loaderFilter"));
        loaderBox.setPrefWidth(140);
        loaderBox.setPrefHeight(42);
        loaderBox.getStyleClass().add("create-combo");

        versionBox = new ComboBox<>();
        versionBox.getItems().add("All versions");
        versionBox.setValue("All versions");
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
        showAllVersions = new CheckBox("Show all versions");
        showAllVersions.setSelected(false);
        showAllVersions.getStyleClass().add("dialog-instance-checkbox");
        showAllVersions.selectedProperty().addListener((obs, oldValue, newValue) ->
                filterVersionChoices(versionSearchField.getText()));

        categoriesButton = new Button("Categories");
        categoriesButton.getStyleClass().add("secondary-button");
        categoriesButton.setOnAction(event -> showCategoryPicker());
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
                case MOD -> "Mods";
                case RESOURCE_PACK -> "Resource Packs";
                case DATA_PACK -> "Data Packs";
                case SHADER -> "Shaders";
                case MODPACK -> "Modpacks";
            };
            Button segment = new Button(label);
            segment.getStyleClass().add("modrinth-content-segment");
            segment.setOnAction(event -> contentTypeBox.setValue(type));
            contentTypeButtons.put(type, segment);
            contentTypeSegments.getChildren().add(segment);
        }
        updateContentTypeSegments();

        Label filtersTitle = new Label("Filters");
        filtersTitle.getStyleClass().add("settings-section-title");
        VBox versionFilters = new VBox(8, versionSearchField, versionBox, showAllVersions);
        VBox loaderFilters = new VBox(8, loaderBox);
        VBox categoryFilters = new VBox(8, categoriesButton);
        TitledPane versionPanel = new TitledPane("Game version", versionFilters);
        TitledPane loaderPanel = new TitledPane("Loader", loaderFilters);
        TitledPane categoryPanel = new TitledPane("Category", categoryFilters);
        versionPanel.setExpanded(true);
        loaderPanel.setExpanded(true);
        categoryPanel.setExpanded(true);
        versionPanel.setAnimated(true);
        loaderPanel.setAnimated(true);
        categoryPanel.setAnimated(true);
        VBox filterSidebar = new VBox(10,
                filtersTitle, versionPanel, loaderPanel, categoryPanel);
        filterSidebar.getStyleClass().add("modrinth-filter-sidebar");
        filterSidebar.setPrefWidth(230);
        filterSidebar.setMinWidth(205);

        contentTypeBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            loaderBox.setDisable(newValue != ModrinthContentType.MOD);
            searchOffset = 0;
            selectedCategories.clear();
            updateCategoriesButton();
            updateContentTypeSegments();
            loadLoaderTags(newValue);
            loadCategoryTags(newValue);
            refreshForCurrentFilters();
        });

        loaderBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (contentTypeBox.getValue() == ModrinthContentType.MOD) {
                searchOffset = 0;
                refreshForCurrentFilters();
            }
        });

        versionBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            searchOffset = 0;
            refreshForCurrentFilters();
        });

        versionSearchField.textProperty().addListener((obs, oldValue, newValue) ->
                filterVersionChoices(newValue));

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
                    });
                } catch (Throwable ex) {
                    ex.printStackTrace();
                }
            });
        }

        // =========================================================
        // SEARCH
        // =========================================================

        searchField =
                new TextField();

        searchField.setPromptText(
                Localization.text("globalMods.searchPrompt")
        );
        searchField.setMinWidth(0);
        searchField.setAccessibleText(Localization.text("globalMods.searchAccessible"));

        searchField.getStyleClass().add(
                "create-field"
        );

        HBox.setHgrow(
                searchField,
                Priority.ALWAYS
        );

        Button searchButton =
                new Button(
                        Localization.text("common.search")
                );

        searchButton.getStyleClass().add(
                "primary-button"
        );
        searchButton.setAccessibleText(Localization.text("globalMods.searchAccessible"));

        searchButton.setOnAction(
                event ->
                        search()
        );

        searchField.setOnAction(
                event ->
                        search()
        );

        HBox searchBar =
                new HBox(
                        10,
                        searchField,
                        searchButton
                );

        searchBar.setAlignment(
                Pos.CENTER_LEFT
        );

        sortBox = new ComboBox<>();
        sortBox.getItems().setAll("Relevance", "Downloads", "Followers", "Date published", "Date updated");
        sortBox.setValue("Relevance");
        sortBox.getStyleClass().add("create-combo");
        sortBox.setAccessibleText("Sort Modrinth results");
        resultLimitBox = new ComboBox<>();
        resultLimitBox.getItems().setAll(5, 10, 15, 20, 50, 100);
        resultLimitBox.setValue(20);
        resultLimitBox.getStyleClass().add("create-combo");
        resultLimitBox.setAccessibleText("Results per page");
        previousPageButton = new Button("‹");
        nextPageButton = new Button("›");
        pageIndicator = new Label("Page 1");
        layoutButton = new Button(LauncherSettings.isModrinthGridLayoutEnabled() ? "▦" : "☷");
        layoutButton.getStyleClass().add("secondary-button");
        layoutButton.setAccessibleText("Toggle result layout");
        layoutButton.setOnAction(event -> {
            boolean grid = !LauncherSettings.isModrinthGridLayoutEnabled();
            LauncherSettings.setModrinthGridLayoutEnabled(grid);
            layoutButton.setText(grid ? "▦" : "☷");
            layoutButton.setTooltip(new javafx.scene.control.Tooltip(
                    grid ? "Switch to list layout" : "Switch to grid layout"));
            applyGlobalResultLayout(grid);
        });
        previousPageButton.getStyleClass().add("secondary-button");
        nextPageButton.getStyleClass().add("secondary-button");
        pageIndicator.getStyleClass().add("instances-status");
        HBox browseControls = new HBox(8,
                new Label("Sort"), sortBox,
                new Label("Per page"), resultLimitBox, layoutButton,
                previousPageButton, pageIndicator, nextPageButton);
        browseControls.setAlignment(Pos.CENTER_LEFT);
        sortBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (oldValue != null && newValue != null && !oldValue.equals(newValue)) {
                searchOffset = 0;
                search();
            }
        });
        resultLimitBox.valueProperty().addListener((obs, oldValue, newValue) -> {
            if (oldValue != null && newValue != null) {
                searchOffset = 0;
                search();
            }
        });
        previousPageButton.setOnAction(event -> {
            if (searchOffset > 0) {
                searchOffset = Math.max(0, searchOffset - resultLimitBox.getValue());
                search();
            }
        });
        nextPageButton.setOnAction(event -> {
            if (searchOffset + resultLimitBox.getValue() < searchTotalHits) {
                searchOffset += resultLimitBox.getValue();
                search();
            }
        });

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
        results.getStyleClass().add("instance-list");

        resultsScrollPane =
                new ScrollPane(
                        results
                );

        resultsScrollPane.setFitToWidth(true);
        resultsScrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) ->
                applyGlobalResultLayout(LauncherSettings.isModrinthGridLayoutEnabled()));

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

        VBox contentPane = new VBox(12,
                popularTitle, popularScrollPane, popularStatusLabel,
                resultsScrollPane, statusLabel);
        contentPane.setFillWidth(true);
        HBox browseContent = new HBox(18, filterSidebar, contentPane);
        HBox.setHgrow(contentPane, Priority.ALWAYS);
        VBox.setVgrow(browseContent, Priority.ALWAYS);

        getChildren().addAll(
                header,
                contentTypeSegments,
                searchBar,
                browseControls,
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
            loadMostDownloadedMods();
        }
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
                });
            } catch (Throwable failure) {
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId == categoryTagRequestId) {
                        statusLabel.setText("Could not load Modrinth categories: "
                                + (failure.getMessage() == null ? "request failed" : failure.getMessage()));
                    }
                });
            }
        });
    }

    private void updateCategoriesButton() {
        categoriesButton.setText(selectedCategories.isEmpty()
                ? "Categories" : "Categories (" + selectedCategories.size() + ")");
    }

    private void showCategoryPicker() {
        Popup popup = new Popup();
        popup.setAutoHide(true);
        popup.setAutoFix(true);
        popup.setHideOnEscape(true);
        VBox root = new VBox(10);
        root.getStyleClass().add("modrinth-popup");
        root.setPrefWidth(340);
        Label title = new Label("Project categories");
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
            box.getStyleClass().add("dialog-instance-checkbox");
            box.setUserData(id);
            boxes.add(box);
            options.getChildren().add(box);
        }
        if (boxes.isEmpty()) {
            options.getChildren().add(new Label("No categories available for this content type."));
        }
        ScrollPane scroll = new ScrollPane(options);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scroll.setPrefViewportHeight(300);
        Button clear = new Button("Clear");
        clear.getStyleClass().add("secondary-button");
        Button apply = new Button("Apply filters");
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
            searchOffset = 0;
            popup.hide();
            refreshForCurrentFilters();
        });
        root.getChildren().addAll(title, scroll, actions);
        ThemeManager.apply(root, ThemeManager.loadAccent());
        popup.getContent().add(root);
        javafx.geometry.Bounds bounds = categoriesButton.localToScreen(categoriesButton.getBoundsInLocal());
        if (bounds != null) popup.show(categoriesButton, bounds.getMinX(), bounds.getMaxY() + 6);
    }

    private void loadLoaderTags(ModrinthContentType contentType) {
        long requestId = ++loaderTagRequestId;
        if (contentType != ModrinthContentType.MOD) {
            loaderBox.getItems().setAll("Any");
            loaderBox.setValue("Any");
            loaderBox.setDisable(true);
            return;
        }
        loaderBox.setDisable(false);
        UiExecutor.shared().execute(() -> {
            try {
                List<dev.itsdropper.vanta.launcher.modrinth.ModrinthTag> tags =
                        modrinthClient.getLoaderTags(contentType);
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != loaderTagRequestId) return;
                    loaderTags = tags == null ? List.of() : List.copyOf(tags);
                    String selected = loaderBox.getValue();
                    loaderBox.getItems().setAll("Any");
                    if (tags != null) {
                        tags.stream()
                                .map(dev.itsdropper.vanta.launcher.modrinth.ModrinthTag::getName)
                                .filter(name -> name != null && !name.isBlank())
                                .distinct()
                                .sorted(String.CASE_INSENSITIVE_ORDER)
                                .forEach(loaderBox.getItems()::add);
                    }
                    if (selected != null && loaderBox.getItems().contains(selected)) {
                        loaderBox.setValue(selected);
                    } else {
                        loaderBox.setValue("Any");
                    }
                });
            } catch (Throwable failure) {
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != loaderTagRequestId) return;
                    // Keep the known loader choices as an offline fallback.
                    if (loaderBox.getItems().isEmpty()) {
                        loaderBox.getItems().setAll(
                                "Any", "Fabric", "Quilt", "Forge", "NeoForge",
                                "Ornithe", "Legacy Fabric", "Babric", "NilLoader",
                                "LiteLoader", "Rift", "ModLoader"
                        );
                    }
                });
            }
        });
    }

    private void filterVersionChoices(String query) {
        String selected = versionBox.getValue();
        String normalizedQuery = query == null ? "" : query.trim().toLowerCase(java.util.Locale.ROOT);

        List<String> sourceVersions = showAllVersions.isSelected()
                ? allMinecraftVersions : stableMinecraftVersions;
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
        String query = searchField.getText();

        if (query != null && !query.trim().isBlank()) {
            search();
        } else {
            showPopularSection();
            loadMostDownloadedMods();
        }
    }

    private String selectedVersion() {
        String value = versionBox.getValue();
        return value == null || "All versions".equals(value) ? null : value;
    }

    private String selectedLoader() {
        if (contentTypeBox.getValue() != ModrinthContentType.MOD) {
            return null;
        }
        String selected = loaderBox.getValue();
        if (selected == null || "Any".equalsIgnoreCase(selected)) {
            return null;
        }
        return dev.itsdropper.vanta.launcher.modrinth.ModrinthLoaderIds.normalize(selected);
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
                        statusLabel.setText("No Minecraft worlds found in your instances. Create or import a world first.");
                        return;
                    }
                    Popup popup = new Popup();
                    popup.setAutoHide(true);
                    popup.setAutoFix(true);
                    popup.setHideOnEscape(true);
                    VBox root = new VBox(10);
                    root.getStyleClass().add("modrinth-popup");
                    root.setPrefWidth(520);
                    Label title = new Label("Install data pack into a world");
                    title.getStyleClass().add("modrinth-popup-title");
                    Label subtitle = new Label("Data packs are stored per-world, not in the instance root.");
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
                                            statusLabel.setText("Installed " + project.getTitle()
                                                    + " into " + world.directory().getFileName() + "."));
                                } catch (Throwable failure) {
                                    UiExecutor.shared().dispatchToUi(() ->
                                            statusLabel.setText(failure.getMessage() == null
                                                    ? "Data pack installation failed." : failure.getMessage()));
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
                            ? "Could not load Minecraft worlds." : failure.getMessage());
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
                statusLabel.setText(project.getTitle() + " installed into " + installed
                        + " instance" + (installed == 1 ? "" : "s")
                        + (failures == 0 ? "." : "; " + failures + " failed."));
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
                                "No description."
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

        Button installButton =
                new Button(Localization.text("common.install"));
        installButton.getStyleClass().add("primary-button");
        installButton.setOnAction(event -> {
            ModrinthContentType selectedType = contentTypeBox.getValue();
            if (selectedType == ModrinthContentType.MODPACK) {
                installButton.setDisable(true);
                installButton.setText(Localization.text("common.installing"));
                UiExecutor.shared().execute(() -> {
                    try {
                        Instance imported = new ModrinthContentService().installModpack(
                                modrinthClient.getProject(project.getProjectId())
                        );
                        UiExecutor.shared().dispatchToUi(() -> {
                            installButton.setDisable(false);
                            installButton.setText(Localization.text("common.install"));
                            statusLabel.setText("Imported " + imported.getName() + ".");
                        });
                    } catch (Throwable failure) {
                        UiExecutor.shared().dispatchToUi(() -> {
                            installButton.setDisable(false);
                            installButton.setText(Localization.text("common.install"));
                            statusLabel.setText(failure.getMessage() == null
                                    ? "Modpack import failed." : failure.getMessage());
                        });
                    }
                });
            } else if (selectedType == ModrinthContentType.DATA_PACK) {
                chooseDataPackWorld(project.getProjectId(), installButton);
            } else {
                loadProjectForInstall(project.getProjectId(), installButton);
            }
        });

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
        long requestId = ++searchRequestId;

        String query =
                searchField
                        .getText()
                        .trim();

        // =========================================================
        // EMPTY = MOST DOWNLOADED
        // =========================================================

        if (query.isBlank()) {
            searchOffset = 0;
            searchTotalHits = 0;
            updateSearchPagination();

            results
                    .getChildren()
                    .clear();

            showPopularSection();

            statusLabel.setText(
                    Localization.text("globalMods.searchHelp")
            );

            return;
        }

        // =========================================================
        // SEARCH = HIDE POPULAR
        // =========================================================

        hidePopularSection();

        results
                .getChildren()
                .clear();

        statusLabel.setText(
                Localization.text("common.searching")
        );

        UiExecutor.shared().execute(() -> {

                    try {

                        String loader = selectedLoader();
                        String version = selectedVersion();
                        dev.itsdropper.vanta.launcher.modrinth.ModrinthSearchResult response =
                                modrinthClient.searchPage(
                                        query,
                                        contentTypeBox.getValue(),
                                        loader == null ? List.of() : List.of(loader),
                                        version == null ? List.of() : List.of(version),
                                        List.copyOf(selectedCategories),
                                        switch (sortBox.getValue()) {
                                            case "Downloads" -> "downloads";
                                            case "Followers" -> "follows";
                                            case "Date published" -> "newest";
                                            case "Date updated" -> "updated";
                                            default -> "relevance";
                                        },
                                        resultLimitBox.getValue(),
                                        searchOffset
                                );
                        List<ModrinthSearchHit> projects = response.getHits();

                        UiExecutor.shared().dispatchToUi(() -> {
                            if (requestId != searchRequestId) return;
                            searchTotalHits = response.getTotalHits();
                            updateSearchPagination();
                            showResults(projects);
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        UiExecutor.shared().dispatchToUi(() -> {
                            if (requestId != searchRequestId) return;
                            statusLabel.setText(ex.getMessage() != null
                                    ? ex.getMessage() : "Failed to search Modrinth.");
                        });
                    }
        });


    }

    private void updateSearchPagination() {
        int limit = resultLimitBox.getValue() == null ? 20 : resultLimitBox.getValue();
        int page = searchOffset / limit + 1;
        int pages = Math.max(1, (int) Math.ceil(searchTotalHits / (double) limit));
        previousPageButton.setDisable(searchOffset <= 0);
        nextPageButton.setDisable(searchOffset + limit >= searchTotalHits);
        pageIndicator.setText("Page " + page + " of " + pages + "  •  " + searchTotalHits + " results");
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

        results
                .getChildren()
                .clear();

        if (projects == null
                || projects.isEmpty()) {

            statusLabel.setText(
                    Localization.text("globalMods.noMods")
            );

            return;
        }

        for (
                ModrinthSearchHit project
                : projects
        ) {

            results.getChildren().add(createSizedGlobalProjectCard(project));
        }

        statusLabel.setText(
                projects.size()
                        + " projects found."
        );
    }

    private VBox createSizedGlobalProjectCard(ModrinthSearchHit project) {
        VBox card = createProjectCard(project);
        double availableWidth = resultsScrollPane.getViewportBounds().getWidth();
        boolean grid = LauncherSettings.isModrinthGridLayoutEnabled();
        card.setPrefWidth(grid ? 300 : Math.max(280, availableWidth - 28));
        card.setMinWidth(grid ? 260 : 0);
        return card;
    }

    private void applyGlobalResultLayout(boolean grid) {
        double availableWidth = resultsScrollPane.getViewportBounds().getWidth();
        for (javafx.scene.Node node : results.getChildren()) {
            if (node instanceof VBox card) {
                card.setPrefWidth(grid ? 300 : Math.max(280, availableWidth - 28));
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
                    safe(version.getVersionNumber(), "Version") + " installed into " + installed
                            + " instance" + (installed == 1 ? "" : "s")
                            + (failures == 0 ? "." : "; " + failures + " failed.")
            ));
        });
    }

    private String formatEnvironment(String client, String server) {
        List<String> values = new ArrayList<>();
        if (client != null && !client.isBlank() && !"unknown".equalsIgnoreCase(client)) {
            values.add("Client " + client.toLowerCase(java.util.Locale.ROOT));
        }
        if (server != null && !server.isBlank() && !"unknown".equalsIgnoreCase(server)) {
            values.add("Server " + server.toLowerCase(java.util.Locale.ROOT));
        }
        return String.join(" / ", values);
    }

    private String relativeUpdateTime(String value) {
        if (value == null || value.isBlank()) return "update date unavailable";
        try {
            java.time.Duration age = java.time.Duration.between(
                    java.time.Instant.parse(value), java.time.Instant.now());
            long days = Math.max(0, age.toDays());
            if (days == 0) return "updated today";
            if (days == 1) return "updated yesterday";
            if (days < 30) return "updated " + days + " days ago";
            if (days < 365) return "updated " + (days / 30) + " months ago";
            return "updated " + (days / 365) + " years ago";
        } catch (java.time.DateTimeException ignored) {
            return "update date unavailable";
        }
    }

    private VBox createProjectCard(
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

        Label author = new Label("by " + safe(project.getAuthor(), "Unknown author"));
        author.getStyleClass().add("instance-loader");

        Label description =
                new Label(safe(project.getDescription(), "No description."));
        description.setWrapText(true);
        description.setMaxWidth(650);
        description.setMaxHeight(42);
        description.getStyleClass().add("global-mod-description");

        FlowPane tags = new FlowPane(6, 4);
        tags.setAlignment(Pos.CENTER_LEFT);
        String environment = formatEnvironment(project.getClientSide(), project.getServerSide());
        if (!environment.isBlank()) {
            Label environmentTag = new Label(environment);
            environmentTag.getStyleClass().add("modrinth-tag-chip");
            tags.getChildren().add(environmentTag);
        }
        java.util.Set<String> loaderIds = loaderTags.stream()
                .map(dev.itsdropper.vanta.launcher.modrinth.ModrinthTag::getName)
                .filter(java.util.Objects::nonNull)
                .map(value -> value.toLowerCase(java.util.Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        for (String category : project.getCategories()) {
            if (category == null || category.isBlank() || loaderIds.contains(category.toLowerCase(java.util.Locale.ROOT))) continue;
            Label categoryTag = new Label(category.replace('-', ' ').replace('_', ' '));
            categoryTag.getStyleClass().add("modrinth-tag-chip");
            tags.getChildren().add(categoryTag);
            if (tags.getChildren().size() >= 6) break;
        }
        for (String category : project.getCategories()) {
            if (category == null || !loaderIds.contains(category.toLowerCase(java.util.Locale.ROOT))) continue;
            Label loaderTag = new Label(category);
            loaderTag.getStyleClass().add("modrinth-tag-chip");
            tags.getChildren().add(loaderTag);
        }

        Label metadata = new Label(
                formatNumber(project.getDownloads()) + " downloads  •  "
                        + formatNumber(project.getFollows()) + " followers  •  "
                        + relativeUpdateTime(project.getDateModified())
        );
        metadata.getStyleClass().add("instance-loader");

        VBox information = new VBox(5, title, author, description, tags, metadata);

        HBox.setHgrow(
                information,
                Priority.ALWAYS
        );

        Button versionsButton = createVersionsButton(project.getProjectId());

        Button installButton =
                new Button(Localization.text("common.install"));
        installButton.getStyleClass().add("primary-button");
        installButton.setOnAction(event ->
                loadProjectForInstall(project.getProjectId(), installButton)
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

