# Vanta Product Direction: Environment Management

This document records candidate subsystems that can give Vanta a distinct product identity. It is a product and architecture direction, not a claim that every capability is implemented or that no other launcher has a similar feature.

## Product thesis

Vanta should manage the lifecycle and reliability of a Minecraft environment, not only install files and start the game.

The differentiator should be the quality of the system connecting installation state, changes, diagnostics, recovery, and reproducibility. Individual features such as instance isolation, mod browsing, backups, or Java selection are useful but are already established launcher capabilities.

## Current implementation progress

The first Environment Engine foundation is now present in the codebase:

- A versioned `EnvironmentLockfile` model with portable, relative artifact identities (schema 4 writes; schemas 1–3 remain readable with their original drift scope).
- A read-only capture service that records every regular file directly inside an instance's `mods/` directory, including files whose mod metadata Vanta cannot identify.
- SHA-256 and byte-size recording for each artifact.
- Fabric mod ID/version metadata when the existing scanner can read it.
- Atomic local persistence under `<instance>/.vanta/environment.lock.json`.
- A structured drift comparison for Minecraft version, loader identity, and added, removed, or modified mod files.
- SHA-256 tracking for files under `resourcepacks/`, `shaderpacks/`, and `config/`, including nested configuration files.
- Initial JUnit coverage for capture, persistence, validation, and drift detection.

The State Center now exposes the first user-facing workflow:

- Each inspected instance shows whether a saved environment baseline exists.
- Users can save or update the baseline without changing Minecraft files.
- Users can run a background drift comparison and inspect the detailed changes in an expandable report.
- Comparison rejects a baseline whose instance identity does not match the selected instance.
- Lockfile capture and comparison run off the JavaFX Application Thread.

The current report covers Minecraft/loader identity, direct files in `mods/`, and recursive regular files in `resourcepacks/`, `shaderpacks/`, and `config/`, plus the instance's Minecraft version manifest, client JAR, platform-resolved classpath/native library artifacts, and the manifest-declared required Java major version. It deliberately excludes worlds, logs, caches, and the identity of the Java runtime actually selected for launch. Symbolic links are not followed. Each file is opened without following a final-component symlink and fingerprinted with SHA-256; capture rejects files whose size, modification time, or file identity visibly changes during hashing. The UI does not automatically reconcile or repair environment drift.

This is deliberately a foundation, not yet a complete portable environment specification. The lockfile records the Minecraft manifest's required Java major separately from the selected runtime's actual Java major. The latter remains unknown until Vanta can inspect the selected runtime reliably without executing an arbitrary user-configured binary. The captured dependency closure covers the installed Minecraft manifest's classpath and native artifacts, not the Java runtime's own modules. Drift detection is read-only; it does not change an instance.

## Existing foundations

Vanta already has meaningful building blocks:

- Isolated Minecraft instances and instance-level data.
- Modrinth search, dependency resolution, scanning, and repair.
- State Center inspection of instances and shared resources.
- Launch failure parsing and repair diagnosis.
- Full instance ZIP backup and restore.
- Instance export through the Modrinth pack format.
- Local server profiles, process management, backups, and crash reports.
- Verified application updates with staging and rollback.

New work should extend these systems instead of creating parallel implementations.

## Recommended subsystems

### 1. Environment Lockfile and Drift Detection

**Priority: P0 — flagship capability**

Define a versioned, machine-readable description of an instance's intended environment.

A lockfile should capture, where known:

- Minecraft version and resolved loader/version.
- Java major version and selected runtime identity, without machine-specific absolute paths by default.
- Mod project identifiers, resolved versions, filenames, and cryptographic hashes.
- Source and dependency metadata where available.
- A schema version and generation timestamp.

The scanner compares the lockfile with the actual instance and produces a structured diff:

- Added files.
- Removed files.
- Changed file hashes.
- Version drift.
- Missing dependencies.
- Unknown or unmanaged files.

Users should be able to review and selectively apply a reconciliation. Never silently delete unknown files or overwrite user-owned data.

The lockfile should not contain authentication tokens, account credentials, private server secrets, or absolute paths that make it unusable on another machine.

**Why it matters:** it makes an instance inspectable, reproducible, shareable, and diagnosable. It also provides a common data model for backups, instance comparison, and future synchronization.

### 2. Environment Guardian

**Priority: P0**

Extend the existing full-instance backup service into a recovery system with:

- User-configurable scheduled snapshots.
- Snapshots before risky operations such as loader upgrades or bulk mod changes.
- Explicit warnings when a world may be opened by a newer Minecraft version.
- Retention limits based on count and storage budget.
- Snapshot integrity verification.
- Restore to a new instance by default.
- A clear distinction between world-only, configuration, and full-instance snapshots.

