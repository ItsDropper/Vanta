# Vanta source reference

> Generated against the `main` source tree inspected on 2026-11-24. This is a source-oriented engineering reference, not a user guide. Paths below are repository-relative and intentionally mirror the hierarchy under `src/`.

## Scope and organization

This reference documents every file under `src/` except UI implementation files (the `ui` Java packages and their UI tests) and CSS files. The inspected scope is **235 files**: 146 non-UI production Java files, 70 non-UI Java tests, and 19 non-UI resources. Binary assets are documented by their role and format; their bytes are not reproduced here.

The sections follow the original source roots and package hierarchy. For a class, read **Overview** as its responsibility and **Technical notes** as the implementation boundary/contracts that matter when changing it. Tests document the behavior and invariants they protect.

## Source hierarchy

```text
src/
├── main/
│   ├── java/dev/itsdropper/vanta/
│   │   ├── [launcher root and subpackages]
│   │   ├── security/
│   │   └── telemetry/
│   └── resources/
│       ├── dev/itsdropper/vanta/i18n/
│       ├── dev/itsdropper/vanta/launcher/repair/
│       ├── fonts/
│       ├── images/
│       └── release-trusted-keys.json, version.properties
└── test/java/
    └── dev/itsdropper/vanta/ [non-UI tests only]
```

## `src/main/java/dev/itsdropper/vanta`

### `LauncherProcessLifecycle.java`

**Overview.** Owns JVM shutdown independently of JavaFX implicit-exit behavior. Cleanup registration is thread-safe; a registration racing with exit is either included in the shutdown pass or executed immediately. Cleanup is best-effort and isolated so one failing action does not prevent later cleanup. Child Minecraft processes are deliberately not terminated here.

**Key contracts.** Cleanup is idempotent at the process boundary; registration racing with exit cannot silently leak a resource. Exceptions are logged and isolated. The lifecycle intentionally leaves the game child process alone.

### `Main.java`

**Overview.** JavaFX application entry point and process-mode dispatcher. Startup performs update transaction recovery before JavaFX, routes updater and cleanup arguments to dedicated entry points, initializes security policy and localization, then transitions from a minimum-duration loading screen to LauncherView only after asynchronous view readiness. Screenshot capture is a separate undecorated path and skips normal telemetry setup.

**Key contracts.** Updater dispatch and update recovery happen before normal JavaFX startup. Screenshot mode is deliberately isolated from the normal lifecycle. The close-request handler saves window bounds and delegates final termination to LauncherProcessLifecycle.

## `src/main/java/dev/itsdropper/vanta/launcher`


## `dev/itsdropper/vanta/launcher`

### `ClasspathBuilder.java`

**Overview.** Builds ordered, de-duplicated game and native classpaths from merged version metadata and instance-local loader state. Its optional experimental cache keys on normalized instance/library roots, loader identity, and version.json file attributes; cached paths are revalidated before reuse. Includes Babric-specific ASM exclusion because Fabric Loader rejects duplicate ASM classes, and the ModLoader exploded bootstrap special case.

### `ClasspathStringBuilder.java`

**Overview.** Converts an ordered Path collection into the platform-specific JVM classpath representation. It is intentionally a formatting boundary: dependency selection and ordering belong to ClasspathBuilder.

### `LaunchData.java`

**Overview.** Immutable-ish launch input carrier assembled before process creation. Holds resolved launch metadata/arguments and the values consumed by MinecraftLauncher; it separates metadata resolution from process execution.

### `LaunchDataBuilder.java`

**Overview.** Transforms a merged Minecraft version manifest plus instance/account/settings context into LaunchData. Resolves modern structured and legacy string arguments, performs placeholder expansion, derives the required Java major from metadata with historical-version fallback, and accounts for NeoForge FML-version requirements.

### `LegacyArgumentResolver.java`

**Overview.** Tokenizes legacy argument templates before placeholder substitution so paths containing spaces remain a single argument. Handles quoting and rejects malformed quoting or unresolved placeholders rather than passing ambiguous strings to the JVM.

### `LibraryResolver.java`

**Overview.** Resolves Maven and modern Mojang artifact descriptors to safe local library paths. Selects native classifiers by OS/architecture, evaluates Mojang allow/disallow rules, substitutes architecture tokens, validates paths remain under the managed libraries root, and verifies resolved artifacts are regular files.

### `MinecraftLauncher.java`

**Overview.** Owns final JVM command construction and child-process creation. Combines Java executable, memory/JVM flags, classpath, natives, main class, authentication/game arguments and legacy LaunchWrapper handling; includes Rift tweaker extraction/validation and runtime-specific mitigations. It does not own instance installation.

### `MinecraftLocator.java`

**Overview.** Centralizes Vanta data, instances and shared libraries directory resolution through Platform. Path accessors distinguish path calculation from directory creation.

### `MinecraftVersion.java`

**Overview.** Reads installed version identifiers from a Minecraft directory. This is a lightweight inventory helper rather than the authoritative metadata parser.

### `NativeExtractor.java`

**Overview.** Extracts platform native entries from resolved library JARs into a staged flat directory, filters native filenames for the current OS, and publishes the completed directory only after extraction succeeds. Existing native output is preserved on failure.

### `Platform.java`

**Overview.** Maps host OS and architecture to Vanta's supported platform identities, Java executable naming, Adoptium distribution identifiers and desktop capability. It deliberately rejects unknown/unvalidated platform combinations instead of guessing.

### `VersionReader.java`

**Overview.** Reads and caches the instance's merged version.json into VersionManifest. Cache invalidation uses file modification time, size and file identity; inheritance handling reflects Vanta's stored merged-profile model.

