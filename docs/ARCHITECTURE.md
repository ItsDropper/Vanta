# Architecture Overview

This document describes the main responsibilities in Vanta. It is a conceptual guide, not a promise that every subsystem is complete or that every feature behaves identically in every release.

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

Version metadata drives the resolution of client files, libraries, assets, native libraries, launch arguments, and game arguments. The selected instance directory must be used consistently as the game directory and process working directory so relative paths do not resolve against the launcher installation.

### Loader integrations

Fabric and Forge each have loader-specific metadata and installation behavior. Loader installation and repair should integrate with the normal instance lifecycle rather than create a separate, inconsistent launch path.

### Modrinth

Modrinth functionality is separated into catalog/search, dependency resolution, installation, scanning, and repair responsibilities. Dependency selection must respect the target Minecraft version and loader, account for already-installed compatible projects, and handle unresolved or incompatible dependencies with actionable errors.

### State inspection and repair

The State Center checks instance metadata and directories as well as shared resources. Health classifications should be based on concrete checks. Repair should validate files and preserve healthy user-owned content such as worlds and configuration whenever possible.

### Launcher updates

The update flow discovers releases, downloads the application package and checksum, verifies the package, and delegates replacement to an external updater. The updater stages the replacement, retains a recoverable backup, verifies the result, and rolls back when replacement fails. Update and rollback paths should use the same verification rules.

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
