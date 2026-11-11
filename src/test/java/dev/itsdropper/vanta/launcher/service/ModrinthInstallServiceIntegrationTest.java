package dev.itsdropper.vanta.launcher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.instance.InstalledModManager;
import dev.itsdropper.vanta.launcher.instance.InstalledModRecord;
import dev.itsdropper.vanta.launcher.modrinth.InstalledModScanner;
import dev.itsdropper.vanta.launcher.modrinth.ModrinthClient;
import dev.itsdropper.vanta.launcher.modrinth.ModrinthVersion;
import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class ModrinthInstallServiceIntegrationTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path instanceDirectory;

    private Instance instance() {
        return new Instance("integration", "Integration", "1.21.1", "fabric", "0.16.0",
                instanceDirectory);
    }

    private ModrinthInstallService installer(ModrinthArtifactDownloader downloader) {
        ModrinthClient client = new ModrinthClient();
        InstalledModScanner scanner = new InstalledModScanner();
        ModrinthCatalogService catalog = new ModrinthCatalogService(client, scanner);
        ModrinthDependencyResolver resolver = new ModrinthDependencyResolver(client, scanner);
        return new ModrinthInstallService(client, scanner, catalog, resolver, downloader);
    }

    private ModrinthVersion version(String projectId, String versionId, String filename) throws IOException {
        String json = """
                {
                  "id": "%s",
                  "project_id": "%s",
                  "version_number": "1.0.0",
                  "version_type": "release",
                  "files": [{
                    "url": "https://downloads.invalid/%s",
                    "filename": "%s",
                    "primary": true,
                    "size": 0
                  }],
                  "dependencies": []
                }
                """.formatted(versionId, projectId, filename, filename);
        return JSON.readValue(json, ModrinthVersion.class);
    }

    private byte[] modJar(String modId, String version) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (JarOutputStream jar = new JarOutputStream(bytes)) {
            jar.putNextEntry(new JarEntry("fabric.mod.json"));
            jar.write(("{\"schemaVersion\":1,\"id\":\"" + modId
                    + "\",\"version\":\"" + version + "\"}").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            jar.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Test
    void installsResolvedDependencyGraphThroughRealFilesystemAndRegistryPath() throws Exception {
        Instance instance = instance();
        AtomicInteger downloads = new AtomicInteger();
        ModrinthInstallService installer = installer((url, destination) -> {
            String modId = url.endsWith("/api-1.0.jar") ? "fabric-api" : "example";
            Files.write(destination, modJar(modId, "1.0.0"));
            downloads.incrementAndGet();
        });

        List<Path> installed = installer.installResolvedGraph(instance, List.of(
                new ModrinthService.ResolvedMod("fabric-api-project",
                        version("fabric-api-project", "api-v1", "api-1.0.jar")),
                new ModrinthService.ResolvedMod("example-project",
                        version("example-project", "example-v1", "example-1.0.jar"))
        ));

        assertEquals(2, downloads.get());
        assertEquals(2, installed.size());
        assertTrue(installed.stream().allMatch(Files::isRegularFile));
        List<InstalledModRecord> registry = InstalledModManager.load(instance);
        assertEquals(2, registry.size());
        assertTrue(registry.stream().anyMatch(record -> "fabric-api-project".equals(record.getProjectId())));
        assertTrue(registry.stream().anyMatch(record -> "example-project".equals(record.getProjectId())));
        assertTrue(registry.stream().anyMatch(record -> "fabric-api".equals(record.getModId())));
        assertNoTransactionArtifacts(instance);
    }

    @Test
    void upgradesTrackedModAndRemovesPreviousFilenameOnlyAfterCommit() throws Exception {
        Instance instance = instance();
        Path mods = instanceDirectory.resolve("mods");
        Files.createDirectories(mods);
        Path oldJar = mods.resolve("example-1.0.jar");
        Files.write(oldJar, modJar("example", "1.0.0"));
        InstalledModManager.save(instance, List.of(new InstalledModRecord(
                "example-project", "v1", "example", "1.0.0", oldJar.getFileName().toString())));

        ModrinthInstallService installer = installer((url, destination) ->
                Files.write(destination, modJar("example", "2.0.0")));

        Path upgraded = installer.installResolvedGraph(instance, List.of(
                new ModrinthService.ResolvedMod("example-project",
                        version("example-project", "v2", "example-2.0.jar"))
        )).get(0);

        assertTrue(Files.isRegularFile(upgraded));
        assertFalse(Files.exists(oldJar));
        assertEquals("v2", InstalledModManager.findByProjectId(instance, "example-project").getVersionId());
        assertNoTransactionArtifacts(instance);
    }

    @Test
    void failureOnSecondGraphArtifactRestoresPriorInstallAndRemovesFirstNewArtifact() throws Exception {
        Instance instance = instance();
        Path mods = instanceDirectory.resolve("mods");
        Files.createDirectories(mods);
        Path existing = mods.resolve("existing.jar");
        Files.write(existing, modJar("existing", "1.0.0"));
        InstalledModManager.save(instance, List.of(new InstalledModRecord(
                "existing-project", "old", "existing", "1.0.0", "existing.jar")));
        String originalRegistry = Files.readString(instanceDirectory.resolve("vanta-mods.json"));
        AtomicInteger downloads = new AtomicInteger();

        ModrinthInstallService installer = installer((url, destination) -> {
            if (downloads.incrementAndGet() == 2) {
                throw new IOException("simulated transfer failure");
            }
            Files.write(destination, modJar("new-first", "1.0.0"));
        });

        IOException failure = assertThrows(IOException.class, () ->
                installer.installResolvedGraph(instance, List.of(
                        new ModrinthService.ResolvedMod("first-project",
                                version("first-project", "first-v1", "first.jar")),
                        new ModrinthService.ResolvedMod("second-project",
                                version("second-project", "second-v1", "second.jar"))
                )));

        assertTrue(failure.getMessage().contains("simulated transfer failure"));
        assertEquals("existing", new InstalledModScanner().scanFile(existing).getModId());
        assertFalse(Files.exists(mods.resolve("first.jar")));
        assertFalse(Files.exists(mods.resolve("second.jar")));
        assertEquals(originalRegistry, Files.readString(instanceDirectory.resolve("vanta-mods.json")));
        assertNoTransactionArtifacts(instance);
    }


    @Test
    void cancellationDuringSecondArtifactRestoresEarlierGraphChanges() throws Exception {
        Instance instance = instance();
        AtomicInteger downloads = new AtomicInteger();
        ModrinthInstallService installer = installer((url, destination) -> {
            if (downloads.incrementAndGet() == 2) {
                throw new InterruptedException("cancelled during dependency graph download");
            }
            Files.write(destination, modJar("first-mod", "1.0.0"));
        });

        assertThrows(InterruptedException.class, () ->
                installer.installResolvedGraph(instance, List.of(
                        new ModrinthService.ResolvedMod("first-project",
                                version("first-project", "first-v1", "first.jar")),
                        new ModrinthService.ResolvedMod("second-project",
                                version("second-project", "second-v1", "second.jar"))
                )));

        assertTrue(InstalledModManager.load(instance).isEmpty());
        Path mods = instanceDirectory.resolve("mods");
        assertFalse(Files.exists(mods.resolve("first.jar")));
        assertFalse(Files.exists(mods.resolve("second.jar")));
        assertNoTransactionArtifacts(instance);
    }

    private void assertNoTransactionArtifacts(Instance instance) throws IOException {
        try (var root = Files.list(instance.getDirectory())) {
            assertTrue(root.noneMatch(path -> path.getFileName().toString()
                    .startsWith(".vanta-graph-transaction-")));
        }
        Path mods = instance.getDirectory().resolve("mods");
        if (Files.isDirectory(mods)) {
            try (var files = Files.list(mods)) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString()
                        .startsWith(".vanta-stage-") || path.getFileName().toString()
                        .startsWith(".vanta-rollback-")));
            }
        }
    }
}
