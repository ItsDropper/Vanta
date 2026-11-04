package dev.itsdropper.vanta.launcher.instance;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NilLoaderInstallerTest {
    @Test
    void resolvesThePublishedLoaderVersion() {
        assertEquals("1.4.1", NilLoaderInstaller.findLatestLoaderVersion("1.4.7"));
    }

    @Test
    void rejectsAnEmptyMinecraftVersion() {
        assertThrows(IllegalArgumentException.class,
                () -> NilLoaderInstaller.findLatestLoaderVersion(" "));
    }

    @Test
    void acceptsJarWithPremainClass() throws Exception {
        Path jar = createAgentJar("example.Agent", null);
        assertTrue(NilLoaderInstaller.hasAgentEntrypoint(jar));
    }

    @Test
    void acceptsJarWithAgentClass() throws Exception {
        Path jar = createAgentJar(null, "example.Agent");
        assertTrue(NilLoaderInstaller.hasAgentEntrypoint(jar));
    }

    @Test
    void rejectsJarWithoutAgentEntrypoint() throws Exception {
        Path jar = createAgentJar(null, null);
        assertFalse(NilLoaderInstaller.hasAgentEntrypoint(jar));
    }

    @Test
    void rejectsMissingAndInvalidFiles() throws Exception {
        Path missing = Path.of("build", "missing-nilloader-test.jar");
        assertFalse(NilLoaderInstaller.hasAgentEntrypoint(missing));

        Path invalid = Files.createTempFile("nilloader-invalid", ".jar");
        Files.writeString(invalid, "not a jar");
        assertFalse(NilLoaderInstaller.hasAgentEntrypoint(invalid));
    }

    private static Path createAgentJar(String premainClass, String agentClass) throws Exception {
        Manifest manifest = new Manifest();
        Attributes attributes = manifest.getMainAttributes();
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0");
        if (premainClass != null) {
            attributes.putValue("Premain-Class", premainClass);
        }
        if (agentClass != null) {
            attributes.putValue("Agent-Class", agentClass);
        }

        Path jar = Files.createTempFile("nilloader-agent-test", ".jar");
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar), manifest)) {
            output.putNextEntry(new JarEntry("test.txt"));
            output.write("test".getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return jar;
    }
}
