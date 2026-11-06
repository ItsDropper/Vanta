# Architecture Overview

This document describes the current subsystem boundaries in Vanta. It is a source-oriented guide; implementation and validation status are documented separately where they differ.

## Design goals

- **Instance isolation:** a Minecraft installation should use its own game directory and instance-specific configuration, mods, worlds, logs, screenshots, resource packs, and shader packs.
- **Recoverability:** installation health should be inspectable, and repair should restore missing or invalid files without needlessly replacing healthy data.
- **Explicit service boundaries:** authentication, metadata resolution, downloads, installation, repair, and updates should remain understandable and independently testable.
- **Bounded work:** parallel scans and background tasks should use controlled concurrency rather than creating unbounded threads.
- **Privacy by design:** account/session data remains local where possible; network access should be limited to the services required by enabled features.

## Major subsystems

### Application and UI

The JavaFX UI coordinates user actions and presents progress, status, and errors. Blocking network, archive, or filesystem operations should execute off the JavaFX Application Thread. UI updates should be marshalled back to that thread.

### Accounts and authentication

Account-facing code coordinates the sign-in flow, session refresh, account switching, and local account persistence. Secrets must not be printed to logs or included in issue reports. The launcher should not request a user's Microsoft password directly.

### Minecraft installation and launch

The launcher resolves Mojang version metadata and the corresponding client, libraries, assets, natives, and launch arguments. Loader integrations then contribute loader-specific metadata, libraries, tweakers, agent arguments, or client patches. The launch pipeline must preserve the selected instance as the game directory and process working directory.

### Loader integrations

Vanta has 13 non-vanilla loader integrations. They deliberately use different installation models rather than forcing every loader through one generic profile merger:

- **Profile metadata integrations:** Fabric, Quilt, Ornithe, and Legacy Fabric obtain version-specific metadata from their respective Meta APIs and merge validated profile data with the vanilla version.
- **Installer-driven integrations:** Forge and NeoForge use their own installer metadata and generated launch profiles; their version metadata must be normalized and merged without leaving invalid inheritance references.
- **Legacy profile/tweaker integrations:** Babric, LiteLoader, and original Rift have version-specific bootstrap and LaunchWrapper/profile requirements. Babric's Beta 1.7.3 bootstrap requires adapting a modern Fabric bootstrap profile and replacing game-specific mappings/libraries.
- **JVM-agent integrations:** NilLoader and VantaLoader launch outside the Minecraft classpath with `-javaagent`. VantaLoader is an independent experimental loader with a separately verified nightly artifact; it is not a Fabric/Forge compatibility layer.
- **Dedicated Stein integration:** Stein runs its official installer against a temporary instance-local Minecraft layout, verifies the installer and generated Java 25 runtime, then imports the patched client, runtime, metadata, and Stein-owned libraries into the selected Vanta instance.
- **Client-JAR patch integration:** Risugami's ModLoader merges a pinned version-specific archive into the instance-local client JAR and extracts its bootstrap classes to an instance-local directory. It must not patch the shared Mojang artifact.

The loader-specific implementation guide identifies the source classes, version gates, metadata sources, and launch model for each integration. The compatibility guide remains authoritative for validation status. Modrinth metadata visibility is not proof that an installer or launch path works.

### Modrinth

Modrinth functionality is separated into catalog/search, dependency resolution, installation, scanning, and repair responsibilities. Dependency selection must respect the target Minecraft version and loader, account for already-installed compatible projects, and handle unresolved or incompatible dependencies with actionable errors. The Modrinth pack format's documented loader dependency IDs do not represent every loader Vanta can install independently.

### State inspection and repair

The State Center checks instance metadata and directories as well as shared resources. Health classifications should be based on concrete checks. Repair should validate files and preserve healthy user-owned content such as worlds and configuration whenever possible.

### Environment state

The Environment Engine stores a versioned instance lockfile and compares fingerprints of managed environment files. Current capture includes direct regular files in `mods/` and recursive files in `resourcepacks/`, `shaderpacks/`, and `config/`, alongside selected Minecraft manifest and runtime requirement metadata. Drift comparison is read-only; it does not automatically reconcile or repair an instance. See [Product Direction](PRODUCT_DIRECTION.md) for the current scope and explicit exclusions.

### Launcher updates

The update flow discovers releases, downloads the application package and checksum, verifies the package, and delegates replacement to an external updater. The updater stages the replacement, retains a recoverable backup, verifies the result, and rolls back when replacement fails. Update and rollback paths should use the same verification rules.

### Local server management

Server profiles, process lifecycle, console output, properties, backups/restores, and crash reports are separate from client-instance loader installation. Server runtime provisioning is platform-gated and must not be confused with the Java runtime selected for a Minecraft client.

## Data boundaries

The application installation and user data are separate concerns. User data may include account state, instances, shared libraries/assets, logs, and settings. Changes to data paths or instance formats require migration planning and backward-compatibility checks.

Never assume deleting an instance is safe without distinguishing launcher-managed files from user-created worlds, configuration, and other personal data.

## Reliability expectations

- Treat remote metadata and downloaded files as untrusted input.
- Validate paths before extracting archives; prevent path traversal.
- Verify checksums when authoritative checksums are available.
- Use temporary files and atomic replacement where supported.
- Bound retries and concurrency.
- Report which operation failed, while redacting credentials and tokens.
- Make repair idempotent where possible.
- Test failure cases, not only the successful path.
