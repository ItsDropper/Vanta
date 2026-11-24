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

### `dev/itsdropper/vanta/i18n/messages.properties`

**Overview.** Default localization bundle used when no explicit locale-specific bundle is selected. Contains the baseline message-key set.

### `dev/itsdropper/vanta/i18n/messages_de.properties`

**Overview.** German localization bundle. Message keys are consumed by the Localization resource-bundle layer.

### `dev/itsdropper/vanta/i18n/messages_en.properties`

**Overview.** English localization bundle and primary English translation source.

### `dev/itsdropper/vanta/i18n/messages_es.properties`

**Overview.** Spanish localization bundle.

### `dev/itsdropper/vanta/i18n/messages_fi.properties`

**Overview.** Finnish localization bundle.

### `dev/itsdropper/vanta/i18n/messages_fr.properties`

**Overview.** French localization bundle.

### `dev/itsdropper/vanta/i18n/messages_it.properties`

**Overview.** Italian localization bundle.

### `dev/itsdropper/vanta/i18n/messages_nl.properties`

**Overview.** Dutch localization bundle.

### `dev/itsdropper/vanta/i18n/messages_pt_PT.properties`

**Overview.** European Portuguese localization bundle; locale suffix distinguishes it from Brazilian Portuguese.

### `dev/itsdropper/vanta/i18n/messages_sv.properties`

**Overview.** Swedish localization bundle.

### `dev/itsdropper/vanta/launcher/repair/crash-cases.tsv`

**Overview.** Versioned tab-separated labelled crash corpus loaded by DeterministicCrashModel. Labels/categories and sanitized examples are training data; edits should preserve schema, balance, deduplication and privacy checks enforced by CrashCorpusQualityTest.

### `fonts/OFL.txt`

**Overview.** SIL Open Font License text accompanying the bundled Poppins font files; retain with redistributed font assets.

### `fonts/Poppins-Light.ttf`

**Overview.** Bundled Poppins Light font face used by the launcher font registration layer.

### `fonts/Poppins-Medium.ttf`

**Overview.** Bundled Poppins Medium font face used for medium-weight typography.

### `fonts/Poppins-Regular.ttf`

**Overview.** Bundled Poppins Regular font face used for default typography.

### `fonts/Poppins-SemiBold.ttf`

**Overview.** Bundled Poppins SemiBold font face used for semibold typography.

### `images/home-background.png`

**Overview.** Raster home-screen background resource. It is a visual asset rather than executable logic.

### `release-trusted-keys.json`

**Overview.** Pinned release-signing trust store consumed by release manifest signature verification. Key IDs must map to trusted public keys; unknown signers are rejected.

### `version.properties`

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
