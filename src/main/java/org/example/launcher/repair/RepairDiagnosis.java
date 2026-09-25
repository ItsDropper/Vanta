package org.example.launcher.repair;

import java.util.List;

public record RepairDiagnosis(
        FailureCategory category,
        RepairConfidence confidence,
        String title,
        String description,
        String evidence,
        List<String> suggestedActions
) {

    public RepairDiagnosis {

        if (category == null) {
            category = FailureCategory.UNKNOWN;
        }

        if (confidence == null) {
            confidence = RepairConfidence.UNSAFE;
        }

        if (title == null || title.isBlank()) {
            title = "Unknown Minecraft problem";
        }

        if (description == null) {
            description = "";
        }

        if (evidence == null) {
            evidence = "";
        }

        if (suggestedActions == null) {
            suggestedActions = List.of();
        } else {
            suggestedActions =
                    List.copyOf(
                            suggestedActions
                    );
        }
    }
}