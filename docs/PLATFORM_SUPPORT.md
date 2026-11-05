# Platform Support

Vanta ships self-contained Java 21 application images. A system-wide Java installation is not required.

## Supported release targets

| Operating system | CPU architecture | Status |
|---|---|---|
| Windows 10/11 | x64 | CI build and test target |
| Linux | x64 | CI build, test, and GUI smoke-test target |
| Linux | ARM64 | CI build and test target |
| macOS | Intel x64 | CI build and test target |
| macOS | Apple Silicon ARM64 | CI build and test target |

The GitHub Actions matrix builds the five targets above. It checks the app-image layout, bundled runtime, updater helper dependencies, and platform ZIP archive structure. Linux x64 also launches the packaged UI under Xvfb in CI.

## Operating-system requirements and limitations

- **Linux:** use a mainstream glibc-based desktop distribution with a working GTK/graphics stack. Secure account-session storage requires a Secret Service implementation and the `secret-tool` command (commonly provided by `libsecret-tools`). Vanta does not silently store account tokens in plaintext when that service is unavailable.
- **macOS:** Intel and Apple Silicon application images are built separately. Packages are currently unsigned and not notarized; native DMG installers are not yet produced.
- **Windows:** the supported application image is x64. Credentials use Windows DPAPI.
- **Not supported:** Windows ARM64, 32-bit systems, FreeBSD, OpenBSD, Solaris, and other unlisted operating systems/architectures. They are intentionally not reported as Linux.

## Updates

The updater selects a package matching the host OS and architecture. Release assets use names such as:

- `Vanta-1.2.8-windows-x64.zip`
- `Vanta-1.2.8-linux-x64.zip`
- `Vanta-1.2.8-linux-aarch64.zip`
- `Vanta-1.2.8-macos-x64.zip`
- `Vanta-1.2.8-macos-aarch64.zip`

Each archive has a corresponding `.sha256` file. The `Platform release packages` workflow builds and publishes these assets for a stable `vMAJOR.MINOR.PATCH` tag.

Windows runs the updater from a copied private runtime. Linux and macOS re-enter the packaged native launcher in a dedicated updater mode because their jpackage runtime images may omit a standalone `bin/java` command. The updater validates the staged image before replacing the existing installation.

The updater has platform-layout and permission tests, but an actual in-place update has not yet been exercised on physical Windows, Linux, and macOS installations. Treat that as remaining validation work, not as a completed guarantee.

## Build locally

On the target operating system:

```bash
./gradlew clean test distZip distTar platformImageChecksum
```

On Windows PowerShell:

```powershell
.\gradlew.bat clean test distZip distTar platformImageChecksum
```

The self-contained app image is written under `build/packages/`; the platform-specific ZIP and checksum are written under `build/distributions/`.
