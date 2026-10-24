package dev.itsdropper.vanta.launcher.repair;

import dev.itsdropper.vanta.ui.views.RepairView;

import java.util.List;
import java.util.Locale;

/**
 * Chooses a conservative repair operation from the available evidence.
 * A managed-file reinstall is useful for damaged installations, but is not
 * a valid fix for authentication, graphics, disk, network or mod failures.
 */
public final class RepairCoordinator {

    private RepairCoordinator() {}

    public static boolean shouldRepairManagedInstallation(List<RepairView.RepairIssue> issues) {
        if (issues == null || issues.isEmpty()) return true;

        boolean sawSpecificIssue = false;
        for (RepairView.RepairIssue issue : issues) {
            if (issue == null) continue;
            String signature = ((issue.type() == null ? "" : issue.type()) + " "
                    + (issue.title() == null ? "" : issue.title()) + " "
                    + (issue.details() == null ? "" : issue.details()))
                    .toLowerCase(Locale.ROOT);

            if (signature.contains("authentication") || signature.contains("invalid session")
                    || signature.contains("expired token") || signature.contains("graphics")
                    || signature.contains("opengl") || signature.contains("disk space")
                    || signature.contains("no space left") || signature.contains("permission")
                    || signature.contains("access denied") || signature.contains("network")
                    || signature.contains("timed out") || signature.contains("http 429")
                    || signature.contains("mixin") || signature.contains("duplicate mod")
                    || signature.contains("mod crash") || signature.contains("mod initialization")
                    || signature.contains("jvm argument") || signature.contains("unsupported java")
                    || signature.contains("java version") || signature.contains("configuration")) {
                sawSpecificIssue = true;
                continue;
            }

            if (signature.contains("missing library") || signature.contains("corrupt library")
                    || signature.contains("missing asset") || signature.contains("native")
                    || signature.contains("classpath") || signature.contains("installation")
                    || signature.contains("corrupt archive") || signature.contains("loader metadata")
                    || signature.contains("general diagnostic") || signature.contains("unknown")) {
                return true;
            }
        }

        // Unknown failures receive a non-destructive integrity pass. Explicitly
        // identified non-installation failures do not trigger a pointless reinstall.
        return !sawSpecificIssue;
    }

    public static String explainNoAutomaticRepair(List<RepairView.RepairIssue> issues) {
        if (issues == null || issues.isEmpty()) {
            return "Vanta could not identify a safe automatic fix. Review the diagnosis and logs.";
        }
        RepairView.RepairIssue issue = issues.get(0);
        String details = issue == null ? "" : issue.details();
        return "No installation repair was run because the detected problem is not fixed by reinstalling Minecraft files. "
                + (details == null || details.isBlank() ? "Follow the suggested diagnostic steps." : details);
    }
}
