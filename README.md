# Vanta

**A lightweight, modern Minecraft launcher built from the ground up with JavaFX.**

Vanta is a custom Minecraft launcher focused on reliable game management, isolated instances, modern account handling, and a clean desktop experience.

It is designed to give players control over their Minecraft installations without turning the launcher into an overloaded collection of unrelated features.

> **Status:** Active development

---

## Features

### 🎮 Minecraft Instances

Vanta keeps Minecraft installations separated into independent instances.

Each instance can have its own:

* Minecraft version
* Mods
* Resource packs
* Shaders
* Worlds
* Configuration
* Screenshots
* Logs
* `options.txt`
* JVM settings

Shared Minecraft libraries and assets can be reused where appropriate instead of duplicating everything for every instance.

---

### 🔐 Microsoft Account Authentication

Vanta supports Microsoft authentication for Minecraft Java Edition.

Accounts are stored locally and sessions are protected using Windows credential storage.

Vanta supports:

* Persistent login
* Automatic session refresh
* Multiple Minecraft accounts
* Account switching
* Manual logout
* Secure local account storage

Vanta does not require users to enter or store their Microsoft password inside the launcher.

---

### ☕ Java Management

Vanta can locate and manage the Java installations required by Minecraft.

The launcher verifies the **actual Java runtime version** instead of relying only on directory names.

This allows different Minecraft versions to use appropriate Java runtimes.

---

### 🧩 Fabric

Vanta supports Fabric installation and Fabric-based instances.

Fabric loader versions are resolved through Fabric's metadata rather than allowing arbitrary Minecraft/loader combinations.

---

### 📦 Modrinth

Vanta integrates with Modrinth for discovering and installing Minecraft content.

Supported functionality includes:

* Mod search
* Mod installation
* Dependency resolution
* Version-aware installation
* Instance-specific mod management

Local `.jar` files can also be added directly to an instance's Mods interface.

---

### 🛠️ Minecraft Launching

Vanta builds Minecraft launch configurations dynamically from the selected instance.

The launcher handles:

* Minecraft libraries
* Assets
* Client JARs
* Native libraries
* Authentication
* JVM arguments
* Game arguments
* Instance directories
* Fabric launch configurations

The goal is to support Minecraft versions across a broad range of releases rather than relying on a single modern version.

---

### ⚙️ Launcher & Instance Settings

Vanta provides configurable settings for things such as:

* Allocated RAM
* Java runtime
* JVM arguments
* Instance configuration
* Launcher behavior

Settings are designed to remain specific to the appropriate scope instead of creating unnecessary global configuration.

---

## Instance Architecture

Vanta does not treat the user's normal `.minecraft` directory as the primary source of truth for every instance.

Conceptually, instances look like:

```text
Vanta/
├── accounts/
├── instances/
│   ├── survival/
│   │   ├── mods/
│   │   ├── resourcepacks/
│   │   ├── shaderpacks/
│   │   ├── saves/
│   │   ├── screenshots/
│   │   ├── logs/
│   │   └── ...
│   │
│   └── pvp/
│       ├── mods/
│       ├── resourcepacks/
│       └── ...
│
└── libraries/
```

This separation makes instances easier to manage, back up, move, and configure independently.

---

## Account Storage

Vanta stores account data separately from Minecraft instances.

On Windows, application data is stored under:

```text
%APPDATA%\Vanta\
```

Account files are stored separately and protected using Windows credential functionality.

Vanta's account system is designed around:

* Local storage
* Persistent sessions
* Encrypted session data
* Multiple accounts
* Atomic account-file updates
* Account migration from previous Vanta installations

---

## Privacy

Vanta is designed with a **local-first approach**.

The launcher does not require a Vanta account or centralized Vanta database to function.

Vanta communicates with external services when required for functionality, such as:

* Microsoft authentication
* Minecraft services
* Mojang metadata
* Fabric metadata
* Modrinth

Vanta does not intentionally collect unnecessary user data.

External services have their own privacy policies and terms, which remain separate from Vanta.

---

## Technology

Vanta is built using:

* **Java 21**
* **JavaFX**
* **Gradle**
* **jlink**
* **jpackage**
* **NSIS**
* **MinecraftAuth**
* **Jackson**

The project currently focuses on **Windows** while keeping the architecture suitable for future platform support.

