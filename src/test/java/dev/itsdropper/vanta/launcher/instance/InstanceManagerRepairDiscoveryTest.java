package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceManagerRepairDiscoveryTest {

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
