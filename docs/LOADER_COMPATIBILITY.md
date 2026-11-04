# Minecraft Loader Compatibility

This document separates implemented integrations from candidates. A loader is not considered supported merely because its Modrinth project metadata can be queried or because a launcher profile can be displayed.

## Support status

| Loader / technology | Current status | Integration notes |
| --- | --- | --- |
| Vanilla | Supported for Mojang manifest entries | Standard Mojang version metadata and libraries. The create-version selector now includes all manifest entries typed `old_alpha` and `old_beta`, with historical argument and virtual-asset handling. Pre-Classic builds absent from Mojang's manifest are not included yet. |
| Fabric | Supported | Fabric Meta profile installation, repair, and Modrinth integration. |
| Forge | Supported | Installer-based client installation and version metadata merge. |
| Quilt | Implemented; runtime validation pending | Uses the official Quilt Meta v3 API and Quilt-specific instance identity. The installer profile is structurally compatible with Vanta's existing launcher-profile merge path. Validate real launches, repair, Modrinth filtering, and mrpack imports before calling this fully production-verified. |
| NeoForge | Implemented; runtime validation pending | Uses NeoForged Maven metadata and the official client installer. Version mapping, installer execution, metadata merge, repair, Modrinth filtering, and mrpack import/export are integrated. Validate clean installs and real launches before calling this production-supported. |
| Ornithe | Implemented; runtime validation pending | Uses the official Ornithe Meta v2 game-version loader endpoint and launcher-profile JSON. Installation, repair, Modrinth filtering, and mrpack import/export are wired. The current Vanta base-game resolver still requires a Mojang-manifest version; non-manifest Pre-Classic builds are not yet installable. |
| Legacy Fabric | Implemented; runtime validation pending | Uses Legacy Fabric's separate Meta v2 API and profile JSON. Version-aware creation, pinned loader installation, Modrinth filtering, and instance metadata are wired. It is intentionally not exported as a non-standard `.mrpack` dependency because the published Modrinth format currently documents only Minecraft, Forge, NeoForge, Fabric Loader, and Quilt Loader dependency IDs. |
| Babric | Planned research | Legacy Fabric-derived toolchain for older Minecraft versions. Treat as a separate integration and version family. |
| NilLoader | Planned research | Legacy loading environment with its own compatibility and launch requirements. |
| LiteLoader | Planned research | Historical client-side loader; requires version-scoped legacy installation and launch handling. |
| Risugami's ModLoader | Planned research | Historical loader for very old Minecraft versions; not a modern version-profile overlay. |

## Implementation rules

1. Keep each loader's identity stable in instance metadata. Never silently relabel Quilt, NeoForge, or a legacy loader as Fabric or Forge.
2. Gate discovery by the selected Minecraft version. A loader is selectable only when Vanta can install it for that exact version. Ornithe and Legacy Fabric are discovered per game version through their own Meta APIs.
3. Treat installation, launch, repair, modpack import/export, Modrinth filtering, and diagnostics as one support contract. Historical vanilla launches must use the metadata's legacy argument template and materialize virtual assets when the asset index requires it.
4. Preserve the vanilla instance directory and shared-library boundaries. Installer output must be validated before marking an instance complete.
5. Use official metadata and installer endpoints where available. Validate downloaded JSON, Maven coordinates, archive paths, and generated version metadata.
6. Keep legacy loaders version-scoped. Do not build a single generic legacy-loader installer until the actual bootstraps and profile formats prove compatible.
7. Treat multi-loader combinations as ordered stacks, not as a comma-separated loader label. `LoaderStackConfiguration` records layer type, version, order, and tweaker classes; `LoaderProfileStackMerger` merges compatible profile libraries and ordered LaunchWrapper tweakers. The model and merger are foundational only: instance UI, installation orchestration, launch consumption, compatibility constraints, and runtime tests are not wired yet. Reject profile stacks with conflicting main classes rather than guessing.
8. Do not assume OptiFine is a loader. Fabric + OptiFine is a Fabric instance plus version-matched OptiFabric and OptiFine mod artifacts; Forge + LiteLoader requires a legacy LaunchWrapper-compatible profile and verified tweaker ordering.
7. Do not advertise a loader as production-supported until a clean install, launch, relaunch, repair, and modpack round-trip have been tested.

## Recommended delivery order

1. Finish NeoForge runtime and regression validation.
2. Finish Quilt runtime and regression validation.
3. Validate Ornithe runtime launches and extend its catalog integration to non-Mojang Pre-Classic versions without weakening instance isolation.
4. Validate Legacy Fabric clean installs and real launches; add repair and supported modpack behavior only where the relevant format supports it.
5. Implement Babric as a separate legacy loader integration.
6. Evaluate NilLoader, LiteLoader, and Risugami's ModLoader individually, one at a time, against their actual install and launch requirements.
7. Wire loader-stack configuration into instance editing, install orchestration, and launch-data construction. Add only combinations with explicit compatibility metadata and integration tests.
