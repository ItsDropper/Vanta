package org.example.launcher.service;

import org.example.ui.views.RepairView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LaunchFailureParser {

    private static final Pattern MISSING_DEPENDENCY =
            Pattern.compile(
                    "Mod '([^']+)' \\(([^)]+)\\) .*?requires version (.+?) of ([^,]+), which is missing!"
            );

    private static final Pattern WRONG_VERSION =
            Pattern.compile(
                    "([^ ]+) .*?requires version (.+?) of mod '([^']+)' \\(([^)]+)\\), but only the wrong version is present: ([^!]+)!"
            );

    public static List<RepairView.RepairIssue> parse(
            String output
    ) {

        List<RepairView.RepairIssue> issues =
                new ArrayList<>();

        if (output == null || output.isBlank()) {
            return issues;
        }

        /*
         * Example:
         *
         * Mod 'Nvidium' (nvidium) requires version
         * 0.8.11 or version 0.8.12 of sodium,
         * which is missing!
         */
        Matcher missing =
                MISSING_DEPENDENCY.matcher(output);

        while (missing.find()) {

            String requestingMod =
                    missing.group(1);

            String requestingModId =
                    missing.group(2);

            String requiredVersions =
                    missing.group(3).trim();

            String dependencyProjectId =
                    missing.group(4).trim();

            List<String> versions =
                    extractVersions(
                            requiredVersions
                    );

            String details =
                    "Recommended version: "
                            + formatVersions(versions)
                            + "\nDependency: "
                            + dependencyProjectId
                            + "\nMod ID: "
                            + requestingModId;

            issues.add(
                    new RepairView.RepairIssue(
                            "MISSING DEPENDENCY",
                            requestingMod
                                    + " requires "
                                    + dependencyProjectId,
                            details,
                            dependencyProjectId,
                            versions
                    )
            );
        }

        /*
         * Example:
         *
         * Nvidium requires version 0.8.11 of mod
         * 'Sodium' (sodium), but only the wrong version
         * is present: 0.8.14!
         */
        Matcher wrong =
                WRONG_VERSION.matcher(output);

        while (wrong.find()) {

            String requestingMod =
                    wrong.group(1);

            String requiredVersions =
                    wrong.group(2).trim();

            String dependencyName =
                    wrong.group(3);

            String dependencyId =
                    wrong.group(4);

            String installedVersion =
                    wrong.group(5).trim();

            List<String> versions =
                    extractVersions(
                            requiredVersions
                    );

            String details =
                    "Required version: "
                            + formatVersions(versions)
                            + "\nInstalled version: "
                            + installedVersion
                            + "\nMod ID: "
                            + dependencyId;

            issues.add(
                    new RepairView.RepairIssue(
                            "DEPENDENCY CONFLICT",
                            requestingMod
                                    + " requires "
                                    + dependencyName,
                            details,
                            dependencyId,
                            versions
                    )
            );
        }

        return issues;
    }

    private static List<String> extractVersions(
            String text
    ) {

        if (text == null || text.isBlank()) {
            return List.of();
        }

        return Arrays.stream(
                        text.split("\\s+or\\s+")
                )
                .map(String::trim)
                .map(version ->
                        version.replaceFirst(
                                "^version\\s+",
                                ""
                        )
                )
                .filter(version ->
                        !version.isBlank()
                )
                .toList();
    }

    private static String formatVersions(
            List<String> versions
    ) {

        if (versions == null
                || versions.isEmpty()) {

            return "Unknown";
        }

        if (versions.size() == 1) {
            return versions.get(0);
        }

        if (versions.size() == 2) {
            return versions.get(0)
                    + " or "
                    + versions.get(1);
        }

        return String.join(
                ", ",
                versions
        );
    }
}