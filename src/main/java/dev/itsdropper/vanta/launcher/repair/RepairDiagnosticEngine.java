package dev.itsdropper.vanta.launcher.repair;

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
                    "\\[(?:[^\\]]+/)?ERROR\\].*"
                            + "(could not find|cannot find|file not found|no such file)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern CLASS_NOT_FOUND =
            Pattern.compile(
                    "\\[(?:[^\\]]+/)?ERROR\\].*"
                            + "(classnotfoundexception|noclassdeffounderror)",
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

    private static final Pattern MIXIN_FAILURE =
            Pattern.compile(
                    "(mixin apply failed|invalidinjectionexception|critical injection failure|failed to apply mixin|could not find any targets matching)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern DUPLICATE_MOD =
            Pattern.compile(
                    "(duplicate mod(?: id)?|duplicate mods? found|mod id .{0,100} already exists|duplicate entry: .+\\.jar)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern MOD_INITIALIZATION_FAILURE =
            Pattern.compile(
                    "(failed to start mod .{1,120}|mod .{1,120} failed to initialize|exception during mod initialization|error during prelaunch)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern GRAPHICS_FAILURE =
            Pattern.compile(
                    "(failed to create (?:an? )?opengl context|could not create (?:an? )?opengl context|glfw error 65542|wgl: the driver does not appear to support opengl|pixel format not accelerated|no opengl context found)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern AUTHENTICATION_FAILURE =
            Pattern.compile(
                    "(invalid session|invalid token|token expired|not authenticated with minecraft\\.net|failed to verify username|minecraft services unavailable|authentication servers? (?:are )?(?:unavailable|down))",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern CORRUPT_ARCHIVE =
            Pattern.compile(
                    "(zip end header not found|invalid loc header|unexpected end of zlib input stream|zip exception|zip file is empty|invalid or corrupt jarfile)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern JAVA_ARCHITECTURE =
            Pattern.compile(
                    "(wrong architecture|%1 is not a valid win32 application|not a valid win32 application|exec format error|cannot run .* executable)",
                    Pattern.CASE_INSENSITIVE
            );

    /*
     * Do NOT match the normal presence of Fabric Loader.
     *
     * A successful Minecraft startup contains lines such as:
     *
     * Loading Minecraft 1.21.11 with Fabric Loader 0.19.5
     *
     * That is not a loader failure.
     */
    private static final Pattern FABRIC_LOADER_ERROR =
            Pattern.compile(
                    "(fabric loader.*(error|failed|exception|could not|unable)"
                            + "|(error|failed|exception|could not|unable).*fabric loader)",
                    Pattern.CASE_INSENSITIVE
            );

    private static final Pattern MINECRAFT_VERSION =
            Pattern.compile(
                    "(incompatible.*minecraft|unsupported.*minecraft version|minecraft version.*not supported)",
                    Pattern.CASE_INSENSITIVE
            );

    /*
     * Configuration errors must contain an actual failure condition.
     *
     * Do NOT match generic words such as:
     *
     *   config
     *   configuration
     *   json
     *   parse
     *
     * by themselves.
     *
     * Minecraft/mod startup logs commonly contain those words in
     * perfectly valid library names, for example:
     *
     *   org_spongepowered_configurate-core
     *
     * which must never be diagnosed as a configuration failure.
     */
    private static final Pattern CONFIGURATION =
            Pattern.compile(
                    "("
                            + "(invalid|malformed|corrupt|broken|bad)"
                            + "\\s+.{0,80}\\s+config(?:uration)?"
                            + "|"
                            + "(config(?:uration)?|config(?:uration)? file)"
                            + "\\s+.{0,80}\\s+"
                            + "(invalid|malformed|corrupt|broken|failed|error)"
                            + "|"
                            + "(failed|error|unable|could not|cannot)"
                            + "\\s+.{0,80}\\s+"
                            + "(load|read|parse|open)"
                            + "\\s+.{0,80}\\s+"
                            + "(config(?:uration)?|json)"
                            + "|"
                            + "(failed|error|unable|could not|cannot)"
                            + "\\s+.{0,80}\\s+"
                            + "(parse|read)"
                            + "\\s+.{0,80}\\s+json"
                            + "|"
                            + "(json|hocon|toml|yaml|yml)"
                            + "\\s+.{0,80}\\s+"
                            + "(parse error|parsing error|malformed|invalid)"
                            + ")",
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

        if (FABRIC_LOADER_ERROR.matcher(text).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.LOADER,
                            RepairConfidence.AMBIGUOUS,
                            "Loader problem",
                            "The Minecraft loader reported an error.",
                            findEvidence(
                                    output,
                                    "fabric loader",
                                    "fabric-loader",
                                    "failed",
                                    "exception"
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

        /*
         * Some mods intentionally fall back to default configuration when
         * their config is missing. This is not a launch failure.
         *
         * Example:
         *   Dynamic FPS config missing or corrupted! Using defaults.
         *
         * Do not let that warning trigger the repair engine and terminate
         * an otherwise healthy Minecraft startup.
         */
        String configurationDiagnosticText =
                text.replaceAll(
                        "(?m)^.*config missing or corrupted! using defaults.*$",
                        ""
                );

        if (CONFIGURATION.matcher(configurationDiagnosticText).find()) {

            diagnoses.add(
                    new RepairDiagnosis(
                            FailureCategory.CONFIGURATION,
                            RepairConfidence.AMBIGUOUS,
                            "Configuration problem",
                            "A configuration file appears to be invalid or malformed.",
                            findEvidence(
                                    output,
                                    "invalid",
                                    "malformed",
                                    "failed",
                                    "error",
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


        if (MIXIN_FAILURE.matcher(text).find()) {
            diagnoses.add(new RepairDiagnosis(
                    FailureCategory.MOD_MIXIN_FAILURE,
                    RepairConfidence.AMBIGUOUS,
                    "Mod mixin compatibility failure",
                    "A mod could not apply a code transformation, usually because of a mod/version conflict.",
                    findEvidence(output, "mixin apply failed", "invalidinjectionexception",
                            "critical injection failure", "failed to apply mixin",
                            "could not find any targets matching"),
                    List.of(
                            "Identify the mod named in the mixin stack trace.",
                            "Check compatibility with the exact Minecraft, loader and dependency versions.",
                            "Disable or replace only the confirmed incompatible mod; preserve worlds and configuration."
                    )
            ));
        }

        if (DUPLICATE_MOD.matcher(text).find()) {
            diagnoses.add(new RepairDiagnosis(
                    FailureCategory.DUPLICATE_MOD,
                    RepairConfidence.AMBIGUOUS,
                    "Duplicate mod detected",
                    "The loader reported a duplicate mod identifier or duplicate JAR entry.",
                    findEvidence(output, "duplicate mod", "duplicate mods", "already exists", "duplicate entry"),
                    List.of(
                            "List mod JARs with duplicate mod IDs and compare their versions.",
                            "Keep one compatible version after confirming which file is duplicated.",
                            "Never remove a mod solely because its filename resembles another mod."
                    )
            ));
        }

        if (MOD_INITIALIZATION_FAILURE.matcher(text).find()) {
            diagnoses.add(new RepairDiagnosis(
                    FailureCategory.MOD_CRASH,
                    RepairConfidence.AMBIGUOUS,
                    "Mod initialization failed",
                    "A mod failed during startup initialization.",
                    findEvidence(output, "failed to start mod", "failed to initialize",
                            "exception during mod initialization", "error during prelaunch"),
                    List.of(
                            "Trace the first relevant exception to its mod ID.",
                            "Check that mod's dependencies and loader compatibility.",
                            "Offer a reversible quarantine/disable action only after identifying the exact mod JAR."
                    )
            ));
        }

        if (GRAPHICS_FAILURE.matcher(text).find()) {
            diagnoses.add(new RepairDiagnosis(
                    FailureCategory.GRAPHICS,
                    RepairConfidence.AMBIGUOUS,
                    "OpenGL or graphics driver failure",
                    "Minecraft could not create a usable graphics context.",
                    findEvidence(output, "failed to create", "glfw error 65542",
                            "wgl:", "pixel format not accelerated", "no opengl context"),
                    List.of(
                            "Check the active GPU and graphics driver version.",
                            "Check whether Minecraft is running through Remote Desktop or a virtualized display.",
                            "Do not replace drivers or change system-wide GPU settings automatically."
                    )
            ));
        }

        if (AUTHENTICATION_FAILURE.matcher(text).find()) {
            diagnoses.add(new RepairDiagnosis(
                    FailureCategory.AUTHENTICATION,
                    RepairConfidence.AMBIGUOUS,
                    "Minecraft authentication failure",
                    "The session or authentication service was rejected or unavailable.",
                    findEvidence(output, "invalid session", "invalid token", "token expired",
                            "not authenticated with minecraft.net", "failed to verify username",
                            "minecraft services unavailable"),
                    List.of(
                            "Check the Microsoft account session and refresh authentication if expired.",
                            "Check Minecraft service status and retry transient service failures.",
                            "Never delete account tokens or credentials as an automatic repair step."
                    )
            ));
        }

        if (CORRUPT_ARCHIVE.matcher(text).find()) {
            diagnoses.add(new RepairDiagnosis(
                    FailureCategory.CORRUPT_LIBRARY,
                    RepairConfidence.AMBIGUOUS,
                    "Corrupt JAR or ZIP archive",
                    "Java reported an archive that may be incomplete or corrupted.",
                    findEvidence(output, "zip end header not found", "invalid loc header",
                            "unexpected end of zlib input stream", "zip exception",
                            "zip file is empty", "invalid or corrupt jarfile"),
                    List.of(
                            "Identify the exact archive path from the surrounding exception.",
                            "Redownload only Vanta-managed files with a trusted expected checksum.",
                            "For user-installed mods, offer a backup-and-quarantine action instead of deleting the file."
                    )
            ));
        }

        if (JAVA_ARCHITECTURE.matcher(text).find()) {
            diagnoses.add(new RepairDiagnosis(
                    FailureCategory.JAVA_RUNTIME,
                    RepairConfidence.DETERMINISTIC,
                    "Java runtime architecture or executable problem",
                    "The selected executable may be incompatible with this operating system or CPU architecture.",
                    findEvidence(output, "wrong architecture", "not a valid win32 application",
                            "exec format error", "cannot run"),
                    List.of(
                            "Detect the operating system and process architecture.",
                            "Select or reinstall a matching Vanta-managed Java runtime.",
                            "Keep the instance's mods, saves and configuration unchanged."
                    )
            ));
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