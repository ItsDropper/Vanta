package org.example.ui.views;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;

import org.example.ui.markdown.MarkdownRenderer;

import org.example.launcher.model.Instance;
import org.example.launcher.modrinth.ModrinthClient;
import org.example.launcher.modrinth.ModrinthProject;
import org.example.launcher.service.ModrinthService;
import org.example.launcher.modrinth.ModrinthTeamMember;

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

public class ModDetailsView extends VBox {

    private final Instance instance;
    private final ModrinthClient modrinthClient;
    private final ModrinthService modrinthService;
    private final HttpClient httpClient;


    private final String projectId;
    private final Runnable onBack;

    private final VBox content;
    private final Label statusLabel;

    public ModDetailsView(
            String projectId,
            Runnable onBack
    ) {

        this(
                null,
                projectId,
                onBack
        );
    }

    public ModDetailsView(
            Instance instance,
            String projectId,
            Runnable onBack
    ) {

        this.instance =
                instance;

        this.projectId =
                projectId;

        this.onBack =
                onBack;

        this.modrinthClient =
                new ModrinthClient();

        this.modrinthService =
                new ModrinthService();

        this.httpClient =
                HttpClient.newHttpClient();

        getStyleClass().add(
                "page"
        );

        setPadding(
                new Insets(36)
        );

        setSpacing(
                20
        );

        // =========================================================
        // BACK
        // =========================================================

        Button backButton =
                new Button(
                        "← BACK"
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
                        "Loading mod..."
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

        Thread thread =
                new Thread(() -> {

                    try {

                        // -------------------------------------------------
                        // FULL PROJECT
                        // -------------------------------------------------

                        ModrinthProject project =
                                modrinthClient.getProject(
                                        projectId
                                );

                        // -------------------------------------------------
                        // TEAM MEMBERS
                        // -------------------------------------------------

                        List<ModrinthTeamMember> teamMembers =
                                modrinthClient.getProjectTeamMembers(
                                        projectId
                                );

                        // -------------------------------------------------
                        // RESOLVE AUTHOR
                        // -------------------------------------------------

                        String author =
                                findAuthor(
                                        teamMembers
                                );

                        Platform.runLater(
                                () ->
                                        showProject(
                                                project,
                                                author
                                        )
                        );

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() ->
                                statusLabel.setText(
                                        ex.getMessage() != null
                                                ? ex.getMessage()
                                                : "Failed to load mod."
                                )
                        );
                    }
                });

        thread.setDaemon(
                true
        );

        thread.start();
    }

    // =============================================================
    // SHOW PROJECT
    // =============================================================

    private void showProject(
            ModrinthProject project,
            String author
    ) {

        content
                .getChildren()
                .clear();

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
                                "Unknown Mod"
                        )
                );

        title.getStyleClass().add(
                "mod-details-title"
        );

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
                        "DOWNLOADS"
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
                        "FOLLOWERS"
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


        HBox metadata =
                new HBox(
                        32,
                        downloadsStat,
                        followersStat
                );

        metadata.setAlignment(
                Pos.CENTER_LEFT
        );


