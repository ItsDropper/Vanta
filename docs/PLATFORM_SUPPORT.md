# Platform Support

Vanta ships self-contained Java 21 application images. A system-wide Java installation is not required.

## Supported release targets

| Operating system | CPU architecture | Validation |
|---|---|---|
| Windows 10/11 | x64 | CI build, tests, and image-layout checks |
| Linux | x64 | CI build, tests, image checks, and GUI smoke tests |
| Linux | ARM64 | CI build, tests, image checks, and GUI smoke tests |
| macOS | Intel x64 | CI build, tests, and image-layout checks |
| macOS | Apple Silicon ARM64 | CI build, tests, and image-layout checks |

The GitHub Actions matrix builds all five targets. A separate Linux compatibility matrix launches the x64 application image on Ubuntu 22.04, Debian 12, current Fedora, and Arch Linux. This is a representative compatibility sample, not proof that every derivative or desktop environment works.

## Operating-system requirements and limitations

- **Windows:** the native application image is x64 and uses Windows DPAPI for account credentials. Windows ARM64 may run the x64 package through Windows 11 x64 emulation with an x64 Java runtime; native Windows ARM64 packaging is not implemented or CI-tested.
- **Linux:** the supported ABI is glibc, with a working GTK and graphics stack. Alpine/musl is not a supported target. Secure account-session storage requires a Secret Service provider and the `secret-tool` command (commonly supplied by `libsecret-tools`). Vanta does not silently store account tokens in plaintext if secure storage is unavailable.
- **macOS:** Intel and Apple Silicon application images are built separately. Packages are currently unsigned and not notarized; native DMG installers are not yet produced.
- **Not supported:** native Windows ARM64, 32-bit systems, RISC-V, PowerPC, FreeBSD, OpenBSD, Solaris, and other unlisted operating systems/architectures. They are intentionally not reported as Linux.

SteamOS and ChromeOS Linux environments may work when they provide a compatible glibc userspace, supported CPU architecture, desktop display stack, and required native libraries. They are not separate release targets or individually certified platforms.

## Updates

The updater selects a package matching the host OS and architecture. Release assets use names such as:

- `Vanta-1.2.8-windows-x64.zip`
- `Vanta-1.2.8-linux-x64.zip`
- `Vanta-1.2.8-linux-aarch64.zip`
- `Vanta-1.2.8-macos-x64.zip`
- `Vanta-1.2.8-macos-aarch64.zip`

Each archive has a corresponding `.sha256` file. The `Platform release packages` workflow builds and publishes these assets for a stable `vMAJOR.MINOR.PATCH` tag.

All three operating systems run the updater from a private copy of the complete application image. This avoids depending on a standalone `bin/java` executable (which jpackage may omit) and prevents the helper from locking files in the installation it must replace. The updater validates the staged image before replacing the existing installation and only starts the updated launcher after replacement succeeds. The installation's parent directory must be writable by the current user; elevated updates for protected locations such as system-wide Program Files or /Applications installs are not implemented. macOS App Translocation is detected and blocks self-update until the bundle is moved to a normal installation location.

The updater has platform-layout, permission, and helper-copy tests. Actual in-place update testing on physical Windows, Linux, and macOS installations remains outstanding; CI does not claim that end-to-end guarantee yet.

## Build locally

On the target operating system:

```bash
./gradlew clean test distZip distTar platformImageChecksum
```

On Windows PowerShell:

```powershell
.\\gradlew.bat clean test distZip distTar platformImageChecksum
```

The self-contained app image is written under `build/packages/`; the platform-specific ZIP and checksum are written under `build/distributions/`.
