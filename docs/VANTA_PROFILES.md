# Vanta Profiles and Bundle Manifests

Vanta Profiles are versioned collections of independently selectable feature bundles.
They are separate from the existing built-in instance presets:

- An **instance preset** is a one-time starting point for a Minecraft installation.
- A **profile manifest** is a maintained description of a Vanta ecosystem installation.
- A **bundle** groups related mods that can be enabled or disabled together.
- A **mod entry** references either a Modrinth project or a verified GitHub release asset and can optionally pin an exact Modrinth version.

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

Each bundle has a stable ID, display name, description, `required` and `defaultEnabled` flags, and a list of components. A required bundle must be enabled by default. Each component has a stable `projectId`, optional exact Modrinth `versionId`, and its own required/default-enabled flags. GitHub release components use a `githubRelease` object with an owner/repository and asset prefix; the launcher selects the latest matching OS/CPU/Minecraft artifact and verifies its SHA-256 checksum before installation.

The resolver applies these rules:

1. Required bundles are always selected.
2. If no explicit bundle selection is supplied, default-enabled bundles are selected.
3. If an explicit selection is supplied, it represents the complete set of selected optional bundles; required bundles remain selected.
4. Required mods in selected bundles are always included.
5. Without an explicit mod selection, default-enabled optional mods are included. With an explicit selection, it is the complete set of optional mods to include, so default-enabled optional mods can also be unchecked.
6. Repeated projects across bundles are deduplicated. Conflicting pinned versions are rejected.
7. Minecraft version, loader and every selected bundle/mod reference are validated before installation.

## Trust and installation boundary

`VantaBundleRepository` parses bounded local manifest files and validates their schema. It rejects unknown JSON fields to catch misspelled manifest properties. The launcher exposes this through **Create instance → Vanta Profiles**. The screen loads the built-in **Vanta Fabric Starter** automatically, so a new user can create a supported instance without finding or downloading a JSON file. Custom JSON manifests remain available through the secondary **Import custom profile** action for advanced users. Only import manifests from publishers you trust. Remote catalog discovery and publisher authentication are not implemented yet.

Remote distribution must be added behind a separate transport/authentication layer. The launcher must verify the source and integrity of remote manifest bytes before handing them to the repository. Modrinth artifacts continue through Vanta's dependency and transactional installation services. GitHub release artifacts are downloaded by the dedicated release installer, validated as Fabric mods, and atomically placed in the instance mods directory only after their release checksum is verified. Optional `versionId` pins are enforced during dependency-graph resolution, including when a pinned project is also pulled in transitively.

Bundle selection state must be stored per instance and must distinguish Vanta-managed content from user-installed mods. Updating a profile must never silently remove user-managed files or override a user's explicit disablement.

## Built-in starter profile

The launcher ships `profiles/vanta-fabric-starter.json` as a validated classpath
resource. It targets Minecraft 1.21.11 on Fabric and installs the Fabric API
through the existing Modrinth dependency resolver, selecting a compatible release
rather than pinning a stale version. The profile is intentionally a small starter,
not a claim that the full Vanta Client mod is installed by default. An optional Vanta Client bundle is available; when selected, the launcher resolves the latest GitHub release and selects the artifact matching the current OS, CPU architecture and Minecraft version. The manifest is loaded
automatically when the Vanta Profiles screen opens; custom manifest import is an
optional advanced path.

## Per-instance state

`VantaBundleStateStore` persists the profile selection in each instance's
`vanta-profile.json`. `VantaBundleManifestSnapshotStore` also saves the exact
validated manifest used during installation as `vanta-profile-manifest.json`.
This snapshot is retained locally even if the user later moves or deletes the
original import file; it is the foundation for future offline profile inspection
and safe update comparisons. Snapshot writes are bounded and atomic, and loading
re-validates the manifest schema. It records the profile revision, selected bundles,
explicitly selected optional projects, the project IDs Vanta itself manages,
and the optional-offer revision the user dismissed. Writes use a temporary
sibling file and atomic replacement where the filesystem supports it.

The managed-project list is an ownership boundary, not a scan result: only
projects installed through the Vanta profile workflow may be recorded there.
Mods installed manually by the user are outside this registry.
