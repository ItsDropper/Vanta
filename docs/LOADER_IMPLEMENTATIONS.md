# Loader Implementation Reference

This document maps each of Vanta's 13 non-vanilla loader integrations to its implementation model. It describes the code currently present on `main`; it is not a blanket compatibility certification. Version gates and validation status are maintained in [Loader Compatibility](LOADER_COMPATIBILITY.md).

## Shared installation and launch pipeline

Loader installers live primarily under `src/main/java/dev/itsdropper/vanta/launcher/instance/`. The launcher first resolves the selected Minecraft version and its vanilla metadata/client artifacts. A loader integration then follows the upstream loader's actual bootstrap contract:

- Profile-based loaders contribute a version JSON profile and libraries.
- Installer-driven loaders run or consume their upstream installer output and normalize generated metadata.
- LaunchWrapper integrations add their own tweaker/profile data and require a compatible ordered profile stack.
- Java-agent loaders add JVM agent arguments and keep the agent outside the Minecraft classpath.
- Client patchers modify only an instance-local client copy and must preserve the shared Mojang artifact.

These mechanisms are intentionally not collapsed into a single generic installer. A valid profile JSON does not prove mod binary compatibility, and Modrinth project metadata does not prove that Vanta can install or launch a loader.

## Loader-by-loader implementation

### 1. Fabric

**Implementation:** `FabricInstaller`.

Vanta queries Fabric Meta for the selected Minecraft version, chooses the newest stable loader entry where available, and validates the returned profile. Profile libraries are resolved from Maven coordinates into the shared libraries directory with path validation. Missing repository URLs use Mojang's library repository default; metadata repository URLs are otherwise respected. The profile is merged with vanilla metadata by replacing the main class where specified, removing the vanilla inheritance reference, and merging libraries by Maven coordinate so loader definitions take precedence. Native-only entries are handled through the normal native installer path.

### 2. Forge

**Implementation:** `ForgeInstaller`.

Forge uses its own version/installer metadata and installer-generated client profile rather than Fabric Meta. Vanta resolves the version-specific installer, executes or processes the official installer flow, validates the generated version data, and merges the Forge launch profile into the selected instance's Minecraft metadata. Forge-specific launch arguments, libraries, and main-class changes must be retained; this is not a Fabric profile with a different label.

### 3. NeoForge

**Implementation:** `NeoForgeInstaller`.

NeoForge resolves its version through NeoForged Maven metadata and uses the official client installer path. The integration maps the selected Minecraft version to the appropriate NeoForge artifact, obtains the generated profile, and normalizes the result for Vanta's launch pipeline. Inheritance references that would point to a temporary or absent profile are removed during merge. Repair and modpack workflows use the instance's preserved NeoForge identity rather than relabeling it as Forge.

### 4. Quilt

**Implementation:** `QuiltInstaller`.

Quilt uses the official Quilt Meta v3 API and Quilt-specific instance identity. It retrieves the game/loader-specific profile and merges the returned main class and libraries through the compatible profile-merge path. Quilt remains distinct from Fabric in metadata and loader filtering even though parts of the launcher-profile structure are compatible.

### 5. Ornithe

**Implementation:** `OrnitheInstaller`.

Ornithe uses its official Meta v2 game-version loader endpoint and the profile JSON it publishes. The installer wires version-aware installation, repair, Modrinth filtering, and pack workflows into the common instance lifecycle. The base-game resolver still requires a Mojang-manifest Minecraft version; Pre-Classic builds absent from that manifest are not made installable merely because Ornithe has historical metadata.

### 6. Legacy Fabric

**Implementation:** `LegacyFabricInstaller`.

Legacy Fabric uses its separate Meta v2 API and its own profile JSON. Installation is selected by Minecraft version, and the loader's identity remains Legacy Fabric throughout instance metadata and Modrinth filtering. Older profile metadata may advertise the retired `maven.legacyfabric.net` host; Vanta normalizes that known host to the current `repo.legacyfabric.net/legacyfabric/` repository path without rewriting arbitrary repository URLs or silently substituting on download failure. The documented Modrinth pack format does not currently define a Legacy Fabric dependency ID, so Vanta does not falsely export it as another loader.

### 7. Babric

**Implementation:** `BabricInstaller`.

Babric is gated to Minecraft Beta 1.7.3. Its bootstrap profile is adapted from a supported modern Fabric game profile because Fabric Meta rejects the unofficial Beta game ID. Vanta removes the modern game-specific intermediary dependency before adding Babric's own mappings and patch libraries, following the official Babric/Prism component versions. This is a version-specific compatibility bridge, not a claim that ordinary Fabric profiles work on Beta 1.7.3.

### 8. VantaLoader

**Implementation:** `VantaLoaderInstaller` plus the separate VantaLoader agent project.

VantaLoader is an independent Java-agent loader, not a Fabric/Forge profile overlay. Vanta pins each instance to an immutable commit-specific nightly release, verifies the agent's SHA-256, and checks the expected agent entrypoint and bundled runtime classes before launch. Launch configuration passes the normalized instance directory, Minecraft version, client environment, and verified official client-mappings path as JVM properties. The agent is attached with `-javaagent:` and is not inserted into the Minecraft classpath.

