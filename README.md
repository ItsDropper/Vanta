# Vanta

Vanta is a Minecraft Java launcher built with JavaFX. It started with the usual job of managing Minecraft instances, but it's growing into a more complete tool for managing game installations, mods, servers, backups and troubleshooting.

The idea is to keep the things you normally end up handling across several tools in one place, without losing control of your files.

## Features

- **Instance management** — create, import, configure, launch, back up and restore separate Minecraft installations.
- **13 loaders** — Fabric, Forge, NeoForge, Quilt, Ornithe, Legacy Fabric, Babric, VantaLoader, NilLoader, LiteLoader, Rift, Stein and Risugami's ModLoader.
- **Modrinth browser** — browse mods, modpacks, resource packs, shaders and data packs; filter projects and install compatible versions.
- **Modpack support** — import supported `.mrpack` packs and export Vanta packs (`.vantapack`).
- **State Center** — check installation health, inspect managed files, save environment baselines and compare changes.
- **Environment Guardian** — scheduled instance ZIP backups, archive verification and retention settings.
- **Crash Lab** — local crash classification, launch history, diagnostic suggestions and crash-case review.
- **Server Manager** — create and import server profiles, manage server processes, use the live console, edit properties, back up and restore, and provision Java for supported servers.
- **Repair tools** — repair managed Minecraft files, restore installation metadata and resolve certain dependency problems.
- **Launcher updates** — release checks, checksum verification, staged application updates and installation repair.
- **Microsoft accounts** — account sign-in and management with platform credential storage.
- **Skin Changer** — launcher skin-management workflows.
- **Local-first** — instance data stays local; optional telemetry and crash diagnostics are off by default.

Vanta has a lot of loader-specific code because older and less common loaders don't all install or launch the same way. For the actual Minecraft version limits and implementation details, see [Loader Compatibility](docs/LOADER_COMPATIBILITY.md) and [Loader Implementations](docs/LOADER_IMPLEMENTATIONS.md).

## Screens and tools

| Page | What it's for |
|---|---|
| Home | Recently used instances and quick launch |
| Instances | Your installations and instance actions |
| Mods | Global Modrinth browsing |
| State Center | Installation health and environment comparisons |
| Crash Lab | Launch failures and diagnosis |
| Skin Changer | Skin workflows |
| Accounts | Microsoft accounts |
| Settings | Appearance, language, launcher, game, download, storage and diagnostic settings |
| Download Center | Download activity |
| Server Manager | Local server management and remote connection tools |
| Experimental | Experimental diagnostics and performance/cache tools |

### Shortcuts

| Shortcut | Action |
|---|---|
| Ctrl+1–9 | Navigate the numbered sidebar pages |
| Ctrl+K | Command palette |
| Ctrl+N | Create an instance |

The command palette also includes instance launch commands, import, creation and launcher repair.

## Instances

Create an instance with the Minecraft version and loader you want, or start from one of Vanta's presets. Instances keep their own game files, mods, worlds, settings, logs, screenshots, resource packs and shader packs.

You can also import supported installations from other launchers, including Prism Launcher and Modrinth App. Imported profiles may need some cleanup if they use custom files or unsupported loader setups.

Instance management includes settings and content tools, backups, restore and backup verification. Presets can install curated mods and shaders; the FPS-oriented preset has its own configuration path.

## Mods and modpacks

The Mods page connects to Modrinth and supports browsing:

- Mods
- Modpacks
- Resource packs
- Shaders
- Data packs

Search results can be filtered by Minecraft version, loader and project categories. Vanta checks project versions against the target installation and handles required dependencies during installation. You can inspect installed mods and browse content for a particular instance as well.

Modpack workflows include Modrinth pack installation, supported `.mrpack` imports and Vanta's `.vantapack` export format. Modrinth and Vanta packs have different format capabilities, so not every custom installation detail is portable.

## State Center, backups and repair

State Center looks at the files Vanta manages and helps find missing or invalid installation components. It can capture an environment baseline and compare it with the current instance to show what changed. A baseline is useful when you're testing mods or trying to reproduce a setup; it doesn't automatically revert changes.

Instance backups can be created and restored manually. Environment Guardian can also schedule full-instance ZIP backups, verify archives and apply retention rules. Scheduled backups run while Vanta is open, and instances Vanta knows are running are skipped. It doesn't detect Minecraft processes started outside the launcher, so keep separate backups of worlds you care about.

Repair tools cover launcher-managed game files, installation metadata and certain dependency problems. They aren't intended to fix every third-party mod, configuration or Java issue.