A live world must not be copied as if it were a consistent backup. Prefer a clean game shutdown; if a consistent snapshot cannot be guaranteed, explain the limitation.

### 3. Crash Lab

**Priority: P0**

Turn the existing launch failure parser and repair diagnosis engine into a complete diagnostic workflow.

A diagnosis should include:

- A concise problem summary.
- Exact supporting log evidence.
- Confidence and uncertainty.
- Likely affected component or mod.
- Safe manual investigation steps.
- Reversible actions when a specific file can be identified confidently.

Add local launch history so a user can compare the last successful launch with the failed one. Redact access tokens, usernames where appropriate, and sensitive paths from any export.

Do not automatically remove or disable a mod based only on a weak stack-trace guess.

### 4. Instance Diff Engine

**Priority: P1**

Compare two instances by normalized component identity rather than filename alone.

Compare:

- Minecraft and loader versions.
- Mod project IDs and versions.
- File hashes.
- Resource and shader packs.
- Selected configuration files.
- Java runtime and JVM settings.

Support a reviewable, selective synchronization plan. Do not copy worlds or overwrite configuration implicitly.

### 5. Mod Change Transactions

**Priority: P1**

Make bulk changes transactional:

1. Resolve the requested changes.
2. Check dependencies and compatibility.
3. Stage downloads outside the active mod directory.
4. Validate file type, expected identity, and available checksums.
5. Record the pre-change state.
6. Apply changes with recoverable file operations.
7. Verify the resulting installation.
8. Roll back if application or verification fails.

A transaction must never report success merely because downloads completed.

### 6. Instance Timeline

**Priority: P1**

Create a local chronological event history for meaningful changes:

- Launch started, succeeded, or failed.
- Mods installed, removed, or updated.
- Loader or Minecraft version changed.
- Configuration snapshot created or restored.
- Backup created and verified.
- Repair started, completed, or failed.

Events should reference stable instance IDs and structured metadata. Avoid storing entire logs in the event database; retain bounded references to local diagnostic files instead.

### 7. Performance Lab

**Priority: P2**

Collect opt-in, local per-instance measurements:

- Launch duration.
- Memory usage samples.
- Exit status.
- Runtime and JVM configuration.
- Available profiling evidence.

Compare runs and provide conservative recommendations. Do not infer that a particular mod causes poor performance without profiling evidence.

### 8. Configuration Workbench

**Priority: P2**

Provide structured configuration comparison and recovery:

- Detect changed files.
- Show line-level diffs for text formats.
- Preserve unknown formats as opaque files.
- Snapshot before edits.
- Restore one file without restoring an entire instance.

Never rewrite a configuration file using a lossy parser or assume all mod configuration formats are interchangeable.

### 9. Server Compatibility Profiles

**Priority: P2**

Allow a server operator or pack maintainer to publish a client setup manifest containing the required Minecraft version, loader, and mod requirements. Vanta can compare a local instance against that manifest and show missing or incompatible components.

Keep server addresses and private connection details out of public manifests unless the user explicitly includes them.

## Delivery order

| Milestone | Deliverable | Depends on |
| --- | --- | --- |
| 1 | Versioned environment lockfile schema and scanner | Existing instance and Modrinth models |
| 2 | Drift report in State Center | Lockfile scanner |
| 3 | Reviewable lockfile reconciliation | Drift report and safe file operations |
| 4 | Environment Guardian snapshots | Existing backup service |
| 5 | Crash Lab with launch history | Existing diagnostics |
| 6 | Instance Diff Engine | Shared normalized environment model |
| 7 | Transactional mod changes | Dependency resolver and backup/recovery primitives |
| 8 | Timeline, Performance Lab, and configuration tools | Structured event and measurement models |

## Engineering constraints

- Keep JavaFX presentation separate from filesystem and domain services.
- Run scanning, hashing, compression, and network operations off the JavaFX Application Thread.
- Bound thread pools, archive sizes, file counts, and retained history.
- Use stable IDs and versioned schemas.
- Treat downloaded metadata and archives as untrusted.
- Never follow archive paths outside the target directory.
- Prefer staging plus atomic replacement where supported.
- Preserve worlds and user configuration by default.
- Make operations idempotent where possible.
- Add tests for interruption, corrupt data, path traversal, duplicate files, and rollback.
- Do not add telemetry or upload diagnostic data without explicit user action.

## Product principle

Vanta should be able to answer four questions for every instance:

1. **What is installed?**
2. **What changed?**
3. **Is it healthy, and what evidence supports that assessment?**
4. **Can I recover it safely?**

That connected workflow is a stronger identity than a larger collection of disconnected launcher features.
