package dev.itsdropper.vanta.launcher.modrinth;

import dev.itsdropper.vanta.json.JsonNode;
import dev.itsdropper.vanta.json.ObjectMapper;
import dev.itsdropper.vanta.launcher.model.Instance;

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

    /**
     * Reads supported loader metadata from one installed JAR.
     *
     * <p>Returns {@code null} for non-JAR files, unsupported metadata formats, malformed
     * archives, and files that cannot be read. The caller remains responsible
     * for recording the artifact itself even when metadata is unavailable.</p>
     */
    public InstalledMod scanFile(Path file) {
        if (file == null
                || !Files.isRegularFile(file, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(file)
                || !file.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".jar")) {
            return null;
        }
        return readMod(file);
    }

    private static final int MAX_METADATA_BYTES = 1024 * 1024;

    private InstalledMod readMod(Path file) {
        try (JarFile jar = new JarFile(file.toFile())) {
            InstalledMod metadata = tryRead(() -> readFabricMetadata(jar, file));
            if (metadata != null) return metadata;
            metadata = tryRead(() -> readQuiltMetadata(jar, file));
            if (metadata != null) return metadata;
            metadata = tryRead(() -> readTomlMetadata(jar, "META-INF/neoforge.mods.toml", file));
            if (metadata != null) return metadata;
            return tryRead(() -> readTomlMetadata(jar, "META-INF/mods.toml", file));
        } catch (Exception ignored) {
            return null;
        }
    }

    @FunctionalInterface
    private interface MetadataReader {
        InstalledMod read() throws IOException;
    }

    private InstalledMod tryRead(MetadataReader reader) {
        try {
            return reader.read();
        } catch (Exception ignored) {
            return null;
        }
    }

    private InstalledMod readFabricMetadata(JarFile jar, Path file) throws IOException {
        JsonNode root = readJsonEntry(jar, "fabric.mod.json");
        if (root == null) return null;
        String id = text(root, "id"), version = text(root, "version");
        if (id == null || version == null) return null;
        List<DependencyRequirement> dependencies = new ArrayList<>();
        addJsonObjectRequirements(dependencies, root.get("depends"), false);
        addJsonObjectRequirements(dependencies, root.get("recommends"), true);
        addJsonObjectRequirements(dependencies, root.get("suggests"), true);
        List<DependencyRequirement> breaks = new ArrayList<>();
        addJsonObjectRequirements(breaks, root.get("breaks"), false);
        return new InstalledMod(id, version, file.getFileName().toString(), dependencies, breaks);
    }

    private InstalledMod readQuiltMetadata(JarFile jar, Path file) throws IOException {
        JsonNode root = readJsonEntry(jar, "quilt.mod.json");
        if (root == null) return null;
        JsonNode loader = root.get("quilt_loader");
        if (loader == null || !loader.isObject()) return null;
        String id = text(loader, "id"), version = text(loader, "version");
        if (id == null || version == null) return null;
        List<DependencyRequirement> dependencies = new ArrayList<>();
        addQuiltRequirements(dependencies, loader.get("depends"));
        List<DependencyRequirement> breaks = new ArrayList<>();
        addQuiltRequirements(breaks, loader.get("breaks"));
        return new InstalledMod(id, version, file.getFileName().toString(), dependencies, breaks);
    }

    private void addJsonObjectRequirements(List<DependencyRequirement> destination, JsonNode object,
                                           boolean optional) {
        if (object == null || !object.isObject()) return;
        Iterator<String> names = object.fieldNames();
        while (names.hasNext()) {
            String id = names.next();
            if (id != null && !id.isBlank()) {
                destination.add(new DependencyRequirement(id, jsonConstraint(object.get(id)), optional));
            }
        }
    }

    private void addQuiltRequirements(List<DependencyRequirement> destination, JsonNode array) {
        if (array == null || !array.isArray()) return;
        for (JsonNode dependency : array) {
            String id = text(dependency, "id");
            if (id == null || "server".equalsIgnoreCase(dependency.path("environment").asText())) {
                continue;
            }
            destination.add(new DependencyRequirement(
                    id, quiltConstraint(dependency.get("versions")),
                    dependency.path("optional").asBoolean(false)));
        }
    }

    private String quiltConstraint(JsonNode value) {
        if (value != null && value.isObject()) {
            JsonNode any = value.get("any");
            JsonNode all = value.get("all");
            if (any != null && any.isArray()) return joinQuiltConstraints(any, " || ");
            if (all != null && all.isArray()) return joinQuiltConstraints(all, " ");
            return "*";
        }
        // Quilt's legacy array form is explicitly an OR, not an AND.
        return jsonConstraint(value);
    }

    private String joinQuiltConstraints(JsonNode values, String separator) {
        List<String> constraints = new ArrayList<>();
        for (JsonNode value : values) {
            String constraint = quiltConstraint(value);
            if (!constraint.isBlank() && !"*".equals(constraint)) constraints.add(constraint);
        }
        return constraints.isEmpty() ? "*" : String.join(separator, constraints);
    }

    private String jsonConstraint(JsonNode value) {
        if (value == null || value.isNull()) return "*";
        if (value.isTextual()) return value.asText();
        if (value.isArray()) {
            List<String> alternatives = new ArrayList<>();
            for (JsonNode item : value) {
                if (item.isTextual() && !item.asText().isBlank()) alternatives.add(item.asText());
            }
            return alternatives.isEmpty() ? "*" : String.join(" || ", alternatives);
        }
        return "*";
    }

    private String text(JsonNode node, String key) {
        JsonNode value = node == null ? null : node.get(key);
        if (value == null || value.isNull()) return null;
        String result = value.asText();
        return result == null || result.isBlank() ? null : result.trim();
    }

    private JsonNode readJsonEntry(JarFile jar, String path) throws IOException {
        JarEntry entry = jar.getJarEntry(path);
        return entry == null ? null : objectMapper.readTree(readBounded(jar, entry));
    }

    private InstalledMod readTomlMetadata(JarFile jar, String path, Path file) throws IOException {
        JarEntry entry = jar.getJarEntry(path);
        if (entry == null) return null;
        org.tomlj.TomlParseResult document = org.tomlj.Toml.parse(
                new String(readBounded(jar, entry), java.nio.charset.StandardCharsets.UTF_8));
        if (document.hasErrors()) return null;
        org.tomlj.TomlArray mods = document.getArray("mods");
        if (mods == null || mods.size() == 0) return null;

        for (int i = 0; i < mods.size(); i++) {
            org.tomlj.TomlTable mod = mods.getTable(i);
            if (mod == null) continue;
            String id = mod.getString("modId"), version = mod.getString("version");
            if (id == null || id.isBlank()) continue;
            if (version == null || version.isBlank() || version.startsWith("$")) version = readManifestVersion(jar);
            if (version == null || version.isBlank()) continue;

            List<DependencyRequirement> dependencies = new ArrayList<>();
            List<DependencyRequirement> breaks = new ArrayList<>();
            org.tomlj.TomlTable tables = document.getTable("dependencies");
            org.tomlj.TomlArray entries = tables == null ? null : tables.getArray(java.util.List.of(id));
            if (entries != null) {
                for (int j = 0; j < entries.size(); j++) {
                    org.tomlj.TomlTable dependency = entries.getTable(j);
                    if (dependency == null) continue;
                    String dependencyId = dependency.getString("modId");
                    if (dependencyId == null || dependencyId.isBlank()) continue;
                    String type = dependency.getString("type");
                    String range = forgeVersionRange(dependency.getString("versionRange"));
                    if ("incompatible".equalsIgnoreCase(type)) {
                        breaks.add(new DependencyRequirement(dependencyId, range));
                        continue;
                    }
                    Boolean mandatory = dependency.getBoolean("mandatory");
                    boolean optional = "optional".equalsIgnoreCase(type)
                            || "discouraged".equalsIgnoreCase(type)
                            || Boolean.FALSE.equals(mandatory);
                    if ("SERVER".equalsIgnoreCase(dependency.getString("side"))) continue;
                    dependencies.add(new DependencyRequirement(dependencyId, range, optional));
                }
            }
            return new InstalledMod(id, version, file.getFileName().toString(), dependencies, breaks);
        }
        return null;
    }

    private String readManifestVersion(JarFile jar) {
        try {
            java.util.jar.Manifest manifest = jar.getManifest();
            if (manifest == null) return null;
            java.util.jar.Attributes attributes = manifest.getMainAttributes();
            String version = attributes.getValue("Implementation-Version");
            return version == null ? attributes.getValue("Specification-Version") : version;
        } catch (IOException ignored) { return null; }
    }

    private String forgeVersionRange(String range) {
        if (range == null || range.isBlank() || "*".equals(range.trim())) return "*";
        String value = range.trim();
        if (!(value.startsWith("[") || value.startsWith("("))) return value;

        List<String> alternatives = new ArrayList<>();
        int cursor = 0;
        while (cursor < value.length()) {
            while (cursor < value.length()
                    && (Character.isWhitespace(value.charAt(cursor)) || value.charAt(cursor) == ',')) {
                cursor++;
            }
            if (cursor >= value.length()) break;
            char opening = value.charAt(cursor);
            if (opening != '[' && opening != '(') return value;

            int closing = -1;
            for (int i = cursor + 1; i < value.length(); i++) {
                char current = value.charAt(i);
                if (current == ']' || current == ')') {
                    closing = i;
                    break;
                }
            }
            if (closing < 0) return value;

            String inner = value.substring(cursor + 1, closing).trim();
            int comma = inner.indexOf(',');
            if (comma < 0) {
                if (opening != '[' || value.charAt(closing) != ']') return value;
                if (!inner.isBlank()) alternatives.add("=" + inner);
            } else {
                String lower = inner.substring(0, comma).trim();
                String upper = inner.substring(comma + 1).trim();
                List<String> bounds = new ArrayList<>(2);
                if (!lower.isEmpty()) bounds.add((opening == '[' ? ">=" : ">") + lower);
                if (!upper.isEmpty()) bounds.add((value.charAt(closing) == ']' ? "<=" : "<") + upper);
                if (!bounds.isEmpty()) alternatives.add(String.join(" ", bounds));
            }
            cursor = closing + 1;
        }
        return alternatives.isEmpty() ? "*" : String.join(" || ", alternatives);
    }

    private byte[] readBounded(JarFile jar, JarEntry entry) throws IOException {
        if (entry.getSize() > MAX_METADATA_BYTES) throw new IOException("Mod metadata entry exceeds the 1 MiB safety limit.");
        try (InputStream input = jar.getInputStream(entry)) {
            byte[] bytes = input.readNBytes(MAX_METADATA_BYTES + 1);
            if (bytes.length > MAX_METADATA_BYTES) throw new IOException("Mod metadata entry exceeds the 1 MiB safety limit.");
            return bytes;
        }
    }

    /**
     * Reads every Fabric module ID/version exposed by a Fabric JAR,
     * including Fabric API's nested JARs. This lets the dependency
     * resolver inspect one API JAR once and reuse the complete result.
     */
    /**
     * Returns mandatory metadata dependencies that are not provided by any
     * installed mod or by Fabric API's nested module JARs.
     */
    public List<MissingDependency> findMissingRequiredDependencies(Instance instance) throws IOException {
        List<InstalledMod> installed = scan(instance);
        java.util.Set<String> installedIds = new java.util.HashSet<>();
        Path modsDirectory = instance.getDirectory().resolve("mods");
        if (Files.isDirectory(modsDirectory)) {
            try (var files = Files.list(modsDirectory)) {
                for (Path file : files.filter(Files::isRegularFile).toList()) {
                    InstalledMod metadata = scanFile(file);
                    if (metadata != null && metadata.getModId() != null) {
                        installedIds.add(metadata.getModId().toLowerCase(java.util.Locale.ROOT));
                    }
                    for (String moduleId : scanFabricModuleVersions(file).keySet()) {
                        installedIds.add(moduleId.toLowerCase(java.util.Locale.ROOT));
                    }
                }
            }
        }

        // Fabric Loader and Fabric API are separate components. Fabric's
        // legacy "fabric" dependency ID is provided by the Fabric API
        // aggregate mod (fabric-api), not by the loader's fabricloader ID.
        // Treat the aggregate API as satisfying that alias when it is present.
        if (installedIds.contains("fabric-api")) {
            installedIds.add("fabric");
        }

        java.util.Map<String, MissingDependency> missing = new java.util.LinkedHashMap<>();
        for (InstalledMod mod : installed) {
            if (mod == null || mod.getDependencies() == null) continue;
            for (DependencyRequirement dependency : mod.getDependencies()) {
                if (dependency == null || dependency.isOptional()
                        || dependency.getModId() == null || dependency.getModId().isBlank()) continue;
                String id = dependency.getModId().trim();
                String normalized = id.toLowerCase(java.util.Locale.ROOT);
                if (installedIds.contains(normalized)
                        || java.util.Set.of("minecraft", "fabricloader", "java", "java-runtime")
                        .contains(normalized)) continue;
                missing.putIfAbsent(normalized, new MissingDependency(
                        id, dependency.getVersionConstraint(), mod.getModId()));
            }
        }
        return List.copyOf(missing.values());
    }

    public record MissingDependency(String modId, String versionConstraint, String requestingModId) {}

    public java.util.Map<String, String> scanFabricModuleVersions(
            Path file
    ) {
        java.util.Map<String, String> modules =
                new java.util.HashMap<>();

        if (file == null) {
            return modules;
        }

        try (JarFile jar = new JarFile(file.toFile())) {
            scanFabricModuleVersions(jar, modules);
        } catch (Exception ignored) {
        }

        return modules;
    }

    private void scanFabricModuleVersions(
            JarFile jar,
            java.util.Map<String, String> modules
    ) {
        try {
            JarEntry metadataEntry = jar.getJarEntry("fabric.mod.json");
            if (metadataEntry == null) {
                return;
            }

            JsonNode root;
            try (InputStream input = jar.getInputStream(metadataEntry)) {
                root = objectMapper.readTree(input);
            }

            JsonNode idNode = root.get("id");
            JsonNode versionNode = root.get("version");
            if (idNode != null && versionNode != null) {
                String id = idNode.asText();
                String version = versionNode.asText();
                if (id != null && !id.isBlank()
                        && version != null && !version.isBlank()) {
                    modules.put(id, version);
                }
            }

            JsonNode jarsNode = root.get("jars");
            if (jarsNode == null || !jarsNode.isArray()) {
                return;
            }

            for (JsonNode jarNode : jarsNode) {
                JsonNode fileNode = jarNode.get("file");
                if (fileNode == null || fileNode.asText().isBlank()) {
                    continue;
                }

                JarEntry nestedEntry = jar.getJarEntry(fileNode.asText());
                if (nestedEntry == null) {
                    continue;
                }

                Path tempNestedJar = Files.createTempFile(
                        "vanta-fabric-api-module-", ".jar");
                try {
                    try (InputStream nestedInput = jar.getInputStream(nestedEntry)) {
                        Files.copy(nestedInput, tempNestedJar,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    try (JarFile nestedJar = new JarFile(tempNestedJar.toFile())) {
                        scanFabricModuleVersions(nestedJar, modules);
                    }
                } finally {
                    Files.deleteIfExists(tempNestedJar);
                }
            }
        } catch (Exception ignored) {
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