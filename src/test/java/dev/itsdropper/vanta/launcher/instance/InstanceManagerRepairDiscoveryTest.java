package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceManagerRepairDiscoveryTest {

    @Test
    void normalDiscoveryKeepsMetadataCompleteInstancesWithAnInterruptedInstallMarker() throws Exception {
        Path root = java.nio.file.Files.createTempDirectory("vanta-instance-discovery-");
        Path directory = root.resolve("survival");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve("instance.json"), """
                {"id":"survival","name":"Survival","minecraftVersion":"1.21.11",
                 "loader":"Fabric","loaderVersion":"0.18.4","directory":"ignored"}
                """);
        java.nio.file.Files.createFile(directory.resolve(".installing"));

        var discovered = InstanceManager.discoverInstances(root, false, null);

        assertTrue(discovered.stream().anyMatch(instance -> "survival".equals(instance.getId())));
        assertTrue(InstanceManager.isInstallationInProgress(discovered.get(0)));
    }

    @Test
    void normalDiscoveryDoesNotRunModrinthTransactionRecovery() throws Exception {
        Path root = java.nio.file.Files.createTempDirectory("vanta-instance-discovery-");
        Path directory = root.resolve("survival");
        java.nio.file.Files.createDirectories(directory);
        java.nio.file.Files.writeString(directory.resolve("instance.json"), """
                {"id":"survival","name":"Survival","minecraftVersion":"1.21.11",
                 "loader":"Fabric","loaderVersion":"0.18.4","directory":"ignored"}
                """);
        Path damagedTransaction = java.nio.file.Files.createDirectory(
                directory.resolve(".vanta-graph-transaction-damaged"));
        java.nio.file.Files.writeString(damagedTransaction.resolve("journal.properties"), "damaged=true");

        var discovered = InstanceManager.discoverInstances(root, false, null);

        assertTrue(discovered.stream().anyMatch(instance -> "survival".equals(instance.getId())));
        assertFalse(java.nio.file.Files.exists(directory.resolve(".vanta-mod-install.lock")));
    }

    @Test
    void acceptsCompleteMetadataForInterruptedInstanceRepair() {
        Instance instance = new Instance(
                "survival", "Survival", "1.21.11",
                "Fabric", "0.18.4", Path.of("instances/survival"));

        assertTrue(InstanceManager.hasRepairMetadata(instance));
    }

    @Test
    void rejectsMetadataThatCannotSafelyIdentifyAnInstallation() {
        assertFalse(InstanceManager.hasRepairMetadata(null));
        assertFalse(InstanceManager.hasRepairMetadata(new Instance(
                null, "Survival", "1.21.11", "Fabric", "0.18.4", Path.of("instances/survival"))));
        assertFalse(InstanceManager.hasRepairMetadata(new Instance(
                "survival", " ", "1.21.11", "Fabric", "0.18.4", Path.of("instances/survival"))));
        assertFalse(InstanceManager.hasRepairMetadata(new Instance(
                "survival", "Survival", null, "Fabric", "0.18.4", Path.of("instances/survival"))));
        assertFalse(InstanceManager.hasRepairMetadata(new Instance(
                "survival", "Survival", "1.21.11", " ", "0.18.4", Path.of("instances/survival"))));
    }

    @Test
    void loaderVersionMayBeAbsentBecauseRepairCanResolveCurrentVersion() {
        Instance instance = new Instance(
                "survival", "Survival", "1.21.11",
                "Fabric", null, Path.of("instances/survival"));

        assertTrue(InstanceManager.hasRepairMetadata(instance));
    }
}