## Crash Lab

Crash Lab keeps launch/crash history and uses local diagnostic rules to identify common failure types and suggest possible fixes. Diagnosis is deterministic and local rather than an online AI service.

Crash-case review lets you examine sanitized cases and maintain verified fix references. Crash text can still contain personal information that automatic filtering misses, so check logs before sharing them.

## Server Manager

Server Manager handles local Java server profiles. Create a profile from a server JAR or import an existing directory, configure Java and memory, start or stop the process, restart it, watch the console and send commands. Server properties, backups, restores and crash reports are also available.

Vanta can provision a Java runtime for supported server software. There's also a remote server connection workflow, separate from the local process manager.

## Accounts, skins and settings

Vanta uses Microsoft's supported account flow and keeps account/session handling in its account subsystem. Credential storage uses Windows DPAPI, macOS Keychain or Linux Secret Service where available. Linux secure storage requires a Secret Service provider and `secret-tool`.

The Skin Changer contains account-related skin management. Settings cover appearance, language, Discord presence, general launcher behavior, Minecraft, downloads, storage/cache, state, diagnostics and application information.

## Updates and recovery

Vanta checks releases and can download a platform-specific package. The updater verifies SHA-256 checksums and stages replacement outside the running application, with recovery handling for failed updates. The command palette also has a repair option that reinstalls the latest compatible verified release.

## Platforms

| OS | Target |
|---|---|
| Windows 10/11 | x64 |
| Linux | glibc desktop, x64 and ARM64 |
| macOS | Intel and Apple Silicon |

Vanta uses Java 21 and JavaFX 21. Release application images include their runtime, so Java doesn't need to be installed separately to use those packages.

Windows ARM runs through x64 emulation; it isn't a native ARM build. Linux needs a working desktop/display stack, and Linux account storage needs Secret Service. macOS packages aren't signed or notarized yet. Native MSI, DEB, RPM and DMG installers aren't currently published.

## Build it

You'll need JDK 21 and Git. Clone the repository and run the Gradle wrapper:

```bash
git clone https://github.com/ItsDropper/Vanta.git
cd Vanta
./gradlew test build
```

On Windows:

```powershell
git clone https://github.com/ItsDropper/Vanta.git
cd Vanta
.\gradlew.bat test build
```

For portable archives and host-platform packaging:

```bash
./gradlew distZip distTar platformImageChecksum
```

Packaging is host-specific. See the [Development Guide](docs/DEVELOPMENT.md) for additional build and debugging instructions.

## Project

Vanta is a Java desktop app. JavaFX handles the interface; launcher services handle authentication, Minecraft files and launch configuration, instances, loader integration, Modrinth, environment checks, servers, diagnostics and updates.

The project is local-first. It still needs network access for Microsoft authentication, Minecraft files, Modrinth content, loader downloads, release checks and runtime provisioning.

Optional telemetry and crash diagnostics are opt-in. Diagnostic data may include exception details and log excerpts after sanitization. Review logs before sharing them. See [Privacy](PRIVACY.md).

## Documentation

- [Architecture](docs/ARCHITECTURE.md)
- [Loader Compatibility](docs/LOADER_COMPATIBILITY.md)
- [Loader Implementations](docs/LOADER_IMPLEMENTATIONS.md)
- [Development](docs/DEVELOPMENT.md)
- [Platform Support](docs/PLATFORM_SUPPORT.md)
- [Localization](docs/LOCALIZATION.md)
- [UX System](docs/UX_SYSTEM.md)
- [Mod Dependency Resolution](docs/MOD_DEPENDENCY_RESOLUTION.md)
- [Product Direction](docs/PRODUCT_DIRECTION.md)
- [Release Manifests](docs/RELEASE_MANIFESTS.md)
- [Crash Diagnosis](docs/CRASH_DIAGNOSIS.md)
- [Runtime Security Policy](docs/RUNTIME_SECURITY_POLICY.md)
- [Contributing](CONTRIBUTING.md)
- [Security](SECURITY.md)
- [Privacy](PRIVACY.md)
- [License](LICENSE)

## Contributing

Issues and contributions are welcome. Bug reports are most useful with steps to reproduce, logs (with personal information removed), Minecraft version, loader and platform details. For code changes, include the relevant tests and explain any compatibility impact.

## Disclaimer

Vanta is an independent project and isn't affiliated with Mojang Studios, Microsoft, Fabric, Forge or Modrinth. Minecraft is a trademark of Microsoft and/or its affiliates.
