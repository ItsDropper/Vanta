package dev.itsdropper.vanta.launcher.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.modrinth.*;
import dev.itsdropper.vanta.launcher.model.Instance;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ModrinthDependencyResolverGraphTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TempDir
    Path instanceDirectory;

    private ModrinthVersion version(String project, String id, String number, String dependencyJson)
            throws Exception {
        String json = """
                {
                  "id": "%s",
                  "project_id": "%s",
                  "version_number": "%s",
                  "version_type": "release",
                  "game_versions": ["1.21.1"],
                  "loaders": ["fabric"],
                  "files": [{
                    "url": "",
                    "filename": "%s.jar",
                    "primary": true,
                    "size": 1
                  }],
                  "dependencies": %s
                }
                """.formatted(id, project, number, id, dependencyJson);
        return JSON.readValue(json, ModrinthVersion.class);
    }

    private ModrinthProject project(String id) throws Exception {
        return JSON.readValue("{\"id\":\"" + id + "\",\"project_id\":\"" + id + "\"}",
                ModrinthProject.class);
    }

    private Instance instance() {
        return new Instance("resolver-test", "Resolver test", "1.21.1", "fabric", "0.16.0",
                instanceDirectory);
    }

    @Test
    void backtracksRootCandidateWhenItsDependencyViolatesAnotherRequiredRoot() throws Exception {
        ModrinthVersion appLatest = version("app", "app-v2", "2.0.0",
                "[{\"project_id\":\"library\",\"version_id\":\"library-v2\",\"dependency_type\":\"required\"}]");
        ModrinthVersion appCompatible = version("app", "app-v1", "1.0.0",
                "[{\"project_id\":\"library\",\"version_id\":\"library-v1\",\"dependency_type\":\"required\"}]");
        ModrinthVersion libraryLatest = version("library", "library-v2", "2.0.0", "[]");
        ModrinthVersion libraryRequired = version("library", "library-v1", "1.0.0", "[]");

        ModrinthClient client = new ModrinthClient() {
            @Override
            public List<ModrinthVersion> getVersions(String projectId) {
                return switch (projectId) {
                    case "app" -> List.of(appLatest, appCompatible);
                    case "library" -> List.of(libraryLatest, libraryRequired);
                    default -> List.of();
                };
            }
        };
        ModrinthDependencyResolver resolver =
                new ModrinthDependencyResolver(client, new InstalledModScanner());

        List<ModrinthService.ResolvedMod> graph = resolver.resolveModGraph(
                instance(), List.of(project("app"), project("library")),
                "library", List.of("1.0.0"));

        Map<String, String> selected = graph.stream().collect(
                java.util.stream.Collectors.toMap(
                        ModrinthService.ResolvedMod::projectId,
                        resolved -> resolved.version().getId()));

        assertEquals(Map.of("app", "app-v1", "library", "library-v1"), selected);
    }

    @Test
    void requiredDependencyVersionIsNotSilentlyReplacedByAnotherVersion() throws Exception {
        ModrinthVersion app = version("app", "app-v1", "1.0.0",
                "[{\"project_id\":\"library\",\"version_id\":\"library-v2\",\"dependency_type\":\"required\"}]");
        ModrinthVersion libraryV1 = version("library", "library-v1", "1.0.0", "[]");
        ModrinthVersion libraryV2 = version("library", "library-v2", "2.0.0", "[]");
        ModrinthClient client = new ModrinthClient() {
            @Override
            public List<ModrinthVersion> getVersions(String projectId) {
                return switch (projectId) {
                    case "app" -> List.of(app);
                    case "library" -> List.of(libraryV1, libraryV2);
                    default -> List.of();
                };
            }
        };
        ModrinthDependencyResolver resolver =
                new ModrinthDependencyResolver(client, new InstalledModScanner());

        List<ModrinthService.ResolvedMod> graph = resolver.resolveModGraph(
                instance(), List.of(project("app")), null, List.of());

        assertEquals(2, graph.size());
        assertTrue(graph.stream().anyMatch(mod ->
                mod.projectId().equals("library") && mod.version().getId().equals("library-v2")));
    }

    @Test
    void unsatisfiableRequiredDependencyFailsInsteadOfReturningPartialGraph() throws Exception {
        ModrinthVersion app = version("app", "app-v1", "1.0.0",
                "[{\"project_id\":\"missing\",\"version_id\":null,\"dependency_type\":\"required\"}]");
        ModrinthClient client = new ModrinthClient() {
            @Override
            public List<ModrinthVersion> getVersions(String projectId) {
                return projectId.equals("app") ? List.of(app) : List.of();
            }
        };
        ModrinthDependencyResolver resolver =
                new ModrinthDependencyResolver(client, new InstalledModScanner());

        java.io.IOException failure = assertThrows(java.io.IOException.class, () ->
                resolver.resolveModGraph(instance(), List.of(project("app")), null, List.of()));

        assertTrue(failure.getMessage().contains("Could not resolve compatible dependency graph"));
    }
}
