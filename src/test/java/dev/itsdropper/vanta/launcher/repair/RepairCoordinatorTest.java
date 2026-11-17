package dev.itsdropper.vanta.launcher.repair;

import dev.itsdropper.vanta.ui.views.RepairView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairCoordinatorTest {

    @Test
    void knownInstallationDamageUsesManagedRepair() {
        assertTrue(RepairCoordinator.shouldRepairManagedInstallation(List.of(
                new RepairView.RepairIssue("CORRUPT_LIBRARY", "Corrupt archive", "zip end header not found")
        )));
        assertTrue(RepairCoordinator.shouldRepairManagedInstallation(List.of(
                new RepairView.RepairIssue("MISSING_ASSET", "Asset missing", "asset index not found")
        )));
    }

    @Test
    void unrelatedFailuresDoNotTriggerPointlessReinstall() {
        for (String category : List.of("AUTHENTICATION", "GRAPHICS", "NETWORK",
                "DISK_SPACE", "PERMISSION", "MOD_MIXIN_FAILURE", "MOD_CRASH", "JVM_ARGUMENTS",
                "MOD_DEPENDENCY", "JAVA_RUNTIME", "JAVA_VERSION", "MINECRAFT_VERSION")) {
            assertFalse(RepairCoordinator.shouldRepairManagedInstallation(List.of(
                    new RepairView.RepairIssue(category, category, "Detected " + category)
            )), category);
        }
    }

    @Test
    void unknownFailureGetsNonDestructiveIntegrityPass() {
        assertTrue(RepairCoordinator.shouldRepairManagedInstallation(List.of(
                new RepairView.RepairIssue("GENERAL DIAGNOSTIC", "Unknown launch error", "No signature matched")
        )));
    }

    @Test
    void explicitUserRequestOverridesNonInstallationDiagnosis() {
        List<RepairView.RepairIssue> issues = List.of(
                new RepairView.RepairIssue("AUTHENTICATION", "Expired session", "Account token expired"),
                new RepairView.RepairIssue("GRAPHICS", "OpenGL failure", "Driver initialization failed")
        );

        assertFalse(RepairCoordinator.shouldRepairManagedInstallation(issues));
        assertTrue(RepairCoordinator.shouldRepairManagedInstallation(issues, true));
    }

    @Test
    void explicitUserRequestAlsoAllowsRepairForUnknownFailures() {
        assertTrue(RepairCoordinator.shouldRepairManagedInstallation(
                List.of(new RepairView.RepairIssue(
                        "UNKNOWN", "Unclassified failure", "No signature matched"
                )),
                true
        ));
    }

    @Test
    void emptyInputCanStillRunBaselineIntegrityPass() {
        assertTrue(RepairCoordinator.shouldRepairManagedInstallation(List.of()));
    }
}