## `src/main/java/dev/itsdropper/vanta/security` and `telemetry`

### `Ed25519Signatures.java`

**Overview.** undefined

### `RuntimeSecurityPolicy.java`

**Overview.** undefined

### `TelemetryService.java`

**Overview.** undefined

## `src/main/resources`

### `src/main/resources/dev/itsdropper/vanta/i18n/messages.properties`

**Overview.** Default localization bundle used when no explicit locale-specific bundle is selected. Contains the baseline message-key set.

### `src/main/resources/dev/itsdropper/vanta/i18n/messages_de.properties`

**Overview.** German localization bundle. Message keys are consumed by the Localization resource-bundle layer.

### `src/main/resources/dev/itsdropper/vanta/i18n/messages_en.properties`

**Overview.** English localization bundle and primary English translation source.

### `src/main/resources/dev/itsdropper/vanta/i18n/messages_es.properties`

**Overview.** Spanish localization bundle.

### `src/main/resources/dev/itsdropper/vanta/i18n/messages_fi.properties`

**Overview.** Finnish localization bundle.

### `src/main/resources/dev/itsdropper/vanta/i18n/messages_fr.properties`

**Overview.** French localization bundle.

### `src/main/resources/dev/itsdropper/vanta/i18n/messages_it.properties`

**Overview.** Italian localization bundle.

### `src/main/resources/dev/itsdropper/vanta/i18n/messages_nl.properties`

**Overview.** Dutch localization bundle.

### `src/main/resources/dev/itsdropper/vanta/i18n/messages_pt_PT.properties`

**Overview.** European Portuguese localization bundle; locale suffix distinguishes it from Brazilian Portuguese.

### `src/main/resources/dev/itsdropper/vanta/i18n/messages_sv.properties`

**Overview.** Swedish localization bundle.

### `src/main/resources/dev/itsdropper/vanta/launcher/repair/crash-cases.tsv`

**Overview.** Versioned tab-separated labelled crash corpus loaded by DeterministicCrashModel. Labels/categories and sanitized examples are training data; edits should preserve schema, balance, deduplication and privacy checks enforced by CrashCorpusQualityTest.

### `src/main/resources/fonts/OFL.txt`

**Overview.** SIL Open Font License text accompanying the bundled Poppins font files; retain with redistributed font assets.

### `src/main/resources/fonts/Poppins-Light.ttf`

**Overview.** Bundled Poppins Light font face used by the launcher font registration layer.

### `src/main/resources/fonts/Poppins-Medium.ttf`

**Overview.** Bundled Poppins Medium font face used for medium-weight typography.

### `src/main/resources/fonts/Poppins-Regular.ttf`

**Overview.** Bundled Poppins Regular font face used for default typography.

### `src/main/resources/fonts/Poppins-SemiBold.ttf`

**Overview.** Bundled Poppins SemiBold font face used for semibold typography.

### `src/main/resources/images/home-background.png`

**Overview.** Raster home-screen background resource. It is a visual asset rather than executable logic.

### `src/main/resources/release-trusted-keys.json`

**Overview.** Pinned release-signing trust store consumed by release manifest signature verification. Key IDs must map to trusted public keys; unknown signers are rejected.

### `src/main/resources/version.properties`

**Overview.** Packaged application version resource read by update/version services; keep synchronized with release/build versioning.

## `src/test/java` — non-UI tests

Tests are grouped by their original package. The test names below are individual files in the same relative structure as the production code. Their descriptions state the invariant each file protects; test source remains the executable authority.

### `src/test/java/JavaLocatorTest.java`

**Purpose.** Standalone diagnostic harness for JavaLocator discovery; exercises local runtime selection outside the JUnit suite.

### `src/test/java/TestNatives.java`

**Purpose.** Standalone native-library extraction harness used to inspect/exercise the native pipeline against a local test fixture.

### `src/test/java/dev/itsdropper/vanta/LauncherProcessLifecycleTest.java`

**Purpose.** Verifies cleanup callbacks execute and the launcher JVM exits even when non-daemon threads remain alive.

### `src/test/java/dev/itsdropper/vanta/MainUpdaterDispatchTest.java`

**Purpose.** Checks updater and cleanup CLI modes are recognized before JavaFX startup and normal arguments are not misrouted.

### `src/test/java/dev/itsdropper/vanta/launcher/LegacyArgumentResolverTest.java`

**Purpose.** Covers whitespace-safe tokenization, quoted arguments, unknown placeholders and unterminated quotes.

### `src/test/java/dev/itsdropper/vanta/launcher/LibraryResolverTest.java`

**Purpose.** Checks fallback from incomplete artifact descriptors to Maven-coordinate resolution.

### `src/test/java/dev/itsdropper/vanta/launcher/MinecraftLauncherTest.java`

**Purpose.** Checks LaunchWrapper/Rift tweaker argument construction, invalid profile rejection and Java runtime mitigation boundaries.

### `src/test/java/dev/itsdropper/vanta/launcher/NativeExtractorTest.java`

**Purpose.** Covers OS-specific native filtering, ARM64 classifier choice, atomic publication and preservation of old native output on failure.

### `src/test/java/dev/itsdropper/vanta/launcher/PlatformTest.java`

**Purpose.** Validates OS/architecture detection, XDG/macOS/Windows data paths, Java executable naming and supported packaging target mapping.

### `src/test/java/dev/itsdropper/vanta/launcher/account/AccountManagerSecurityTest.java`

**Purpose.** Validates canonical UUID normalization and rejects path traversal/noncanonical identifiers before persistence.

### `src/test/java/dev/itsdropper/vanta/launcher/auth/AuthManagerFailureClassificationTest.java`

