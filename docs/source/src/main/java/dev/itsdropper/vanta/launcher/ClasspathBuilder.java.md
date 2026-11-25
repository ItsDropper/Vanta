# `ClasspathBuilder.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/ClasspathBuilder.java`

## Overview

Builds ordered, de-duplicated game and native classpaths from merged version metadata and instance-local loader state. Its optional experimental cache keys on normalized instance/library roots, loader identity, and version.json file attributes; cached paths are revalidated before reuse. Includes Babric-specific ASM exclusion because Fabric Loader rejects duplicate ASM classes, and the ModLoader exploded bootstrap special case.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: production implementation
- Top-level declaration: `public class ClasspathBuilder`

## API surface

- `public static CacheStats getCacheStats() {`
- `public static void clearCache() {`

## Dependencies

Imports: 14 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.launcher.model.Library`
- `dev.itsdropper.vanta.launcher.model.VersionManifest`
- `dev.itsdropper.vanta.ui.LauncherSettings`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
