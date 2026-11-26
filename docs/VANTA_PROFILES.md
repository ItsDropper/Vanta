# Vanta Profiles and Bundle Manifests

Vanta Profiles are versioned collections of independently selectable feature bundles.
They are separate from the existing built-in instance presets:

- An **instance preset** is a one-time starting point for a Minecraft installation.
- A **profile manifest** is a maintained description of a Vanta ecosystem installation.
- A **bundle** groups related mods that can be enabled or disabled together.
- A **mod entry** references a Modrinth project and can optionally pin an exact Modrinth version.

## Manifest schema version 1

A manifest contains:

| Field | Meaning |
|---|---|
| `schemaVersion` | Manifest format version; currently `1`. |
| `profileId` | Stable lowercase profile identifier. |
| `profileVersion` | Semantic profile version. |
| `minecraftVersions` | Exact Minecraft versions supported by this manifest. |
| `loader` | Lowercase loader identifier, such as `fabric`. |
| `bundles` | Ordered feature bundle definitions. |

Each bundle has a stable ID, display name, description, `required` and `defaultEnabled` flags, and a list of Modrinth projects. A required bundle must be enabled by default. Each mod has a Modrinth `projectId`, optional exact `versionId`, and its own required/default-enabled flags.

The resolver applies these rules:

1. Required bundles are always selected.
2. If no explicit bundle selection is supplied, default-enabled bundles are selected.
3. If an explicit selection is supplied, it represents the complete set of selected optional bundles; required bundles remain selected.
4. Required mods in selected bundles are always included.
5. Without an explicit mod selection, default-enabled optional mods are included. With an explicit selection, it is the complete set of optional mods to include, so default-enabled optional mods can also be unchecked.
6. Repeated projects across bundles are deduplicated. Conflicting pinned versions are rejected.
7. Minecraft version, loader and every selected bundle/mod reference are validated before installation.

## Trust and installation boundary

`VantaBundleRepository` parses bounded local manifest files and validates their schema. It rejects unknown JSON fields to catch misspelled manifest properties. The launcher exposes this through **Create instance → Vanta Profiles**, where a user explicitly selects a local JSON manifest, chooses a Minecraft version, and configures required/default/optional bundles and mods. Only import manifests from publishers you trust. Remote catalog discovery and publisher authentication are not implemented yet.

Remote distribution must be added behind a separate transport/authentication layer. The launcher must verify the source and integrity of remote manifest bytes before handing them to the repository. Resolved mod artifacts continue through Vanta's existing Modrinth dependency and transactional installation services; bundle code must not write JAR files directly. Optional `versionId` pins are enforced during dependency-graph resolution, including when a pinned project is also pulled in transitively.

Bundle selection state must be stored per instance and must distinguish Vanta-managed content from user-installed mods. Updating a profile must never silently remove user-managed files or override a user's explicit disablement.

## Per-instance state

`VantaBundleStateStore` persists the profile selection in each instance's
`vanta-profile.json`. It records the profile revision, selected bundles,
explicitly selected optional projects, the project IDs Vanta itself manages,
and the optional-offer revision the user dismissed. Writes use a temporary
sibling file and atomic replacement where the filesystem supports it.

The managed-project list is an ownership boundary, not a scan result: only
projects installed through the Vanta profile workflow may be recorded there.
Mods installed manually by the user are outside this registry.
