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
    @Test
    void crashTextSanitizationRemovesCommonPrivateValues() {
        String source = "token=secret-value user@example.com "
                + "123e4567-e89b-12d3-a456-426614174000 "
                + "192.168.1.20 C:\\Users\\Example\\\\AppData";
        String sanitized = TelemetryService.sanitizeText(source, 2000);

        assertFalse(sanitized.contains("secret-value"));
        assertFalse(sanitized.contains("user@example.com"));
        assertFalse(sanitized.contains("123e4567-e89b-12d3-a456-426614174000"));
        assertFalse(sanitized.contains("192.168.1.20"));
        assertFalse(sanitized.contains("C:\\Users\\Example"));
        assertTrue(sanitized.contains("[REDACTED]"));
    }

    @Test
    void crashTextSanitizationEnforcesLengthLimit() {
        assertEquals(32, TelemetryService.sanitizeText("x".repeat(100), 32).length());
    }

}