**Purpose.** Ensures only invalid_grant triggers interactive reauthentication, transport/service errors remain retryable and popup cancellation is recognized.

### `src/test/java/dev/itsdropper/vanta/launcher/environment/EnvironmentDiagnosticServiceTest.java`

**Purpose.** Checks healthy, missing, malformed and incomplete instance metadata plus diagnostics against an already-resolved classpath.

### `src/test/java/dev/itsdropper/vanta/launcher/environment/EnvironmentDriftComparisonTest.java`

**Purpose.** Checks read-only drift detection for versions, mods and required Java; verifies instance identity and schema compatibility.

### `src/test/java/dev/itsdropper/vanta/launcher/environment/EnvironmentLockfileServiceTest.java`

**Purpose.** Validates portable capture, stable SHA-256 fingerprints, schema migration, nested content, symlink rejection and path-traversal defenses.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/BabricInstallerTest.java`

**Purpose.** Exercises Babric overlay composition, legacy native classifier conversion, pinned versions and unsupported/malformed profile rejection.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/DownloadUtilIntegrationTest.java`

**Purpose.** Runs a local HTTP transfer server to validate publication, resume semantics, range handling, checksum failure and preservation of existing files.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/DownloadUtilTest.java`

**Purpose.** Unit tests segmented/resumable range arithmetic, malformed Content-Range rejection, response-size limits and URL/digest validation.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/EnvironmentGuardianServiceTest.java`

**Purpose.** Checks backup eligibility, verification-before-pruning, retention, disabled mode and preservation of old archives after backup failure.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/InstalledModManagerTest.java`

**Purpose.** Checks registry round trips, filename-based enabled/disabled tracking and atomic persistence on serialization failure.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/InstanceBackupServiceTest.java`

**Purpose.** Checks portable restore settings, archive verification, required Vanta metadata and ZIP path traversal rejection.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/InstanceManagerDuplicateTest.java`

**Purpose.** Checks duplicate content selection, symlink containment, normalized directory names and race-safe unique directory claims.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/InstanceManagerRepairDiscoveryTest.java`

**Purpose.** Checks incomplete-install discovery accepts only safely identifiable metadata and tolerates unresolved loader versions.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/InstanceRecoveryServiceTest.java`

**Purpose.** Checks recovery point contents, preservation of worlds, legacy profile preservation, journal rollback/commit recovery and retention.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/InstanceRepairServiceTest.java`

**Purpose.** Checks repair rollback of client/runtime/native trees and ensures user content/transient or mod failures are not automatically repaired.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/LiteLoaderInstallerTest.java`

**Purpose.** Checks official stable metadata overlay generation and rejects missing stable releases/untrusted repositories.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/LoaderCompatibilityServiceTest.java`

**Purpose.** Checks available/unsupported outcomes and ensures network, malformed metadata and unexpected errors remain retryable rather than unsupported.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/MinecraftFileInstallerPlatformTest.java`

**Purpose.** Checks native library architecture matching for ARM64, x86-64 and generic bitness rules.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/ModLoaderInstallerTest.java`

**Purpose.** Checks patch application to a copy, exploded bootstrap extraction, archive traversal rejection and pinned supported game versions.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/ModrinthAppImporterTest.java`

**Purpose.** Checks app directory discovery, loader mapping and read-only SQLite profile parsing.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/MrpackCompatibilityTest.java`

**Purpose.** Ensures unsupported/custom loaders are not misrepresented as vanilla and invalid/multi-loader manifests are rejected.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/NilLoaderInstallerTest.java`

**Purpose.** Checks version resolution and validates that downloaded JARs expose a valid Java-agent entry point.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/PrismLauncherImporterTest.java`

**Purpose.** Checks Prism config parsing, path resolution, loader component recognition, safe root detection and selective user-data migration.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/RiftInstallerTest.java`

**Purpose.** Checks official Rift support is limited to Minecraft 1.13 and generated LaunchWrapper profile shape.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/RiftModMetadataCompatibilityTest.java`

