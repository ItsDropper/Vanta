# `ApplicationLocator.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/update/ApplicationLocator.java`

## Overview

ApplicationLocator is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.update`
- Role: production implementation
- Top-level declaration: `public final class ApplicationLocator`
- Source size: 267 lines

## API surface

- `public static Path getApplicationDirectory() {`
- `public static Path getApplicationDirectoryForRecovery() {`
- `public static Path getRuntimeDirectory(Path root) {`
- `public static Path getApplicationPayloadDirectoryForRecovery(Path root) {`
- `public static Path getApplicationPayloadDirectory(Path root) {`
- `public static Path getLauncherExecutable(Path root) {`
- `public static boolean isSupportedInstallation(Path root) {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 4 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
