package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.async.UiExecutor;

import dev.itsdropper.vanta.ui.components.PageHeader;
import dev.itsdropper.vanta.ui.components.ModrinthBrowserToolbar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.modrinth.ModrinthProject;
import dev.itsdropper.vanta.launcher.service.ModrinthService;
import dev.itsdropper.vanta.ui.LauncherSettings;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

public class BrowseModsView extends VBox {

    private final Instance instance;
    private final Runnable onBack;

    private final ModrinthService modrinthService;
    private final HttpClient httpClient;

    private final TextField searchField;
    private final ModrinthBrowserToolbar browserToolbar;

    private final FlowPane results;
    private final Label statusLabel;
    private Button retryButton;

    private final VBox popularResults;
    private final Label popularStatusLabel;

    private final Label popularTitle;
    private final ScrollPane popularScrollPane;

    private final ScrollPane searchScrollPane;

    private final java.util.function.Consumer<ModrinthProject> onModSelected;
    private long searchRequestId;
    private long popularRequestId;

    public BrowseModsView(
            Instance instance,
            Runnable onBack,
            java.util.function.Consumer<ModrinthProject> onModSelected
    ) {

        this.instance =
                instance;

        this.onBack =
                onBack;

        this.onModSelected =
                onModSelected;

        this.modrinthService =
                new ModrinthService();

        this.httpClient =
                HttpClient.newHttpClient();

        getStyleClass().add(
                "page"
        );

        dev.itsdropper.vanta.ui.ResponsiveLayout.bindPagePadding(this);

        setSpacing(
                20
        );

        // =========================================================
        // HEADER
        // =========================================================

        Button backButton =
                new Button(
                        Localization.text("common.backArrow")
                );

        backButton.getStyleClass().add(
                "secondary-button"
        );

        backButton.setOnAction(
                event ->
                        onBack.run()
        );

        PageHeader headerText = new PageHeader(
                Localization.text("modrinth.browseModsTitle"),
                instance.getName()
                        + " • Minecraft "
                        + instance.getMinecraftVersion()
                        + " • "
                        + instance.getDisplayLoader(),
                "page-title",
                "page-subtitle"
        );

        VBox header =
                new VBox(
                        14,
                        backButton,
                        headerText
                );

        // =========================================================
        // SEARCH
        // =========================================================

        browserToolbar = new ModrinthBrowserToolbar(
                Localization.text("browseMods.searchPrompt"),
                this::search,
                this::applyResultLayout
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
                Pos.TOP_LEFT
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

        popularScrollPane.setPrefHeight(
                320
        );

        popularScrollPane.setMaxHeight(
                320
        );

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

        searchScrollPane =
                new ScrollPane(
                        results
                );

        searchScrollPane.setFitToWidth(true);
        searchScrollPane.viewportBoundsProperty().addListener((obs, oldBounds, newBounds) ->
                applyResultLayout(browserToolbar.isGridLayout()));

        searchScrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        searchScrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        searchScrollPane.getStyleClass().add(
                "instances-scroll"
        );

        VBox.setVgrow(
                searchScrollPane,
                Priority.ALWAYS
        );

        // =========================================================
        // STATUS
        // =========================================================

        statusLabel =
                new Label(
                        Localization.text("browseMods.searchPromptHelp")
                );

        statusLabel.getStyleClass().add(
                "instances-status"
        );

        // =========================================================
        // BUILD
        // =========================================================

        retryButton = new Button(Localization.text("common.retry"));
        retryButton.getStyleClass().add("secondary-button");
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        retryButton.setOnAction(event -> browserToolbar.refresh());

        getChildren().addAll(
                header,
                browserToolbar,
                searchScrollPane,
                statusLabel,
                retryButton
        );

        searchScrollPane.setVisible(true);
        searchScrollPane.setManaged(true);
        browserToolbar.refresh();
    }

    // =============================================================
    // MOST DOWNLOADED
    // =============================================================

    private void loadMostDownloadedMods() {
        long requestId = ++popularRequestId;
        popularStatusLabel.setText(Localization.text("browseMods.loadingPopular"));

        UiExecutor.shared().execute(() -> {
            try {
                List<ModrinthProject> broad =
                        modrinthService.getMostDownloadedModsFastBroad(instance);
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != popularRequestId) return;
                    showMostDownloadedMods(broad, true);
                });

                List<ModrinthProject> compatible =
                        modrinthService.getMostDownloadedModsFast(instance);
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != popularRequestId) return;
                    showMostDownloadedMods(compatible, false);
                });
            } catch (Throwable ex) {
                ex.printStackTrace();
                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != popularRequestId) return;
                    if (popularResults.getChildren().isEmpty()) {
                        popularStatusLabel.setText(Localization.text("browseMods.popularFailed"));
                    }
                });
            }
        });
    }

    // =============================================================
    // SHOW MOST DOWNLOADED
    // =============================================================

    private void showMostDownloadedMods(
            List<ModrinthProject> projects,
            boolean checkingCompatibility
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
                ModrinthProject project
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
                        + (checkingCompatibility
                            ? " " + Localization.text("modrinth.popularModsChecking")
                            : " compatible popular mods.")
        );
    }

    // =============================================================
    // POPULAR CARD
    // =============================================================

    private VBox createPopularCard(
            ModrinthProject project
    ) {

        // =========================================================
        // ICON
        // =========================================================

        ImageView icon =
                new ImageView();

        icon.setFitWidth(
                48
        );

        icon.setFitHeight(
                48
        );

        icon.setPreserveRatio(
                true
        );

        icon.setSmooth(
                true
        );

        Rectangle clip =
                new Rectangle(
                        48,
                        48
                );

        clip.setArcWidth(
                12
        );

        clip.setArcHeight(
                12
        );

        icon.setClip(
                clip
        );

        VBox iconBox =
                new VBox(
                        icon
                );

        iconBox.setAlignment(
                Pos.CENTER
        );

        iconBox.setMinSize(
                48,
                48
        );

        iconBox.setPrefSize(
                48,
                48
        );

        iconBox.setMaxSize(
                48,
                48
        );

        iconBox.getStyleClass().add(
                "mod-icon-box"
        );

        loadIcon(
                project,
                icon
        );

        // =========================================================
        // TITLE
        // =========================================================

        Label title =
                new Label(
                        project.getTitle()
                );

        title.getStyleClass().add(
                "instance-name"
        );

        // =========================================================
        // DESCRIPTION
        // =========================================================

        Label description =
                new Label(
                        project.getDescription() != null
                                ? project.getDescription()
                                : "No description."
                );

        description.setWrapText(
                true
        );

        description.setMaxWidth(
                210
        );

        description.setMaxHeight(
                42
        );

        description.getStyleClass().add(
                "instance-version"
        );

        // =========================================================
        // DOWNLOADS
        // =========================================================

        Label downloads =
                new Label(
                        formatNumber(
                                project.getDownloads()
                        )
                                + " " + Localization.text("modrinth.downloadsLabel").replace("{count}", "")
                );

        downloads.getStyleClass().add(
                "instance-loader"
        );

        // =========================================================
        // INFORMATION
        // =========================================================

        VBox information =
                new VBox(
                        5,
                        title,
                        description
                );

        if (LauncherSettings.isShowModrinthDownloadsEnabled()) {
            information.getChildren().add(downloads);
        }

        // =========================================================
        // ROW
        // =========================================================

        HBox row =
                new HBox(
                        12,
                        iconBox,
                        information
                );

        row.setAlignment(
                Pos.TOP_LEFT
        );

        // =========================================================
        // CARD
        // =========================================================

        VBox card =
                new VBox(
                        row
                );

        card.setMaxWidth(
                Double.MAX_VALUE
        );

        card.setPadding(
                new Insets(
                        14
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

                        onModSelected.accept(
                                project
                        );
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
        String query = state.query();
        long requestId = ++searchRequestId;

        hidePopularSection();
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        statusLabel.setText(Localization.text("common.searching"));
        results.getChildren().clear();
        for (int i = 0; i < Math.min(state.limit(), 6); i++) {
            VBox skeleton = new VBox(8);
            skeleton.setMinHeight(112);
            skeleton.getStyleClass().add("modrinth-result-skeleton");
            results.getChildren().add(skeleton);
        }

        UiExecutor.shared().execute(() -> {
            try {
                dev.itsdropper.vanta.launcher.modrinth.ModrinthSearchResult response =
                        modrinthService.searchModsPage(
                                instance,
                                query,
                                state.index(),
                                state.limit(),
                                state.offset()
                        );
                List<ModrinthProject> projects =
                        modrinthService.mapSearchHitsToProjects(response.getHits());

                UiExecutor.shared().dispatchToUi(() -> {
                    if (requestId != searchRequestId) return;
                    browserToolbar.setTotalHits(response.getTotalHits());
                    showResults(projects, false);
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
    // SHOW POPULAR
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

        searchScrollPane.setVisible(
                false
        );

        searchScrollPane.setManaged(
                false
        );

        // If the popular results were unloaded,
        // load them again when returning to the empty search.
        if (popularResults.getChildren().isEmpty()) {

            loadMostDownloadedMods();
        }
    }

    // =============================================================
    // HIDE POPULAR
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

        // Actually unload the popular cards.
        popularResults
                .getChildren()
                .clear();

        searchScrollPane.setVisible(
                true
        );

        searchScrollPane.setManaged(
                true
        );
    }

    // =============================================================
    // RESULTS
    // =============================================================

    private void showResults(
            List<ModrinthProject> projects,
            boolean checkingCompatibility
    ) {

        results.getChildren().clear();
        retryButton.setVisible(false);
        retryButton.setManaged(false);
        if (projects == null || projects.isEmpty()) {
            VBox emptyState = new VBox(7,
                    new Label(Localization.text("modrinth.emptyTitle")),
                    new Label(Localization.text("modrinth.emptyDescription")),
                    new Label(Localization.text("modrinth.emptyHint")));
            emptyState.getStyleClass().add("modrinth-empty-state");
            results.getChildren().add(emptyState);
            statusLabel.setText(Localization.text("modrinth.showingResults")
                    .replace("{start}", "0").replace("{end}", "0").replace("{total}", "0"));
            return;
        }

        for (
                ModrinthProject project
                : projects
        ) {

            results.getChildren().add(createSizedProjectCard(project));
        }

        ModrinthBrowserToolbar.SearchState page = browserToolbar.getState();
        statusLabel.setText(Localization.text("modrinth.showingResults")
                .replace("{start}", Integer.toString(page.offset() + 1))
                .replace("{end}", Integer.toString(page.offset() + projects.size()))
                .replace("{total}", formatNumber(browserToolbar.getTotalHits())));
    }

    private VBox createSizedProjectCard(ModrinthProject project) {
        VBox card = createProjectCard(project);
        double availableWidth = searchScrollPane.getViewportBounds().getWidth();
        double width = browserToolbar.isGridLayout()
                ? 300 : Math.max(420, availableWidth - 28);
        card.setPrefWidth(width);
        card.setMinWidth(browserToolbar.isGridLayout() ? 260 : 0);
        return card;
    }

    private void applyResultLayout(boolean grid) {
        results.getStyleClass().removeAll("modrinth-grid-results", "modrinth-list-results");
        results.getStyleClass().add(grid
                ? "modrinth-grid-results" : "modrinth-list-results");
        double availableWidth = searchScrollPane.getViewportBounds().getWidth();
        for (javafx.scene.Node node : results.getChildren()) {
            if (node instanceof VBox card) {
                card.setPrefWidth(grid ? 300 : Math.max(420, availableWidth - 28));
                card.setMinWidth(grid ? 260 : 0);
            }
        }
    }

    // =============================================================
    // PROJECT CARD
    // =============================================================

    private VBox createProjectCard(ModrinthProject project) {
        ImageView icon = new ImageView();
        icon.setFitWidth(58);
        icon.setFitHeight(58);
        icon.setPreserveRatio(true);
        icon.setSmooth(true);
        Rectangle clip = new Rectangle(58, 58);
        clip.setArcWidth(16);
        clip.setArcHeight(16);
        icon.setClip(clip);

        VBox iconBox = new VBox(icon);
        iconBox.setAlignment(Pos.CENTER);
        iconBox.setMinSize(60, 60);
        iconBox.setPrefSize(60, 60);
        iconBox.setMaxSize(60, 60);
        iconBox.getStyleClass().add("mod-icon-box");
        loadIcon(project, icon);

        Label title = new Label(project.getTitle() == null ? "" : project.getTitle());
        title.getStyleClass().add("modrinth-project-title");
        Label author = new Label(Localization.text("modrinth.byAuthor").replace("{author}",
                project.getTeam() == null || project.getTeam().isBlank()
                        ? Localization.text("modrinth.unknownAuthor") : project.getTeam()));
        author.getStyleClass().add("modrinth-project-author");
        HBox titleLine = new HBox(7, title, author);
        titleLine.setAlignment(Pos.CENTER_LEFT);

        Label description = new Label(project.getDescription() == null
                ? Localization.text("modrinth.noDescription") : project.getDescription());
        description.setWrapText(true);
        description.setMaxHeight(42);
        description.getStyleClass().add("modrinth-project-description");

        FlowPane projectTags = new FlowPane(5, 4);
        projectTags.setAlignment(Pos.CENTER_LEFT);
        String environment = formatEnvironment(project.getClientSide(), project.getServerSide());
        if (!environment.isBlank()) {
            Label environmentTag = new Label(environment);
            environmentTag.getStyleClass().addAll("modrinth-tag-chip", "modrinth-environment-chip");
            projectTags.getChildren().add(environmentTag);
        }
        List<String> tagValues = new java.util.ArrayList<>();
        if (project.getCategories() != null) tagValues.addAll(project.getCategories());
        if (project.getLoaders() != null) tagValues.addAll(project.getLoaders());
        int shown = 0;
        for (String tagValue : tagValues) {
            if (tagValue == null || tagValue.isBlank() || shown >= (browserToolbar.isGridLayout() ? 3 : 7)) continue;
            Label tag = new Label(tagValue.replace('-', ' ').replace('_', ' '));
            tag.getStyleClass().add("modrinth-tag-chip");
            if (project.getLoaders() != null && project.getLoaders().contains(tagValue)) {
                tag.getStyleClass().add("modrinth-loader-chip");
                tag.setStyle("-fx-text-fill: " + colorToCss(dev.itsdropper.vanta.ui.components.ModrinthTagIcon.loaderColor(tagValue)) + ";");
            }
            projectTags.getChildren().add(tag);
            shown++;
        }
        if (tagValues.size() > shown) {
            Label more = new Label("+" + (tagValues.size() - shown));
            more.getStyleClass().add("modrinth-tag-chip");
            projectTags.getChildren().add(more);
        }

        Button menuButton = new Button("⋮");
        menuButton.getStyleClass().add("modrinth-versions-button");
        menuButton.setAccessibleText(Localization.text("modrinth.projectMenu"));
        javafx.scene.control.MenuItem openProject = new javafx.scene.control.MenuItem(
                Localization.text("modrinth.openProject"));
        openProject.setOnAction(event -> onModSelected.accept(project));
        javafx.scene.control.ContextMenu menu = new javafx.scene.control.ContextMenu(openProject);
        menuButton.setOnAction(event -> menu.show(menuButton, javafx.geometry.Side.BOTTOM, 0, 0));

        Button installButton = new Button(Localization.text("common.install"));
        installButton.getStyleClass().add("primary-button");
        installButton.setPrefWidth(110);
        installButton.setOnAction(event -> {
            event.consume();
            install(project, installButton);
        });

        return new dev.itsdropper.vanta.ui.components.ModrinthProjectCard(
                iconBox, title, author, description, projectTags,
                project.getDownloads(), project.getFollows(),
                relativeUpdateTime(project.getDateModified()),
                menuButton, installButton, browserToolbar.isGridLayout(),
                () -> onModSelected.accept(project));
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

    private String colorToCss(javafx.scene.paint.Color color) {
        return String.format(java.util.Locale.ROOT, "#%02X%02X%02X",
                (int) Math.round(color.getRed() * 255),
                (int) Math.round(color.getGreen() * 255),
                (int) Math.round(color.getBlue() * 255));
    }
    // =============================================================
    // LOAD ICON
    // =============================================================

    private void loadIcon(
            ModrinthProject project,
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
            long days = Math.max(0, java.time.Duration.between(
                    java.time.Instant.parse(value), java.time.Instant.now()).toDays());
            if (days == 0) return Localization.text("modrinth.updatedToday");
            if (days == 1) return Localization.text("modrinth.updatedYesterday");
            if (days < 30) return Localization.text("modrinth.updatedDaysAgo").replace("{count}", Long.toString(days));
            if (days < 365) return Localization.text("modrinth.updatedMonthsAgo").replace("{count}", Long.toString(days / 30));
            return Localization.text("modrinth.updatedYearsAgo").replace("{count}", Long.toString(days / 365));
        } catch (java.time.DateTimeException ignored) {
            return Localization.text("modrinth.updateUnavailable");
        }
    }

    // =============================================================
    // INSTALL
    // =============================================================

    private void install(
            ModrinthProject project,
            Button button
    ) {

        button.setDisable(
                true
        );

        button.setText(
                Localization.text("common.installing")
        );

        statusLabel.setText(
                Localization.text("common.installingPrefix")
                        + project.getTitle()
                        + "..."
        );

        UiExecutor.shared().execute(() -> {

                    try {

                        modrinthService.installMod(
                                instance,
                                project
                        );

                        UiExecutor.shared().dispatchToUi(() -> {

                            button.setText(
                                    Localization.text("common.installed")
                            );

                            statusLabel.setText(
                                    project.getTitle()
                                            + " installed successfully."
                            );
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        UiExecutor.shared().dispatchToUi(() -> {

                            button.setDisable(
                                    false
                            );

                            button.setText(
                                    Localization.text("common.install")
                            );

                            statusLabel.setText(
                                    ex.getMessage() != null
                                            ? ex.getMessage()
                                            : "Failed to install mod."
                            );
                        });
                    }
        });


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