**Purpose.** Checks missing listener-side normalization while preserving original archive content and ignoring unrelated JARs.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/VPackServiceTest.java`

**Purpose.** Exercises selective pack export, portable settings, symlink and ZIP path validation, declared selection enforcement and malformed archive rejection.

### `src/test/java/dev/itsdropper/vanta/launcher/instance/VantaLoaderInstallerTest.java`

**Purpose.** Checks supported runtime baseline, nightly asset selection, release tag parsing, per-instance agent path and JAR manifest/content validation.

### `src/test/java/dev/itsdropper/vanta/launcher/loader/LoaderProfileStackMergerTest.java`

**Purpose.** Checks library merge and tweaker ordering, OS-disjoint versions and incompatible main-class rejection.

### `src/test/java/dev/itsdropper/vanta/launcher/loader/LoaderStackConfigurationTest.java`

**Purpose.** Checks ordered layers, persistence without derived fields and validation of duplicate IDs/invalid tweaker names.

### `src/test/java/dev/itsdropper/vanta/launcher/modrinth/InstalledModScannerTest.java`

**Purpose.** Checks Fabric/Quilt/Forge/NeoForge metadata parsing and safe handling of unsupported or malformed archives.

### `src/test/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthLoaderIdsTest.java`

**Purpose.** Checks custom loader facet fallback, VantaLoader canonical ID mapping and game-version family matching.

### `src/test/java/dev/itsdropper/vanta/launcher/repair/CrashCaseRepositoryTest.java`

**Purpose.** Checks sanitized local storage, review transitions, confirmed-only exports, schema upgrades, bounded imports and concurrent writers.

### `src/test/java/dev/itsdropper/vanta/launcher/repair/CrashCorpusQualityTest.java`

**Purpose.** Checks corpus label balance, duplicate quality, privacy redaction and held-out classifier stability.

### `src/test/java/dev/itsdropper/vanta/launcher/repair/DeterministicCrashModelTest.java`

**Purpose.** Checks corpus training, stable deterministic predictions and rejection of generic/unrelated log text.

### `src/test/java/dev/itsdropper/vanta/launcher/repair/RepairBackupServiceTest.java`

**Purpose.** Checks rollback snapshots restore protected metadata without touching user data and handles absent/incomplete snapshots safely.

### `src/test/java/dev/itsdropper/vanta/launcher/repair/RepairCoordinatorTest.java`

**Purpose.** Checks conservative repair gating for known damage, unrelated/unknown failures and explicit user authorization.

### `src/test/java/dev/itsdropper/vanta/launcher/repair/RepairDiagnosticEngineTest.java`

**Purpose.** Checks known mod, mixin, graphics, auth and archive signatures, category mapping and suppression of normal startup messages.

### `src/test/java/dev/itsdropper/vanta/launcher/security/CredentialManagerPlatformTest.java`

**Purpose.** Platform-gated tests for Windows DPAPI and macOS Keychain credential round trips.

### `src/test/java/dev/itsdropper/vanta/launcher/service/AccountServiceHealthTest.java`

**Purpose.** Checks explicit no-account/closed health states and that refresh without an account does not corrupt service state.

### `src/test/java/dev/itsdropper/vanta/launcher/service/AccountSessionMaintenanceTest.java`

**Purpose.** Checks background refresh decisions against the complete upstream token chain and avoids unnecessary refreshes.

### `src/test/java/dev/itsdropper/vanta/launcher/service/LaunchHistoryRepositoryTest.java`

**Purpose.** Checks durable lifecycle transitions, evidence sanitization, concurrent writers, retention and unified instance-operation timeline events.

### `src/test/java/dev/itsdropper/vanta/launcher/service/LaunchPerformanceTrackerTest.java`

**Purpose.** Checks separate medians for baseline/optimized and modded/unmodded launch cohorts.

### `src/test/java/dev/itsdropper/vanta/launcher/service/ModrinthDependencyResolverConcurrencyTest.java`

**Purpose.** Checks graph resolution and cache clearing share an exclusive resolver boundary while candidate caches remain concurrency-safe.

### `src/test/java/dev/itsdropper/vanta/launcher/service/ModrinthDependencyResolverGraphTest.java`

**Purpose.** Checks backtracking, required-version preservation, unsatisfiable graphs, cycles and optional dependency behavior.

### `src/test/java/dev/itsdropper/vanta/launcher/service/ModrinthGraphInstallTransactionTest.java`

**Purpose.** Checks journaled rollback/commit, interruption recovery, path traversal defense, nested transactions and per-instance serialization.

### `src/test/java/dev/itsdropper/vanta/launcher/service/ModrinthInstallServiceIntegrationTest.java`

**Purpose.** Exercises resolved graph installation through real filesystem/registry paths, upgrade commit order and rollback after download/registry failures.

### `src/test/java/dev/itsdropper/vanta/launcher/update/ApplicationLocatorTest.java`

**Purpose.** Checks jpackage image discovery on Windows, Linux and macOS, recovery from bundled runtimes and rejection of incomplete layouts.

### `src/test/java/dev/itsdropper/vanta/launcher/update/ReleaseManifestSignaturesTest.java`

**Purpose.** Checks exact-byte Ed25519 signature verification, pinned signer enforcement and malformed/duplicate field rejection.

### `src/test/java/dev/itsdropper/vanta/launcher/update/ReleaseManifestTest.java`

**Purpose.** Checks schema, platform artifact selection, URL/file identity, canonical version and minimum launcher version validation.

### `src/test/java/dev/itsdropper/vanta/launcher/update/UpdateDownloaderSecurityTest.java`

**Purpose.** Checks safe release version parsing and restricts downloads to HTTPS GitHub release assets.

### `src/test/java/dev/itsdropper/vanta/launcher/update/UpdateDownloaderTest.java`

**Purpose.** Checks update payload download/checksum flow and cleanup/error behavior.

### `src/test/java/dev/itsdropper/vanta/launcher/update/UpdateServicePlatformTest.java`

**Purpose.** Checks platform-scoped signed manifest URL construction and rejects unsafe version path components.

### `src/test/java/dev/itsdropper/vanta/launcher/update/UpdateServiceTest.java`

**Purpose.** Checks explicit release tag normalization, path-injection rejection and duplicate JSON-key rejection.

### `src/test/java/dev/itsdropper/vanta/launcher/update/UpdateTransactionTest.java`

**Purpose.** Exercises interruption at each payload swap phase, health-marker commit, process liveness and exclusive installation locking.

### `src/test/java/dev/itsdropper/vanta/launcher/update/UpdaterLauncherPlatformTest.java`

**Purpose.** Checks install parent requirements, translocation handling, complete image copying and symlink/destination containment.

### `src/test/java/dev/itsdropper/vanta/launcher/update/UpdaterMainPlatformTest.java`

**Purpose.** Checks process identity, private readiness markers, stable-bootstrap recovery and executable permissions across packaged layouts.

### `src/test/java/dev/itsdropper/vanta/launcher/update/UpdaterProcessExitTest.java`

**Purpose.** Verifies external updater handoff terminates the original JVM despite remaining non-daemon threads.

### `src/test/java/dev/itsdropper/vanta/launcher/update/UpdaterWorkspaceCleanupTest.java`

**Purpose.** Checks managed workspace scope, recursive deletion without following links and process-aware stale cleanup.

### `src/test/java/dev/itsdropper/vanta/security/Ed25519SignaturesTest.java`

**Purpose.** Checks raw-key decoding, exact-payload verification and malformed key/signature rejection.

### `src/test/java/dev/itsdropper/vanta/security/RuntimeSecurityPolicyTest.java`

**Purpose.** Checks signed policy verification, tamper/expiry/unknown-mitigation rejection and architecture alias normalization.

### `src/test/java/dev/itsdropper/vanta/telemetry/TelemetryServiceTest.java`

**Purpose.** Checks event allowlisting and crash payload sanitization/redaction/length bounds.

## Change-impact guide

- **Launch path:** start at `Main`, then `LaunchService`, `LaunchDataBuilder`, `ClasspathBuilder`, `LibraryResolver`, `NativeExtractor` and `MinecraftLauncher`. Keep metadata resolution, filesystem preparation and process execution separate.
- **Instance lifecycle:** `InstanceManager` owns discovery and identity; `InstanceInstaller` coordinates creation; loader-specific installers own profile integration; repair/recovery/backup services must preserve user content.
- **Mod installation:** `ModrinthClient`/catalog models provide remote metadata; `ModrinthDependencyResolver` selects a satisfiable graph; `ModrinthGraphInstallTransaction` and `ModrinthInstallService` own safe publication/rollback.
- **Security and updates:** signed metadata must be verified over exact bytes against pinned keys; downloaded files must be origin-checked and digest-verified before publication; update payload swaps must remain journaled and health-acknowledged.
- **Filesystem safety:** paths derived from archives, metadata, IDs or remote manifests must remain within their intended managed roots. Preserve the existing symlink, size-limit, checksum, staging and atomic-publication invariants.
- **Privacy:** account sessions stay in OS credential storage; local diagnostics and crash corpora are sanitized; telemetry behavior is isolated in TelemetryService and must not be inferred from unrelated services.

## Accuracy notes

This reference describes implementation boundaries observed in the inspected source. It intentionally does not claim that a test passed, that a loader works on every platform/version, or that a feature is supported beyond what the code and tests establish. When behavior changes, update the matching file section and its corresponding test section in the same change.

### `src/main/java/dev/itsdropper/vanta/launcher/account/Account.java`

**Overview.** Account is part of the account subsystem. Account persistence and identity models. The manager owns canonical account IDs, encrypted storage, migration and atomic writes; Account is the MinecraftAuth session wrapper. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the account package's Account implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/account/AccountManager.java`

