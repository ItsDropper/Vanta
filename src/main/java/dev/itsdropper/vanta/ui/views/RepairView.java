package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.Localization;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.repair.CrashCaseRepository;
import dev.itsdropper.vanta.ui.components.PageHeader;

import java.util.List;
import java.nio.file.Files;

public class RepairView extends VBox {


    private final Instance instance;
    private final Runnable onBack;
    private final Runnable onRepair;

    public RepairView(
            Instance instance,
            String title,
            String description,
            List<RepairIssue> issues,
            Runnable onBack,
            Runnable onRepair
    ) {
        this(instance, title, description, issues, onBack, onRepair, null, null, null);
    }

    public RepairView(
            Instance instance,
            String title,
            String description,
            List<RepairIssue> issues,
            Runnable onBack,
            Runnable onRepair,
            Runnable onRestore
    ) {
        this(instance, title, description, issues, onBack, onRepair, onRestore, null, null, List.of());
    }

    public RepairView(
            Instance instance,
            String title,
            String description,
            List<RepairIssue> issues,
            Runnable onBack,
            Runnable onRepair,
            Runnable onRestore,
            Runnable onSaveCrashCase,
            Runnable onReviewCrashCases
    ) {
        this(instance, title, description, issues, onBack, onRepair, onRestore,
                onSaveCrashCase, onReviewCrashCases, List.of());
    }

    public RepairView(
            Instance instance,
            String title,
            String description,
            List<RepairIssue> issues,
            Runnable onBack,
            Runnable onRepair,
            Runnable onRestore,
            Runnable onSaveCrashCase,
            Runnable onReviewCrashCases,
            List<CrashCaseRepository.VerifiedFix> verifiedFixes
    ) {

        this.instance = instance;
        this.onBack = onBack;
        this.onRepair = onRepair;

        getStyleClass().addAll(
                "page",
                "vanta-consistent-page",
                "repair-page"
        );

        setFillWidth(
                true
        );

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        Button backButton =
                new Button(
                        Localization.text("common.backArrow")
                );

        backButton.getStyleClass().add(
                "secondary-button"
        );

        backButton.setOnAction(
                event -> onBack.run()
        );

        PageHeader headerText = new PageHeader(
                title,
                description,
                "page-title",
                "page-subtitle"
        );

        VBox header =
                new VBox(
                        12,
                        backButton,
                        headerText
                );
        header.getStyleClass().add("repair-page-header");

        // ---------------------------------------------------------
        // ISSUE LIST
        // ---------------------------------------------------------

        VBox issueList =
                new VBox(
                        12
                );

        issueList.setFillWidth(
                true
        );

        if (issues == null || issues.isEmpty()) {

            Label empty =
                    new Label(
                            Localization.text("repair.noIssues")
                    );

            empty.getStyleClass().add(
                    "repair-empty"
            );

            issueList.getChildren().add(
                    empty
            );

        } else {

            for (RepairIssue issue : issues) {

                issueList.getChildren().add(
                        createIssueCard(
                                issue
                        )
                );
            }
        }

        VBox issueSection =
                new VBox(
                        12,
                        createSectionTitle(
                                "PROBLEMS FOUND"
                        ),
                        issueList
                );
        issueSection.getStyleClass().add("repair-issue-section");

        // ---------------------------------------------------------
        // ACTIONS
        // ---------------------------------------------------------

        Label statusLabel =
                new Label(
                        Localization.text("repair.description")
                );

        statusLabel.setWrapText(
                true
        );

        statusLabel.getStyleClass().add(
                "repair-status"
        );

        Button cancelButton =
                new Button(
                        Localization.text("common.back")
                );

        cancelButton.getStyleClass().add(
                "secondary-button"
        );

        cancelButton.setOnAction(
                event -> onBack.run()
        );

        Button repairButton =
                new Button(
                        Localization.text("repair.action")
                );

        repairButton.getStyleClass().add(
                "primary-button"
        );

        repairButton.setOnAction(
                event -> onRepair.run()
        );

        HBox actions =
                new HBox(
                        10,
                        statusLabel,
                        cancelButton
                );

        if (onRestore != null && instance != null && instance.getDirectory() != null
                && Files.isDirectory(instance.getDirectory().resolve(".vanta-repair-backups"))) {
            Button restoreButton = new Button(Localization.text("repair.restoreMetadata"));
            restoreButton.getStyleClass().add("secondary-button");
            restoreButton.setOnAction(event -> onRestore.run());
            actions.getChildren().add(restoreButton);
        }
        if (onReviewCrashCases != null) {
            Button reviewCasesButton = new Button(Localization.text("crashCases.review"));
            reviewCasesButton.getStyleClass().add("secondary-button");
            reviewCasesButton.setOnAction(event -> onReviewCrashCases.run());
            actions.getChildren().add(reviewCasesButton);
        }
        if (onSaveCrashCase != null) {
            Button saveCaseButton = new Button(Localization.text("crashCases.saveCurrent"));
            saveCaseButton.getStyleClass().add("secondary-button");
            saveCaseButton.setOnAction(event -> onSaveCrashCase.run());
            actions.getChildren().add(saveCaseButton);
        }
        actions.getChildren().add(repairButton);

        actions.setAlignment(
                Pos.CENTER_RIGHT
        );
        actions.getStyleClass().add("repair-actions");

        HBox.setHgrow(
                statusLabel,
                Priority.ALWAYS
        );

        // ---------------------------------------------------------
        // CONTENT
        // ---------------------------------------------------------

        VBox content = new VBox(28, header, issueSection);
        if (verifiedFixes != null && !verifiedFixes.isEmpty()) {
            content.getChildren().add(createVerifiedFixSection(verifiedFixes));
        }
        content.getChildren().add(actions);

        dev.itsdropper.vanta.ui.ResponsiveLayout.bindPagePadding(content);

        content.setFillWidth(
                true
        );

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

        getChildren().add(
                scrollPane
        );
    }

