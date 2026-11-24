# `UpdateInfo.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/update/UpdateInfo.java`

## Overview

UpdateInfo is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.update`
- Role: production implementation
- Top-level declaration: `public class UpdateInfo`
- Source size: 75 lines

## API surface

- `public boolean isUpdateAvailable() {`
- `public boolean isDowngrade() {`
- `public static int compareVersions(String first, String second) {`

## State and fields

- `private final String currentVersion;`
- `private final String latestVersion;`
- `private final String downloadUrl;`
- `private final String checksumUrl;`
- `private final String releaseNotes;`
- `private final String sha256;`
- `private final long sizeBytes;`
- `private final String minimumLauncherVersion;`

## Dependencies

No explicit imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
