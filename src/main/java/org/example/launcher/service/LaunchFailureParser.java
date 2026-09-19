package org.example.launcher.service;

import org.example.ui.views.RepairView;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LaunchFailureParser {

    private static final Pattern INCOMPATIBLE_MOD =
            Pattern.compile(
                    "mod '([^']+)' \\(([^)]+)\\) ([^ ]+) with version ([^ ]+)"
            );

    private static final Pattern REQUIRES_VERSION =
            Pattern.compile(
                    "([^ ]+) .* requires version (.+?) of mod '([^']+)' \\(([^)]+)\\), but only the wrong version is present: ([^!]+)!"
            );

    private static final Pattern MISSING_MOD =
            Pattern.compile(
                    "Mod '([^']+)' \\(([^)]+)\\) is required"
            );

    public static List<RepairView.RepairIssue> parse(
            String output
    ) {

        List<RepairView.RepairIssue> issues =
                new ArrayList<>();

        if (output == null || output.isBlank()) {
            return issues;
        }

        Matcher incompatible =
                INCOMPATIBLE_MOD.matcher(output);

        if (incompatible.find()) {

            String modName =
                    incompatible.group(1);

            String modId =
                    incompatible.group(2);

            String installedVersion =
                    incompatible.group(4);

            issues.add(
                    new RepairView.RepairIssue(
                            "INCOMPATIBLE MOD",
                            modName
                                    + " (" + modId + ")",
                            "Installed version "
                                    + installedVersion
                                    + " is incompatible with another mod."
                    )
            );
        }

        Matcher requires =
                REQUIRES_VERSION.matcher(output);

        if (requires.find()) {

            String requestingMod =
                    requires.group(1);

            String requiredVersions =
                    requires.group(2);

            String dependencyName =
                    requires.group(3);

            String dependencyId =
                    requires.group(4);

            String installedVersion =
                    requires.group(5).trim();

            issues.add(
                    new RepairView.RepairIssue(
                            "DEPENDENCY CONFLICT",
                            requestingMod
                                    + " requires "
                                    + dependencyName,
                            "Required: "
                                    + requiredVersions
                                    + "\nInstalled: "
                                    + installedVersion
                                    + "\nMod ID: "
                                    + dependencyId
                    )
            );
        }

        Matcher missing =
                MISSING_MOD.matcher(output);

        if (missing.find()) {

            String modName =
                    missing.group(1);

            String modId =
                    missing.group(2);

            issues.add(
                    new RepairView.RepairIssue(
                            "MISSING DEPENDENCY",
                            modName,
                            "Required mod "
                                    + modId
                                    + " is not installed."
                    )
            );
        }

        return issues;
    }
}