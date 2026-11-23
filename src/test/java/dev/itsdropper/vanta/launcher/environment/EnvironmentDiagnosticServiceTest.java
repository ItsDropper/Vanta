package dev.itsdropper.vanta.launcher.environment;

import dev.itsdropper.vanta.launcher.LaunchData;
import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentDiagnosticServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsHealthyInstanceAndDeclaredJavaRequirement() throws Exception {
        Instance instance = createInstance("healthy");
        EnvironmentDiagnosticService.Report report =
                new EnvironmentDiagnosticService().inspect(instance);

        assertTrue(report.isHealthy(), report.diagnostics().toString());
        assertTrue(report.diagnostics().stream().anyMatch(item ->
                item.code().equals("JAVA_REQUIREMENT")
                        && item.message().contains("Java 21")));
    }

    @Test
    void reportsMissingClientJarAsRepairableError() throws Exception {
        Instance instance = createInstance("missing-client");
        Files.delete(instance.getDirectory().resolve("minecraft/client.jar"));

        EnvironmentDiagnosticService.Report report =
                new EnvironmentDiagnosticService().inspect(instance);

        assertFalse(report.isHealthy());
        assertTrue(report.diagnostics().stream().anyMatch(item ->
                item.code().equals("CLIENT_JAR_MISSING")
                        && item.severity() == EnvironmentDiagnosticService.Severity.ERROR
                        && item.repairable()));
    }

    @Test
    void reportsInvalidVersionMetadataWithoutThrowing() throws Exception {
        Instance instance = createInstance("invalid-metadata");
        Files.writeString(instance.getDirectory().resolve("minecraft/version.json"), "{not-json");

        EnvironmentDiagnosticService.Report report =
                new EnvironmentDiagnosticService().inspect(instance);

        assertFalse(report.isHealthy());
        assertTrue(report.diagnostics().stream().anyMatch(item ->
                item.code().equals("VERSION_METADATA_INVALID")));
    }

    @Test
    void reportsMissingInstanceDirectory() {
        Instance instance = new Instance("missing", "Missing", "1.21.1",
                "Fabric", "0.16.0", temporaryDirectory.resolve("does-not-exist"));

        EnvironmentDiagnosticService.Report report =
                new EnvironmentDiagnosticService().inspect(instance);

        assertFalse(report.isHealthy());
        assertEquals("INSTANCE_DIRECTORY_NOT_FOUND", report.diagnostics().getFirst().code());
    }

    @Test
    void checksResolvedClasspathAndNativeArtifactsWithoutResolvingAgain() throws Exception {
        Instance instance = createInstance("resolved-artifacts");
        LaunchData launchData = new LaunchData();
        launchData.classpath = List.of(temporaryDirectory.resolve("missing-library.jar"));
        launchData.nativeJars = List.of();

        EnvironmentDiagnosticService.Report report =
                new EnvironmentDiagnosticService().inspect(instance, launchData);

        assertFalse(report.isHealthy());
        assertTrue(report.diagnostics().stream().anyMatch(item ->
                item.code().equals("CLASSPATH_ARTIFACT_MISSING")));
    }

    private Instance createInstance(String id) throws Exception {
        Path directory = Files.createDirectory(temporaryDirectory.resolve(id));
        Path minecraft = Files.createDirectory(directory.resolve("minecraft"));
        Files.writeString(minecraft.resolve("version.json"), """
                {
                  "id": "1.21.1",
                  "mainClass": "net.minecraft.client.main.Main",
                  "javaVersion": { "majorVersion": 21 },
                  "libraries": []
                }
                """);
        Files.writeString(minecraft.resolve("client.jar"), "minecraft-client");
        Files.createDirectory(directory.resolve("mods"));
        return new Instance(id, id, "1.21.1", "Fabric", "0.16.0", directory);
    }
}