**Overview.** AccountManager is part of the account subsystem. Account persistence and identity models. The manager owns canonical account IDs, encrypted storage, migration and atomic writes; Account is the MinecraftAuth session wrapper. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the account package's AccountManager implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/auth/AuthManager.java`

**Overview.** AuthManager is part of the auth subsystem. Microsoft device-code OAuth and token-chain refresh. Preserve the distinction between explicit credential rejection, transient network failures and user cancellation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the auth package's AuthManager implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/download/DownloadCenterService.java`

**Overview.** DownloadCenterService is part of the download subsystem. Observable in-memory transfer task lifecycle. Download execution remains in DownloadUtil; this package publishes progress/state to listeners. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the download package's DownloadCenterService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/environment/EnvironmentDiagnosticService.java`

**Overview.** EnvironmentDiagnosticService is part of the environment subsystem. Read-only environment inspection and portable lockfile capture/comparison. These services must not mutate instance files. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the environment package's EnvironmentDiagnosticService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/environment/EnvironmentDriftReport.java`

**Overview.** EnvironmentDriftReport is part of the environment subsystem. Read-only environment inspection and portable lockfile capture/comparison. These services must not mutate instance files. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the environment package's EnvironmentDriftReport implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/environment/EnvironmentLockfile.java`

**Overview.** EnvironmentLockfile is part of the environment subsystem. Read-only environment inspection and portable lockfile capture/comparison. These services must not mutate instance files. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the environment package's EnvironmentLockfile implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/environment/EnvironmentLockfileService.java`

**Overview.** EnvironmentLockfileService is part of the environment subsystem. Read-only environment inspection and portable lockfile capture/comparison. These services must not mutate instance files. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the environment package's EnvironmentLockfileService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/fps/FpsHardwareDetector.java`

**Overview.** FpsHardwareDetector is part of the fps subsystem. Hardware profiling, local benchmark learning and option generation. Optimizer changes must remain constrained to owned performance keys. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the fps package's FpsHardwareDetector implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/fps/FpsHardwareProfile.java`

**Overview.** FpsHardwareProfile is part of the fps subsystem. Hardware profiling, local benchmark learning and option generation. Optimizer changes must remain constrained to owned performance keys. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the fps package's FpsHardwareProfile implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/fps/FpsInstanceGenerator.java`

**Overview.** FpsInstanceGenerator is part of the fps subsystem. Hardware profiling, local benchmark learning and option generation. Optimizer changes must remain constrained to owned performance keys. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the fps package's FpsInstanceGenerator implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/fps/FpsLearningEngine.java`

**Overview.** FpsLearningEngine is part of the fps subsystem. Hardware profiling, local benchmark learning and option generation. Optimizer changes must remain constrained to owned performance keys. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the fps package's FpsLearningEngine implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/fps/FpsOptionsOptimizer.java`

**Overview.** FpsOptionsOptimizer is part of the fps subsystem. Hardware profiling, local benchmark learning and option generation. Optimizer changes must remain constrained to owned performance keys. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the fps package's FpsOptionsOptimizer implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/fps/FpsTuningProfile.java`

