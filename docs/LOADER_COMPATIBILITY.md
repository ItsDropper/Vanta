# Minecraft Loader Compatibility

Vanta integrates 13 non-vanilla loaders. This document separates implementation from runtime validation. A loader is not considered fully supported merely because project metadata can be queried, a selector entry is visible, or an installer profile can be generated.

## Support status

| Loader | Current status | Version scope and implementation boundary |
| --- | --- | --- |
| Fabric | Supported | Fabric Meta profile installation, repair, and Modrinth integration. |
| Forge | Supported | Official installer-based client installation and version metadata merge. |
| NeoForge | Implemented; runtime validation pending | NeoForged Maven metadata and official client installer. Version mapping, installer execution, metadata merge, repair, Modrinth filtering, and mrpack import/export are integrated; clean-install and real-launch validation remains necessary. |
| Quilt | Implemented; runtime validation pending | Official Quilt Meta v3 API and Quilt-specific identity. Profile installation is wired into the common merge path; validate real launches, repair, Modrinth filtering, and mrpack imports before calling it fully production-verified. |
| Ornithe | Implemented; runtime validation pending | Official Ornithe Meta v2 game-version loader endpoint and profile JSON. The base-game resolver still requires a Mojang-manifest version; non-manifest Pre-Classic builds are not installable. |
| Legacy Fabric | Implemented; runtime validation pending | Separate Meta v2 API, profile JSON, version-aware creation, pinned loader installation, and Modrinth filtering. The documented Modrinth pack format has no Legacy Fabric dependency ID, so it is not exported as another loader. |
| Babric | Implemented for Beta 1.7.3; runtime validation pending | Adapts a supported modern Fabric bootstrap profile, removes its game-specific intermediary dependency, and adds Babric's own mappings and patch libraries. The unofficial Beta game ID is not accepted by Fabric Meta. |
| VantaLoader | Experimental independent Java-agent loader for Minecraft 1.20.5+; nightly only | Pins instances to immutable commit-specific nightly releases, verifies SHA-256 and required classes, and launches with `-javaagent:`. CI launches Minecraft 1.21.11, initializes native mods, and verifies the title and Mods screens. It does not enter a world; player behavior is not verified. Experimental Mixin service/bootstrap exists, but third-party Mixin compatibility is unverified. Fabric/Forge APIs and mod compatibility are not claimed. |
| NilLoader | Implemented; runtime validation pending | Installs the published Maven artifact, validates its JAR manifest Java-agent entrypoint, and launches it using `-javaagent:`. Its upstream source moved from GitHub to Forgejo; published coordinates use `repo.sleeping.town`. Broad game/mod compatibility still requires real-game tests. |
| LiteLoader | Implemented for stable releases in the official manifest; runtime validation pending | Selects stable `artefacts` entries, validates the HTTPS repository host, downloads with MD5 integrity verification, installs dependencies, and adds a version-specific LaunchWrapper profile overlay with `LiteLoaderTweaker`. Snapshot-only versions are excluded. |
| Rift | Implemented for original Rift 1.0.4-87 on Minecraft 1.13; runtime validation pending | Uses the original Dimensional Development LaunchWrapper tweaker and upstream Maven coordinates. Community RiftLoader forks and 1.13.2 ports are excluded. |
| Stein | Implemented for Minecraft 1.8.9; real-game validation pending | Runs the official Stein Installer CLI against a temporary instance-local `.minecraft` layout, verifies the installer SHA-256 and generated profile/runtime, then imports the patched client, Stein-owned libraries, native archives, and bundled Java 25 runtime into the Vanta instance. It does not edit the user's normal `.minecraft` folder or launcher profiles. Stein uses its own `.steinmod` ecosystem; Fabric/Forge/Modrinth JAR compatibility is not claimed. |
| Risugami's ModLoader | Implemented for pinned archive versions; runtime validation pending | Downloads a version-specific MCArchive ZIP with a pinned SHA-256 digest, validates and safely extracts patch entries, and merges them into the instance-local client JAR. Bootstrap classes are loaded from an exploded instance-local directory to support legacy resource-root URI behavior. Pinned for 1.2.5, 1.3.1–1.3.2, 1.4.2, 1.4.4–1.4.7, 1.5–1.5.2, 1.6.1–1.6.2, and b1.8.1. Other historical versions remain unavailable until a directly retrievable, checksum-verified archive is registered. |

