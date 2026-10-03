# Vanta

**A modern, modular Minecraft launcher built for reliable instance management.**

Vanta is a Windows-focused Minecraft launcher built from the ground up with JavaFX. It provides isolated Minecraft instances, Microsoft account management, Fabric and Forge support, Modrinth integration, automatic repair, installation health monitoring, and a self-updating launcher architecture.

The project is designed around a simple principle:

> **Keep Minecraft management powerful without making the launcher unnecessarily complicated.**

**Status:** Active development

---

## Features

### Minecraft Instances

Vanta treats each Minecraft installation as an independent instance.

Instances maintain their own:

* Minecraft version
* Mod loader and loader version
* Mods
* Configuration
* Resource packs
* Shader packs
* Worlds
* Logs
* Screenshots
* Native files
* Installation metadata

This prevents separate Minecraft environments from interfering with one another.

Vanta also maintains shared Minecraft resources such as libraries and assets separately from individual instances where appropriate.

---

## Microsoft Account Support

Vanta supports Microsoft authentication for Minecraft Java Edition.

The account system supports:

* Multiple accounts
* Persistent sessions
* Account switching
* Manual logout
* Session refresh
* Local account storage
* Account migration

Authentication is handled through the launcher rather than requiring users to provide their Microsoft password directly to Vanta.

Windows-specific credential functionality is supported through JNA.

---

## Minecraft Version Management

Vanta retrieves official Minecraft version metadata and uses it to construct installations.

The launcher handles:

* Version metadata
* Client JARs
* Libraries
* Assets
* Native libraries
* Launch arguments
* JVM arguments
* Game arguments
* Authentication
* Per-instance game directories

Minecraft installations are created from version metadata rather than relying on a pre-existing `.minecraft` installation.

---

## Fabric Support

Vanta has dedicated Fabric installation and metadata handling.

Fabric loader versions are resolved through Fabric's metadata services, and Fabric profiles are merged into the corresponding Minecraft installation metadata.

Fabric instances can also be repaired automatically when their loader or installation data becomes inconsistent.

---

## Forge Support

Forge is supported alongside Fabric.

Vanta can:

* Resolve Forge loader versions
* Install Forge
* Maintain Forge instance metadata
* Repair Forge installations
* Integrate Forge installations into the normal instance lifecycle

Forge version resolution uses Forge's promotion metadata rather than arbitrary version selection.

---

## Modrinth Integration

Vanta integrates directly with Modrinth for Minecraft content management.

Supported functionality includes:

* Mod search
* Version-aware searches
* Loader-aware searches
* Minecraft-version filtering
* Mod installation
* Resource-pack installation
* Shader installation
* Modpack discovery
* Dependency resolution
* Dependency graph installation
* Dependency repair
* Installed-mod scanning

Vanta's Modrinth system is separated into dedicated catalog, dependency, installation, and repair services rather than placing the entire system into one class.

---

## Dependency Resolution

Vanta includes a dedicated Modrinth dependency resolver.

When a mod requires another project, Vanta can resolve the dependency graph against:

* Minecraft version
* Mod loader
* Required project
* Required compatible versions
* Already installed mods

Resolved dependencies can then be installed as a complete graph.

This architecture also allows the launcher to repair a specific broken dependency without unnecessarily rebuilding the entire instance.

---

## Automatic Installation Repair

Vanta includes an instance repair system designed to recover incomplete or damaged Minecraft installations.

The repair process can restore:

* Minecraft client files
* Libraries
* Native libraries
* Assets
* Instance directories
* Fabric installations
* Forge installations
* Instance metadata

Existing healthy files are retained where possible, while missing or invalid installation files are restored.

---

## State Center

Vanta includes a dedicated **State Center** for inspecting launcher and Minecraft installation health.

The State Center checks individual instances as well as shared resources.

### Instance checks

Vanta can verify:

* Instance directory
* Instance metadata
* Minecraft version
* Loader configuration
* Installation completion state
* Required directories
* Empty mod files
* Instance fingerprint information

Instances are classified as:

```text
HEALTHY
ATTENTION
BROKEN
```

### Shared-resource checks

Shared libraries and assets can also be inspected.

Library archives can be validated as JAR/ZIP files, while empty or invalid files are detected during scanning.

---

## Automatic Repair

The State Center can integrate with Vanta's repair services to rebuild unhealthy installations.

Shared resources can also be repaired when necessary.

For example, a damaged or zero-byte shared library can be detected and removed before the corresponding Minecraft metadata is used to restore the required resource.