---

## Architecture

Vanta is structured around separate systems rather than placing launcher functionality into one large class.

Major components include:

```text
Account
 ├── Authentication
 ├── Persistent storage
 └── Account switching

Instance
 ├── Instance management
 ├── Minecraft installation
 ├── Mods
 ├── Resource packs
 └── Configuration

Minecraft
 ├── Version metadata
 ├── Libraries
 ├── Assets
 ├── Natives
 └── Launch configuration

Loader
 └── Fabric

Services
 ├── Authentication
 ├── Launching
 ├── Downloads
 ├── Modrinth
 └── Java management
```

The architecture is intentionally designed so that new functionality can be added without turning the launcher into a monolithic system.

---

## Installation

Download the latest Vanta release from the **Releases** section of this repository.

The Windows installer installs Vanta and creates the required application files.

Your Minecraft accounts and Vanta instances are stored separately from the application installation, allowing the launcher itself to be updated without unnecessarily replacing user data.

---

## First Launch

On the first launch:

1. Sign in with your Microsoft account.
2. Vanta stores the authenticated session securely.
3. Create or select an instance.
4. Select the desired Minecraft version.
5. Configure the instance if needed.
6. Launch Minecraft.

After authentication has been established, Vanta can refresh the saved session automatically when possible.

---

## Development

### Requirements

* Windows 10/11
* Java 21 JDK
* IntelliJ IDEA
* Gradle

Clone the repository and open the project in IntelliJ IDEA.

Run the launcher using the IntelliJ run configuration.

---

## Project Structure

The project separates launcher responsibilities into dedicated packages.

Examples include:

```text
src/
└── main/
    ├── java/
    │   └── org/example/launcher/
    │       ├── account/
    │       ├── instance/
    │       ├── java/
    │       ├── model/
    │       └── ...
    │
    └── resources/
        └── ...
```

The exact structure may change as Vanta develops.

---

## Project Philosophy

Vanta prioritizes:

**Reliability over feature count.**

**Maintainability over unnecessary abstraction.**

**A polished user experience over visual complexity.**

**Local control over unnecessary data collection.**

New functionality should solve a real launcher problem rather than exist simply to increase the feature list.

---

## Roadmap

Planned development includes:

* [ ] Further launcher reliability and error handling
* [ ] More robust download and file verification
* [ ] Improved Minecraft compatibility
* [ ] More complete loader support
* [ ] Instance import/export
* [ ] `.mrpack` importing
* [ ] `.vantapack` exporting
* [ ] Launcher updates
* [ ] Improved launcher settings
* [ ] Linux support
* [ ] macOS support
* [ ] Additional social functionality

The roadmap is subject to change.

---

## Contributing

Contributions are welcome when they improve Vanta without compromising its architecture, security, reliability, or user experience.

Before submitting a pull request, contributors should clearly explain:

* What the change does
* Why the change is necessary
* How the implementation works
* Which files were changed
* How the change was tested
* Any known limitations or edge cases

Pull requests are reviewed carefully before being merged.

A pull request being submitted does not mean it will automatically be accepted.

---

## Security

Security issues should **not** be publicly disclosed through ordinary GitHub issues when they could expose sensitive information or create an exploitable vulnerability.

Avoid publishing:

* Authentication tokens
* Account credentials
* Private keys
* Personal data
* Exploit details that could put users at risk

---

## Disclaimer

Vanta is provided **"as is"**, without warranties of any kind, express or implied.

Vanta is an independent project and is not affiliated with, endorsed by, or sponsored by Mojang Studios, Microsoft, Fabric, or Modrinth.

Minecraft is a trademark of Microsoft Corporation and/or its affiliates.

---

## License

Vanta is distributed under the license included in this repository.

See [`LICENSE`](LICENSE) for the complete terms.

---

## Acknowledgements

Vanta relies on several open-source projects and external services.

Notable dependencies and services include:

* MinecraftAuth
* Fabric
* Modrinth
* JavaFX
* Jackson
* Gradle
* NSIS

Their respective licenses and terms apply independently.

---

## Repository

**Vanta**
Custom Minecraft launcher by **ItsDropper**.

The project is actively developed with a focus on turning Vanta into a reliable, maintainable launcher rather than simply maximizing its feature count.
