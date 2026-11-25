# `VersionReader.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/VersionReader.java`

## Overview

Reads and caches the instance's merged version.json into VersionManifest. Cache invalidation uses file modification time, size and file identity; inheritance handling reflects Vanta's stored merged-profile model.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: production implementation
- Top-level declaration: `public class VersionReader`
- Source size: 111 lines

## API surface

- `public static void clearCache() {`

## State and fields

- `private static final ObjectMapper MAPPER =`

## Dependencies

Imports: 10 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.VersionManifest`
- `dev.itsdropper.vanta.ui.LauncherSettings`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
