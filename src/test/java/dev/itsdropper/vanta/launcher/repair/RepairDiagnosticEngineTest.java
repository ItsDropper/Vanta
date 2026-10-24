package dev.itsdropper.vanta.launcher.repair;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepairDiagnosticEngineTest {

    @Test
    void diagnosesMixinCompatibilityFailures() {
        assertCategory("org.spongepowered.asm.mixin.transformer.throwables.MixinApplyError: Mixin apply failed",
                FailureCategory.MOD_MIXIN_FAILURE);
        assertCategory("org.spongepowered.asm.mixin.injection.throwables.InvalidInjectionException",
                FailureCategory.MOD_MIXIN_FAILURE);
    }

    @Test
    void diagnosesDuplicateMods() {
        assertCategory("Duplicate mod ID found: examplemod", FailureCategory.DUPLICATE_MOD);
    }

    @Test
    void diagnosesModInitializationFailures() {
        assertCategory("Mod examplemod failed to initialize during startup", FailureCategory.MOD_CRASH);
        assertCategory("Error during preLaunch entrypoint", FailureCategory.MOD_CRASH);
    }

    @Test
    void diagnosesGraphicsContextFailures() {
        assertCategory("GLFW error 65542: WGL: The driver does not appear to support OpenGL",
                FailureCategory.GRAPHICS);
    }

    @Test
    void diagnosesAuthenticationFailures() {
        assertCategory("Failed to verify username: Invalid session", FailureCategory.AUTHENTICATION);
        assertCategory("Minecraft services unavailable", FailureCategory.AUTHENTICATION);
    }

    @Test
    void diagnosesCorruptArchives() {
        assertCategory("java.util.zip.ZipException: zip END header not found",
                FailureCategory.CORRUPT_LIBRARY);
        assertCategory("Error: Invalid or corrupt jarfile client.jar",
                FailureCategory.CORRUPT_LIBRARY);
    }

    @Test
    void diagnosesJavaArchitectureFailures() {
        assertCategory("The specified executable is not a valid Win32 application",
                FailureCategory.JAVA_RUNTIME);
    }

    @Test
    void normalStartupMessagesDoNotBecomeRepairFailures() {
        var diagnoses = RepairDiagnosticEngine.diagnose(
                "Loading Minecraft 1.21.11 with Fabric Loader 0.19.5\n"
                        + "Dynamic FPS config missing or corrupted! Using defaults.\n"
                        + "Loaded 124 mods successfully."
        );

        assertFalse(diagnoses.stream().anyMatch(d ->
                d.category() == FailureCategory.GRAPHICS
                        || d.category() == FailureCategory.AUTHENTICATION
                        || d.category() == FailureCategory.MOD_MIXIN_FAILURE
                        || d.category() == FailureCategory.MOD_CRASH
                        || d.category() == FailureCategory.DUPLICATE_MOD));
    }

    private static void assertCategory(String output, FailureCategory expected) {
        assertTrue(RepairDiagnosticEngine.diagnose(output).stream()
                .anyMatch(diagnosis -> diagnosis.category() == expected),
                () -> "Expected " + expected + " for diagnostic output: " + output);
    }
}
