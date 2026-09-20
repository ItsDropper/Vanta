package org.example.launcher.modrinth;

public final class VersionConstraintChecker {

    private VersionConstraintChecker() {
    }

    public static boolean matches(
            String version,
            String constraint
    ) {
        if (version == null || constraint == null) {
            return false;
        }

        version = version.trim();
        constraint = constraint.trim();

        if (constraint.isEmpty() || "*".equals(constraint)) {
            return true;
        }

        String[] alternatives =
                constraint.split("\\|\\|");

        for (String alternative : alternatives) {

            if (matchesSingle(
                    version,
                    alternative.trim()
            )) {
                return true;
            }
        }

        return false;
    }

    private static boolean matchesSingle(
            String version,
            String constraint
    ) {
        if (constraint.isBlank()) {
            return true;
        }

        /*
         * Fabric constraints can contain multiple
         * space-separated requirements.
         */
        String[] parts =
                constraint.split("\\s+");

        for (String part : parts) {

            if (part.isBlank()) {
                continue;
            }

            if (!matchesOperator(
                    version,
                    part
            )) {
                return false;
            }
        }

        return true;
    }

    private static boolean matchesOperator(
            String version,
            String requirement
    ) {
        requirement = requirement.trim();

        if (requirement.contains("x")
                || requirement.contains("X")
                || requirement.contains("*")) {

            return matchesWildcard(
                    version,
                    requirement
            );
        }

        if (requirement.startsWith(">=")) {
            return compare(
                    version,
                    requirement.substring(2)
            ) >= 0;
        }

        if (requirement.startsWith("<=")) {
            return compare(
                    version,
                    requirement.substring(2)
            ) <= 0;
        }

        if (requirement.startsWith(">")) {
            return compare(
                    version,
                    requirement.substring(1)
            ) > 0;
        }

        if (requirement.startsWith("<")) {
            return compare(
                    version,
                    requirement.substring(1)
            ) < 0;
        }

        if (requirement.startsWith("=")) {
            return compare(
                    version,
                    requirement.substring(1)
            ) == 0;
        }

        if (requirement.startsWith("^")) {
            return matchesCaret(
                    version,
                    requirement.substring(1)
            );
        }

        if (requirement.startsWith("~")) {
            return matchesTilde(
                    version,
                    requirement.substring(1)
            );
        }

        return version.equals(requirement);
    }

    private static boolean matchesWildcard(
            String version,
            String requirement
    ) {
        String[] versionParts =
                version.split("\\.");

        String[] requirementParts =
                requirement
                        .replace('*', 'x')
                        .replace('X', 'x')
                        .split("\\.");

        for (int i = 0; i < requirementParts.length; i++) {

            String required =
                    requirementParts[i];

            if ("x".equalsIgnoreCase(required)) {
                return true;
            }

            if (i >= versionParts.length) {
                return false;
            }

            if (!versionParts[i].equals(
                    required
            )) {
                return false;
            }
        }

        return versionParts.length ==
                requirementParts.length;
    }

    private static boolean matchesCaret(
            String version,
            String minimum
    ) {
        if (compare(version, minimum) < 0) {
            return false;
        }

        String[] parts =
                minimum.split("\\.");

        if (parts.length == 0) {
            return false;
        }

        int major = number(parts[0]);

        if (major > 0) {
            return compare(
                    version,
                    (major + 1) + ".0.0"
            ) < 0;
        }

        if (parts.length > 1) {
            int minor = number(parts[1]);

            if (minor > 0) {
                return compare(
                        version,
                        "0." + (minor + 1) + ".0"
                ) < 0;
            }
        }

        if (parts.length > 2) {
            int patch = number(parts[2]);

            return compare(
                    version,
                    "0.0." + (patch + 1)
            ) < 0;
        }

        return true;
    }

    private static boolean matchesTilde(
            String version,
            String minimum
    ) {
        if (compare(version, minimum) < 0) {
            return false;
        }

        String[] parts =
                minimum.split("\\.");

        if (parts.length == 1) {
            return compare(
                    version,
                    (number(parts[0]) + 1) + ".0.0"
            ) < 0;
        }

        int major = number(parts[0]);
        int minor = number(parts[1]);

        return compare(
                version,
                major + "." + (minor + 1) + ".0"
        ) < 0;
    }

    private static int compare(
            String a,
            String b
    ) {
        if (a == null || b == null) {
            return 0;
        }

        String[] aParts =
                a.trim().split("\\.");

        String[] bParts =
                b.trim().split("\\.");

        int length =
                Math.max(
                        aParts.length,
                        bParts.length
                );

        for (int i = 0; i < length; i++) {

            int aValue =
                    i < aParts.length
                            ? number(aParts[i])
                            : 0;

            int bValue =
                    i < bParts.length
                            ? number(bParts[i])
                            : 0;

            if (aValue != bValue) {
                return Integer.compare(
                        aValue,
                        bValue
                );
            }
        }

        return 0;
    }

    private static int number(
            String value
    ) {
        if (value == null || value.isBlank()) {
            return 0;
        }

        StringBuilder digits =
                new StringBuilder();

        for (char c : value.toCharArray()) {

            if (Character.isDigit(c)) {
                digits.append(c);
            } else {
                break;
            }
        }

        if (digits.isEmpty()) {
            return 0;
        }

        try {
            return Integer.parseInt(
                    digits.toString()
            );
        } catch (NumberFormatException ignored) {
            return 0;
        }
    }
}