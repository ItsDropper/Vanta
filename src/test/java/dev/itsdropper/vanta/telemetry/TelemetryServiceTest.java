package dev.itsdropper.vanta.telemetry;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TelemetryServiceTest {

    @Test
    void onlyExplicitlySupportedUsageEventsCanBeTracked() {
        assertTrue(TelemetryService.isSupportedUsageEvent("launcher_started"));
        assertTrue(TelemetryService.isSupportedUsageEvent("view_instances_opened"));
        assertFalse(TelemetryService.isSupportedUsageEvent("telemetry_test"));
        assertFalse(TelemetryService.isSupportedUsageEvent("account_connected"));
        assertFalse(TelemetryService.isSupportedUsageEvent("minecraft_username"));
        assertFalse(TelemetryService.isSupportedUsageEvent(null));
    }

    @Test
    void crashTypeSanitizationRemovesMessagesAndUnsafeCharacters() {
        assertEquals("IllegalStateException",
                TelemetryService.sanitizeErrorType("IllegalStateException"));
        assertEquals("IllegalStateException",
                TelemetryService.sanitizeErrorType("IllegalStateException: token=secret"));
        assertEquals("UnknownError", TelemetryService.sanitizeErrorType("///"));
        assertEquals("UnknownError", TelemetryService.sanitizeErrorType(null));
        assertTrue(TelemetryService.sanitizeErrorType("A".repeat(200)).length() <= 120);
    }
}
