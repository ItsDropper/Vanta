package dev.itsdropper.vanta.launcher.instance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class MinecraftSecurityMitigationServiceTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void rejectsAnUnverifiedOrNonAgentJar() throws Exception {
        Path invalid = temporaryDirectory.resolve("not-an-agent.jar");
        Files.writeString(invalid, "not a Java agent");

        assertThrows(IOException.class,
                () -> MinecraftSecurityMitigationService.validateAgent(invalid));
    }
}
