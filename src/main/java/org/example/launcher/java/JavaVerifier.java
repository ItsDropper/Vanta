package org.example.launcher.java;

import java.nio.file.Path;

public class JavaVerifier {

    public static void verify(
            Path java,
            int expectedVersion
    ) throws Exception {

        Process process =
                new ProcessBuilder(
                        java.toString(),
                        "-version"
                )
                        .redirectErrorStream(true)
                        .start();

        String output =
                new String(
                        process.getInputStream()
                                .readAllBytes()
                );

        int exitCode =
                process.waitFor();

        if (exitCode != 0) {

            throw new IllegalStateException(
                    "Java failed verification:\n"
                            + output
            );
        }

        int actualVersion =
                parseVersion(output);

        if (actualVersion != expectedVersion) {

            throw new IllegalStateException(
                    "Java version mismatch.\n"
                            + "Expected: Java "
                            + expectedVersion
                            + "\n"
                            + "Found: Java "
                            + actualVersion
                            + "\n\n"
                            + output.trim()
            );
        }

        System.out.println(
                "Java verification successful: Java "
                        + actualVersion
                        + "\n"
                        + output.trim()
        );
    }

    public static int parseVersion(
            String output
    ) {

        java.util.regex.Matcher matcher =
                java.util.regex.Pattern
                        .compile(
                                "version \"(?:1\\.)?(\\d+)"
                        )
                        .matcher(output);

        if (!matcher.find()) {

            throw new IllegalStateException(
                    "Could not determine Java version from:\n"
                            + output
            );
        }

        return Integer.parseInt(
                matcher.group(1)
        );
    }
}