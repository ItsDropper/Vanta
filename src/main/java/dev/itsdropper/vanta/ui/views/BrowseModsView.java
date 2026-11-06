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
                "Browse Mods",
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
                gridLayout -> {
                    results.getStyleClass().removeAll("modrinth-grid-results", "modrinth-list-results");
                    results.getStyleClass().add(gridLayout
                            ? "modrinth-grid-results" : "modrinth-list-results");
                    applyResultLayout(gridLayout);
                }
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
        results.getStyleClass().add("instance-list");

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

        getChildren().addAll(
                header,
                browserToolbar,
                popularTitle,
                popularScrollPane,
                popularStatusLabel,
                searchScrollPane,
                statusLabel
        );

        // Search results are hidden initially.
        searchScrollPane.setVisible(
                false
        );

        searchScrollPane.setManaged(
                false
        );

        // =========================================================
        // LOAD POPULAR MODS
        // =========================================================

        loadMostDownloadedMods();
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
                            ? " popular mods • checking version compatibility..."
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
                                + " downloads"
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

        if (query.isBlank()) {
            showPopularSection();
            results.getChildren().clear();
            statusLabel.setText(Localization.text("browseMods.searchPromptHelp"));
            browserToolbar.setTotalHits(0);
            return;
        }

        hidePopularSection();
        statusLabel.setText(Localization.text("common.searching"));
        results.getChildren().clear();

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
                    if (results.getChildren().isEmpty()) {
                        statusLabel.setText(failure.getMessage() == null
                                ? "Failed to search Modrinth." : failure.getMessage());
                    }
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

        results
                .getChildren()
                .clear();

        if (projects == null
                || projects.isEmpty()) {

            statusLabel.setText(
                    Localization.text("browseMods.noCompatible")
            );

            return;
        }

        for (
                ModrinthProject project
                : projects
        ) {

            results.getChildren().add(createSizedProjectCard(project));
        }

        statusLabel.setText(
                projects.size()
                        + (checkingCompatibility
                            ? " results • checking Minecraft version compatibility..."
                            : " compatible projects found.")
        );
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

    private VBox createProjectCard(
            ModrinthProject project
    ) {

        // =========================================================
        // ICON
        // =========================================================

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

        VBox iconBox =
                new VBox(
                        icon
                );

        iconBox.setAlignment(
                Pos.CENTER
        );

        iconBox.setMinSize(
                52,
                52
        );

        iconBox.setPrefSize(
                52,
                52
        );

        iconBox.setMaxSize(
                52,
                52
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
                650
        );

        description.getStyleClass().add(
                "instance-version"
        );

        // =========================================================
        // METADATA
        // =========================================================

        Label metadata =
                new Label(
                        Localization.text("common.modrinthPrefix")
                                + instance.getMinecraftVersion()
                                + " • "
                                + instance.getDisplayLoader()
                                + " • "
                                + formatNumber(
                                project.getDownloads()
                        )
                                + " downloads"
                );

        metadata.getStyleClass().add(
                "instance-loader"
        );

        // =========================================================
        // INSTALL BUTTON
        // =========================================================

        Button installButton =
                new Button(
                        Localization.text("common.install")
                );

        installButton.getStyleClass().add(
                "instance-play-button"
        );

        installButton.setPrefWidth(
                110
        );

        installButton.setOnAction(
                event -> {

                    event.consume();

                    install(
                            project,
                            installButton
                    );
                }
        );

        // =========================================================
        // INFORMATION
        // =========================================================

        VBox information =
                new VBox(
                        6,
                        title,
                        description,
                        metadata
                );

        HBox.setHgrow(
                information,
                Priority.ALWAYS
        );

        // =========================================================
        // ROW
        // =========================================================

        HBox row =
                new HBox(
                        16,
                        iconBox,
                        information,
                        installButton
                );

        row.setAlignment(
                Pos.CENTER_LEFT
        );

        // =========================================================
        // CARD
        // =========================================================

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

                        onModSelected.accept(
                                project
                        );
                    }
                }
        );

        return card;
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

