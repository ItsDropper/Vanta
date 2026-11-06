# Minecraft Loader Compatibility

Vanta integrates 13 non-vanilla loaders. **All 13 integrations are end-to-end functional within the version and ecosystem boundaries listed below.** The restrictions in this document describe the actual upstream loader scope; they are not pending-validation warnings.

## Support status

| Loader | Current status | Version scope and implementation boundary |
| --- | --- | --- |
| Fabric | End-to-end supported | Fabric Meta profile installation, launch, repair, and Modrinth integration. |
| Forge | End-to-end supported | Official installer-based client installation, launch, repair, and version metadata merge. |
| NeoForge | End-to-end supported | NeoForged Maven metadata and official client installer. Version mapping, installer execution, metadata merge, repair, Modrinth filtering, and mrpack import/export are integrated. |
| Quilt | End-to-end supported | Official Quilt Meta v3 API and Quilt-specific identity. Profile installation uses the common merge path while preserving Quilt metadata and Modrinth filtering. |
| Ornithe | End-to-end supported | Official Ornithe Meta v2 game-version loader endpoint and profile JSON. The base-game resolver requires a Mojang-manifest version; non-manifest Pre-Classic builds are outside the supported game-version source. |
| Legacy Fabric | End-to-end supported | Separate Meta v2 API, profile JSON, version-aware creation, loader installation, launch, repair, and Modrinth filtering. The documented Modrinth pack format has no Legacy Fabric dependency ID, so Vanta does not misrepresent it as another loader during pack export. |
| Babric | End-to-end supported for Beta 1.7.3 | Adapts a supported modern Fabric bootstrap profile, removes its game-specific intermediary dependency, and adds Babric's own mappings and patch libraries. Babric is intentionally scoped to b1.7.3; the unofficial Beta game ID is not accepted by Fabric Meta. |
| VantaLoader | End-to-end supported; independent Java-agent loader for Minecraft 1.20.5+; nightly release channel | Pins instances to immutable commit-specific nightly releases, verifies SHA-256 and required classes, and launches with `-javaagent:`. Its Minecraft 1.21.11 integration path initializes native mods and reaches the title and Mods screens. It is an independent loader, not a Fabric/Forge compatibility layer; Fabric/Forge APIs and their mod compatibility are not implied. Experimental Mixin service/bootstrap code does not claim universal third-party Mixin compatibility. |
| NilLoader | End-to-end supported | Installs the published Maven artifact, validates its JAR manifest Java-agent entrypoint, and launches it using `-javaagent:`. Its upstream source moved from GitHub to Forgejo; published coordinates use `repo.sleeping.town`. Compatibility remains scoped to NilLoader's own supported game versions and Nilmods. |
| LiteLoader | End-to-end supported for stable releases in the official manifest | Selects stable `artefacts` entries, validates the HTTPS repository host, downloads with MD5 integrity verification, installs dependencies, and adds a version-specific LaunchWrapper profile overlay with `LiteLoaderTweaker`. Snapshot-only versions are excluded by design. |
| Rift | End-to-end supported for original Rift 1.0.4-87 on Minecraft 1.13 | Uses the original Dimensional Development LaunchWrapper tweaker and upstream Maven coordinates. Community RiftLoader forks and 1.13.2 ports are separate projects and are outside this integration's scope. |
| Stein | End-to-end supported for Minecraft 1.8.9 | Runs the official Stein Installer CLI against a temporary instance-local `.minecraft` layout, verifies the installer SHA-256 and generated profile/runtime, then imports the patched client, Stein-owned libraries, native archives, and bundled Java 25 runtime into the Vanta instance. It does not edit the user's normal `.minecraft` folder or launcher profiles. Stein uses its own `.steinmod` ecosystem; Fabric/Forge/Modrinth JAR compatibility is not implied. |
| Risugami's ModLoader | End-to-end supported for pinned archive versions | Downloads a version-specific MCArchive ZIP with a pinned SHA-256 digest, validates and safely extracts patch entries, and merges them into the instance-local client JAR. Bootstrap classes are loaded from an exploded instance-local directory to support legacy resource-root URI behavior. Pinned for 1.2.5, 1.3.1–1.3.2, 1.4.2, 1.4.4–1.4.7, 1.5–1.5.2, 1.6.1–1.6.2, and b1.8.1. Other historical versions are outside the currently registered archive set. |

Vanilla is not counted as one of these 13 loaders. Vanta's vanilla version picker includes Mojang manifest entries typed `old_alpha` and `old_beta`, with historical argument and virtual-asset handling. Pre-Classic builds absent from Mojang's manifest are not included.

## Implementation details

See [Loader Implementation Reference](LOADER_IMPLEMENTATIONS.md) for source class mappings, metadata endpoints, installer behavior, profile merging, JVM-agent handling, Stein's isolated installer staging, and the legacy ModLoader patch pipeline.

## Compatibility boundaries

1. Keep each loader's identity stable in instance metadata. Never silently relabel Quilt, NeoForge, or a legacy loader as Fabric or Forge.
2. Gate discovery by the selected Minecraft version. Ornithe and Legacy Fabric are discovered per game version through their own Meta APIs; Babric is restricted to b1.7.3; Stein is restricted to 1.8.9 and supported OS/architecture combinations; VantaLoader is exposed only for Minecraft 1.20.5+ when its verified nightly release and checksum are available; LiteLoader uses stable entries from its upstream manifest; Risugami's ModLoader is exposed only for versions with pinned SHA-256 archive records.
3. Installation, launch, repair, modpack import/export, Modrinth filtering, and diagnostics follow each loader's supported integration path. Historical vanilla launches use the metadata's legacy argument template and materialize virtual assets when required.
4. Preserve the vanilla instance directory and shared-library boundaries. Installer output is validated before an instance is marked complete.
5. Use official metadata and installer endpoints where available. Validate downloaded JSON, Maven coordinates, archive paths, generated version metadata, Java-agent manifest entrypoints, and checksums where pinned or published. Keep JVM agents outside the Minecraft classpath and preserve deterministic `-javaagent` ordering.
6. Keep legacy loaders version-scoped. NilLoader is a distinct JVM-agent path; Risugami's ModLoader is a client-JAR patcher and patches only the instance-local client copy.
7. Treat multi-loader combinations as ordered stacks, not as a comma-separated loader label. ModLoader's JAR patch model is not itself a profile overlay; do not combine it with Forge or LiteLoader automatically unless that exact combination is explicitly configured and supported. The profile-stack editor is a low-level mechanism, not an automatic compatibility solver.
8. The profile stack rejects incompatible main classes, but cannot infer dependency constraints, remap mods, or prove arbitrary third-party tweaker ordering. Remove a stack in Instance Settings to restore its saved base profile.
9. Do not assume OptiFine is a loader. Fabric + OptiFine is a Fabric instance plus version-matched OptiFabric and OptiFine mod artifacts; Forge + LiteLoader requires a legacy LaunchWrapper-compatible profile and supported tweaker ordering.
10. Loader support is scoped to the versions, upstream distributions, and ecosystems explicitly listed above. End-to-end support does not mean every mod, fork, historical version, or arbitrary loader combination is compatible.

## Support terminology

- **End-to-end supported:** Vanta's supported workflow for the stated loader/version scope is implemented and functional through installation and launch, with the applicable instance lifecycle integration.
- **Version-scoped:** only the listed Minecraft versions or upstream release channel are exposed.
- **Ecosystem-scoped:** loader-specific mods and APIs are supported only where the loader itself defines compatibility; support for another loader's mods is not implied.
