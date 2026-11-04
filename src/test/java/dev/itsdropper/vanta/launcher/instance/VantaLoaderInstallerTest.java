package dev.itsdropper.vanta.launcher.instance;

import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VantaLoaderInstallerTest {
    @TempDir Path temporaryDirectory;

    @Test
    void supportsOnlyMinecraftVersionsWithTheJava21RuntimeBaseline() {
        assertTrue(VantaLoaderInstaller.supportsMinecraftVersion("1.20.5"));
        assertTrue(VantaLoaderInstaller.supportsMinecraftVersion("1.20.6"));
        assertTrue(VantaLoaderInstaller.supportsMinecraftVersion("1.21.1"));
        assertTrue(VantaLoaderInstaller.supportsMinecraftVersion("1.21.11"));
        assertFalse(VantaLoaderInstaller.supportsMinecraftVersion("1.20.4"));
        assertFalse(VantaLoaderInstaller.supportsMinecraftVersion("1.19.4"));
        assertFalse(VantaLoaderInstaller.supportsMinecraftVersion("b1.7.3"));
        assertFalse(VantaLoaderInstaller.supportsMinecraftVersion("26.1"));
        assertFalse(VantaLoaderInstaller.supportsMinecraftVersion("not-a-version"));
        assertFalse(VantaLoaderInstaller.supportsMinecraftVersion(null));
    }

    @Test
    void resolvesAgentInsideTheInstanceAndRejectsPathLikeReleaseIdentifiers() {
        Instance instance = new Instance("test", "Test", "1.21.11",
                "VantaLoader", "nightly", temporaryDirectory);
        assertEquals(temporaryDirectory.resolve(
                        "libraries/dev/itsdropper/vantaloader/nightly/VantaLoader-agent.jar"),
                VantaLoaderInstaller.agentPath(instance));
        assertEquals(temporaryDirectory.resolve(
                        "libraries/dev/itsdropper/vantaloader/nightly-0123456789abcdef0123456789abcdef01234567/VantaLoader-agent.jar"),
                VantaLoaderInstaller.agentPath(instance, "nightly-0123456789abcdef0123456789abcdef01234567"));

        Instance invalid = new Instance("test", "Test", "1.21.11",
                "VantaLoader", "../outside", temporaryDirectory);
        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> VantaLoaderInstaller.agentPath(invalid));
    }

    @Test
    void validatesAgentManifestAndRequiredBundledClasses() throws Exception {
        Path valid = createAgentJar(true);
        Path missingClasses = createAgentJar(false);
        assertTrue(VantaLoaderInstaller.isValidAgentJar(valid));
        assertFalse(VantaLoaderInstaller.isValidAgentJar(missingClasses));
        assertFalse(VantaLoaderInstaller.isValidAgentJar(
                temporaryDirectory.resolve("missing.jar")));
    }

    private Path createAgentJar(boolean includeClasses) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue(
                "Premain-Class", "dev.itsdropper.vantaloader.bootstrap.VantaLoaderAgent");
        Path jar = temporaryDirectory.resolve(includeClasses ? "valid.jar" : "incomplete.jar");
        try (JarOutputStream output = new JarOutputStream(
                java.nio.file.Files.newOutputStream(jar), manifest)) {
            if (includeClasses) {
                output.putNextEntry(new JarEntry(
                        "dev/itsdropper/vantaloader/bootstrap/VantaLoaderAgent.class"));
                output.write(new byte[]{0});
                output.closeEntry();
                output.putNextEntry(new JarEntry("com/google/gson/JsonParser.class"));
                output.write(new byte[]{0});
                output.closeEntry();
                for (String entry : java.util.List.of(
                        "org/objectweb/asm/ClassReader.class",
                        "org/objectweb/asm/tree/ClassNode.class",
                        "org/objectweb/asm/commons/AdviceAdapter.class",
                        "org/objectweb/asm/util/CheckClassAdapter.class",
                        "org/objectweb/asm/tree/analysis/Analyzer.class")) {
                    output.putNextEntry(new JarEntry(entry));
                    output.write(new byte[]{0});
                    output.closeEntry();
                }
            }
        }
        return jar;
    }
}
