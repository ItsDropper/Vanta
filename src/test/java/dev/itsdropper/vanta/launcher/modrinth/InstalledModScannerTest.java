package dev.itsdropper.vanta.launcher.modrinth;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import static org.junit.jupiter.api.Assertions.*;

class InstalledModScannerTest {
    @TempDir Path temporaryDirectory;

    private Path jar(String name, String entryName, String metadata) throws IOException {
        Path file = temporaryDirectory.resolve(name);
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(file))) {
            output.putNextEntry(new JarEntry(entryName));
            output.write(metadata.getBytes(StandardCharsets.UTF_8));
            output.closeEntry();
        }
        return file;
    }

    @Test void readsFabricRequiredOptionalAndBreakMetadata() throws Exception {
        Path file = jar("fabric.jar", "fabric.mod.json", """
                {"id":"fabric_example","version":"1.2.0",
                 "depends":{"fabricloader":">=0.15.0","minecraft":"1.21.x"},
                 "suggests":{"modmenu":">=10.0.0"},"breaks":{"old_api":"<2.0.0"}}
                """);
        InstalledMod mod = new InstalledModScanner().scanFile(file);
        assertNotNull(mod);
        assertEquals("fabric_example", mod.getModId());
        assertEquals(2, mod.getDependencies().size());
        assertTrue(mod.getDependencies().stream().anyMatch(DependencyRequirement::isOptional));
        assertEquals("old_api", mod.getBreaks().get(0).getModId());
    }

    @Test void readsQuiltLoaderDependenciesAndOptionalEdges() throws Exception {
        Path file = jar("quilt.jar", "quilt.mod.json", """
                {"quilt_loader":{"id":"quilt_example","version":"2.1.0",
                 "depends":[{"id":"quilt_loader","versions":">=0.26.0"},
                 {"id":"shared_api","versions":[">=2.0.0","<3.0.0"],"optional":true}],
                 "breaks":[{"id":"broken_api","versions":"<1.0.0"}]}}
                """);
        InstalledMod mod = new InstalledModScanner().scanFile(file);
        assertNotNull(mod);
        assertEquals("quilt_example", mod.getModId());
        assertEquals(">=0.26.0", mod.getDependencies().get(0).getVersionConstraint());
        assertTrue(mod.getDependencies().get(1).isOptional());
        assertEquals(">=2.0.0 || <3.0.0", mod.getDependencies().get(1).getVersionConstraint());
        assertEquals("broken_api", mod.getBreaks().get(0).getModId());
    }

    @Test void supportsQuiltAnyAndAllVersionSpecifierObjects() throws Exception {
        Path file = jar("quilt-complex.jar", "quilt.mod.json", """
                {"quilt_loader":{"id":"quilt_complex","version":"1.0.0",
                 "depends":[
                   {"id":"bounded_api","versions":{"all":[">=1.2.0","<2.0.0"]}},
                   {"id":"alternative_api","versions":{"any":[">=3.0.0","<1.0.0"]}},
                   {"id":"legacy_array","versions":[">=4.0.0","<5.0.0"]}
                 ]}}
                """);
        InstalledMod mod = new InstalledModScanner().scanFile(file);
        assertNotNull(mod);
        assertEquals(">=1.2.0 <2.0.0", mod.getDependencies().get(0).getVersionConstraint());
        assertEquals(">=3.0.0 || <1.0.0", mod.getDependencies().get(1).getVersionConstraint());
        assertEquals(">=4.0.0 || <5.0.0", mod.getDependencies().get(2).getVersionConstraint());
    }

    @Test void readsForgeTomlDependenciesAndConvertsRanges() throws Exception {
        Path file = jar("forge.jar", "META-INF/mods.toml", """
                modLoader="javafml"
                loaderVersion="[47,)"
                license="MIT"
                [[mods]]
                modId="forge_example"
                version="1.4.0"
                [[dependencies.forge_example]]
                modId="minecraft"
                mandatory=true
                versionRange="[1.20.1,1.21)"
                side="BOTH"
                [[dependencies.forge_example]]
                modId="optional_api"
                mandatory=false
                versionRange="[2.0,)"
                side="CLIENT"

                [[dependencies.forge_example]]
                modId="multi_range_api"
                mandatory=true
                versionRange="[1.0,2.0),[3.0,4.0)"
                side="BOTH"
                """);
        InstalledMod mod = new InstalledModScanner().scanFile(file);
        assertNotNull(mod);
        assertEquals("forge_example", mod.getModId());
        assertEquals(">=1.20.1 <1.21", mod.getDependencies().get(0).getVersionConstraint());
        assertFalse(mod.getDependencies().get(0).isOptional());
        assertTrue(mod.getDependencies().get(1).isOptional());
        assertEquals(">=2.0", mod.getDependencies().get(1).getVersionConstraint());
        assertEquals(">=1.0 <2.0 || >=3.0 <4.0",
                mod.getDependencies().get(2).getVersionConstraint());
    }

    @Test void readsNeoForgeDedicatedDescriptorAndIncompatibilities() throws Exception {
        Path file = jar("neoforge.jar", "META-INF/neoforge.mods.toml", """
                modLoader="javafml"
                loaderVersion="[4,)"
                license="MIT"
                [[mods]]
                modId="neo_example"
                version="3.0.0"
                [[dependencies.neo_example]]
                modId="neoforge"
                type="required"
                versionRange="[21.0,)"
                side="BOTH"
                [[dependencies.neo_example]]
                modId="legacy_api"
                type="incompatible"
                versionRange="[1.0,2.0)"
                side="BOTH"
                """);
        InstalledMod mod = new InstalledModScanner().scanFile(file);
        assertNotNull(mod);
        assertEquals("neo_example", mod.getModId());
        assertEquals("neoforge", mod.getDependencies().get(0).getModId());
        assertEquals("legacy_api", mod.getBreaks().get(0).getModId());
    }

    @Test void ignoresUnrecognizedAndMalformedMetadata() throws Exception {
        Path unknown = jar("unknown.jar", "META-INF/MANIFEST.MF", "Manifest-Version: 1.0\n");
        Path malformed = jar("malformed.jar", "META-INF/mods.toml", "[[mods]\nmodId = ");
        InstalledModScanner scanner = new InstalledModScanner();
        assertNull(scanner.scanFile(unknown));
        assertNull(scanner.scanFile(malformed));
    }
}
