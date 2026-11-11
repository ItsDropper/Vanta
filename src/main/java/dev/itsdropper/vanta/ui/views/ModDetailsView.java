package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
import dev.itsdropper.vanta.ui.async.UiExecutor;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import dev.itsdropper.vanta.ui.markdown.MarkdownRenderer;

import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.modrinth.ModrinthClient;
import dev.itsdropper.vanta.launcher.modrinth.ModrinthContentType;
import dev.itsdropper.vanta.launcher.modrinth.ModrinthProject;
import dev.itsdropper.vanta.launcher.modrinth.ModrinthTag;
import dev.itsdropper.vanta.launcher.modrinth.ModrinthLoaderIds;
import dev.itsdropper.vanta.ui.components.ModrinthTagIcon;
import javafx.scene.paint.Color;
import dev.itsdropper.vanta.launcher.service.ModrinthContentService;
import dev.itsdropper.vanta.launcher.service.ModrinthService;
import dev.itsdropper.vanta.launcher.modrinth.ModrinthTeamMember;

import javax.imageio.ImageIO;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public class ModDetailsView extends VBox {

    private final Instance instance;

    private final ModrinthClient modrinthClient;
    private final ModrinthService modrinthService;
    private final ModrinthContentService contentService;

    private final HttpClient httpClient;

    private final String projectId;
    private final ModrinthContentType contentType;

    private final Runnable onBack;

    private final VBox content;
    private final Label statusLabel;
    private final AtomicBoolean installRunning = new AtomicBoolean();
    private final AtomicBoolean installCancellationRequested = new AtomicBoolean();
    private final AtomicReference<Thread> installWorker = new AtomicReference<>();

    public ModDetailsView(
            String projectId,
            Runnable onBack
    ) {

        this(
                null,
                projectId,
                ModrinthContentType.MOD,
                onBack
        );
    }

    public ModDetailsView(
            Instance instance,
            String projectId,
            Runnable onBack
    ) {

        this(
                instance,
                projectId,
                ModrinthContentType.MOD,
                onBack
        );
    }

    public ModDetailsView(
            Instance instance,
            String projectId,
            ModrinthContentType contentType,
            Runnable onBack
    ) {

        this.instance = instance;
        this.projectId = projectId;
        this.contentType = contentType;
        this.onBack = onBack;

        this.modrinthClient =
                new ModrinthClient();

        this.modrinthService =
                new ModrinthService();

        this.contentService =
                new ModrinthContentService();

        this.httpClient =
                HttpClient.newBuilder()
                        .followRedirects(
                                HttpClient.Redirect.NORMAL
                        )
                        .build();

        getStyleClass().add(
                "page"
        );

        dev.itsdropper.vanta.ui.ResponsiveLayout.bindPagePadding(this);

        setSpacing(
                20
        );

        // =========================================================
        // BACK
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

        // =========================================================
        // CONTENT
        // =========================================================

        content =
                new VBox(
                        20
                );

        content.setPadding(
                new Insets(
                        4,
                        0,
                        20,
                        0
                )
        );

        // =========================================================
        // SCROLL
        // =========================================================

        ScrollPane scrollPane =
                new ScrollPane(
                        content
                );

        scrollPane.setFitToWidth(
                true
        );

        scrollPane.setHbarPolicy(
                ScrollPane.ScrollBarPolicy.NEVER
        );

        scrollPane.setVbarPolicy(
                ScrollPane.ScrollBarPolicy.AS_NEEDED
        );

        scrollPane.getStyleClass().add(
                "settings-scroll"
        );

        VBox.setVgrow(
                scrollPane,
                Priority.ALWAYS
        );

        // =========================================================
        // STATUS
        // =========================================================

        statusLabel =
                new Label(
                        "Loading "
                                + contentType
                                .getDisplayName()
                                .toLowerCase()
                                + "..."
                );

        statusLabel.getStyleClass().add(
                "instances-status"
        );

        // =========================================================
        // BUILD
        // =========================================================

        getChildren().addAll(
                backButton,
                scrollPane,
                statusLabel
        );

        loadProject();
    }

    // =============================================================
    // LOAD PROJECT
    // =============================================================

    private void loadProject() {

        UiExecutor.shared().execute(() -> {

                    try {

                        ModrinthProject project =
                                modrinthClient.getProject(
                                        projectId
                                );

                        List<ModrinthTeamMember> teamMembers =
                                modrinthClient.getProjectTeamMembers(
                                        projectId
                                );

                        String author =
                                findAuthor(
                                        teamMembers
                                );
                        List<ModrinthTag> loadedLoaderTags;
                        List<ModrinthTag> loadedCategoryTags;
                        try {
                            loadedLoaderTags = modrinthClient.getLoaderTags(contentType);
                            loadedCategoryTags = modrinthClient.getCategoryTags(contentType);
                        } catch (Exception tagLoadFailure) {
                            // Project details remain useful if the optional tag catalogue is unavailable.
                            java.util.logging.Logger.getLogger(ModDetailsView.class.getName())
                                    .log(java.util.logging.Level.WARNING,
                                            "Could not load Modrinth tag metadata", tagLoadFailure);
                            loadedLoaderTags = List.of();
                            loadedCategoryTags = List.of();
                        }

                        // Capture immutable references for the UI-dispatch lambda.
                        final List<ModrinthTag> loaderTags = loadedLoaderTags;
                        final List<ModrinthTag> categoryTags = loadedCategoryTags;
                        UiExecutor.shared().dispatchToUi(
                                () ->
                                        showProject(
                                                project,
                                                author,
                                                loaderTags,
                                                categoryTags
                                        )
                        );

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        UiExecutor.shared().dispatchToUi(() ->
                                statusLabel.setText(
                                        ex.getMessage() != null
                                                ? ex.getMessage()
                                                : "Failed to load "
                                                  + contentType
                                                .getDisplayName()
                                                .toLowerCase()
                                                  + "."
                                )
                        );
                    }
        });


    }

    // =============================================================
    // SHOW PROJECT
    // =============================================================

    private void showProject(
            ModrinthProject project,
            String author,
            List<ModrinthTag> loaderTags,
            List<ModrinthTag> categoryTags
    ) {

        content.getChildren().clear();

        // =========================================================
        // ICON
        // =========================================================

        ImageView icon =
                new ImageView();

        icon.setFitWidth(
                96
        );

        icon.setFitHeight(
                96
        );

        icon.setPreserveRatio(
                true
        );

        icon.setSmooth(
                true
        );

        Rectangle clip =
                new Rectangle(
                        96,
                        96
                );

        clip.setArcWidth(
                22
        );

        clip.setArcHeight(
                22
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
                96,
                96
        );

        iconBox.setPrefSize(
                96,
                96
        );

        iconBox.setMaxSize(
                96,
                96
        );

        iconBox.getStyleClass().add(
                "mod-details-icon"
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
                        safe(
                                project.getTitle(),
                                "Unknown "
                                        + contentType
                                        .getDisplayName()
                        )
                );

        title.getStyleClass().add(
                "mod-details-title"
        );
        title.setMinWidth(0);
        title.setWrapText(true);

        // =========================================================
        // AUTHOR
        // =========================================================

        Label authorLabel =
                new Label(
                        "by "
                                + safe(
                                author,
                                "Unknown author"
                        )
                );

        authorLabel.getStyleClass().add(
                "mod-details-author"
        );

        // =========================================================
        // SHORT DESCRIPTION
        // =========================================================

        Label shortDescription =
                new Label(
                        safe(
                                project.getDescription(),
                                "No description available."
                        )
                );

        shortDescription.setWrapText(
                true
        );

        shortDescription.getStyleClass().add(
                "mod-details-description"
        );

        // =========================================================
        // MODRINTH PROJECT TAGS
        // =========================================================

        FlowPane projectTags = createProjectTags(project, loaderTags, categoryTags);

        // =========================================================
        // META / STATS
        // =========================================================

        Label downloadsValue =
                new Label(
                        formatNumber(
                                project.getDownloads()
                        )
                );

        downloadsValue.getStyleClass().add(
                "mod-details-stat-value"
        );

        Label downloadsLabel =
                new Label(
                        Localization.text("modDetails.downloads")
                );

        downloadsLabel.getStyleClass().add(
                "mod-details-stat-label"
        );

        VBox downloadsStat =
                new VBox(
                        2,
                        downloadsValue,
                        downloadsLabel
                );

        downloadsStat.setAlignment(
                Pos.CENTER_LEFT
        );

        Label followersValue =
                new Label(
                        formatNumber(
                                project.getFollows()
                        )
                );

        followersValue.getStyleClass().add(
                "mod-details-stat-value"
        );

        Label followersLabel =
                new Label(
                        Localization.text("modDetails.followers")
                );

        followersLabel.getStyleClass().add(
                "mod-details-stat-label"
        );

        VBox followersStat =
                new VBox(
                        2,
                        followersValue,
                        followersLabel
                );

        followersStat.setAlignment(
                Pos.CENTER_LEFT
        );

        FlowPane metadata = new FlowPane(
                32, 8,
                downloadsStat,
                followersStat
        );
        metadata.setAlignment(Pos.CENTER_LEFT);
        metadata.setMaxWidth(Double.MAX_VALUE);

        // =========================================================
        // HEADER INFORMATION
        // =========================================================

        VBox headerInformation =
                new VBox(
                        10,
                        title,
                        authorLabel,
                        shortDescription,
                        projectTags,
                        metadata
                );

        headerInformation.setFillWidth(
                true
        );
        headerInformation.setMinWidth(0);

        HBox.setHgrow(
                headerInformation,
                Priority.ALWAYS
        );

        // =========================================================
        // PROJECT HEADER
        // =========================================================

        HBox projectHeader =
                new HBox(
                        20,
                        iconBox,
                        headerInformation
                );

        projectHeader.setAlignment(
                Pos.TOP_LEFT
        );

        projectHeader.setFillHeight(
                true
        );

        // =========================================================
        // INSTALL
        // =========================================================

        Button installButton =
                new Button(
                        Localization.text("common.install")
                );

        installButton.getStyleClass().add(
                "instance-play-button"
        );

        installButton.setMinWidth(
                120
        );

        installButton.setPrefWidth(
                120
        );

        installButton.setMaxWidth(
                120
        );

        installButton.setMinHeight(
                38
        );

        installButton.setPrefHeight(
                38
        );

        installButton.setMaxHeight(
                38
        );

        installButton.setAlignment(
                Pos.CENTER
        );
        installButton.setAccessibleText("Install " + safe(project.getTitle(), "this project"));

        installButton.setOnAction(
                event ->
                        install(
                                project,
                                installButton
                        )
        );

        if (instance == null
                || contentType == ModrinthContentType.MODPACK) {

            if (instance == null) {

                installButton.setVisible(
                        false
                );

                installButton.setManaged(
                        false
                );
            }
        }

        // =========================================================
        // HEADER CONTAINER
        // =========================================================

        VBox headerContainer =
                new VBox(
                        16,
                        projectHeader,
                        installButton
                );

        headerContainer.setFillWidth(
                true
        );

        headerContainer.setPadding(
                new Insets(
                        22
                )
        );

        headerContainer.getStyleClass().add(
                "mod-details-header-container"
        );

        // =========================================================
        // DESCRIPTION
        // =========================================================

        VBox descriptionCard =
                createDescriptionCard(
                        project
                );

        // =========================================================
        // INFORMATION
        // =========================================================

        VBox informationCard =
                createInformationCard(
                        project,
                        author
                );

        // =========================================================
        // ADD
        // =========================================================

        content.getChildren().addAll(
                headerContainer,
                descriptionCard,
                informationCard
        );

        statusLabel.setText(
                ""
        );
    }

    private FlowPane createProjectTags(
            ModrinthProject project,
            List<ModrinthTag> loaderTags,
            List<ModrinthTag> categoryTags
    ) {
        FlowPane tags = new FlowPane(6, 6);
        tags.setAlignment(Pos.CENTER_LEFT);
        tags.setMaxWidth(Double.MAX_VALUE);
        tags.getStyleClass().add("mod-details-tags");

        String environment = formatEnvironment(project.getClientSide(), project.getServerSide());
        if (!environment.isBlank()) {
            Label chip = new Label(environment);
            chip.getStyleClass().addAll("modrinth-tag-chip", "modrinth-environment-chip");
            tags.getChildren().add(chip);
        }

        java.util.Map<String, ModrinthTag> loadersById = new java.util.LinkedHashMap<>();
        if (loaderTags != null) {
            for (ModrinthTag tag : loaderTags) {
                if (tag != null && tag.getName() != null) {
                    loadersById.putIfAbsent(ModrinthLoaderIds.normalize(tag.getName()), tag);
                }
            }
        }

        java.util.Map<String, ModrinthTag> categoriesById = new java.util.LinkedHashMap<>();
        if (categoryTags != null) {
            for (ModrinthTag tag : categoryTags) {
                if (tag != null && tag.getName() != null) {
                    categoriesById.putIfAbsent(tag.getName().toLowerCase(java.util.Locale.ROOT), tag);
                }
            }
        }

        java.util.LinkedHashSet<String> projectTagIds = new java.util.LinkedHashSet<>();
        if (project.getCategories() != null) projectTagIds.addAll(project.getCategories());
        java.util.Set<String> projectLoaderIds = new java.util.HashSet<>();
        if (project.getLoaders() != null) {
            projectTagIds.addAll(project.getLoaders());
            project.getLoaders().stream().filter(java.util.Objects::nonNull)
                    .map(ModrinthLoaderIds::normalize).forEach(projectLoaderIds::add);
        }

        for (String value : projectTagIds) {
            if (value == null || value.isBlank()) continue;
            String normalized = ModrinthLoaderIds.normalize(value);
            ModrinthTag loader = loadersById.get(normalized);
            Label chip = new Label(formatTagName(value));
            chip.getStyleClass().add("modrinth-tag-chip");
            if (loader != null || projectLoaderIds.contains(normalized)
                    || ModrinthLoaderIds.supportsSearchFacet(value)) {
                Color color = ModrinthTagIcon.loaderColor(value);
                chip.getStyleClass().add("modrinth-loader-chip");
                chip.setGraphic(new ModrinthTagIcon(loader, value, color, 12));
                String cssColor = String.format(java.util.Locale.ROOT, "#%02x%02x%02x",
                        (int) Math.round(color.getRed() * 255),
                        (int) Math.round(color.getGreen() * 255),
                        (int) Math.round(color.getBlue() * 255));
                chip.setStyle("-fx-text-fill: " + cssColor + ";");
            } else {
                ModrinthTag category = categoriesById.get(value.toLowerCase(java.util.Locale.ROOT));
                chip.setGraphic(new ModrinthTagIcon(category, value, Color.web("#9299A6"), 12));
            }
            tags.getChildren().add(chip);
        }

        return tags;
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

    private String formatTagName(String value) {
        if (value == null || value.isBlank()) return "";
        String normalized = value.replace('-', ' ').replace('_', ' ');
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    // =============================================================
    // DESCRIPTION
    // =============================================================

    private VBox createDescriptionCard(
            ModrinthProject project
    ) {

        Label sectionTitle =
                new Label(
                        Localization.text("common.description")
                );

        sectionTitle.getStyleClass().add(
                "settings-section-title"
        );

        String body =
                project.getBody();

        if (body == null
                || body.isBlank()) {

            body =
                    project.getDescription();
        }

        if (body == null
                || body.isBlank()) {

            body =
                    "No description available.";
        }

        MarkdownRenderer renderer =
                new MarkdownRenderer();

        VBox description =
                renderer.render(
                        body
                );

        VBox card =
                new VBox(
                        14,
                        sectionTitle,
                        description
                );

        card.setPadding(
                new Insets(22)
        );

        card.getStyleClass().add(
                "mod-details-description-card"
        );

        description.getStyleClass().add(
                "mod-details-markdown"
        );

        return card;
    }

    // =============================================================
    // PROJECT INFORMATION
    // =============================================================

    private VBox createInformationCard(
            ModrinthProject project,
            String author
    ) {

        Label sectionTitle =
                new Label(
                        Localization.text("modDetails.projectInfo")
                );

        sectionTitle.getStyleClass().add(
                "settings-section-title"
        );

        VBox information =
                new VBox(
                        12
                );

        addInformationRow(
                information,
                "PROJECT ID",
                project.getProjectId()
        );

        addInformationRow(
                information,
                "SLUG",
                project.getSlug()
        );

        String license =
                null;

        if (project.getLicense() != null) {

            license =
                    project
                            .getLicense()
                            .getName();

            if (license == null
                    || license.isBlank()) {

                license =
                        project
                                .getLicense()
                                .getId();
            }
        }

        addInformationRow(
                information,
                "LICENSE",
                license
        );

        addInformationRow(
                information,
                "AUTHOR",
                author
        );

        addInformationRow(
                information,
                Localization.text("modDetails.downloads"),
                formatNumber(
                        project.getDownloads()
                )
        );

        addInformationRow(
                information,
                Localization.text("modDetails.followers"),
                formatNumber(
                        project.getFollows()
                )
        );

        VBox card =
                new VBox(
                        14,
                        sectionTitle,
                        information
                );

        card.setPadding(
                new Insets(
                        22
                )
        );

        card.getStyleClass().add(
                "mod-details-card"
        );

        return card;
    }

    // =============================================================
    // INFORMATION ROW
    // =============================================================

    private void addInformationRow(
            VBox parent,
            String labelText,
            String valueText
    ) {

        Label label =
                new Label(
                        labelText
                );

        label.getStyleClass().add(
                "instance-setting-label"
        );

        Label value =
                new Label(
                        safe(
                                valueText,
                                "Unknown"
                        )
                );

        value.setWrapText(
                true
        );

        value.getStyleClass().add(
                "mod-details-info-value"
        );

        VBox row =
                new VBox(
                        4,
                        label,
                        value
                );

        parent.getChildren().add(
                row
        );
    }

    // =============================================================
    // INSTALL
    // =============================================================

    private void install(
            ModrinthProject project,
            Button button
    ) {
        if (instance == null || !installRunning.compareAndSet(false, true)) {
            return;
        }

        installCancellationRequested.set(false);
        button.setDisable(false);
        button.setText(Localization.text("common.cancelTitleCase"));
        button.setOnAction(event -> cancelInstall(button));
        statusLabel.setText("Resolving dependencies for " + project.getTitle() + "…");

        try {
            UiExecutor.shared().execute(() -> {
                Thread worker = Thread.currentThread();
                installWorker.set(worker);
                if (installCancellationRequested.get()) {
                    worker.interrupt();
                }

                try {
                    if (contentType == ModrinthContentType.MOD) {
                        modrinthService.installMod(instance, project, progress ->
                                UiExecutor.shared().dispatchToUi(() -> {
                                    if (installRunning.get()
                                            && !installCancellationRequested.get()) {
                                        statusLabel.setText(progress);
                                    }
                                }));
                    } else if (contentType == ModrinthContentType.RESOURCE_PACK) {
                        contentService.installResourcePack(instance, project);
                    } else if (contentType == ModrinthContentType.SHADER) {
                        contentService.installShader(instance, project);
                    } else {
                        throw new IllegalStateException("Unsupported content type: " + contentType);
                    }

                    UiExecutor.shared().dispatchToUi(() -> {
                        installRunning.set(false);
                        button.setDisable(true);
                        button.setText(Localization.text("common.installed"));
                        button.setOnAction(null);
                        statusLabel.setText(project.getTitle() + " installed successfully.");
                    });
                } catch (Throwable failure) {
                    boolean cancelled = installCancellationRequested.get()
                            || failure instanceof InterruptedException
                            || worker.isInterrupted();
                    if (cancelled) {
                        Thread.interrupted();
                    }
                    UiExecutor.shared().dispatchToUi(() -> {
                        installRunning.set(false);
                        button.setDisable(false);
                        button.setText(Localization.text("common.install"));
                        button.setOnAction(event -> install(project, button));
                        statusLabel.setText(cancelled
                                ? "Installation cancelled. Existing installed files were preserved."
                                : failure.getMessage() != null
                                        ? failure.getMessage()
                                        : "Failed to install " + project.getTitle() + ".");
                    });
                } finally {
                    installWorker.compareAndSet(worker, null);
                }
            });
        } catch (RuntimeException rejected) {
            installRunning.set(false);
            button.setDisable(false);
            button.setText(Localization.text("common.install"));
            button.setOnAction(event -> install(project, button));
            statusLabel.setText("Could not start installation: " + rejected.getMessage());
        }
    }

    private void cancelInstall(Button button) {
        if (!installRunning.get() || !installCancellationRequested.compareAndSet(false, true)) {
            return;
        }
        button.setDisable(true);
        button.setText("Cancelling…");
        statusLabel.setText("Cancelling installation and restoring the previous state…");
        Thread worker = installWorker.get();
        if (worker != null) {
            worker.interrupt();
        }
    }

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

                        if (image.isError()) {
                            return;
                        }

                        UiExecutor.shared().dispatchToUi(() ->
                                imageView.setImage(
                                        image
                                )
                        );

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

    // =============================================================
    // FIND AUTHOR
    // =============================================================

    private String findAuthor(
            List<ModrinthTeamMember> members
    ) {

        if (members == null
                || members.isEmpty()) {

            return null;
        }

        for (
                ModrinthTeamMember member
                : members
        ) {

            if (member == null) {
                continue;
            }

            if (!"Owner".equalsIgnoreCase(
                    member.getRole()
            )) {

                continue;
            }

            if (member.getUser() == null) {
                continue;
            }

            String displayName =
                    member
                            .getUser()
                            .getDisplayName();

            if (displayName != null
                    && !displayName.isBlank()) {

                return displayName;
            }

            String username =
                    member
                            .getUser()
                            .getUsername();

            if (username != null
                    && !username.isBlank()) {

                return username;
            }
        }

        for (
                ModrinthTeamMember member
                : members
        ) {

            if (member == null
                    || member.getUser() == null) {

                continue;
            }

            String displayName =
                    member
                            .getUser()
                            .getDisplayName();

            if (displayName != null
                    && !displayName.isBlank()) {

                return displayName;
            }

            String username =
                    member
                            .getUser()
                            .getUsername();

            if (username != null
                    && !username.isBlank()) {

                return username;
            }
        }

        return null;
    }
}