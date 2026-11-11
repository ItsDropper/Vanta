# Vanta

**A cross-platform Minecraft launcher built around isolated instances, repairability, and control.**

Vanta is an independently developed JavaFX launcher for Minecraft Java Edition. It manages Minecraft versions, Microsoft account sessions, isolated instances, Modrinth content, installation health, and launcher self-updates. Its loader integration set contains 13 non-vanilla loaders: Fabric, Forge, NeoForge, Quilt, Ornithe, Legacy Fabric, Babric, VantaLoader, NilLoader, LiteLoader, Rift, Stein, and Risugami's ModLoader.

> Vanta is under active development. Implementation, runtime validation, and compatibility are separate things: a loader being selectable or installable does not by itself prove that every game version, mod, repair path, or modpack workflow is production-verified.

## At a glance

- **Instance isolation** — keep mods, settings, worlds, logs, screenshots, resource packs, and shader packs separated by instance.
- **Minecraft installation** — resolve version metadata, client files, libraries, assets, natives, and launch arguments.
- **13 loader integrations** — version-aware integrations for Fabric, Forge, NeoForge, Quilt, Ornithe, Legacy Fabric, Babric, VantaLoader, NilLoader, LiteLoader, Rift, Stein, and Risugami's ModLoader. Their installation models are not interchangeable: integrations include profile overlays, installer-generated profiles, JVM agents, LaunchWrapper tweakers, and instance-local client-JAR patching. Stein is restricted to Minecraft 1.8.9 and invokes its official installer in an isolated staging layout. See [Loader Compatibility](docs/LOADER_COMPATIBILITY.md) for support boundaries and [Loader Implementations](docs/LOADER_IMPLEMENTATIONS.md) for code-level details.
- **Modrinth and modpacks** — search projects, install content, resolve dependencies, scan installed mods, repair dependencies, import `.mrpack` modpacks, and export `.vantapack` packs. Format-level support is not a promise that every loader can be represented by the Modrinth pack format.
- **State Center** — inspect instance and shared-resource health and identify missing or invalid files.
- **Deterministic crash diagnosis** — explicit signatures plus a compact Java classifier, local crash-case review, deduplicated reports, manually verified fix notes, and cross-platform CI regression checks. The model runs locally without an LLM; local case-review data is never uploaded. Optional telemetry is separate and disabled by default. See [Crash diagnosis](docs/CRASH_DIAGNOSIS.md).
- **Self-updating releases** — download platform-specific release packages, verify SHA-256 checksums, stage updates through an external updater, replace the application image, and roll back on failure.
- **Local server management** — manage Java server profiles, process lifecycle, live console, basic properties, offline backups/restores, and crash reports. Vanta can provision a private Eclipse Temurin JRE for supported server JARs on supported OS/CPU combinations.
- **Local-first data** — keep launcher and instance data on the user's device wherever possible.
- **JavaFX interface** — a native Java desktop application rather than an embedded web application.

## Loader support boundary

Vanta has 13 non-vanilla loader integrations, but they do not all have the same validation status or version coverage. Examples of deliberate limits include Babric on Beta 1.7.3, original Rift 1.0.4-87 on Minecraft 1.13, Stein on Minecraft 1.8.9, LiteLoader stable manifest entries, and Risugami's ModLoader only on versions with pinned archive hashes. VantaLoader is an experimental independent Java-agent loader; its documented CI smoke test reaches the Minecraft 1.21.11 title and Mods screens, not a live-world gameplay test. Experimental Mixin bootstrap code is not a claim of third-party Mixin compatibility. The compatibility document is the source of truth for per-loader status.

## Status and platform

- **Development status:** Active development
- **Platform targets:** Windows 10/11 x64 (Windows 11 ARM64 via x64 emulation only), mainstream glibc Linux distributions on x64 and ARM64, and macOS on Intel and Apple Silicon; cross-platform support is validated through a multi-architecture CI matrix
- **Platform builds:** self-contained jpackage application images for Windows, Linux, and macOS, plus portable ZIP/TAR.GZ distributions; builds are not yet code-signed/notarized and native installers (MSI/DEB/RPM/DMG) are not yet provided
- **Automatic self-update:** platform-specific application-image archives for supported Windows x64, Linux x64/ARM64, and macOS Intel/Apple Silicon releases
- **Secure account storage:** Windows DPAPI, macOS Keychain, or Linux Secret Service (requires a Secret Service provider and the secret-tool command from libsecret-tools)
- **Runtime/toolchain:** Java 21
- **UI:** JavaFX 21
- **Build:** Gradle wrapper
- **License:** See [LICENSE](LICENSE)

Windows, Linux, and macOS are active build targets. CI runs the Gradle test suite and validates application-image and update-archive layouts across five OS/architecture targets; Linux x64 and ARM64 also get JavaFX GUI smoke tests, and the x64 image is exercised on Ubuntu, Debian, Fedora, and Arch Linux containers. This matrix validates the tested build and packaging paths, not every loader or every physical-machine update scenario. See [Platform Support](docs/PLATFORM_SUPPORT.md) for the exact support boundary. Signed/notarized builds and native installers remain unfinished.

## Getting started

### Requirements

- Windows 10/11, a supported Linux desktop, or macOS 12+
- JDK 21 for source development
- On Linux, a working JavaFX display stack; account persistence additionally requires Secret Service and the secret-tool command
- Git
- IntelliJ IDEA or another Java IDE (recommended)

### Build and test