The integration's CI smoke path is documented as launching Minecraft 1.21.11, initializing native mods, and verifying the title and Mods screens. That does not verify player behavior inside a world. ASM transformation infrastructure and a fixture are tested; the experimental SpongePowered Mixin service/bootstrap is not evidence of general third-party Mixin compatibility. Fabric/Forge APIs and compatibility with their mods are not claimed.

### 9. NilLoader

**Implementation:** `NilLoaderInstaller`.

NilLoader is modeled as a JVM agent rather than a launcher-profile overlay. Vanta retrieves the published Maven artifact, validates that its JAR manifest declares a Java-agent entrypoint, and launches it with `-javaagent:` before Minecraft bootstrap. Its upstream project moved from GitHub to Forgejo and its published artifacts use the `repo.sleeping.town` coordinates. Agent startup being wired into the launch command is distinct from validating every Nilmod or Minecraft version.

### 10. LiteLoader

**Implementation:** `LiteLoaderInstaller`.

Vanta reads LiteLoader's official `versions.json` manifest and selects stable `artefacts` entries; snapshot-only versions are not advertised. It validates the HTTPS repository host, downloads the published JAR into the shared Maven library store with MD5 integrity verification, installs declared dependencies, and creates a version-specific LaunchWrapper profile overlay with `LiteLoaderTweaker`. The ordered profile-stack mechanism allows compatible Forge/LaunchWrapper layering, but the presence of a stack does not prove arbitrary tweaker combinations are valid.

### 11. Rift

**Implementation:** `RiftInstaller`.

Vanta targets the original Dimensional Development Rift 1.0.4-87 release for Minecraft 1.13. The integration uses its original LaunchWrapper tweaker and upstream Maven dependency coordinates. Community RiftLoader forks and 1.13.2 ports are deliberately outside this integration's version gate; they must not be inferred as supported from the shared name.

### 12. Stein

**Implementation:** `SteinInstaller`.

Stein is a dedicated integration for Minecraft 1.8.9, not a Modrinth loader lookup or a profile-stack overlay. Vanta downloads the official Stein Installer JAR from the Stein project's GitHub release and verifies its pinned SHA-256 and maximum file size. It prepares a temporary, instance-local `.minecraft`-style staging tree containing the vanilla 1.8.9 metadata, client JAR, and required Paulscode library, then invokes the official CLI installer with the staging directory as its target.

Before importing the result, Vanta checks for the generated patched client, generated version JSON, bundled Java 25 runtime, and required Bridge/Loader/LWJGL/native metadata. It verifies the runtime by executing `java -version`, normalizes Stein's platform-native metadata into Vanta's native-classifier contract, and copies the Stein-owned and LWJGL library trees. The runtime is installed under the instance's `stein/runtime` directory; generated metadata and an install manifest are saved for the instance. The user's normal `.minecraft` directory and launcher profiles are not modified. Stein uses its own `.steinmod` ecosystem; Fabric, Forge, and Modrinth JAR compatibility is not implied. The installer currently pins its installer, Bridge, and Loader versions in source.

### 13. Risugami's ModLoader

**Implementation:** `ModLoaderInstaller`.

Risugami's historical ModLoader is a client-JAR patcher, not a version-profile overlay. Vanta selects a version-specific MCArchive ZIP from a source-controlled table of pinned SHA-256 digests, downloads and verifies the archive, validates ZIP entry paths and expansion limits, and merges patch entries into the instance-local client JAR. It strips stale signature metadata and uses temporary output before replacing the instance client. The shared Mojang client artifact is not modified.

The installer also extracts ModLoader bootstrap classes to an instance-local exploded directory. This accommodates legacy resource-root URI behavior that expects a hierarchical file URI rather than a `jar:file:...!/` resource URL. Only Minecraft versions with registered, verified patch archives are exposed; other historical versions remain unavailable until an archive and digest are reviewed and added.

## Profile stacks and compatibility boundaries

`LoaderStackConfiguration`, `LoaderProfileStackMerger`, and `LoaderStackManager` provide an ordered, low-level mechanism for compatible launcher-profile overlays. The configuration persists ordered layers, profile references, and tweaker classes. The manager installs profile libraries, snapshots the base version metadata, merges compatible overlays, and writes the merged classpath/arguments into instance metadata. Launch-data construction validates the saved stack; repair and version rebuild paths rebase and reapply it.

This mechanism does not automatically resolve dependency constraints, remap mods, prove tweaker ordering, or make unrelated loaders compatible. It rejects incompatible main classes, but that is only one structural check. Do not automatically combine ModLoader's client-patch model with Forge or LiteLoader until the exact resulting bootstrap has real-launch tests. Use only combinations with verified shared bootstrap requirements.

## Validation language

Use these terms precisely:

- **Implemented:** the integration code exists and is wired into the relevant launcher path.
- **Runtime validation pending:** code and metadata handling exist, but the required clean install/launch/repair/modpack round-trip evidence is not complete.
- **Experimental:** behavior is deliberately limited or still lacks broad compatibility evidence.
- **End-to-end tested:** the exact workflow has been exercised from install through launch and the relevant post-install lifecycle. Do not generalize one smoke test to every version, operating system, mod, or feature.

The current per-loader status table in [Loader Compatibility](LOADER_COMPATIBILITY.md) takes precedence over any broad summary in marketing copy or issue discussion.
