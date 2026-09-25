package org.example.launcher.repair;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

public final class RepairDiagnosticEngine {

    private static final Pattern MOD_DEPENDENCY =
            Pattern.compile(
                    "(requires|depends on).*?(mod|version)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern MISSING_FILE =
            Pattern.compile(
                    "(could not find|cannot find|file not found|no such file)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern CLASS_NOT_FOUND =
            Pattern.compile(
                    "(classnotfoundexception|noclassdeffounderror)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern UNSATISFIED_LINK =
            Pattern.compile(
                    "(unsatisfiedlinkerror|could not load.*native)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern JAVA_VERSION =
            Pattern.compile(
                    "(unsupportedclassversion|unsupported.*java version|requires java)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern JVM_ARGUMENT =
            Pattern.compile(
                    "(unrecognized vm option|invalid maximum heap size|could not create the java virtual machine)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern OUT_OF_MEMORY =
            Pattern.compile(
                    "(outofmemoryerror|out of memory|could not reserve enough space)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern DISK_SPACE =
            Pattern.compile(
                    "(not enough space|no space left|insufficient disk space|disk full)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern PERMISSION =
            Pattern.compile(
                    "(accessdeniedexception|permission denied|access is denied)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern NETWORK =
            Pattern.compile(
                    "(unknownhostexception|connectexception|connection timed out|connection refused|http 429|too many requests|network is unreachable)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern FABRIC_LOADER =
            Pattern.compile(
                    "(fabric loader|fabric-loader)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern MINECRAFT_VERSION =
            Pattern.compile(
                    "(incompatible.*minecraft|unsupported.*minecraft version|minecraft version.*not supported)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern CONFIGURATION =
            Pattern.compile(
                    "(invalid.*configuration|malformed.*json|json.*parse|parse.*json|invalid.*config)",
                    Pattern.CASE_INSENSITIVE
            );

    private RepairDiagnosticEngine() {
    }

    public static List<RepairDiagnosis> diagnose(
            String output
    ) {

        List<RepairDiagnosis> diagnoses =
                new ArrayList<>();

        if (output == null || output.isBlank()) {

            diagnoses.add(
                    unknown(
                            "Minecraft produced no diagnostic output."
                    )
            );

            return diagnoses;
        }

        String text =
                output.toLowerCase(
                        Locale.ROOT
                );

        /*
         * =========================================================
         * MOD DEPENDENCIES
         * =========================================================
         */

        if (MOD_DEPENDENCY.matcher(text).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.MOD_DEPENDENCY,
                            RepairConfidence.DETERMINISTIC,
                            "Mod dependency problem",
                            "A mod reported a missing or incompatible dependency.",
                            findEvidence(
                                    output,
                                    "requires",
                                    "depends on"
                            ),
                            List.of(
                                    "Resolve the installed mod dependency graph.",
                                    "Install the required dependency version.",
                                    "Remove an incompatible dependency only when the graph uniquely determines that it is safe."
                            )
                    )
            );
        }

        /*
         * =========================================================
         * MISSING FILE
         * =========================================================
         */

        if (MISSING_FILE.matcher(text).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.MISSING_LIBRARY,
                            RepairConfidence.AMBIGUOUS,
                            "Missing file",
                            "Minecraft attempted to access a file that was not available.",
                            findEvidence(
                                    output,
                                    "could not find",
                                    "cannot find",
                                    "file not found",
                                    "no such file"
                            ),
                            List.of(
                                    "Check managed Minecraft libraries and natives.",
                                    "Verify the instance installation.",
                                    "Redownload the file only when Vanta can identify it unambiguously."
                            )
                    )
            );
        }

        /*
         * =========================================================
         * CLASS NOT FOUND
         * =========================================================
         */

        if (CLASS_NOT_FOUND.matcher(text).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.CLASSPATH,
                            RepairConfidence.AMBIGUOUS,
                            "Missing class or library",
                            "Minecraft could not load a required Java class.",
                            findEvidence(
                                    output,
                                    "classnotfoundexception",
                                    "noclassdeffounderror"
                            ),
                            List.of(
                                    "Check the resolved Minecraft classpath.",
                                    "Verify the corresponding library.",
                                    "Check for a missing or incompatible mod dependency."
                            )
                    )
            );
        }

        /*
         * =========================================================
         * NATIVE
         * =========================================================
         */

        if (UNSATISFIED_LINK.matcher(text).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.NATIVE,
                            RepairConfidence.DETERMINISTIC,
                            "Native library problem",
                            "Minecraft failed to load a native library.",
                            findEvidence(
                                    output,
                                    "unsatisfiedlinkerror",
                                    "could not load"
                            ),
                            List.of(
                                    "Verify the native libraries.",
                                    "Re-extract the required natives.",
                                    "Redownload the corresponding library if it is missing or corrupt."
                            )
                    )
            );
        }

        /*
         * =========================================================
         * JAVA VERSION
         * =========================================================
         */

        if (JAVA_VERSION.matcher(text).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.JAVA_VERSION,
                            RepairConfidence.DETERMINISTIC,
                            "Java version problem",
                            "The selected Java runtime is incompatible with this Minecraft version or application.",
                            findEvidence(
                                    output,
                                    "unsupportedclassversion",
                                    "requires java",
                                    "unsupported"
                            ),
                            List.of(
                                    "Determine the Java version required by the instance.",
                                    "Select the matching Vanta-managed Java runtime."
                            )
                    )
            );
        }

        /*
         * =========================================================
         * JVM ARGUMENTS
         * =========================================================
         */

        if (JVM_ARGUMENT.matcher(text).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.JVM_ARGUMENTS,
                            RepairConfidence.DETERMINISTIC,
                            "Invalid Java arguments",
                            "The Java virtual machine rejected one of the configured JVM arguments.",
                            findEvidence(
                                    output,
                                    "unrecognized vm option",
                                    "invalid maximum heap size",
                                    "could not create the java virtual machine"
                            ),
                            List.of(
                                    "Identify the rejected JVM argument.",
                                    "Remove or correct the invalid argument.",
                                    "Preserve unrelated user arguments."
                            )
                    )
            );
        }

        /*
         * =========================================================
         * MEMORY
         * =========================================================
         */

        if (OUT_OF_MEMORY.matcher(text).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.JVM_ARGUMENTS,
                            RepairConfidence.AMBIGUOUS,
                            "Memory allocation problem",
                            "Java was unable to allocate the requested memory.",
                            findEvidence(
                                    output,
                                    "outofmemoryerror",
                                    "out of memory",
                                    "could not reserve enough space"
                            ),
                            List.of(
                                    "Check the configured maximum heap size.",
                                    "Check available system memory.",
                                    "Do not automatically change the user's RAM setting unless the required correction is unambiguous."
                            )
                    )
            );
        }

        /*
         * =========================================================
         * LOADER
         * =========================================================
         */

        if (FABRIC_LOADER.matcher(text).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.LOADER,
                            RepairConfidence.AMBIGUOUS,
                            "Loader problem",
                            "The Minecraft loader reported an error.",
                            findEvidence(
                                    output,
                                    "fabric loader",
                                    "fabric-loader"
                            ),
                            List.of(
                                    "Verify the configured loader version.",
                                    "Verify that the loader matches the Minecraft version.",
                                    "Repair the loader installation if its required files are missing."
                            )
                    )
            );
        }

        /*
         * =========================================================
         * MINECRAFT VERSION
         * =========================================================
         */

        if (MINECRAFT_VERSION.matcher(text).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.MINECRAFT_VERSION,
                            RepairConfidence.AMBIGUOUS,
                            "Minecraft version compatibility problem",
                            "A component reported that the selected Minecraft version is unsupported.",
                            findEvidence(
                                    output,
                                    "incompatible",
                                    "unsupported"
                            ),
                            List.of(
                                    "Verify the instance Minecraft version.",
                                    "Verify the loader and installed content against that version."
                            )
                    )
            );
        }

        /*
         * =========================================================
         * CONFIGURATION
         * =========================================================
         */

        if (CONFIGURATION.matcher(text).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.CONFIGURATION,
                            RepairConfidence.AMBIGUOUS,
                            "Configuration problem",
                            "A configuration file appears to be invalid or malformed.",
                            findEvidence(
                                    output,
                                    "configuration",
                                    "malformed",
                                    "json",
                                    "parse"
                            ),
                            List.of(
                                    "Identify the exact configuration file.",
                                    "Restore a known-good backup when available.",
                                    "Do not overwrite unknown third-party configuration files automatically."
                            )
                    )
            );
        }

        /*
         * =========================================================
         * DISK
         * =========================================================
         */

        if (DISK_SPACE.matcher(text).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.DISK_SPACE,
                            RepairConfidence.DETERMINISTIC,
                            "Insufficient disk space",
                            "Minecraft or Vanta could not continue because the filesystem ran out of usable space.",
                            findEvidence(
                                    output,
                                    "not enough space",
                                    "no space left",
                                    "disk full"
                            ),
                            List.of(
                                    "Check available disk space.",
                                    "Free space before retrying the operation."
                            )
                    )
            );
        }

        /*
         * =========================================================
         * PERMISSION
         * =========================================================
         */

        if (PERMISSION.matcher(text).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.PERMISSION,
                            RepairConfidence.UNSAFE,
                            "File access problem",
                            "Windows denied access to a file or directory.",
                            findEvidence(
                                    output,
                                    "accessdeniedexception",
                                    "permission denied",
                                    "access is denied"
                            ),
                            List.of(
                                    "Identify the locked or protected file.",
                                    "Check whether another process is using it.",
                                    "Do not delete or overwrite the file automatically."
                            )
                    )
            );
        }

        /*
         * =========================================================
         * NETWORK
         * =========================================================
         */

        if (NETWORK.matcher(text).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.NETWORK,
                            RepairConfidence.UNSAFE,
                            "Network problem",
                            "Vanta or Minecraft could not reach a required network service.",
                            findEvidence(
                                    output,
                                    "unknownhostexception",
                                    "connection timed out",
                                    "connection refused",
                                    "http 429"
                            ),
                            List.of(
                                    "Check the network connection.",
                                    "Retry the operation later.",
                                    "Do not modify the installation because of a temporary network failure."
                            )
                    )
            );
        }

        if (diagnoses.isEmpty()) {

            diagnoses.add(
                    unknown(
                            "No known failure signature was detected."
                    )
            );
        }

        return List.copyOf(
                diagnoses
        );
    }

    private static RepairDiagnosis unknown(
            String description
    ) {

        return new RepairDiagnosis(
                FailureCategory.UNKNOWN,
                RepairConfidence.UNSAFE,
                "Minecraft failure",
                description,
                "",
                List.of(
                        "Inspect the Minecraft log and crash report.",
                        "Do not automatically modify the instance until the cause is known."
                )
        );
    }

    private static String findEvidence(
            String output,
            String... terms
    ) {

        if (output == null
                || output.isBlank()) {

            return "";
        }

        String lower =
                output.toLowerCase(
                        Locale.ROOT
                );

        for (String term : terms) {

            int index =
                    lower.indexOf(
                            term.toLowerCase(
                                    Locale.ROOT
                            )
                    );

            if (index < 0) {
                continue;
            }

            int start =
                    Math.max(
                            0,
                            index - 180
                    );

            int end =
                    Math.min(
                            output.length(),
                            index + term.length() + 300
                    );

            return output.substring(
                    start,
                    end
            ).trim();
        }

        return "";
    }
}