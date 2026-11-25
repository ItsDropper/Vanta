# `UpdaterMain.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/update/UpdaterMain.java`

## Overview

UpdaterMain is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.update`
- Role: production implementation
- Top-level declaration: `public final class UpdaterMain`
- Source size: 924 lines

## API surface

- `public static void main(String[] args) {`
- `public synchronized IOException getCause() {`

## State and fields

- `private static final Path LOG =`
- `private static final long PROCESS_WAIT_TIMEOUT_MS =`
- `private static final long FILE_RELEASE_TIMEOUT_MS =`
- `private static final long RETRY_DELAY_MS =`
- `private static volatile boolean replacementLauncherStarted;`
- `private static UpdateTransaction.InstallationLock installationLock;`

## Dependencies

Imports: 14 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
