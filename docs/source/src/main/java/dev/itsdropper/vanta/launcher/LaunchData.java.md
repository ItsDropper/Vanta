# `LaunchData.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/LaunchData.java`

## Overview

Immutable-ish launch input carrier assembled before process creation. Holds resolved launch metadata/arguments and the values consumed by MinecraftLauncher; it separates metadata resolution from process execution.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: production implementation
- Top-level declaration: `public class LaunchData`

## API surface

No public/protected method declarations matched the source scan.

## Dependencies

Imports: 2 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
