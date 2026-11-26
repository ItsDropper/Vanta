package dev.itsdropper.vanta.ui.views;

import dev.itsdropper.vanta.ui.VantaUI;

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
import dev.itsdropper.vanta.ui.kit.Badge;
import dev.itsdropper.vanta.ui.kit.Callout;
import dev.itsdropper.vanta.ui.kit.Card;
import dev.itsdropper.vanta.ui.kit.SectionHeader;

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
                VantaUI.backButton();

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

            issueList.getChildren().add(new Callout(
                    Callout.Tone.SUCCESS,
                    Localization.text("repair.allClear"),
                    Localization.text("repair.noIssues")));

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
                        new SectionHeader(Localization.text("repair.problems")),
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
        SectionHeader title = new SectionHeader(Localization.text("repair.verifiedFixesTitle"),
                Localization.text("repair.verifiedFixesDescription"));
        VBox cards = new VBox(10);
        for (CrashCaseRepository.VerifiedFix fix : fixes) {
            Badge category = new Badge(fix.category().name().replace('_', ' ').toLowerCase(), Badge.Tone.INFO);
            Label note = new Label(fix.verifiedFix());
            note.setWrapText(true);
            Label seen = new Label(Localization.text("repair.verifiedFixesSeen")
                    .formatted(fix.occurrences()));
            seen.getStyleClass().add("muted");
            Card card = new Card(category, note, seen).padded(16).spaced(8);
            cards.getChildren().add(card);
        }
        return new VBox(12, title, cards);
    }

    private VBox createIssueCard(RepairIssue issue) {
        Badge type = new Badge(issue.type(), Badge.Tone.WARNING);
        Label title = new Label(issue.title());
        title.setWrapText(true);
        title.getStyleClass().add("repair-issue-title");
        Label details = new Label(issue.details());
        details.setWrapText(true);
        details.getStyleClass().add("repair-issue-details");
        HBox top = new HBox(10, type);
        top.setAlignment(Pos.CENTER_LEFT);
        Card card = new Card(top, title, details).padded(18).spaced(8);
        card.setMaxWidth(Double.MAX_VALUE);
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
