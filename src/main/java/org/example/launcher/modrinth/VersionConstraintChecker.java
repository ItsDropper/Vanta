package org.example.launcher.modrinth;

public class VersionConstraintChecker {

    public static boolean matches(
            String version,
            String constraint
    ) {
        if (version == null || constraint == null) {
            return false;
        }

        constraint = constraint.trim();

        if (constraint.isEmpty()) {
            return true;
        }

        // Fabric allows alternatives:
        // "0.8.11 || 0.8.12"
        String[] alternatives =
                constraint.split("\\|\\|");

        for (String alternative : alternatives) {

            if (matchesSingle(
                    version.trim(),
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

        // Exact version
        if (!constraint.contains(" ")
                && !constraint.startsWith(">=")
                && !constraint.startsWith("<=")
                && !constraint.startsWith(">")
                && !constraint.startsWith("<")
                && !constraint.startsWith("=")
                && !constraint.startsWith("^")
                && !constraint.startsWith("~")) {

            return version.equals(constraint);
        }

        // Multiple space-separated requirements
        String[] parts =
                constraint.split("\\s+");

        for (String part : parts) {

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

        return version.equals(requirement);
    }

    private static int compare(
            String a,
            String b
    ) {

        String[] aParts = a.split("\\.");
        String[] bParts = b.split("\\.");

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

        return Integer.parseInt(
                digits.toString()
        );
    }
}