The goal is to make installation recovery a normal launcher operation rather than requiring manual deletion of Minecraft files.

---

## Parallel State Scanning

State inspection is designed to remain responsive as the number of instances and files increases.

Vanta uses bounded worker pools to parallelize filesystem inspection, including:

* Instance file counting
* Configuration counting
* Empty-file detection
* Shared-resource validation

Shared resource scans can also validate multiple files concurrently.

The implementation uses bounded concurrency rather than creating an unrestricted thread for every file.

---

## Launcher Updates

Vanta includes its own launcher update system.

Updates are retrieved from the Vanta GitHub Releases repository and support:

* Release discovery
* Version comparison
* Application ZIP downloads
* SHA-256 checksum downloads
* Checksum verification
* External updater execution
* Installation replacement
* Automatic restart

The updater runs outside the main Vanta process so that the launcher can safely replace its own installation.

### Update architecture

```text
Vanta
  │
  ├── Check GitHub Releases
  │
  ├── Download Vanta package
  │
  ├── Download SHA-256 checksum
  │
  ├── Verify package
  │
  └── Start external updater
          │
          ├── Wait for Vanta to exit
          ├── Stage new installation
          ├── Back up existing installation
          ├── Replace installation
          ├── Verify replacement
          ├── Roll back if replacement fails
          └── Start Vanta again
```

This keeps the update mechanism independent from the running launcher executable.

---

## Version Rollback

Vanta supports **launcher rollback**, not just normal updates.

The Settings interface can retrieve published Vanta releases and display older compatible releases.

A selected release can then be downloaded and installed using the same verified external updater architecture.

This allows the launcher itself to be downgraded when a newer version needs to be replaced.

Rollback releases are filtered to published releases with the required Vanta application package and SHA-256 checksum.

---

## Automatic Updates

Vanta can optionally check for launcher updates automatically.

The setting is configurable from the launcher settings.

When enabled, Vanta can perform its normal update workflow on launch rather than requiring the user to manually select the update action.

---

## Isolated Data

Vanta separates application files from Minecraft instance data.

The launcher installation contains the application itself, while user data is maintained separately.

Conceptually:

```text
%APPDATA%\Vanta\
├── accounts\
├── instances\
│   ├── <instance>\
│   │   ├── mods\
│   │   ├── config\
│   │   ├── resourcepacks\
│   │   ├── shaderpacks\
│   │   ├── saves\
│   │   ├── logs\
│   │   ├── screenshots\
│   │   ├── natives\
│   │   └── instance.json
│   │
│   └── ...
│
├── libraries\
├── assets\
└── ...
```

The exact storage structure can evolve as the launcher develops, but application installation files and user Minecraft data are intentionally treated as separate concerns.

When Minecraft is launched, Vanta explicitly sets the Minecraft instance directory as the process working directory as well as the Minecraft game directory. This prevents relative paths such as `config` from accidentally resolving beside the launcher executable.

---

## Launcher Settings

Vanta provides a dedicated settings system covering launcher-wide behavior.

Current settings areas include:

* Appearance
* Discord
* General
* Minecraft
* Downloads
* State
* Repair & Diagnostics
* About Vanta

General launcher options include controls for:

* Animations
* Update checks
* Automatic updates
* Launch confirmations
* Hiding the launcher when Minecraft starts
* Automatic browser opening
* Launcher version rollback
* Data-directory information
* Runtime information
* Workspace configuration

State-related settings include configurable state-scan workers and automatic state repair.

---

## Discord Rich Presence

Vanta includes Discord Rich Presence integration.

Discord presence can be controlled from the launcher settings.

The integration is implemented as an optional launcher service rather than being part of the core Minecraft installation system.

---

## Technology

Vanta is currently built with:

* **Java 21**
* **JavaFX 21**
* **Gradle**
* **Shadow**
* **jlink**
* **jpackage**
* **NSIS**
* **Jackson**
* **MinecraftAuth**
* **JNA**
* **TwelveMonkeys ImageIO**
* **Flexmark**
* **DiscordIPC**

The launcher currently targets Windows while keeping the codebase modular enough for future platform support.

---

## Architecture

Vanta is deliberately split into separate systems.

A simplified representation is:

```text
Vanta
│
├── UI
│   ├── Home
│   ├── Instances
│   ├── Accounts
│   ├── Global Mods
│   ├── State Center
│   └── Settings
│
├── Accounts
│   ├── Authentication
│   ├── Account storage
│   └── Session management
│
├── Instances
│   ├── Creation
│   ├── Installation
│   ├── Repair
│   ├── Metadata
│   └── Usage tracking
│
├── Minecraft
│   ├── Version metadata
│   ├── Client installation
│   ├── Libraries
│   ├── Assets
│   ├── Natives
│   └── Launch configuration
│
├── Loaders
│   ├── Fabric
│   └── Forge
│
├── Modrinth
│   ├── Catalog
│   ├── Dependency resolver
│   ├── Installer
│   ├── Scanner
│   └── Repair
│
├── State
│   ├── Instance state
│   ├── Shared resource state
│   ├── Parallel scanning
│   └── Repair integration
│
└── Updates
    ├── Release service
    ├── Checksum verification
    ├── External updater
    ├── Installation replacement
    └── Rollback
```

The architecture favors dedicated services over monolithic launcher classes so that individual systems can evolve independently.

---

## Project Structure

The main source tree is organized approximately as follows:

```text
src/
└── main/
    ├── java/
    │   ├── org/example/Main.java
    │   │
    │   ├── org/example/launcher/
    │   │   ├── account/
    │   │   ├── auth/
    │   │   ├── instance/
    │   │   ├── java/
    │   │   ├── modrinth/
    │   │   ├── service/
    │   │   ├── state/
    │   │   ├── update/
    │   │   └── ...
    │   │
    │   └── org/example/ui/
    │       ├── components/
    │       ├── views/
    │       └── ...
    │
    └── resources/
        ├── launcher.css
        └── ...
```

The codebase is actively evolving, so the exact package structure may change.

---

## Build & Development

### Requirements

* Windows 10/11
* JDK 21
* IntelliJ IDEA
* Gradle

The project uses a Java 21 Gradle toolchain.

Open the repository in IntelliJ IDEA and use the project's configured run/build tasks.

For packaged distributions, Vanta uses the Java runtime packaging ecosystem provided by `jlink` and `jpackage`, with NSIS used as part of the Windows distribution pipeline.

---

## Privacy

Vanta follows a local-first design.

The launcher does not require a Vanta account or centralized Vanta service for normal operation.

External network communication is used where required by launcher functionality, including services such as:

* Microsoft authentication
* Mojang/Minecraft metadata
* Fabric metadata
* Forge metadata
* Modrinth
* GitHub Releases for launcher updates

Vanta does not intentionally collect unnecessary telemetry.

Third-party services remain subject to their own privacy policies and terms.

---

## Reliability Philosophy

Vanta prioritizes:

**Reliability over feature count.**

**Maintainability over unnecessary abstraction.**

**Recovery over manual troubleshooting.**

**Isolated data over shared mutable state.**

**A clean interface over unnecessary complexity.**

The launcher should not merely install Minecraft once. It should be able to understand the state of an installation, detect problems, and recover from them.

---

## Roadmap

Vanta is actively developed. Areas of ongoing development include:

* More robust dependency resolution
* Additional Minecraft compatibility
* Improved repair coverage
* More complete loader support
* Better diagnostics
* Instance import/export
* `.mrpack` support
* `.vantapack` support
* Further launcher update improvements
* Linux support
* macOS support
* Additional launcher integrations

The roadmap is subject to change as the architecture develops.

---

## Contributing

Contributions are welcome when they improve Vanta without compromising its architecture, reliability, security, or user experience.

Before submitting a pull request, explain:

* What the change does
* Why it is needed
* How it works
* Which files were changed
* How it was tested
* Any known limitations or edge cases

Large architectural changes should be discussed before implementation.

---

## Security

Do not publicly disclose sensitive security information through ordinary issues.

Never publish:

* Authentication tokens
* Account credentials
* Private keys
* Sensitive personal information
* Exploit details that could put users at risk

Security-related issues should be reported responsibly.

---

## Disclaimer

Vanta is an independent project and is not affiliated with, endorsed by, or sponsored by Mojang Studios, Microsoft, Fabric, Forge, or Modrinth.

Minecraft is a trademark of Microsoft Corporation and/or its affiliates.

Vanta is provided without guarantees of availability or compatibility.

---

## License

See [`LICENSE`](LICENSE) for the complete license terms.

---

## Acknowledgements

Vanta builds upon a number of open-source projects and external services, including:

* JavaFX
* Gradle
* MinecraftAuth
* JNA
* Jackson
* Fabric
* Forge
* Modrinth
* TwelveMonkeys
* Flexmark
* DiscordIPC
* NSIS

Their respective licenses and terms apply independently.

---

## Vanta

**Vanta — a modern Minecraft launcher focused on control, isolation, and recovery.**

Built by **ItsDropper**.
