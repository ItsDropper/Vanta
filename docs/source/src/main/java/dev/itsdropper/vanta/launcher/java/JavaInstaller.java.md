# `JavaInstaller.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/java/JavaInstaller.java`

## Overview

JavaInstaller is part of the java subsystem. Managed/system Java discovery, installation and version validation. The required Java major comes from Minecraft metadata where available. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.java`
- Role: production implementation
- Top-level declaration: `public class JavaInstaller`
- Source size: 310 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 9 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`
- `dev.itsdropper.vanta.launcher.Platform`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
