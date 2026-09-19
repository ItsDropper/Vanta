package org.example.ui.views;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import org.example.launcher.model.Instance;

import java.util.List;

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

        this.instance = instance;
        this.onBack = onBack;
        this.onRepair = onRepair;

        getStyleClass().add(
                "page"
        );

        setFillWidth(
                true
        );

        // ---------------------------------------------------------
        // HEADER
        // ---------------------------------------------------------

        Button backButton =
                new Button(
                        "← BACK"
                );

        backButton.getStyleClass().add(
                "secondary-button"
        );

        backButton.setOnAction(
                event -> onBack.run()
        );

        Label titleLabel =
                new Label(
                        title
                );

        titleLabel.getStyleClass().add(
                "page-title"
        );

        Label descriptionLabel =
                new Label(
                        description
                );

        descriptionLabel.setWrapText(
                true
        );

        descriptionLabel.getStyleClass().add(
                "page-subtitle"
        );

        VBox headerText =
                new VBox(
                        6,
                        titleLabel,
                        descriptionLabel
                );

        VBox header =
                new VBox(
                        16,
                        backButton,
                        headerText
                );

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
                            "No specific issues were detected."
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

        // ---------------------------------------------------------
        // ACTIONS
        // ---------------------------------------------------------

        Label statusLabel =
                new Label(
                        "Vanta can attempt to repair these problems automatically."
                );

        statusLabel.setWrapText(
                true
        );

        statusLabel.getStyleClass().add(
                "repair-status"
        );

        Button cancelButton =
                new Button(
                        "BACK"
                );

        cancelButton.getStyleClass().add(
                "secondary-button"
        );

        cancelButton.setOnAction(
                event -> onBack.run()
        );

        Button repairButton =
                new Button(
                        "REPAIR AUTOMATICALLY"
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
                        cancelButton,
                        repairButton
                );

        actions.setAlignment(
                Pos.CENTER_RIGHT
        );

        HBox.setHgrow(
                statusLabel,
                Priority.ALWAYS
        );

        // ---------------------------------------------------------
        // CONTENT
        // ---------------------------------------------------------

        VBox content =
                new VBox(
                        28,
                        header,
                        issueSection,
                        actions
                );

        content.setPadding(
                new Insets(36)
        );

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
            String details
    ) {
    }


}