**Overview.** FpsTuningProfile is part of the fps subsystem. Hardware profiling, local benchmark learning and option generation. Optimizer changes must remain constrained to owned performance keys. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the fps package's FpsTuningProfile implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/AssetDownloader.java`

**Overview.** AssetDownloader is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's AssetDownloader implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/AssetInstaller.java`

**Overview.** AssetInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's AssetInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/BabricInstaller.java`

**Overview.** BabricInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's BabricInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/DownloadUtil.java`

**Overview.** DownloadUtil is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's DownloadUtil implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/EnvironmentGuardianService.java`

**Overview.** EnvironmentGuardianService is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's EnvironmentGuardianService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/FabricInstaller.java`

**Overview.** FabricInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's FabricInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/ForgeInstaller.java`

**Overview.** ForgeInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's ForgeInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/InstalledModManager.java`

**Overview.** InstalledModManager is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's InstalledModManager implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/InstalledModRecord.java`

**Overview.** InstalledModRecord is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's InstalledModRecord implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/InstanceBackupService.java`

**Overview.** InstanceBackupService is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's InstanceBackupService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/InstanceInstallationProgress.java`

**Overview.** InstanceInstallationProgress is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's InstanceInstallationProgress implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/InstanceInstaller.java`

**Overview.** InstanceInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's InstanceInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/InstanceManager.java`

**Overview.** InstanceManager is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's InstanceManager implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/InstancePresets.java`

**Overview.** InstancePresets is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's InstancePresets implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/InstanceRecoveryService.java`

**Overview.** InstanceRecoveryService is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's InstanceRecoveryService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/InstanceRepairService.java`

**Overview.** InstanceRepairService is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's InstanceRepairService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/LegacyFabricInstaller.java`

**Overview.** LegacyFabricInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's LegacyFabricInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/LiteLoaderInstaller.java`

**Overview.** LiteLoaderInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's LiteLoaderInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/LoaderCompatibilityService.java`

**Overview.** LoaderCompatibilityService is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's LoaderCompatibilityService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/MinecraftFileInstaller.java`

**Overview.** MinecraftFileInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's MinecraftFileInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/MinecraftMappingsInstaller.java`

**Overview.** MinecraftMappingsInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's MinecraftMappingsInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/MinecraftVersionResolver.java`

**Overview.** MinecraftVersionResolver is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's MinecraftVersionResolver implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/ModLoaderInstaller.java`

**Overview.** ModLoaderInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's ModLoaderInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/ModrinthAppImporter.java`

**Overview.** ModrinthAppImporter is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's ModrinthAppImporter implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/MrpackExporter.java`

**Overview.** MrpackExporter is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's MrpackExporter implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/MrpackInstaller.java`

**Overview.** MrpackInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's MrpackInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/NativeInstaller.java`

**Overview.** NativeInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's NativeInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/NeoForgeInstaller.java`

**Overview.** NeoForgeInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's NeoForgeInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/NilLoaderInstaller.java`

**Overview.** NilLoaderInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's NilLoaderInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/OrnitheInstaller.java`

**Overview.** OrnitheInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's OrnitheInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/PrismLauncherImporter.java`

**Overview.** PrismLauncherImporter is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's PrismLauncherImporter implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/QuiltInstaller.java`

**Overview.** QuiltInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's QuiltInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/RiftInstaller.java`

**Overview.** RiftInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's RiftInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/RiftModMetadataCompatibility.java`

**Overview.** RiftModMetadataCompatibility is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's RiftModMetadataCompatibility implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/SharedResourceRepairService.java`

**Overview.** SharedResourceRepairService is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's SharedResourceRepairService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/SteinInstaller.java`

**Overview.** SteinInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's SteinInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/VPackService.java`

**Overview.** VPackService is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's VPackService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/instance/VantaLoaderInstaller.java`

**Overview.** VantaLoaderInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the instance package's VantaLoaderInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/java/JavaInstaller.java`

**Overview.** JavaInstaller is part of the java subsystem. Managed/system Java discovery, installation and version validation. The required Java major comes from Minecraft metadata where available. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the java package's JavaInstaller implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/java/JavaLocator.java`

**Overview.** JavaLocator is part of the java subsystem. Managed/system Java discovery, installation and version validation. The required Java major comes from Minecraft metadata where available. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the java package's JavaLocator implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/java/JavaVerifier.java`

**Overview.** JavaVerifier is part of the java subsystem. Managed/system Java discovery, installation and version validation. The required Java major comes from Minecraft metadata where available. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the java package's JavaVerifier implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/loader/LoaderProfileStackMerger.java`

**Overview.** LoaderProfileStackMerger is part of the loader subsystem. Ordered multi-loader profile configuration, deterministic profile merging and persistence. Validate layer IDs, tweaker names and paths before launch. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the loader package's LoaderProfileStackMerger implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/loader/LoaderStackConfiguration.java`

**Overview.** LoaderStackConfiguration is part of the loader subsystem. Ordered multi-loader profile configuration, deterministic profile merging and persistence. Validate layer IDs, tweaker names and paths before launch. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the loader package's LoaderStackConfiguration implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/loader/LoaderStackManager.java`

**Overview.** LoaderStackManager is part of the loader subsystem. Ordered multi-loader profile configuration, deterministic profile merging and persistence. Validate layer IDs, tweaker names and paths before launch. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the loader package's LoaderStackManager implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/model/Instance.java`