Clone the repository, open the root directory in your IDE, and allow Gradle to import the project.

On Windows PowerShell:

```powershell
.\gradlew.bat clean test distZip distTar platformAppImage
```

On Linux and macOS:

```bash
./gradlew clean test distZip distTar platformImageChecksum
```

Use the checked-in Gradle wrapper rather than installing a separate Gradle version. See the [Development Guide](docs/DEVELOPMENT.md) for setup, packaging, and validation notes.

The `platformAppImage` task creates a self-contained application image on the host OS. Linux launches from `build/packages/Vanta/bin/Vanta`, macOS from `build/packages/Vanta.app`, and Windows from `build/packages/Vanta/Vanta.exe`. CI uploads each OS build as a separate artifact. For release publishing, run `./gradlew platformImageChecksum -PvantaVersion=1.2.8` (or the Windows wrapper) on each target OS; the `Platform release packages` workflow performs this matrix build and publishes the matching archive and SHA-256 file when a version tag is pushed.

To run from an IDE, use the configured Gradle application task or create a run configuration for the application's main class. A successful build confirms compilation and configured checks only; it does not prove every authentication, download, repair, loader, or packaging path works on every machine.

## How Vanta is organized

| Area | Responsibility |
| --- | --- |
| UI | JavaFX screens, settings, navigation, progress, and user feedback |
| Accounts and authentication | Microsoft sign-in flow, session lifecycle, account switching, and local account state |
| Minecraft installation | Version metadata, client files, libraries, assets, natives, and launch configuration |
| Loader integrations | Version-aware installation, launch, repair, and loader-specific bootstrap handling |
| Modrinth | Catalog search, dependency graph resolution, installation, scanning, and repair |
| State Center | Instance/shared-resource inspection and health classification |
| Update system | Release discovery, SHA-256 verification, external replacement, and rollback |
| Server manager | Local Java server processes, console commands, properties, backups, restores, and crash diagnostics |

See [Architecture Overview](docs/ARCHITECTURE.md) for subsystem boundaries and reliability expectations.

## Instance data and safety

Vanta separates application files from user data. Instance data may include worlds, configuration, mods, logs, screenshots, resource packs, and shader packs. Back up important worlds and configurations before manually deleting instances or launcher data.

When launching Minecraft, Vanta should consistently use the selected instance as both the game directory and process working directory. This prevents relative game paths from accidentally resolving beside the launcher executable.

Downloaded metadata and archives are untrusted input. Installation, extraction, repair, and update code should validate inputs, constrain archive paths, and avoid replacing healthy user-owned files unnecessarily.

## Privacy and network behavior

Optional usage statistics and crash diagnostics are disabled by default and require separate, explicit opt-in. Users can change either choice in Settings. Crash diagnostics can include redacted exception messages, stack frames, and a short error excerpt from a newly generated Minecraft crash report. Common credentials, identifiers, IP addresses, and local paths are filtered, but automated redaction cannot guarantee that arbitrary mod-generated text contains no personal information. See [PRIVACY.md](PRIVACY.md) for details.

Depending on the features used, Vanta may contact Microsoft/Xbox/Minecraft services, Modrinth, loader metadata and artifact repositories, configured GitHub release endpoints (including the official Stein installer release), and Eclipse Adoptium's API/GitHub releases when a compatible server Java runtime must be provisioned. Those third parties have their own privacy policies and terms.

- Read the [Privacy Policy](PRIVACY.md).
- Never publish account tokens, session credentials, private account data, or unreviewed logs.
- Use only authentic builds and the official Microsoft sign-in flow.

## Documentation

- [Architecture Overview](docs/ARCHITECTURE.md) — subsystem responsibilities and design principles
- [Loader Compatibility](docs/LOADER_COMPATIBILITY.md) — supported loaders, version limits, and validation status
- [Loader Implementations](docs/LOADER_IMPLEMENTATIONS.md) — per-loader installer, bootstrap, metadata, and launch mechanics
- [Product Direction](docs/PRODUCT_DIRECTION.md) — environment-management subsystems and delivery order
- [Development Guide](docs/DEVELOPMENT.md) — setup, build, packaging, and debugging
- [Localization](docs/LOCALIZATION.md) — locale architecture and translation workflow
- [Platform Support](docs/PLATFORM_SUPPORT.md) — OS/architecture targets and limitations
- [UX System](docs/UX_SYSTEM.md) — JavaFX interaction and visual conventions
- [Contributing](CONTRIBUTING.md) — change expectations and checks
- [Security Policy](SECURITY.md) — responsible vulnerability reporting
- [Privacy Policy](PRIVACY.md) — local data, telemetry intent, and third-party connections

## Contributing

Contributions should improve correctness, maintainability, security, or the user experience without compromising instance isolation or user data. Before proposing a broad architectural change, open an issue or discussion to explain the design.

For code changes, include the problem, implementation approach, compatibility impact, tests run, and known limitations. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Roadmap

Remaining work is focused on broader edge-case compatibility, deeper loader-specific regression coverage, diagnostics, code signing/notarization, native installers, and platform-specific polish. The roadmap is not a guarantee that any particular feature is already implemented or scheduled for a specific release.

## Disclaimer

Vanta is an independent project and is not affiliated with, endorsed by, or sponsored by Mojang Studios, Microsoft, Fabric, Forge, or Modrinth. Minecraft is a trademark of Microsoft Corporation and/or its affiliates. Third-party names and services remain the property of their respective owners.

See [LICENSE](LICENSE) for the project's license terms.
