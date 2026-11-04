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
| Babric | Implemented for b1.7.3; runtime validation pending | Mirrors the official Babric Prism instance composition: Babric patch libraries and intermediary mappings layered over a compatible Fabric Loader profile. Availability is deliberately limited to Beta 1.7.3. The installer queries Fabric Meta for a compatible loader profile and adds Babric-specific Maven libraries and legacy game arguments. A real launch and platform-native validation are still required. |
| NilLoader | Implemented; runtime validation pending | Installs the 1.4.1 Maven artifact into the instance, validates its JAR manifest for a Java-agent entrypoint, and launches it using `-javaagent:` before Minecraft bootstrap. The loader is modeled as a JVM agent rather than a profile overlay. Its upstream source moved from GitHub to Forgejo; the published repository coordinates use `repo.sleeping.town`. The broad version claim is not a substitute for real-game compatibility tests. |
| LiteLoader | Planned research | Historical client-side loader; requires version-scoped legacy installation and launch handling. |
| Risugami's ModLoader | Planned research | Historical loader for very old Minecraft versions; not a modern version-profile overlay. |

## Implementation rules

1. Keep each loader's identity stable in instance metadata. Never silently relabel Quilt, NeoForge, or a legacy loader as Fabric or Forge.
2. Gate discovery by the selected Minecraft version. A loader is selectable only when Vanta can install it for that exact version. Ornithe and Legacy Fabric are discovered per game version through their own Meta APIs; Babric is currently restricted to b1.7.3. NilLoader is exposed for the selected Mojang version because its agent bootstrap is independent of Minecraft profile JSON; game/mod compatibility still requires testing.
3. Treat installation, launch, repair, modpack import/export, Modrinth filtering, and diagnostics as one support contract. Historical vanilla launches must use the metadata's legacy argument template and materialize virtual assets when the asset index requires it.
4. Preserve the vanilla instance directory and shared-library boundaries. Installer output must be validated before marking an instance complete.
5. Use official metadata and installer endpoints where available. Validate downloaded JSON, Maven coordinates, archive paths, generated version metadata, and Java-agent manifest entrypoints. Keep JVM agents outside the Minecraft classpath and preserve deterministic `-javaagent` ordering.
6. Keep legacy loaders version-scoped. Do not build a single generic legacy-loader installer until the actual bootstraps and profile formats prove compatible. NilLoader is a distinct JVM-agent path and must not be represented as a Fabric-style profile merge.
7. Treat multi-loader combinations as ordered stacks, not as a comma-separated loader label. `LoaderStackConfiguration` persists ordered layers, profile references, and tweaker classes. The Instance Settings screen can import multiple launcher-profile JSON overlays and specify LaunchWrapper tweakers. `LoaderStackManager` installs profile libraries, snapshots the primary version metadata, merges compatible overlays, and stores the merged classpath/arguments in the instance version metadata. Launch-data construction validates the saved stack, and repair/version rebuild paths rebase and reapply it. Profile JSON compatibility is not proof that the underlying mods or tweakers are binary-compatible; only use combinations with a verified shared bootstrap.
8. The profile stack editor is intentionally a low-level mechanism, not an automatic compatibility solver. It rejects incompatible main classes, but cannot infer dependency constraints, remap mods, or prove tweaker ordering is valid. Remove a stack in Instance Settings to restore its saved base profile.
9. Do not assume OptiFine is a loader. Fabric + OptiFine is a Fabric instance plus version-matched OptiFabric and OptiFine mod artifacts; Forge + LiteLoader requires a legacy LaunchWrapper-compatible profile and verified tweaker ordering.
10. Do not advertise a loader as production-supported until a clean install, launch, relaunch, repair, and modpack round-trip have been tested.

## Recommended delivery order

1. Finish NeoForge runtime and regression validation.
2. Finish Quilt runtime and regression validation.
3. Validate Ornithe runtime launches and extend its catalog integration to non-Mojang Pre-Classic versions without weakening instance isolation.
4. Validate Legacy Fabric clean installs and real launches; add repair and supported modpack behavior only where the relevant format supports it.
5. Validate Babric clean installation and real b1.7.3 launches on the supported Windows target, including native extraction and mod loading.
6. Validate NilLoader artifact retrieval, Java 8 and modern-Java launch behavior, agent ordering, and at least one known Nilmod on a real instance.
7. Evaluate LiteLoader and Risugami's ModLoader individually, one at a time, against their actual install and launch requirements.
8. Add curated compatibility presets on top of the low-level profile stack only after each combination has integration tests and real launch evidence.
