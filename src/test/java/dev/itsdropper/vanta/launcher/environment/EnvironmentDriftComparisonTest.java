package dev.itsdropper.vanta.launcher.environment;

import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class EnvironmentDriftComparisonTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void reportsVersionAndModFileDriftWithoutChangingInstanceFiles() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("instance"));
        Path mods = Files.createDirectory(instanceDirectory.resolve("mods"));
        Path config = Files.createDirectories(instanceDirectory.resolve("config/example"));
        Files.writeString(config.resolve("options.json"), "before");
        Path resourcepacks = Files.createDirectory(instanceDirectory.resolve("resourcepacks"));
        Files.writeString(resourcepacks.resolve("added.zip"), "new pack");
        Path changed = Files.writeString(mods.resolve("changed.jar"), "before");
        Files.writeString(mods.resolve("removed.jar"), "remove");

        Instance baselineInstance = new Instance(
                "instance", "Instance", "1.21.1", "fabric", "0.16.9", instanceDirectory
        );
        EnvironmentLockfileService service = new EnvironmentLockfileService();
        EnvironmentLockfile baseline = service.capture(baselineInstance);

        Files.writeString(changed, "after");
        Files.writeString(config.resolve("options.json"), "after");
        Files.writeString(instanceDirectory.resolve("config/example/new.json"), "new config");
        Files.delete(resourcepacks.resolve("added.zip"));
        Files.delete(mods.resolve("removed.jar"));
        Files.writeString(mods.resolve("added.jar"), "new");
        Instance currentInstance = new Instance(
                "instance", "Instance", "1.21.2", "fabric", "0.16.9", instanceDirectory
        );

        EnvironmentDriftReport report = service.compare(currentInstance, baseline);

        Set<EnvironmentDriftReport.ChangeType> types = report.changes().stream()
                .map(EnvironmentDriftReport.Change::type)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                EnvironmentDriftReport.ChangeType.MINECRAFT_VERSION_CHANGED,
                EnvironmentDriftReport.ChangeType.MOD_MODIFIED,
                EnvironmentDriftReport.ChangeType.MOD_REMOVED,
                EnvironmentDriftReport.ChangeType.MOD_ADDED,
                EnvironmentDriftReport.ChangeType.ENVIRONMENT_FILE_MODIFIED,
                EnvironmentDriftReport.ChangeType.ENVIRONMENT_FILE_ADDED,
                EnvironmentDriftReport.ChangeType.ENVIRONMENT_FILE_REMOVED
        ), types);
        assertEquals(7, report.changes().size());
        assertFalse(report.isClean());
        assertTrue(Files.exists(changed));
        assertTrue(Files.exists(mods.resolve("added.jar")));
    }

    @Test
    void rejectsBaselineOwnedByAnotherInstance() throws Exception {
        Path firstDirectory = Files.createDirectory(temporaryDirectory.resolve("first"));
        Path secondDirectory = Files.createDirectory(temporaryDirectory.resolve("second"));
        Files.createDirectory(firstDirectory.resolve("mods"));
        Files.createDirectory(secondDirectory.resolve("mods"));

        Instance first = new Instance(
                "first-id", "First", "1.21.1", "fabric", "0.16.9", firstDirectory
        );
        Instance second = new Instance(
                "second-id", "Second", "1.21.1", "fabric", "0.16.9", secondDirectory
        );

        EnvironmentLockfileService service = new EnvironmentLockfileService();
        EnvironmentLockfile baseline = service.capture(first);

        java.io.IOException failure = assertThrows(
                java.io.IOException.class,
                () -> service.compare(second, baseline)
        );
        assertTrue(failure.getMessage().contains("different instance"));
    }


    @Test
    void schemaTwoBaselineDoesNotReportNewRuntimeCategoriesAsDrift() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("legacy-schema-two"));
        Files.createDirectory(instanceDirectory.resolve("mods"));
        Path minecraft = Files.createDirectory(instanceDirectory.resolve("minecraft"));
        Files.writeString(minecraft.resolve("version.json"), """
                {
                  "mainClass": "net.minecraft.client.main.Main",
                  "libraries": []
                }
                """);
        Files.writeString(minecraft.resolve("client.jar"), "client");

        Instance instance = new Instance(
                "legacy-v2", "Legacy", "1.21.1", "fabric", "0.16.9", instanceDirectory
        );
        EnvironmentLockfile schemaTwoBaseline = new EnvironmentLockfile(
                2, "2026-10-31T00:00:00Z", instance.getId(), instance.getMinecraftVersion(),
                instance.getLoader(), instance.getLoaderVersion(), null, java.util.List.of(), java.util.List.of()
        );

        EnvironmentDriftReport report = new EnvironmentLockfileService()
                .compare(instance, schemaTwoBaseline);

        assertTrue(report.isClean());
    }


    @Test
    void reportsRequiredJavaMajorVersionDrift() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("java-requirement"));
        Files.createDirectory(instanceDirectory.resolve("mods"));
        Path minecraft = Files.createDirectory(instanceDirectory.resolve("minecraft"));
        Path version = minecraft.resolve("version.json");
        Files.writeString(version, """
                {
                  "mainClass": "net.minecraft.client.main.Main",
                  "javaVersion": { "majorVersion": 21 },
                  "libraries": []
                }
                """);
        Files.writeString(minecraft.resolve("client.jar"), "client");

        Instance instance = new Instance(
                "java-requirement", "Java Requirement", "1.21.1", "fabric", "0.16.9", instanceDirectory
        );
        EnvironmentLockfileService service = new EnvironmentLockfileService();
        EnvironmentLockfile baseline = service.capture(instance);

        Files.writeString(version, """
                {
                  "mainClass": "net.minecraft.client.main.Main",
                  "javaVersion": { "majorVersion": 17 },
                  "libraries": []
                }
                """);

        EnvironmentDriftReport report = service.compare(instance, baseline);

        assertEquals(1, report.changes().size());
        assertEquals(EnvironmentDriftReport.ChangeType.JAVA_REQUIREMENT_CHANGED,
                report.changes().getFirst().type());
        assertTrue(report.changes().getFirst().description().contains("21 to 17"));
    }

    @Test
    void reportsCleanWhenLiveEnvironmentMatchesBaseline() throws Exception {
        Path instanceDirectory = Files.createDirectory(temporaryDirectory.resolve("clean-instance"));
        Path mods = Files.createDirectory(instanceDirectory.resolve("mods"));
        Files.writeString(mods.resolve("same.jar"), "same");

        Instance instance = new Instance(
                "clean", "Clean", "1.21.1", "fabric", "0.16.9", instanceDirectory
        );
        EnvironmentLockfileService service = new EnvironmentLockfileService();

        EnvironmentDriftReport report = service.compare(instance, service.capture(instance));

        assertTrue(report.isClean());
        assertTrue(report.changes().isEmpty());
    }
}
