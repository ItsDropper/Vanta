# Minecraft Loader Compatibility

This document separates implemented integrations from candidates. A loader is not considered supported merely because its Modrinth project metadata can be queried or because a launcher profile can be displayed.

## Support status

| Loader / technology | Current status | Integration notes |
| --- | --- | --- |
| Vanilla | Supported | Standard Mojang version metadata and libraries. |
| Fabric | Supported | Fabric Meta profile installation, repair, and Modrinth integration. |
| Forge | Supported | Installer-based client installation and version metadata merge. |
| Quilt | Implemented; runtime validation pending | Uses the official Quilt Meta v3 API and Quilt-specific instance identity. The installer profile is structurally compatible with Vanta's existing launcher-profile merge path. Validate real launches, repair, Modrinth filtering, and mrpack imports before calling this fully production-verified. |
| NeoForge | Next priority | Separate Maven coordinates and installer artifacts; version mapping and installer-generated metadata need dedicated handling. Do not reuse Forge's version discovery assumptions. |
| Ornithe | Planned research | Legacy-focused ecosystem. Support must be tied to its actual Minecraft version matrix and installer/profile format. |
| Legacy Fabric | Planned research | Separate legacy version coverage and metadata from modern Fabric; do not route it through Fabric Meta without verifying the target version. |
| Babric | Planned research | Legacy Fabric-derived toolchain for older Minecraft versions. Treat as a separate integration and version family. |
| Better Than Adventure (BTA) | Planned research | BTA is a Minecraft-derived game/project, not itself a generic loader name. Its Babric relationship and supported releases need explicit modeling. |
| NilLoader | Planned research | Legacy loading environment with its own compatibility and launch requirements. |
| LiteLoader | Planned research | Historical client-side loader; requires version-scoped legacy installation and launch handling. |
| Risugami's ModLoader | Planned research | Historical loader for very old Minecraft versions; not a modern version-profile overlay. |
| Java agents | Separate runtime feature, not a loader | A Java agent is a JVM instrumentation mechanism. If added, it belongs in per-instance runtime extensions/JVM arguments, with explicit user control and clear warnings, not in the mod-loader selector. |

## Implementation rules

1. Keep each loader's identity stable in instance metadata. Never silently relabel Quilt, NeoForge, or a legacy loader as Fabric or Forge.
2. Gate discovery by the selected Minecraft version. A loader is selectable only when Vanta can install it for that exact version.
3. Treat installation, launch, repair, modpack import/export, Modrinth filtering, and diagnostics as one support contract.
4. Preserve the vanilla instance directory and shared-library boundaries. Installer output must be validated before marking an instance complete.
5. Use official metadata and installer endpoints where available. Validate downloaded JSON, Maven coordinates, archive paths, and generated version metadata.
6. Keep legacy loaders version-scoped. Do not build a single generic legacy-loader installer until the actual bootstraps and profile formats prove compatible.
7. Do not advertise a loader as production-supported until a clean install, launch, relaunch, repair, and modpack round-trip have been tested.

## Recommended delivery order

1. Finish Quilt runtime and regression validation.
2. Implement NeoForge with its own metadata resolver and installer adapter.
3. Add a loader integration contract so modern and legacy installers share lifecycle handling without sharing incorrect assumptions.
4. Research and implement Ornithe, Legacy Fabric, and Babric by supported Minecraft release ranges.
5. Evaluate NilLoader, LiteLoader, and Risugami's ModLoader as separate historical integrations based on demand and testable installation flows.
6. Add optional Java-agent configuration only as a separate, opt-in per-instance runtime extension.
