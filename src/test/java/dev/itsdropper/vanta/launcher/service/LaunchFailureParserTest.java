package dev.itsdropper.vanta.launcher.service;

import dev.itsdropper.vanta.ui.views.RepairView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LaunchFailureParserTest {

    @Test
    void parsesFabricMissingDependencyWithAnyVersion() {
        List<RepairView.RepairIssue> issues = LaunchFailureParser.parse(
                "Mod 'Nvidium' (nvidium) 0.5.9 requires any version of sodium, which is missing!"
        );

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).canRepair());
        assertEquals("sodium", issues.get(0).repairProjectId());
        assertTrue(issues.get(0).repairVersions().isEmpty());
    }

    @Test
    void parsesFabricMissingDependencyWithoutExplicitVersionPhrase() {
        List<RepairView.RepairIssue> issues = LaunchFailureParser.parse(
                "Mod 'Nvidium' (nvidium) 0.5.9 requires sodium, which is missing!"
        );

        assertEquals(1, issues.size());
        assertTrue(issues.get(0).canRepair());
        assertEquals("sodium", issues.get(0).repairProjectId());
    }

    @Test
    void ignoresOrdinaryFabricLoaderStartupLine() {
        List<RepairView.RepairIssue> issues = LaunchFailureParser.parse(
                "Loading Minecraft 1.21.11 with Fabric Loader 0.19.5"
        );

        assertTrue(issues.isEmpty());
    }
}
