# `UpdateTransaction.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/update/UpdateTransaction.java`

## Overview

UpdateTransaction is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.update`
- Role: production implementation
- Top-level declaration: `public final class UpdateTransaction`
- Source size: 332 lines

## API surface

- `public void commit() throws IOException {`
- `public static boolean hasPendingTransaction(Path applicationRoot) {`
- `public static InstallationLock acquireInstallationLock(Path applicationRoot) throws IOException {`
- `public void close() throws IOException {`
- `public static boolean isHealthInvocation(String[] args) {`
- `public static void acknowledgeHealthyStartup(String[] args) throws IOException {`
- `public static void ensureNoActiveUpdate(Path applicationRoot) throws IOException {`
- `public static void recoverBeforeLaunch(Path applicationRoot) throws IOException {`

## State and fields

- `private final Path applicationRoot;`
- `private final Path stagingRoot;`
- `private final Path activePayload;`
- `private final Path stagedPayload;`
- `private final Path backupPayload;`
- `private final Path journal;`
- `private final Path healthMarker;`
- `private final FileChannel channel;`
- `private final FileLock lock;`

## Dependencies

Imports: 12 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
