package org.example.launcher.modrinth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.example.launcher.model.Instance;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class InstalledModScanner {

    private final ObjectMapper objectMapper =
            new ObjectMapper();

    public List<InstalledMod> scan(
            Instance instance
    ) throws IOException {

        Path modsDirectory =
                instance.getDirectory()
                        .resolve("mods");

        if (!Files.exists(modsDirectory)) {
            return List.of();
        }

        List<InstalledMod> mods =
                new ArrayList<>();

        try (var stream =
                     Files.list(modsDirectory)) {

            for (Path file : stream.toList()) {

                if (!Files.isRegularFile(file)) {
                    continue;
                }

                if (!file.getFileName()
                        .toString()
                        .toLowerCase()
                        .endsWith(".jar")) {

                    continue;
                }

                InstalledMod mod =
                        readMod(file);

                if (mod != null) {
                    mods.add(mod);
                }
            }
        }

        return mods;
    }

    private InstalledMod readMod(
            Path file
    ) {

        try (JarFile jar =
                     new JarFile(file.toFile())) {

            JarEntry entry =
                    jar.getJarEntry(
                            "fabric.mod.json"
                    );

            if (entry == null) {
                return null;
            }

            try (InputStream input =
                         jar.getInputStream(entry)) {

                JsonNode root =
                        objectMapper.readTree(input);

                JsonNode idNode =
                        root.get("id");

                JsonNode versionNode =
                        root.get("version");

                if (idNode == null
                        || versionNode == null) {

                    return null;
                }

                String modId =
                        idNode.asText();

                String version =
                        versionNode.asText();

                if (modId.isBlank()
                        || version.isBlank()) {

                    return null;
                }

                List<DependencyRequirement> dependencies =
                        new ArrayList<>();

                JsonNode dependsNode =
                        root.get("depends");

                if (dependsNode != null
                        && dependsNode.isObject()) {

                    Iterator<String> names =
                            dependsNode.fieldNames();

                    while (names.hasNext()) {

                        String dependencyId =
                                names.next();

                        JsonNode requirement =
                                dependsNode.get(
                                        dependencyId
                                );

                        dependencies.add(
                                new DependencyRequirement(
                                        dependencyId,
                                        requirement.asText()
                                )
                        );
                    }
                }

                List<DependencyRequirement> breaks =
                        new ArrayList<>();

                JsonNode breaksNode =
                        root.get("breaks");

                if (breaksNode != null
                        && breaksNode.isObject()) {

                    Iterator<String> breakNames =
                            breaksNode.fieldNames();

                    while (breakNames.hasNext()) {

                        String dependencyId =
                                breakNames.next();

                        JsonNode requirement =
                                breaksNode.get(
                                        dependencyId
                                );

                        breaks.add(
                                new DependencyRequirement(
                                        dependencyId,
                                        requirement.asText()
                                )
                        );
                    }
                }

                return new InstalledMod(
                        modId,
                        version,
                        file.getFileName().toString(),
                        dependencies,
                        breaks
                );
            }

        } catch (Exception ignored) {

            return null;
        }
    }

    public boolean containsFabricModId(
            Path file,
            String targetModId
    ) {

        if (file == null
                || targetModId == null
                || targetModId.isBlank()) {

            return false;
        }

        try (JarFile jar =
                     new JarFile(file.toFile())) {

            return containsFabricModId(
                    jar,
                    targetModId
            );

        } catch (Exception ignored) {

            return false;
        }
    }

    private boolean containsFabricModId(
            JarFile jar,
            String targetModId
    ) {

        try {

            /*
             * Check the main fabric.mod.json first.
             */
            JarEntry metadataEntry =
                    jar.getJarEntry(
                            "fabric.mod.json"
                    );

            if (metadataEntry != null) {

                try (InputStream input =
                             jar.getInputStream(
                                     metadataEntry
                             )) {

                    JsonNode root =
                            objectMapper.readTree(input);

                    JsonNode idNode =
                            root.get("id");

                    if (idNode != null
                            && targetModId.equals(
                            idNode.asText()
                    )) {

                        return true;
                    }

                    /*
                     * Fabric API lists its bundled modules in the
                     * "jars" array. Inspect those nested JARs.
                     */
                    JsonNode jarsNode =
                            root.get("jars");

                    if (jarsNode != null
                            && jarsNode.isArray()) {

                        for (JsonNode jarNode :
                                jarsNode) {

                            JsonNode fileNode =
                                    jarNode.get("file");

                            if (fileNode == null) {
                                continue;
                            }

                            String nestedPath =
                                    fileNode.asText();

                            if (nestedPath == null
                                    || nestedPath.isBlank()) {
                                continue;
                            }

                            JarEntry nestedEntry =
                                    jar.getJarEntry(
                                            nestedPath
                                    );

                            if (nestedEntry == null) {
                                continue;
                            }

                            Path tempNestedJar =
                                    Files.createTempFile(
                                            "vanta-fabric-api-",
                                            ".jar"
                                    );

                            try {

                                try (InputStream nestedInput =
                                             jar.getInputStream(
                                                     nestedEntry
                                             )) {

                                    Files.copy(
                                            nestedInput,
                                            tempNestedJar,
                                            java.nio.file.StandardCopyOption
                                                    .REPLACE_EXISTING
                                    );
                                }

                                if (containsFabricModId(
                                        tempNestedJar,
                                        targetModId
                                )) {

                                    return true;
                                }

                            } finally {

                                Files.deleteIfExists(
                                        tempNestedJar
                                );
                            }
                        }
                    }
                }
            }

            return false;

        } catch (Exception ignored) {

            return false;
        }
    }

    public InstalledMod scanFile(
            Path file
    ) {
        return readMod(file);
    }

    public DependencyRequirement findDependencyRequirement(
            Instance instance,
            String requestingModId,
            String dependencyModId
    ) throws IOException {

        List<InstalledMod> mods =
                scan(instance);

        for (InstalledMod mod : mods) {

            if (!requestingModId.equals(
                    mod.getModId()
            )) {
                continue;
            }

            for (DependencyRequirement dependency :
                    mod.getDependencies()) {

                if (dependencyModId.equals(
                        dependency.getModId()
                )) {

                    return dependency;
                }
            }
        }

        return null;
    }
}