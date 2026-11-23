package dev.itsdropper.vanta.launcher.environment;

import dev.itsdropper.vanta.launcher.ClasspathBuilder;
import dev.itsdropper.vanta.launcher.VersionReader;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.model.VersionManifest;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Read-only diagnostics for the files and metadata required to launch an instance.
 *
 * <p>This service deliberately does not repair or download anything. The launch
 * pipeline remains responsible for recovery so diagnostics cannot bypass its
 * backup and rollback safeguards.</p>
 */
public final class EnvironmentDiagnosticService {
    public enum Severity { INFO, WARNING, ERROR }

    public record Diagnostic(String code, Severity severity, String message, boolean repairable) {
        public Diagnostic {
            Objects.requireNonNull(code, "code");
            Objects.requireNonNull(severity, "severity");
            Objects.requireNonNull(message, "message");
        }
    }

    public record Report(String instanceId, Instant checkedAt, List<Diagnostic> diagnostics) {
        public Report {
            diagnostics = diagnostics == null ? List.of() : List.copyOf(diagnostics);
        }

        public boolean isHealthy() {
            return diagnostics.stream().noneMatch(item -> item.severity() == Severity.ERROR);
        }

        public long count(Severity severity) {
            return diagnostics.stream().filter(item -> item.severity() == severity).count();
        }
    }

    public Report inspect(Instance instance) {
        List<Diagnostic> findings = new ArrayList<>();
        if (instance == null) {
            findings.add(error("INSTANCE_MISSING", "No instance was supplied.", false));
            return report(null, findings);
        }

        Path directory = instance.getDirectory();
        if (directory == null) {
            findings.add(error("INSTANCE_DIRECTORY_MISSING", "The instance has no directory configured.", false));
            return report(instance.getId(), findings);
        }

        Path root = directory.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(root)) {
            findings.add(error("INSTANCE_DIRECTORY_SYMLINK",
                    "The instance directory is a symbolic link; Vanta will not inspect it.", false));
            return report(instance.getId(), findings);
        }
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
            findings.add(error("INSTANCE_DIRECTORY_NOT_FOUND",
                    "The instance directory does not exist.", true));
            return report(instance.getId(), findings);
        }

        if (blank(instance.getMinecraftVersion())) {
            findings.add(error("MINECRAFT_VERSION_MISSING", "The instance has no Minecraft version.", false));
        }
        if (blank(instance.getLoader())) {
            findings.add(error("LOADER_MISSING", "The instance has no loader configured.", false));
        }

        Path minecraft = root.resolve("minecraft");
        if (Files.isSymbolicLink(minecraft)) {
            findings.add(error("MINECRAFT_DIRECTORY_SYMLINK",
                    "The Minecraft runtime directory is a symbolic link.", false));
            return report(instance.getId(), findings);
        }
        if (!Files.isDirectory(minecraft, LinkOption.NOFOLLOW_LINKS)) {
            findings.add(error("MINECRAFT_DIRECTORY_MISSING",
                    "The Minecraft runtime directory is missing.", true));
            return report(instance.getId(), findings);
        }

        Path metadata = minecraft.resolve("version.json");
        if (Files.isSymbolicLink(metadata) || !Files.isRegularFile(metadata, LinkOption.NOFOLLOW_LINKS)) {
            findings.add(error("VERSION_METADATA_MISSING",
                    "Minecraft version metadata is missing or is not a regular file.", true));
            return report(instance.getId(), findings);
        }

        VersionManifest manifest;
        try {
            manifest = VersionReader.readVersion(root);
        } catch (RuntimeException failure) {
            findings.add(error("VERSION_METADATA_INVALID",
                    "Minecraft version metadata cannot be parsed: " + safeMessage(failure), true));
            return report(instance.getId(), findings);
        }
        if (manifest == null) {
            findings.add(error("VERSION_METADATA_MISSING",
                    "Minecraft version metadata could not be loaded.", true));
            return report(instance.getId(), findings);
        }
        if (blank(manifest.mainClass)) {
            findings.add(error("MAIN_CLASS_MISSING",
                    "Minecraft version metadata does not declare a main class.", true));
        }
        if (manifest.libraries == null) {
            findings.add(error("LIBRARY_METADATA_MISSING",
                    "Minecraft version metadata does not contain a library list.", true));
        } else {
            try {
                List<Path> classpath = ClasspathBuilder.build(instance);
                List<Path> missing = classpath.stream()
                        .filter(path -> !Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                        .toList();
                if (!missing.isEmpty()) {
                    findings.add(error("CLASSPATH_ARTIFACT_MISSING",
                            missing.size() + " required classpath artifact(s) are missing.", true));
                }
            } catch (RuntimeException failure) {
                findings.add(error("DEPENDENCY_RESOLUTION_FAILED",
                        "Required Minecraft or loader libraries could not be resolved: "
                                + safeMessage(failure), true));
            }
        }

        Path clientJar = minecraft.resolve("client.jar");
        if (Files.isSymbolicLink(clientJar) || !Files.isRegularFile(clientJar, LinkOption.NOFOLLOW_LINKS)
                || fileSize(clientJar) == 0L) {
            findings.add(error("CLIENT_JAR_MISSING",
                    "The Minecraft client JAR is missing, empty, or not a regular file.", true));
        }

        if (manifest.javaVersion != null && manifest.javaVersion.majorVersion > 0) {
            findings.add(new Diagnostic("JAVA_REQUIREMENT",
                    Severity.INFO,
                    "This Minecraft profile requires Java " + manifest.javaVersion.majorVersion
                            + "; Vanta's Java selection step will verify the runtime before launch.",
                    false));
        } else {
            findings.add(new Diagnostic("JAVA_REQUIREMENT_UNKNOWN", Severity.WARNING,
                    "The profile does not declare a Java major version; Vanta will use its legacy version rules.",
                    false));
        }

        return report(instance.getId(), findings);
    }

    private static long fileSize(Path path) {
        try {
            return Files.size(path);
        } catch (IOException ignored) {
            return -1L;
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String safeMessage(Throwable failure) {
        String message = failure.getMessage();
        return message == null || message.isBlank() ? failure.getClass().getSimpleName() : message;
    }

    private static Diagnostic error(String code, String message, boolean repairable) {
        return new Diagnostic(code, Severity.ERROR, message, repairable);
    }

    private static Report report(String instanceId, List<Diagnostic> findings) {
        return new Report(instanceId, Instant.now(), findings);
    }
}
