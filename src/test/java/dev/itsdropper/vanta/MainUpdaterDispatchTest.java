package dev.itsdropper.vanta;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MainUpdaterDispatchTest {
    @Test
    void recognizesUpdaterModeBeforeStartingJavaFx() {
        assertTrue(Main.isUpdaterInvocation(new String[]{
                "--vanta-updater", "123", "/tmp/update.zip", "/opt/Vanta"
        }));
    }

    @Test
    void recognizesUpdaterWorkspaceCleanupMode() {
        assertTrue(Main.isUpdaterCleanupInvocation(new String[]{
                "--vanta-cleanup-updater", "/tmp/Vanta-updater/session-test", "123"
        }));
        assertFalse(Main.isUpdaterCleanupInvocation(new String[]{
                "--vanta-cleanup-updater", "/tmp/Vanta-updater/session-test"
        }));
    }

    @Test
    void doesNotTreatNormalLauncherArgumentsAsUpdaterMode() {
        assertFalse(Main.isUpdaterInvocation(new String[0]));
        assertFalse(Main.isUpdaterInvocation(new String[]{"--capture-sidebar-screenshots"}));
        assertFalse(Main.isUpdaterInvocation(new String[]{"--vanta-updater-extra"}));
        assertFalse(Main.isUpdaterInvocation(null));
    }
}
