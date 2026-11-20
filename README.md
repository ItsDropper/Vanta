# Vanta

**A cross-platform Minecraft Java Edition launcher for managing isolated installations, content, and recovery.**

Vanta is a JavaFX desktop launcher focused on keeping Minecraft environments manageable. Instead of treating each installation as a disposable launch button, Vanta provides instance management, loader installation, content discovery, installation-health tools, backups, diagnostics, server management, and update/recovery workflows in one desktop application.

Vanta is actively developed. Feature availability, loader version limits, and platform-specific validation are documented separately; a feature's presence in the UI is not a guarantee of universal compatibility.

## Contents

- [What Vanta does](#what-vanta-does)
- [Launcher navigation](#launcher-navigation)
- [Instance workflows](#instance-workflows)
- [Loader integrations](#loader-integrations)
- [Mod and content management](#mod-and-content-management)
- [Health, repair, and recovery](#health-repair-and-recovery)
- [Environment baselines and backups](#environment-baselines-and-backups)
- [Accounts, skins, and settings](#accounts-skins-and-settings)
- [Server Manager](#server-manager)
- [Crash diagnosis and review](#crash-diagnosis-and-review)
- [Downloads, updates, and application recovery](#downloads-updates-and-application-recovery)
- [Experimental tools](#experimental-tools)
- [Data, privacy, and security](#data-privacy-and-security)
- [Platform support](#platform-support)
- [Build from source](#build-from-source)
- [Project architecture](#project-architecture)
- [Documentation](#documentation)
- [Contributing](#contributing)
- [Disclaimer](#disclaimer)

## What Vanta does

- **Isolated instances:** Minecraft versions, loaders, mods, configuration, worlds, logs, screenshots, resource packs, and shader packs are organized per installation.
- **Minecraft installation and launching:** Resolve Minecraft metadata, download required game files, libraries, assets and native dependencies, prepare launch arguments, and launch the selected installation.
- **Loader integrations:** 13 non-vanilla loader integrations with loader-specific installers, metadata, bootstrap and launch behavior.
- **Modrinth integration:** Discover projects and compatible versions, browse content, install projects with dependencies, inspect installed content, and repair missing mod dependencies.
- **Modpacks:** Browse Modrinth modpacks, import supported `.mrpack` archives, and export Vanta packs (`.vantapack`). Compatibility depends on the pack format and loader support.
- **Instance recovery:** Inspect managed files, repair installation components, restore repair metadata, back up and restore instance archives, and validate backup archives.
- **Environment inspection:** Capture an environment baseline and compare it with the current installation to identify drift.
- **Server tools:** Manage local server profiles and processes, console, server properties, backups/restores and compatible Java runtime provisioning; a remote connection workflow is also available.
- **Crash tools:** Deterministic local crash diagnosis, launch history, crash-case review and evidence-based fix references.
- **Application updates:** Check releases, download platform packages, verify checksums, and use an external updater to replace the application image. Application repair can reinstall the latest compatible verified package.
- **Local-first:** Instances and launcher state remain on the device. Optional telemetry and crash diagnostics are separate settings and disabled by default.

## Launcher navigation

The sidebar is the primary navigation. It can switch to a compact icon layout when the window is narrow.

| Page | Purpose |
|---|---|
| **Home** | Launcher dashboard, recent instances, launch entry points and onboarding |
| **Instances** | Find, sort, create, launch, back up, restore and manage installations |
| **Mods** | Global Modrinth content browser for mods, resource packs, data packs, shaders and modpacks |
| **State Center** | Inspect managed installation health and shared resources; save and compare environment baselines; repair managed files |
| **Crash Lab** | Review launch/crash history and diagnosis |
| **Skin Changer** | View and manage account skin workflows |
| **Accounts** | Microsoft account sign-in, account selection and account management |
| **Settings** | Appearance, Discord presence, general launcher behavior, Minecraft, downloads, storage/cache, state, repair/diagnostics and About |
| **Download Center** | Inspect download activity and available download status information |
| **Server Manager** | Create/import local server profiles, manage server processes and console, or open remote connection tools |
| **Experimental** | Experimental authentication/performance/cache diagnostics and local crash-case review entry points |

### Keyboard shortcuts

- `Ctrl+1` — Home
- `Ctrl+2` — Instances
- `Ctrl+3` — Mods
- `Ctrl+4` — State Center
- `Ctrl+5` — Skin Changer
- `Ctrl+6` — Accounts
- `Ctrl+7` — Settings
- `Ctrl+8` — Download Center
- `Ctrl+9` — Server Manager
- `Ctrl+K` — Open or close the command palette
- `Ctrl+N` — Start the instance-creation flow

The command palette also provides navigation commands, create/import actions, application repair, and launch commands for instances currently loaded into the instance list. Shortcut handling is designed not to consume ordinary text-editing shortcuts.

## Instance workflows

### Create an installation

Use **Instances → Create** to enter the creation flow. Vanta provides a creation-type selection for supported workflows, including:

- A custom installation where you select the Minecraft version and loader.
- Built-in instance presets, with configuration before creation.
- Modrinth modpack creation from the pack browser.
- Importing an existing installation rather than starting from an empty directory.

Installation work is performed by launcher services and reports progress to the UI. The selected installation is then added to the instance library.

### Configure and launch

Instances keep their own game directory. Open an instance's management view to access its installed mods and the associated settings, content browsing, and screenshots workflows. Launching through Vanta prepares the selected instance's Minecraft and loader configuration and starts its game process using its instance directory.

Instance cards/list controls include search and sorting. The instance library also exposes backup-related actions for creating, restoring and verifying backups.

### Presets

Vanta includes built-in presets. Presets can define the base Minecraft version, loader choice and curated content such as mods or shaders. Preset installation resolves requested projects against the selected game version and loader; projects that cannot be found or have no compatible version may be skipped and reported. The FPS-optimized preset has a dedicated generator and may apply hardware-aware choices.

Preset contents and compatibility are subject to upstream project availability and version constraints.

### Import existing instances

Vanta includes migration workflows for supported external launcher formats, including Prism Launcher and Modrinth App. Import reconstructs the installation through Vanta's installation/loader paths where possible rather than assuming every foreign profile can be copied without conversion.

Imported content may need review when the source uses unsupported loaders, custom files, or pack-specific behavior. Review the import report and back up the source installation before migration.

## Loader integrations

Vanta has 13 non-vanilla loader integrations:

| Loader | Integration |
|---|---|
| Fabric | Meta profile installation and launch integration |
| Forge | Official installer and generated profile integration |
| NeoForge | Installer/profile metadata and version mapping |
| Quilt | Quilt Meta profile integration |
| Ornithe | Ornithe Meta profile integration |
| Legacy Fabric | Legacy Fabric profile integration |
| Babric | Legacy Beta-focused integration |
| VantaLoader | Independent experimental Java-agent loader |
| NilLoader | Java-agent installation and launch |
| LiteLoader | Stable-manifest-based legacy integration |
| Rift | Original Rift integration for its supported target |
| Stein | Isolated official installer integration |
| Risugami's ModLoader | Pinned, version-specific archive patch integration |

These integrations do not share one generic installation method. Some use metadata profile overlays, some execute official installers, some attach Java agents, some use legacy tweakers, and some patch an instance-local client JAR. Vanta keeps these differences in loader-specific paths.

Compatibility limits are intentional. The loader list does not imply arbitrary Minecraft-version support, cross-loader mod compatibility, or support for every modpack format. VantaLoader is independent of Fabric and Forge; its own ecosystem is not a substitute for those APIs. Read the loader matrix and implementation notes before choosing a loader for a specific Minecraft version.

- [Loader Compatibility](docs/LOADER_COMPATIBILITY.md) — version boundaries and support status.
- [Loader Implementations](docs/LOADER_IMPLEMENTATIONS.md) — code paths, metadata sources, installer/bootstrap details.

## Mod and content management

### Global Modrinth browser

The **Mods** page is a global discovery browser. It supports the Modrinth content categories:

- Mods
- Resource packs
- Data packs
- Shaders
- Modpacks

Search and browse results can be filtered by available categories, loaders, Minecraft versions and project metadata. The browser supports loader and game-version filtering, tag/category filtering, sorting and project detail navigation. The available filters depend on the selected content type and Modrinth's metadata.

### Instance-specific content

From an instance's installed-content management, Vanta can inspect installed mods and open compatible configuration screens where available. Instance content workflows include browsing and installing content for that instance, opening project details, and managing screenshots.

### Installation and dependency handling

Vanta uses Modrinth metadata to find versions compatible with the target instance and resolve required dependencies. The installation/repair path validates downloads and works within the instance's content directories.

Compatibility is determined by the project's published versions, target Minecraft version, loader and content type. Installing a project from Modrinth is not a guarantee that it will behave correctly in-game or with other installed mods.

### Modpacks

Vanta can browse and install supported Modrinth modpacks, import supported `.mrpack` archives and export `.vantapack` packs. These formats have different capabilities; loader identities or files that the target format cannot express may not be portable. Inspect pack/import results rather than assuming the resulting installation is byte-for-byte identical to its source.

## Health, repair, and recovery

### State Center

State Center inspects Vanta-managed installations and shared resources, classifies health problems, and provides a view of affected installations. It is intended to help distinguish missing or invalid launcher-managed files from user-owned content.

The repair-all workflow rebuilds Vanta-managed installations/shared resources. Repairs should preserve user data such as worlds, configurations and installed mods where the repair path is designed to do so. Review the repair action and keep a backup of important data before destructive or broad repair operations.

### Instance repair

Vanta offers a dedicated repair view when a launch failure can be associated with an instance. Diagnosis may identify known Minecraft or dependency issues and provide deterministic repair options. The repair workflow can restore managed metadata and reinstall affected dependencies; broader repair rebuilds the managed Minecraft installation components.

Repair is not a universal mod-fixer: third-party mod bugs, unsupported loader combinations, corrupted user configuration and external Java/runtime issues may require manual troubleshooting.

### Application repair

The command palette provides an application-repair action. After confirmation, Vanta checks for a compatible release, downloads the platform package, verifies its checksum and hands off replacement to its external updater. It is intended to repair the launcher installation without intentionally modifying instance data, worlds, accounts or launcher settings.

## Environment baselines and backups

### Environment baseline

State Center supports saving a baseline for an instance and comparing it against the current installation. The baseline is a local record intended to help make changes reproducible and identify environment drift; it does not automatically reconcile or repair drift.

The tracked scope includes Minecraft/loader identity, mod files, resource packs, shader packs, configuration files and relevant managed installation artifacts. Scope and schema evolve; consult [Product Direction](docs/PRODUCT_DIRECTION.md) for the current capture contract and exclusions.

### Instance backups

The Instances page exposes manual backup, restore and backup verification workflows. Environment Guardian can optionally schedule full-instance ZIP backups while Vanta is running. It verifies an archive before publication and uses per-instance retention settings.

Environment Guardian is not an operating-system service: scheduled backups do not run while Vanta is closed. It skips instances that Vanta currently knows are running, but it does not detect Minecraft launched externally. Do not rely on automatic backup scheduling as the only protection for important worlds.

## Accounts, skins, and settings

### Accounts

Vanta supports Microsoft account authentication and account/session management. Use the supported sign-in flow; do not enter Microsoft passwords into launcher forms or share authentication tokens. Account data and session secrets are handled through the local account-storage subsystem and platform credential storage where supported.

### Skin Changer

The Skin Changer provides the launcher-side skin management interface for the current account workflow. Availability of account-specific operations depends on the account being signed in and the Minecraft services.

### Settings

Settings are grouped into sections rather than one long undifferentiated page:

- **Appearance:** accent color and visual preferences.
- **Discord:** presence integration and related controls.
- **General:** launcher behavior and defaults.
- **Minecraft:** game/runtime behavior and launch preferences.
- **Downloads:** download behavior.
- **Storage & Cache:** storage, cache and backup-related controls.
- **State:** installation-state settings.
- **Repair & Diagnostics:** diagnostic and repair configuration.
- **About Vanta:** version and application information.

Settings include language selection and other preferences supported by the current build. Localization is maintained through resource bundles; the supported language list can evolve. See [Localization](docs/LOCALIZATION.md).

## Server Manager

Server Manager is for locally managed Java server profiles. It can create a profile from a server JAR or import an existing server directory, manage profile-specific settings, start/stop/restart a server process, inspect live console output and send console commands, configure Java and memory values, and work with server properties, backups/restores and crash reports.

A remote connection entry point is also present for connecting to an existing server; this is separate from locally hosting a server process.

Vanta can provision a compatible private Java runtime for supported server JARs and platforms. Server support varies by JAR, Java requirement, platform and runtime provisioning availability. Server software and server mods are third-party components; Vanta does not make them inherently safe.

## Crash diagnosis and review

Vanta's crash tooling combines launch/crash history with deterministic diagnostic rules. It uses recognizable signatures and a local Java-oriented classifier to produce likely failure categories and actionable suggestions. It is not an LLM-based cloud diagnosis service.

The crash-case review workflow stores sanitized cases locally for review and supports manually verified fix references. Case-review data is not uploaded. Redaction reduces risk but cannot guarantee that arbitrary mod-generated text contains no personal information; inspect a report before sharing it.

See [Crash Diagnosis](docs/CRASH_DIAGNOSIS.md) for the diagnostic approach, privacy boundaries and limitations.

## Downloads, updates, and application recovery

Download Center is the launcher surface for download activity. Installation and content operations can report their progress and errors through the launcher UI.

When release checking is enabled, Vanta can check for newer releases. Automatic updating is a separate preference. The update pipeline selects a compatible platform/architecture package, downloads it and verifies its SHA-256 checksum, then stages the update through an external process so the running application can be replaced. Update rollback/recovery logic is included for failed replacement.

Updates require a writable installation location. Protected locations that require elevation, platform-specific signing/notarization and some in-place update scenarios have limitations. Check [Platform Support](docs/PLATFORM_SUPPORT.md) and [Release Manifests](docs/RELEASE_MANIFESTS.md) for the current package/update contract.

## Experimental tools

The Experimental page contains development/diagnostic features that are not ordinary launcher promises. Current source includes launch/cache performance measurements, cache controls, session refresh status and crash-case-review access. These tools can change between builds.

Experimental toggles should be treated as opt-in test features. Performance measurements are specific to the machine, launcher state, Minecraft version, selected instance, JVM, operating system and workload; do not compare values from unlike environments as if they were controlled benchmarks.

## Data, privacy, and security

Vanta is local-first, but it is not offline-only. Minecraft installation, Microsoft account authentication, Modrinth browsing/content installation, loader downloads, release checks, update downloads and some runtime provisioning require network connections.

Optional usage telemetry and crash diagnostics are disabled by default and have separate opt-in controls. Crash diagnostics may contain sanitized exception details, stack traces and excerpts from crash logs. Automatic filtering may miss personal information in third-party text. Local crash-case review data is not uploaded.

Account secrets use platform-backed credential storage where available (Windows DPAPI, macOS Keychain and Linux Secret Service). Linux account storage requires a working Secret Service provider and the `secret-tool` utility. Vanta does not silently fall back to plaintext credential persistence if secure storage is unavailable.

Downloads, mod JARs, modpack archives, server JARs and configuration files are external content. Only use sources you trust. Back up worlds and important configuration before changing loaders, repairing installations, importing packs or restoring archives.

- [Privacy Policy](PRIVACY.md) — data handling, optional diagnostics, telemetry and network services.
- [Security Policy](SECURITY.md) — reporting vulnerabilities.
- [Runtime Security Policy](docs/RUNTIME_SECURITY_POLICY.md) — application runtime security controls.

## Platform support

Vanta targets:

| Platform | Architecture |
|---|---|
| Windows 10/11 | x64 |
| Linux (glibc desktop) | x64, ARM64 |
| macOS | Intel x64, Apple Silicon ARM64 |

The launcher is built with Java 21 and JavaFX 21. Self-contained application images bundle a private runtime, so end users of these packages do not need to install Java separately.

Windows ARM64 currently relies on x64 emulation; it is not a native package target. Linux requires a compatible glibc desktop/display stack and Secret Service for secure account persistence. macOS images are currently unsigned and not notarized. Native MSI/DEB/RPM/DMG installers are not yet published. CI coverage and platform caveats are described in [Platform Support](docs/PLATFORM_SUPPORT.md); builds and smoke tests do not guarantee compatibility with every physical machine, graphics driver or desktop environment.

## Build from source

### Requirements

- Windows 10/11, supported glibc Linux desktop or macOS
- JDK 21
- Git
- The repository's Gradle wrapper
- IntelliJ IDEA or another Java IDE (optional)

### Clone and build

```bash
git clone https://github.com/ItsDropper/Vanta.git
cd Vanta
./gradlew clean test
./gradlew build
```

On Windows PowerShell, use:

```powershell
git clone https://github.com/ItsDropper/Vanta.git
cd Vanta
.\gradlew.bat clean test
.\gradlew.bat build
```

For portable archives and host platform packaging, use the wrapper tasks configured in the project:

```bash
./gradlew distZip distTar platformImageChecksum
```

Windows packaging tasks are host-specific; use the Windows wrapper and tasks documented in [Development](docs/DEVELOPMENT.md). CI is the reference for cross-platform matrix builds.

### Run and package

Open the project root in an IDE and import the Gradle project, or use its application run configuration. The host platform application-image task creates a self-contained image under `build/packages/`. Portable ZIP/TAR distributions and checksum archives are produced under `build/distributions/` by the configured tasks.

Do not commit build output, generated application images, local runtime data, account information or credentials.

## Project architecture

Vanta is a Java desktop application with JavaFX presentation and service-oriented launcher workflows.

| Subsystem | Role |
|---|---|
| JavaFX UI | Screens, navigation, dialogs, notifications, theme, localization and responsive layout |
| Account/authentication | Microsoft sign-in, session handling, account state and credential persistence |
| Minecraft launcher core | Version metadata, classpath, libraries, native extraction, launch arguments and process lifecycle |
| Instance management | Installation identity, directories, presets, import, backups and recovery |
| Loader integrations | Loader-specific metadata, installers, launch configuration and repair paths |
| Modrinth services | Project/version discovery, content installation, dependency resolution and repair |
| Environment tools | Lockfile/baseline capture, drift comparison, health scan and managed repair |
| Server services | Local server process/profile lifecycle, runtime provisioning and backups |
| Diagnostics | Launch failure parsing, diagnosis, crash-case storage and verified fix review |
| Update services | Release checking, verified downloads, external updater and replacement recovery |
| Privacy/telemetry | Explicitly optional collection and diagnostics controls |

The JavaFX interface uses shared theme tokens, components, responsive navigation and localization resource bundles. Background work is coordinated through launcher services and marshalled back to the UI thread.

## Documentation

- [Architecture Overview](docs/ARCHITECTURE.md) — subsystem boundaries and design.
- [Loader Compatibility](docs/LOADER_COMPATIBILITY.md) — loader support and version limits.
- [Loader Implementations](docs/LOADER_IMPLEMENTATIONS.md) — installer/bootstrap mechanics.
- [Development Guide](docs/DEVELOPMENT.md) — build, run, debugging and screenshots.
- [Platform Support](docs/PLATFORM_SUPPORT.md) — target OS/architecture, validation and update caveats.
- [Localization](docs/LOCALIZATION.md) — languages and translation workflow.
- [UX System](docs/UX_SYSTEM.md) — UI design and interaction conventions.
- [Mod Dependency Resolution](docs/MOD_DEPENDENCY_RESOLUTION.md) — dependency resolution behavior.
- [Product Direction](docs/PRODUCT_DIRECTION.md) — environment-management design and implemented foundation.
- [Release Manifests](docs/RELEASE_MANIFESTS.md) — release metadata, checksums and signing contract.
- [Crash Diagnosis](docs/CRASH_DIAGNOSIS.md) — local crash classification and privacy limits.
- [Runtime Security Policy](docs/RUNTIME_SECURITY_POLICY.md) — runtime policy.
- [Contributing](CONTRIBUTING.md) — contribution process and expectations.
- [Security Policy](SECURITY.md) — responsible disclosure.
- [Privacy Policy](PRIVACY.md) — local data, telemetry and network behavior.
- [License](LICENSE) — MIT license.

## Contributing

Contributions should improve correctness, maintainability, security, usability or platform reliability while respecting instance isolation and user-owned data. For code changes, include the problem, implementation, compatibility impact, tests and known limitations. See [CONTRIBUTING.md](CONTRIBUTING.md) before submitting work.

## Roadmap

Work continues on compatibility edge cases, deeper loader regression coverage, environment-management capabilities, diagnostics, platform polish, code signing/notarization and native installers. The roadmap is directional; it is not a guarantee that every listed area is complete or scheduled.

## Disclaimer

Vanta is an independent project and is not affiliated with, endorsed by, or sponsored by Mojang Studios, Microsoft, Fabric, Forge or Modrinth. Minecraft is a trademark of Microsoft Corporation and/or its affiliates. Third-party names and services remain the property of their respective owners.