**Overview.** Instance is part of the model subsystem. Persistence/data-transfer models used by installation, launch, server history and version metadata. These classes should remain free of I/O orchestration. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the model package's Instance implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/model/InstancePreset.java`

**Overview.** InstancePreset is part of the model subsystem. Persistence/data-transfer models used by installation, launch, server history and version metadata. These classes should remain free of I/O orchestration. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the model package's InstancePreset implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/model/InstanceSettings.java`

**Overview.** InstanceSettings is part of the model subsystem. Persistence/data-transfer models used by installation, launch, server history and version metadata. These classes should remain free of I/O orchestration. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the model package's InstanceSettings implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/model/Library.java`

**Overview.** Library is part of the model subsystem. Persistence/data-transfer models used by installation, launch, server history and version metadata. These classes should remain free of I/O orchestration. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the model package's Library implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/model/ServerHistoryEntry.java`

**Overview.** ServerHistoryEntry is part of the model subsystem. Persistence/data-transfer models used by installation, launch, server history and version metadata. These classes should remain free of I/O orchestration. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the model package's ServerHistoryEntry implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/model/VersionManifest.java`

**Overview.** VersionManifest is part of the model subsystem. Persistence/data-transfer models used by installation, launch, server history and version metadata. These classes should remain free of I/O orchestration. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the model package's VersionManifest implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/DependencyRequirement.java`

**Overview.** DependencyRequirement is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's DependencyRequirement implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/InstalledMod.java`

**Overview.** InstalledMod is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's InstalledMod implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/InstalledModScanner.java`

**Overview.** InstalledModScanner is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's InstalledModScanner implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthClient.java`

**Overview.** ModrinthClient is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's ModrinthClient implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthContentType.java`

**Overview.** ModrinthContentType is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's ModrinthContentType implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthDependency.java`

**Overview.** ModrinthDependency is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's ModrinthDependency implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthFile.java`

**Overview.** ModrinthFile is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's ModrinthFile implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthLicense.java`

**Overview.** ModrinthLicense is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's ModrinthLicense implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthLoaderIds.java`

**Overview.** ModrinthLoaderIds is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's ModrinthLoaderIds implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthProject.java`

**Overview.** ModrinthProject is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's ModrinthProject implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthSearchHit.java`

**Overview.** ModrinthSearchHit is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's ModrinthSearchHit implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthSearchResult.java`

**Overview.** ModrinthSearchResult is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's ModrinthSearchResult implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthTag.java`

**Overview.** ModrinthTag is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's ModrinthTag implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthTeamMember.java`

**Overview.** ModrinthTeamMember is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's ModrinthTeamMember implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthUser.java`

**Overview.** ModrinthUser is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's ModrinthUser implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthVersion.java`

**Overview.** ModrinthVersion is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's ModrinthVersion implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/modrinth/VersionConstraintChecker.java`

**Overview.** VersionConstraintChecker is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the modrinth package's VersionConstraintChecker implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/repair/CrashCaseRepository.java`

**Overview.** CrashCaseRepository is part of the repair subsystem. Local crash evidence, diagnosis, confidence and rollback policy. Classification is advisory; only the coordinator may authorize managed repair. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the repair package's CrashCaseRepository implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/repair/DeterministicCrashModel.java`

**Overview.** DeterministicCrashModel is part of the repair subsystem. Local crash evidence, diagnosis, confidence and rollback policy. Classification is advisory; only the coordinator may authorize managed repair. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the repair package's DeterministicCrashModel implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/repair/FailureCategory.java`

**Overview.** FailureCategory is part of the repair subsystem. Local crash evidence, diagnosis, confidence and rollback policy. Classification is advisory; only the coordinator may authorize managed repair. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the repair package's FailureCategory implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/repair/RepairBackupService.java`

**Overview.** RepairBackupService is part of the repair subsystem. Local crash evidence, diagnosis, confidence and rollback policy. Classification is advisory; only the coordinator may authorize managed repair. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the repair package's RepairBackupService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/repair/RepairConfidence.java`

**Overview.** RepairConfidence is part of the repair subsystem. Local crash evidence, diagnosis, confidence and rollback policy. Classification is advisory; only the coordinator may authorize managed repair. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the repair package's RepairConfidence implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/repair/RepairCoordinator.java`

**Overview.** RepairCoordinator is part of the repair subsystem. Local crash evidence, diagnosis, confidence and rollback policy. Classification is advisory; only the coordinator may authorize managed repair. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the repair package's RepairCoordinator implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/repair/RepairDiagnosis.java`

**Overview.** RepairDiagnosis is part of the repair subsystem. Local crash evidence, diagnosis, confidence and rollback policy. Classification is advisory; only the coordinator may authorize managed repair. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the repair package's RepairDiagnosis implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/repair/RepairDiagnosticEngine.java`

**Overview.** RepairDiagnosticEngine is part of the repair subsystem. Local crash evidence, diagnosis, confidence and rollback policy. Classification is advisory; only the coordinator may authorize managed repair. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the repair package's RepairDiagnosticEngine implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/security/CredentialManager.java`

**Overview.** CredentialManager is part of the security subsystem. Credential protection and platform security boundaries. Secrets must remain in OS credential stores rather than launcher config files. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the security package's CredentialManager implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/security/WindowsCredentialManager.java`

**Overview.** WindowsCredentialManager is part of the security subsystem. Credential protection and platform security boundaries. Secrets must remain in OS credential stores rather than launcher config files. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the security package's WindowsCredentialManager implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/server/ManagedJavaRuntimeService.java`

