# Development Guide

## Requirements

- Windows 10 or 11 (the currently supported development target)
- JDK 21
- The repository's Gradle wrapper
- IntelliJ IDEA or another Java IDE

Vanta uses the Java 21 toolchain and JavaFX 21. Use the checked-in wrapper so local builds use the Gradle version declared by the project.

## Get started

1. Clone the repository from GitHub.
2. Open the repository root in IntelliJ IDEA and allow Gradle to import the project.
3. Confirm the IDE uses JDK 21 for Gradle and the project toolchain.
4. Run the application through the configured Gradle application task or IDE run configuration.

From PowerShell, run the baseline checks:

```powershell
.gradlew.bat clean test
.gradlew.bat build
```

The first build may download Gradle dependencies. A successful compile is not a substitute for testing the affected runtime behavior.

## Packaging

The build uses the Shadow plugin for the application JAR and defines a separate updater JAR task. Windows packaging also uses tools from the Java runtime packaging ecosystem, including `jlink` and `jpackage`, and NSIS in the distribution pipeline. Packaging may require a Windows environment and the relevant external tools.

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
- For update failures, check package verification, staging, replacement, rollback, and restart as separate steps.

## Change checklist

- [ ] Existing behavior and relevant edge cases were inspected.
- [ ] Network and filesystem work does not block the JavaFX Application Thread.
- [ ] Error paths preserve useful diagnostics without leaking secrets.
- [ ] Existing user data and instances remain intact.
- [ ] Tests/build checks were run, or the missing validation is documented.
- [ ] README and focused documentation reflect user-visible changes.