// =========================================================
// INSTALL
// =========================================================

        Button installButton =
                new Button(
                        "INSTALL"
                );

        if (instance == null) {

            installButton.setVisible(false);
            installButton.setManaged(false);
        }

        installButton.getStyleClass().add(
                "instance-play-button"
        );

        installButton.setPrefWidth(
                120
        );

        installButton.setOnAction(
                event ->
                        install(
                                project,
                                installButton
                        )
        );


        // =========================================================
        // HEADER INFORMATION
        // =========================================================

        VBox headerInformation =
                new VBox(
                        10,
                        title,
                        authorLabel,
                        shortDescription,
                        metadata
                );

        headerInformation.setFillWidth(
                true
        );

        HBox.setHgrow(
                headerInformation,
                Priority.ALWAYS
        );

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
                        headerInformation,
                        installButton
                );

        projectHeader.setAlignment(
                Pos.TOP_LEFT
        );

        projectHeader.setPadding(
                new Insets(
                        22
                )
        );

        projectHeader.getStyleClass().add(
                "mod-details-header"
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

        content
                .getChildren()
                .addAll(
                        projectHeader,
                        descriptionCard,
                        informationCard
                );

        statusLabel.setText(
                ""
        );
    }

    // =============================================================
    // DESCRIPTION
    // =============================================================

    private VBox createDescriptionCard(
            ModrinthProject project
    ) {

        Label sectionTitle =
                new Label(
                        "DESCRIPTION"
                );

        sectionTitle.getStyleClass().add(
                "settings-section-title"
        );

        String body =
                project.getBody();

        if (body == null || body.isBlank()) {

            body =
                    project.getDescription();
        }

        MarkdownRenderer renderer =
                new MarkdownRenderer();

        VBox description =
                renderer.render(body);

        VBox card =
                new VBox(
                        14,
                        sectionTitle,
                        description
                );

        card.setPadding(
                new Insets(
                        22,
                        0,
                        22,
                        0
                )
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
                        "PROJECT INFORMATION"
                );

        sectionTitle.getStyleClass().add(
                "settings-section-title"
        );

        VBox information =
                new VBox(
                        12
                );

        // ---------------------------------------------------------
        // PROJECT ID
        // ---------------------------------------------------------

        addInformationRow(
                information,
                "PROJECT ID",
                project.getProjectId()
        );

        // ---------------------------------------------------------
        // SLUG
        // ---------------------------------------------------------

        addInformationRow(
                information,
                "SLUG",
                project.getSlug()
        );

        // ---------------------------------------------------------
        // LICENSE
        // ---------------------------------------------------------

        String license =
                null;

        if (project.getLicense() != null) {

            license =
                    project.getLicense().getName();

            if (license == null
                    || license.isBlank()) {

                license =
                        project.getLicense().getId();
            }
        }

        addInformationRow(
                information,
                "LICENSE",
                license
        );

        // ---------------------------------------------------------
        // AUTHOR
        // ---------------------------------------------------------

        addInformationRow(
                information,
                "AUTHOR",
                author
        );

        // ---------------------------------------------------------
        // DOWNLOADS
        // ---------------------------------------------------------

        addInformationRow(
                information,
                "DOWNLOADS",
                formatNumber(
                        project.getDownloads()
                )
        );

        // ---------------------------------------------------------
        // FOLLOWERS
        // ---------------------------------------------------------

        addInformationRow(
                information,
                "FOLLOWERS",
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

        parent
                .getChildren()
                .add(
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

        button.setDisable(
                true
        );

        button.setText(
                "INSTALLING..."
        );

        statusLabel.setText(
                "Installing "
                        + project.getTitle()
                        + "..."
        );

        Thread thread =
                new Thread(() -> {

                    try {

                        modrinthService.installMod(
                                instance,
                                project
                        );

                        Platform.runLater(() -> {

                            button.setText(
                                    "INSTALLED"
                            );

                            statusLabel.setText(
                                    project.getTitle()
                                            + " installed successfully."
                            );
                        });

                    } catch (Throwable ex) {

                        ex.printStackTrace();

                        Platform.runLater(() -> {

                            button.setDisable(
                                    false
                            );

                            button.setText(
                                    "INSTALL"
                            );

                            statusLabel.setText(
                                    ex.getMessage() != null
                                            ? ex.getMessage()
                                            : "Failed to install mod."
                            );
                        });
                    }
                });

        thread.setDaemon(
                true
        );

        thread.start();
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

        Thread thread =
                new Thread(() -> {

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

                        Platform.runLater(() -> {

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

        thread.setDaemon(
                true
        );

        thread.start();
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

        // ---------------------------------------------------------
        // PREFER OWNER
        // ---------------------------------------------------------

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

        // ---------------------------------------------------------
        // FALLBACK TO FIRST VALID MEMBER
        // ---------------------------------------------------------

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