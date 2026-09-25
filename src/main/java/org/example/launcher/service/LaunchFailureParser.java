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
                    "Mod '([^']+)' \\(([^)]+)\\).*?requires version (.+?) of ([^,]+), which is missing!"
            );

    private static final Pattern WRONG_VERSION =
            Pattern.compile(
                    "([^ ]+) .*?requires version (.+?) of mod '([^']+)' \\(([^)]+)\\), but only the wrong version is present: ([^!]+)!"
            );

    private static final Pattern INCOMPATIBLE_MODS =
            Pattern.compile(
                    "Mod '([^']+)' \\(([^)]+)\\).*?"
                            + "is incompatible with version (.+?) of mod '([^']+)' \\(([^)]+)\\),"
                            + " yet a conflicting version is present: ([^!]+)!"
            );

    private static final Pattern MINECRAFT_VERSION_CONFLICT =
            Pattern.compile(
                    "Mod '([^']+)' \\(([^)]+)\\)\\s+[^ ]+"
                            + "\\s+requires any version between "
                            + "([0-9]+\\.[0-9]+\\.[0-9]+)-"
                            + ".*?of 'Minecraft' \\(minecraft\\),"
                            + " but only the wrong version is present: ([^!]+)!"
            );

    private static final Pattern MOD_REQUIRES_MINECRAFT_VERSION =
            Pattern.compile(
                    "Mod '([^']+)' \\(([^)]+)\\).*?"
                            + "requires .*? of 'Minecraft' \\(minecraft\\),"
                            + " but only the wrong version is present: ([^!]+)!"
            );

    private static final Pattern MOD_MINECRAFT_VERSION_CONFLICT =
            Pattern.compile(
                    "Mod '([^']+)' \\(([^)]+)\\)\\s+([^\\s]+)"
                            + "\\s+requires .*?'Minecraft' \\(minecraft\\),"
                            + "\\s+but only the wrong version is present:\\s*([^!]+)!"
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
         * Fabric Loader:
         *
         * Mod 'Fabric API' (fabric-api) 0.155.3+26.1.2
         * requires any 26.1.x version of 'Minecraft' (minecraft),
         * but only the wrong version is present: 1.21.11!
         *
         * The safe repair here is to repair the mod, not upgrade
         * the instance's Minecraft version.
         */
        Matcher modMinecraftVersion =
                MOD_MINECRAFT_VERSION_CONFLICT.matcher(output);

        while (modMinecraftVersion.find()) {

            String requestingMod =
                    modMinecraftVersion.group(1);

            String requestingModId =
                    modMinecraftVersion.group(2);

            String installedMinecraft =
                    modMinecraftVersion.group(4).trim();

            String details =
                    modMinecraftVersion.group(0)
                            + "\nMod ID: "
                            + requestingModId
                            + "\nInstalled Minecraft: "
                            + installedMinecraft;

            issues.add(
                    new RepairView.RepairIssue(
                            "MOD DEPENDENCY",
                            requestingMod
                                    + " is incompatible with Minecraft",
                            details,
                            requestingModId,
                            List.of()
                    )
            );
        }

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

        Matcher incompatible =
                INCOMPATIBLE_MODS.matcher(output);

        while (incompatible.find()) {

            String requestingMod =
                    incompatible.group(1);

            String requestingModId =
                    incompatible.group(2);

            String requiredVersions =
                    incompatible.group(3).trim();

            String conflictingMod =
                    incompatible.group(4);

            String conflictingModId =
                    incompatible.group(5);

            String installedVersion =
                    incompatible.group(6).trim();

            List<String> versions =
                    extractVersions(
                            requiredVersions
                    );

            String details =
                    "Incompatible with: "
                            + conflictingMod
                            + "\n"
                            + "Installed version: "
                            + installedVersion
                            + "\n"
                            + "Required version: "
                            + formatVersions(versions)
                            + "\n"
                            + "Mod ID: "
                            + requestingModId
                            + "\n"
                            + "Conflicting Mod ID: "
                            + conflictingModId;

            issues.add(
                    new RepairView.RepairIssue(
                            "MOD INCOMPATIBILITY",
                            requestingMod
                                    + " conflicts with "
                                    + conflictingMod,
                            details,
                            conflictingModId,
                            versions
                    )
            );
        }

        Matcher minecraftVersion =
                MINECRAFT_VERSION_CONFLICT.matcher(output);

        while (minecraftVersion.find()) {

            String requestingMod =
                    minecraftVersion.group(1);

            String requestingModId =
                    minecraftVersion.group(2);

            String requiredVersion =
                    minecraftVersion.group(3).trim();

            String installedVersion =
                    minecraftVersion.group(4).trim();

            String details =
                    "Required Minecraft version: "
                            + requiredVersion
                            + "\nInstalled Minecraft version: "
                            + installedVersion
                            + "\nMod ID: "
                            + requestingModId;

            issues.add(
                    new RepairView.RepairIssue(
                            "MINECRAFT VERSION CONFLICT",
                            requestingMod
                                    + " requires Minecraft "
                                    + requiredVersion,
                            details,
                            null,
                            List.of(),
                            requiredVersion
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
