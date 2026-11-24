# `MinecraftLocator.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/MinecraftLocator.java`

## Overview

Centralizes Vanta data, instances and shared libraries directory resolution through Platform. Path accessors distinguish path calculation from directory creation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: production implementation
- Top-level declaration: `public class MinecraftLocator`

## API surface

- `public static Path getVantaDirectoryPath() {`
- `public static Path getVantaDirectory() {`
- `public static Path getInstancesDirectory() {`
- `public static Path getLibrariesDirectoryPath() {`
- `public static Path getLibrariesDirectory() {`

## Dependencies

Imports: 3 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