**Overview.** ManagedJavaRuntimeService is part of the server subsystem. Local server process lifecycle, RCON protocol and per-server Java runtime management. Process output and backups remain local. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the server package's ManagedJavaRuntimeService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/server/RemoteRconClient.java`

**Overview.** RemoteRconClient is part of the server subsystem. Local server process lifecycle, RCON protocol and per-server Java runtime management. Process output and backups remain local. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the server package's RemoteRconClient implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/server/ServerManagerService.java`

**Overview.** ServerManagerService is part of the server subsystem. Local server process lifecycle, RCON protocol and per-server Java runtime management. Process output and backups remain local. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the server package's ServerManagerService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/AccountService.java`

**Overview.** AccountService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's AccountService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/AccountSkinService.java`

**Overview.** AccountSkinService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's AccountSkinService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/CompatibilityResult.java`

**Overview.** CompatibilityResult is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's CompatibilityResult implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/DiscordPresenceService.java`

**Overview.** DiscordPresenceService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's DiscordPresenceService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/InstanceUsageManager.java`

**Overview.** InstanceUsageManager is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's InstanceUsageManager implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/LaunchFailure.java`

**Overview.** LaunchFailure is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's LaunchFailure implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/LaunchFailureParser.java`

**Overview.** LaunchFailureParser is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's LaunchFailureParser implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/LaunchHistoryRepository.java`

**Overview.** LaunchHistoryRepository is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's LaunchHistoryRepository implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/LaunchPerformanceTracker.java`

**Overview.** LaunchPerformanceTracker is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's LaunchPerformanceTracker implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/LaunchService.java`

**Overview.** LaunchService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's LaunchService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/ModrinthArtifactDownloader.java`

**Overview.** ModrinthArtifactDownloader is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's ModrinthArtifactDownloader implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/ModrinthCatalogService.java`

**Overview.** ModrinthCatalogService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's ModrinthCatalogService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/ModrinthContentService.java`

**Overview.** ModrinthContentService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's ModrinthContentService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/ModrinthDependencyResolver.java`

**Overview.** ModrinthDependencyResolver is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's ModrinthDependencyResolver implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/ModrinthGraphInstallTransaction.java`

**Overview.** ModrinthGraphInstallTransaction is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's ModrinthGraphInstallTransaction implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/ModrinthInstallService.java`

**Overview.** ModrinthInstallService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's ModrinthInstallService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/ModrinthRepairService.java`

**Overview.** ModrinthRepairService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's ModrinthRepairService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/ModrinthService.java`

**Overview.** ModrinthService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's ModrinthService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/MultiLaunchService.java`

**Overview.** MultiLaunchService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's MultiLaunchService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/ServerHistoryManager.java`

**Overview.** ServerHistoryManager is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's ServerHistoryManager implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/service/ServerTarget.java`

**Overview.** ServerTarget is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the service package's ServerTarget implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/state/InstanceState.java`

**Overview.** InstanceState is part of the state subsystem. Read-only aggregate health snapshots and filesystem fingerprinting for instance/shared-resource status. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the state package's InstanceState implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/state/InstanceStateEngine.java`

**Overview.** InstanceStateEngine is part of the state subsystem. Read-only aggregate health snapshots and filesystem fingerprinting for instance/shared-resource status. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the state package's InstanceStateEngine implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/state/SharedState.java`

**Overview.** SharedState is part of the state subsystem. Read-only aggregate health snapshots and filesystem fingerprinting for instance/shared-resource status. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the state package's SharedState implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/update/ApplicationLocator.java`

**Overview.** ApplicationLocator is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the update package's ApplicationLocator implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/update/ReleaseManifest.java`

**Overview.** ReleaseManifest is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the update package's ReleaseManifest implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/update/ReleaseManifestSignatures.java`

**Overview.** ReleaseManifestSignatures is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the update package's ReleaseManifestSignatures implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/update/UpdateChecker.java`

**Overview.** UpdateChecker is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the update package's UpdateChecker implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/update/UpdateDownloader.java`

**Overview.** UpdateDownloader is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the update package's UpdateDownloader implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/update/UpdateInfo.java`

**Overview.** UpdateInfo is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the update package's UpdateInfo implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/update/UpdateService.java`

**Overview.** UpdateService is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the update package's UpdateService implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/update/UpdateTransaction.java`

**Overview.** UpdateTransaction is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the update package's UpdateTransaction implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/update/UpdaterLauncher.java`

**Overview.** UpdaterLauncher is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the update package's UpdaterLauncher implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/update/UpdaterMain.java`

**Overview.** UpdaterMain is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the update package's UpdaterMain implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

### `src/main/java/dev/itsdropper/vanta/launcher/update/UpdaterWorkspaceCleanup.java`

**Overview.** UpdaterWorkspaceCleanup is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

**Change boundary.** Treat this file as the update package's UpdaterWorkspaceCleanup implementation unit. Preserve the package invariants above and update its corresponding tests when changing behavior.

## Source-root packages omitted from `launcher/`

### `src/main/java/dev/itsdropper/vanta/launcher/security/CredentialManager.java`

**Overview.** Cross-platform credential-vault facade for account session encryption, decryption and deletion using native operating-system credential facilities.

### `src/main/java/dev/itsdropper/vanta/launcher/security/WindowsCredentialManager.java`

**Overview.** Windows DPAPI implementation behind CredentialManager; it isolates Windows-specific protected-data operations from cross-platform account code.

### `src/main/java/dev/itsdropper/vanta/launcher/telemetry/TelemetryService.java`

**Overview.** Optional telemetry and crash-report delivery boundary. It controls event allowlisting, crash extraction, sanitization and bounded HTTP submission.