Vanilla is not counted as one of these 13 loaders. Vanta's vanilla version picker includes Mojang manifest entries typed `old_alpha` and `old_beta`, with historical argument and virtual-asset handling. Pre-Classic builds absent from Mojang's manifest are not included.

## Implementation details

See [Loader Implementation Reference](LOADER_IMPLEMENTATIONS.md) for source class mappings, metadata endpoints, installer behavior, profile merging, JVM-agent handling, Stein's isolated installer staging, and the legacy ModLoader patch pipeline.

## Implementation rules

1. Keep each loader's identity stable in instance metadata. Never silently relabel Quilt, NeoForge, or a legacy loader as Fabric or Forge.
2. Gate discovery by the selected Minecraft version. Ornithe and Legacy Fabric are discovered per game version through their own Meta APIs; Babric is restricted to b1.7.3; Stein is restricted to 1.8.9 and supported OS/architecture combinations; VantaLoader is exposed only for Minecraft 1.20.5+ when its verified nightly release and checksum are available; LiteLoader uses stable entries from its upstream manifest; Risugami's ModLoader is exposed only for versions with pinned SHA-256 archive records.
3. Treat installation, launch, repair, modpack import/export, Modrinth filtering, and diagnostics as separate parts of the support contract. Historical vanilla launches must use the metadata's legacy argument template and materialize virtual assets when required.
4. Preserve the vanilla instance directory and shared-library boundaries. Installer output must be validated before marking an instance complete.
5. Use official metadata and installer endpoints where available. Validate downloaded JSON, Maven coordinates, archive paths, generated version metadata, Java-agent manifest entrypoints, and checksums where pinned or published. Keep JVM agents outside the Minecraft classpath and preserve deterministic `-javaagent` ordering.
6. Keep legacy loaders version-scoped. Do not build a generic legacy-loader installer until the actual bootstraps and profile formats prove compatible. NilLoader is a distinct JVM-agent path; Risugami's ModLoader is a client-JAR patcher and must patch only the instance-local client copy.
7. Treat multi-loader combinations as ordered stacks, not as a comma-separated loader label. ModLoader's JAR patch model is not itself a profile overlay; do not combine it with Forge or LiteLoader automatically until the resulting patched-client and tweaker ordering have real-launch tests. The profile-stack editor is a low-level mechanism, not an automatic compatibility solver.
8. The profile stack rejects incompatible main classes, but cannot infer dependency constraints, remap mods, or prove tweaker ordering. Remove a stack in Instance Settings to restore its saved base profile.
9. Do not assume OptiFine is a loader. Fabric + OptiFine is a Fabric instance plus version-matched OptiFabric and OptiFine mod artifacts; Forge + LiteLoader requires a legacy LaunchWrapper-compatible profile and verified tweaker ordering.
10. Do not advertise a loader as production-supported until a clean install, launch, relaunch, repair, and relevant modpack round-trip have been tested. A narrower CI smoke test must be described narrowly.

## Recommended validation order

1. Finish NeoForge runtime and regression validation.
2. Finish Quilt runtime and regression validation.
3. Validate Ornithe runtime launches and extend its catalog integration to non-Mojang Pre-Classic versions without weakening instance isolation.
4. Validate Legacy Fabric clean installs and real launches; add repair and supported modpack behavior only where the relevant format supports it.
5. Validate Babric clean installation and real b1.7.3 launches on supported targets, including native extraction and mod loading.
6. Validate NilLoader artifact retrieval, Java 8 and modern-Java launch behavior, agent ordering, and at least one known Nilmod on a real instance.
7. Validate LiteLoader on each stable manifest version with clean install, Forge chaining, relaunch, repair, and actual mod loading.
8. Validate original Rift 1.13 installation, LaunchWrapper bootstrap, real mod loading, repair, and relaunch; do not widen the version gate to community forks.
9. Validate Stein 1.8.9 on each supported OS/architecture, including bundled Java 25 startup, native extraction, clean relaunch, and a known `.steinmod`.
10. Evaluate Risugami's ModLoader separately against its actual legacy installation and launch requirements for every pinned archive.
11. Add curated compatibility presets only after each combination has integration tests and real-launch evidence.
