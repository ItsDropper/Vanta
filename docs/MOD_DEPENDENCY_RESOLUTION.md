# Mod dependency resolution

Vanta resolves a mod installation as one compatibility graph, scoped to the target instance's Minecraft version and loader.

## Resolution order

1. Fetch candidate versions for requested projects concurrently.
2. Filter candidates by Minecraft version, normalized loader ID, and downloadable primary artifact.
3. Resolve Modrinth-declared required and incompatible project edges, honoring pinned version IDs.
4. Inspect supported archive metadata as a second source of dependency constraints.
5. Backtrack across candidate versions when a transitive dependency, version range, or conflict invalidates a branch.
6. Validate the complete graph before transactional installation begins.

Optional metadata edges do not block installation. Incompatible edges and declared break ranges do.

## Archive metadata readers

The scanner reads Fabric `fabric.mod.json`, Quilt `quilt.mod.json`, Forge `META-INF/mods.toml`, and NeoForge `META-INF/neoforge.mods.toml`. TOML uses a standards-compliant parser. Forge/NeoForge ranges such as `[1.20.1,1.21)` are normalized into inclusive/exclusive bounds.

Metadata entries are capped at 1 MiB. Invalid or unsupported supplemental metadata is treated as unavailable, never as proof that a mod has no dependencies.

For loaders without a recognized embedded metadata format, Modrinth's version dependency metadata remains the authoritative graph source. Candidate versions are filtered against the instance loader; Vanta does not assume Fabric compatibility or invent dependency identifiers.

## Environment-provided dependencies

The selected loader, Minecraft, and Java runtime are treated as environment-provided dependencies when their metadata IDs match the instance. Fabric API's bundled-module handling remains isolated to Fabric.

## Failure and installation semantics

Known required edges must resolve. Known conflicts reject a candidate branch and trigger backtracking. The installer receives only a fully resolved graph and retains its transactional download, validation, publication, and rollback behavior.
