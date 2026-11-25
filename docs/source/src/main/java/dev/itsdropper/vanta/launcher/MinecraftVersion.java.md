# `MinecraftVersion.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/MinecraftVersion.java`

## Overview

Reads installed version identifiers from a Minecraft directory. This is a lightweight inventory helper rather than the authoritative metadata parser.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: production implementation
- Top-level declaration: `public class MinecraftVersion`
- Source size: 31 lines

## API surface

- `public static List<String> getInstalledVersions(Path minecraftDirectory) {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 5 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