    private VBox createVerifiedFixSection(List<CrashCaseRepository.VerifiedFix> fixes) {
        Label title = new Label(Localization.text("repair.verifiedFixesTitle"));
        title.getStyleClass().add("settings-section-title");
        Label description = new Label(Localization.text("repair.verifiedFixesDescription"));
        description.setWrapText(true);
        VBox cards = new VBox(10);
        for (CrashCaseRepository.VerifiedFix fix : fixes) {
            Label category = new Label(fix.category().name());
            category.getStyleClass().add("repair-issue-type");
            Label note = new Label(fix.verifiedFix());
            note.setWrapText(true);
            Label seen = new Label(Localization.text("repair.verifiedFixesSeen")
                    .formatted(fix.occurrences()));
            seen.getStyleClass().add("muted");
            VBox card = new VBox(8, category, note, seen);
            card.setPadding(new Insets(16));
            card.getStyleClass().add("repair-issue-card");
            cards.getChildren().add(card);
        }
        return new VBox(12, title, description, cards);
    }

    private VBox createIssueCard(
            RepairIssue issue
    ) {

        Label type =
                new Label(
                        issue.type()
                );

        type.getStyleClass().add(
                "repair-issue-type"
        );

        Label title =
                new Label(
                        issue.title()
                );

        title.setWrapText(
                true
        );

        title.getStyleClass().add(
                "repair-issue-title"
        );

        Label details =
                new Label(
                        issue.details()
                );

        details.setWrapText(
                true
        );

        details.getStyleClass().add(
                "repair-issue-details"
        );

        VBox card =
                new VBox(
                        8,
                        type,
                        title,
                        details
                );

        card.setPadding(
                new Insets(20)
        );

        card.setMaxWidth(
                Double.MAX_VALUE
        );

        card.getStyleClass().add(
                "repair-issue-card"
        );

        return card;
    }

    private Label createSectionTitle(
            String text
    ) {

        Label label =
                new Label(
                        text
                );

        label.getStyleClass().add(
                "settings-section-title"
        );

        return label;
    }

    public Instance getInstance() {
        return instance;
    }

    public record RepairIssue(
            String type,
            String title,
            String details,
            String repairProjectId,
            List<String> repairVersions,
            String repairMinecraftVersion
    ) {

        public RepairIssue(
                String type,
                String title,
                String details
        ) {
            this(
                    type,
                    title,
                    details,
                    null,
                    List.of(),
                    null
            );
        }

        public RepairIssue(
                String type,
                String title,
                String details,
                String repairProjectId,
                List<String> repairVersions
        ) {
            this(
                    type,
                    title,
                    details,
                    repairProjectId,
                    repairVersions,
                    null
            );
        }

        public boolean canRepair() {
            return (
                    repairProjectId != null
                            && !repairProjectId.isBlank()
            ) || (
                    repairMinecraftVersion != null
                            && !repairMinecraftVersion.isBlank()
            );
        }

        public boolean canRepairMinecraftVersion() {

            return repairMinecraftVersion != null
                    && !repairMinecraftVersion.isBlank();
        }
    }


}
