package dev.itsdropper.vanta.launcher.instance;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PrismLauncherImporterTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void parsesPrismIniValuesAndSectionsWithoutTruncatingEqualsSigns() throws Exception {
        Path config = temporaryDirectory.resolve("prismlauncher.cfg");
        Files.writeString(config, "[General]\nInstanceDir=instances\nJvmArgs=-Dexample=a=b\n# comment\n");

        Map<String, String> values = PrismLauncherImporter.readIni(config);

        assertEquals("instances", values.get("InstanceDir"));
        assertEquals("-Dexample=a=b", values.get("JvmArgs"));
        assertEquals("instances", values.get("General/InstanceDir"));
    }

    @Test
    void resolvesRelativeAndAbsoluteInstanceDirectories() {
        Path relative = PrismLauncherImporter.resolveInstancesDirectory(
                temporaryDirectory, Map.of("InstanceDir", "custom-instances"));
        assertEquals(temporaryDirectory.resolve("custom-instances").normalize(), relative);

        Path absoluteRoot = temporaryDirectory.resolve("external-instances").toAbsolutePath();
        Path absolute = PrismLauncherImporter.resolveInstancesDirectory(
                temporaryDirectory, Map.of("InstanceDir", absoluteRoot.toString()));
        assertEquals(absoluteRoot.normalize(), absolute);
    }

    @Test
    void recognizesPrismLoaderComponentIds() {
        assertEquals("Fabric", PrismLauncherImporter.mapLoader("net.fabricmc.fabric-loader"));
        assertEquals("Quilt", PrismLauncherImporter.mapLoader("org.quiltmc.quilt-loader"));
        assertEquals("NeoForge", PrismLauncherImporter.mapLoader("net.neoforged"));
        assertEquals("Forge", PrismLauncherImporter.mapLoader("net.minecraftforge"));
        assertEquals("Legacy Fabric", PrismLauncherImporter.mapLoader("net.legacyfabric.legacy-fabric"));
        assertNull(PrismLauncherImporter.mapLoader("org.lwjgl3"));
    }

    @Test
    void recognizesLauncherRootsWithoutFollowingSymbolicLinks() throws Exception {
        Path root = temporaryDirectory.resolve("PrismLauncher");
        Files.createDirectories(root.resolve("instances"));
        assertTrue(PrismLauncherImporter.looksLikePrismRoot(root));
        assertFalse(PrismLauncherImporter.looksLikePrismRoot(temporaryDirectory.resolve("not-prism")));
    }
}
