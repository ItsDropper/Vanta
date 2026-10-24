# Vanta

**A Windows-focused Minecraft launcher built around isolated instances, repairability, and control.**

Vanta is an independently developed JavaFX launcher for Minecraft Java Edition. It manages Minecraft versions, Microsoft account sessions, Fabric and Forge installations, Modrinth content, installation health, and verified launcher updates.

> Vanta is under active development. Features and compatibility can change between releases; do not treat roadmap items as shipped functionality.

## At a glance

- **Instance isolation** — keep mods, settings, worlds, logs, screenshots, resource packs, and shader packs separated by instance.
- **Minecraft installation** — resolve version metadata, client files, libraries, assets, natives, and launch arguments.
- **Fabric and Forge** — install and repair supported loader profiles.
- **Modrinth integration** — search projects, install content, resolve dependencies, scan installed mods, and repair dependencies.
- **State Center** — inspect instance and shared-resource health and identify missing or invalid files.
- **Recovery-oriented updates** — download release packages, verify SHA-256 checksums, and use an external updater with staging and rollback.
- **Local server management** — manage Java server profiles, process lifecycle, live console, basic properties, player-list commands, offline backups/restores, and crash reports.
- **Local-first data** — keep launcher and instance data on the user's device wherever possible.
- **JavaFX interface** — a native Java desktop application rather than an embedded web application.

## Status and platform

- **Development status:** Active development
- **Current target:** Windows 10/11
- **Runtime/toolchain:** Java 21
- **UI:** JavaFX 21
- **Build:** Gradle wrapper
- **License:** See [LICENSE](LICENSE)

Linux and macOS support are future goals, not a claim of current platform support.

## Getting started

### Requirements

- Windows 10 or Windows 11
- JDK 21 for source development
- Git
- IntelliJ IDEA or another Java IDE (recommended)

### Build and test

Clone the repository, open the root directory in your IDE, and allow Gradle to import the project. From PowerShell, run:

```powershell
.gradlew.bat clean test
.gradlew.bat build
```

Use the checked-in Gradle wrapper rather than installing a separate Gradle version. See the [Development Guide](docs/DEVELOPMENT.md) for setup, packaging, debugging, and validation notes.

To run from an IDE, use the configured Gradle application task or create a run configuration for the application's main class. A successful build confirms compilation and configured checks only; it does not prove every authentication, download, repair, or packaging path works on every machine.

## How Vanta is organized

| Area | Responsibility |
| --- | --- |
| UI | JavaFX screens, settings, navigation, progress, and user feedback |
| Accounts and authentication | Microsoft sign-in flow, session lifecycle, account switching, and local account state |
| Minecraft installation | Version metadata, client files, libraries, assets, natives, and launch configuration |
| Loader integrations | Fabric and Forge metadata, installation, and repair |
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

Vanta is designed to avoid intentional usage analytics or telemetry sent to a Vanta-operated analytics service. This describes the project's intended behavior; it is not a guarantee about every dependency or future build.

Depending on the features used, Vanta may contact Microsoft/Xbox/Minecraft services, Modrinth, loader metadata services, and configured GitHub release endpoints. Those third parties have their own privacy policies and terms.

- Read the [Privacy Policy](PRIVACY.md).
- Never publish account tokens, session credentials, private account data, or unreviewed logs.
- Use only authentic builds and the official Microsoft sign-in flow.

## Documentation

- [Architecture Overview](docs/ARCHITECTURE.md) — subsystem responsibilities and design principles
- [Development Guide](docs/DEVELOPMENT.md) — setup, build, packaging, and debugging
- [Contributing](CONTRIBUTING.md) — change expectations, checks, and pull request guidance
- [Security Policy](SECURITY.md) — responsible vulnerability reporting
- [Privacy Policy](PRIVACY.md) — local data, telemetry intent, and third-party connections

## Contributing

Contributions should improve correctness, maintainability, security, or the user experience without compromising instance isolation or user data. Before proposing a broad architectural change, open an issue or discussion to explain the design.

For code changes, include the problem, implementation approach, compatibility impact, tests run, and known limitations. See [CONTRIBUTING.md](CONTRIBUTING.md).

## Roadmap

Areas under consideration include broader Minecraft compatibility, improved repair coverage, instance import/export, modpack formats, further diagnostics, and additional platform support. The roadmap is not a guarantee that any particular feature is already implemented or scheduled for a specific release.

## Disclaimer

Vanta is an independent project and is not affiliated with, endorsed by, or sponsored by Mojang Studios, Microsoft, Fabric, Forge, or Modrinth. Minecraft is a trademark of Microsoft Corporation and/or its affiliates. Third-party names and services remain the property of their respective owners.

See [LICENSE](LICENSE) for the project's license terms.
