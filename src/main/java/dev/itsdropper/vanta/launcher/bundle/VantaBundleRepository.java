package dev.itsdropper.vanta.launcher.bundle;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Loads and validates Vanta bundle manifests from trusted local sources.
 *
 * Network transport and signature verification intentionally live outside this
 * class: callers must authenticate remote bytes before passing them here.
 */
public final class VantaBundleRepository {
    public static final int MAX_MANIFEST_BYTES = 1024 * 1024;

    private static final Pattern SLUG = Pattern.compile("[a-z0-9][a-z0-9._-]{0,63}");
    private static final Pattern PROFILE_VERSION = Pattern.compile("[0-9]+\\.[0-9]+\\.[0-9]+(?:[-+][0-9A-Za-z.-]+)?");

    private final ObjectMapper mapper;

    public VantaBundleRepository() {
        this(new ObjectMapper().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES));
    }

    VantaBundleRepository(ObjectMapper mapper) {
        this.mapper = mapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    /**
     * Loads a profile shipped inside the launcher. Resource names are relative
     * to the classpath root and cannot escape the bundled profiles directory.
     */
    public VantaBundleManifest loadBundled(String resourceName) throws IOException {
        if (resourceName == null || !resourceName.matches("[a-zA-Z0-9._/-]+")
                || resourceName.startsWith("/") || resourceName.contains("..")
                || !resourceName.startsWith("profiles/")) {
            throw new IOException("Invalid bundled profile resource name.");
        }
        try (InputStream input = VantaBundleRepository.class.getClassLoader()
                .getResourceAsStream(resourceName)) {
            if (input == null) {
                throw new IOException("Bundled Vanta profile was not found: " + resourceName);
            }
            return parse(input.readNBytes(MAX_MANIFEST_BYTES + 1));
        }
    }

    public VantaBundleManifest load(Path path) throws IOException {
        if (path == null || !Files.isRegularFile(path)) {
            throw new IOException("Bundle manifest file does not exist.");
        }
        long size = Files.size(path);
        if (size <= 0 || size > MAX_MANIFEST_BYTES) {
            throw new IOException("Bundle manifest size is outside the allowed range.");
        }
        try (InputStream input = Files.newInputStream(path)) {
            return parse(input.readNBytes(MAX_MANIFEST_BYTES + 1));
        }
    }

    public VantaBundleManifest parse(byte[] json) throws IOException {
        if (json == null || json.length == 0 || json.length > MAX_MANIFEST_BYTES) {
            throw new IOException("Bundle manifest size is outside the allowed range.");
        }

        final VantaBundleManifest manifest;
        try {
            manifest = mapper.readValue(json, VantaBundleManifest.class);
        } catch (JsonProcessingException malformed) {
            throw new IOException("Bundle manifest JSON is invalid: " + malformed.getOriginalMessage(), malformed);
        }

        validate(manifest);
        return manifest;
    }

    public static void validate(VantaBundleManifest manifest) throws IOException {
        if (manifest == null) {
            throw new IOException("Bundle manifest is required.");
        }
        if (manifest.schemaVersion() != VantaBundleManifest.SUPPORTED_SCHEMA_VERSION) {
            throw new IOException("Unsupported bundle manifest schema version: " + manifest.schemaVersion());
        }
        requireSlug(manifest.profileId(), "profileId");
        if (manifest.profileVersion() == null || !PROFILE_VERSION.matcher(manifest.profileVersion()).matches()) {
            throw new IOException("profileVersion must be a semantic version with major, minor and patch components.");
        }
        if (manifest.minecraftVersions().isEmpty()) {
            throw new IOException("At least one supported Minecraft version is required.");
        }
        Set<String> versions = new HashSet<>();
        for (String version : manifest.minecraftVersions()) {
            if (version == null || version.isBlank() || !versions.add(version)) {
                throw new IOException("Minecraft versions must be non-empty and unique.");
            }
        }
        if (manifest.loader() == null || manifest.loader().isBlank()
                || !manifest.loader().equals(manifest.loader().toLowerCase(Locale.ROOT))) {
            throw new IOException("loader must be a non-empty lowercase loader identifier.");
        }
        if (manifest.bundles().isEmpty()) {
            throw new IOException("A profile must define at least one bundle.");
        }

        Set<String> bundleIds = new HashSet<>();
        for (VantaBundleManifest.Bundle bundle : manifest.bundles()) {
            if (bundle == null) {
                throw new IOException("Bundle entries cannot be null.");
            }
            requireSlug(bundle.id(), "bundle id");
            if (!bundleIds.add(bundle.id())) {
                throw new IOException("Duplicate bundle id: " + bundle.id());
            }
            if (bundle.name() == null || bundle.name().isBlank()
                    || bundle.description() == null || bundle.description().isBlank()) {
                throw new IOException("Every bundle requires a name and description: " + bundle.id());
            }
            if (bundle.required() && !bundle.defaultEnabled()) {
                throw new IOException("Required bundles must be enabled by default: " + bundle.id());
            }

            Set<String> projectIds = new HashSet<>();
            for (VantaBundleManifest.Mod mod : bundle.mods()) {
                if (mod == null || mod.projectId() == null || mod.projectId().isBlank()) {
                    throw new IOException("Every bundle mod requires a Modrinth project id: " + bundle.id());
                }
                if (!projectIds.add(mod.projectId())) {
                    throw new IOException("Duplicate Modrinth project in bundle " + bundle.id() + ": " + mod.projectId());
                }
                if (mod.versionId() != null && mod.versionId().isBlank()) {
                    throw new IOException("Pinned Modrinth version ids cannot be blank: " + mod.projectId());
                }
                if (mod.githubRelease() != null) {
                    VantaBundleManifest.GitHubRelease release = mod.githubRelease();
                    if (release.repository() == null
                            || !release.repository().matches("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+")
                            || release.assetPrefix() == null
                            || !release.assetPrefix().matches("[A-Za-z0-9._-]+")
                            || mod.versionId() != null) {
                        throw new IOException("Invalid GitHub release source for profile component: " + mod.projectId());
                    }
                }
                if (mod.required() && !mod.defaultEnabled()) {
                    throw new IOException("Required mods must be enabled by default: " + mod.projectId());
                }
            }
        }
    }

    private static void requireSlug(String value, String field) throws IOException {
        if (value == null || !SLUG.matcher(value).matches()) {
            throw new IOException(field + " must be a lowercase slug containing only letters, numbers, '.', '_' or '-'.");
        }
    }
}
