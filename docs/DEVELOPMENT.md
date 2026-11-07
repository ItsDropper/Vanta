# Development Guide

## Requirements

- Windows 10/11, Linux, or macOS
- JDK 21
- The repository's Gradle wrapper
- IntelliJ IDEA or another Java IDE

Vanta uses the Java 21 toolchain and JavaFX 21. Use the checked-in wrapper so local builds use the Gradle version declared by the project.

## Get started

1. Clone the repository from GitHub.
2. Open the repository root in IntelliJ IDEA and allow Gradle to import the project.
3. Confirm the IDE uses JDK 21 for Gradle and the project toolchain.
4. Run the application through the configured Gradle application task or IDE run configuration.

On Windows PowerShell:

```powershell
.\gradlew.bat clean test
.\gradlew.bat build
```

On Linux and macOS:

```bash
./gradlew clean test
./gradlew build
```

For host-specific distribution and application-image checks, use the tasks configured in `build.gradle`:

```bash
./gradlew distZip distTar platformImageChecksum
```

On Windows, `platformAppImage` creates the host's jpackage application image; on Linux/macOS use the platform tasks available to that host. Do not assume a cross-OS jpackage image can be produced from a different host. The CI workflow is the source of truth for the current matrix and task invocations.

The first build may download Gradle dependencies. A successful compile is not a substitute for testing the affected runtime behavior. Loader-specific integration status is documented in [Loader Compatibility](LOADER_COMPATIBILITY.md), and implementation paths are mapped in [Loader Implementations](LOADER_IMPLEMENTATIONS.md).

## UI screenshots

Capture all major production JavaFX views, including the nine sidebar destinations and nested instance/content/setup screens:

```powershell
.\gradlew.bat run --args="--capture-ui-screenshots build/ui-screenshots"
```

The output keeps the original nine sidebar PNGs, index and overview at the root. Additional nested views are written under `additional/`, with their own PNGs, index and overview. The original `--capture-sidebar-screenshots` argument remains supported as a compatibility alias.

The UI screenshots GitHub Actions workflow performs the corresponding capture under Xvfb, generates individual PNGs plus an overview contact sheet, and uploads the output as an artifact. Screenshot mode does not require a signed-in account or a pre-existing Minecraft instance. On Linux, the launcher resolves its data directory through XDG_DATA_HOME (or ~/.local/share).

## Packaging

The application plugin creates portable distributions through `distZip` and `distTar`. The platform image task uses the host JDK's `jpackage` to create a self-contained application image with a private runtime. `platformImageChecksum` additionally creates a platform/architecture-specific ZIP and SHA-256 file. CI validates Windows x64, Linux x64/ARM64, and macOS Intel/Apple Silicon. Signed/notarized packages and native installers (MSI/DEB/RPM/DMG) are not yet produced.

Do not commit build output from `build/`, generated installers, or local runtime data.

## Source layout

- `src/main/java/` — application and service code
- `src/main/resources/` — CSS, images, and packaged resources
- `src/test/java/` — automated tests
- `build.gradle` — plugins, dependencies, Java toolchain, and packaging tasks
- `gradle/wrapper/` — pinned Gradle wrapper configuration

See [Architecture](ARCHITECTURE.md) for the major responsibilities and data flows.

## Debugging guidance

- Reproduce the issue with the smallest affected instance or workflow.
- Inspect launcher and game logs before changing repair behavior.
- Avoid including account tokens or other sensitive values in diagnostic output.
- For download failures, distinguish metadata lookup, transport, checksum/content validation, and installation errors.
- For loader failures, distinguish upstream metadata lookup, installer execution, profile validation, artifact resolution, JVM-agent startup, and game bootstrap.
- For update failures, check package verification, staging, replacement, rollback, and restart as separate steps.

## Change checklist

- [ ] Existing behavior and relevant edge cases were inspected.
- [ ] Network and filesystem work does not block the JavaFX Application Thread.
- [ ] Error paths preserve useful diagnostics without leaking secrets.
- [ ] Existing user data and instances remain intact.
- [ ] Tests/build checks were run, or the missing validation is documented.
- [ ] README and focused documentation reflect user-visible changes.
