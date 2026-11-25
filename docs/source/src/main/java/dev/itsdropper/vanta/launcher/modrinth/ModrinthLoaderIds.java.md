# `ModrinthLoaderIds.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthLoaderIds.java`

## Overview

ModrinthLoaderIds is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.modrinth`
- Role: production implementation
- Top-level declaration: `public final class ModrinthLoaderIds`
- Source size: 74 lines

## API surface

- `public static String normalize(String loader) {`
- `public static boolean supportsGameVersion(java.util.List<String> supportedVersions, String requestedVersion) {`
- `public static boolean supportsSearchFacet(String loader) {`

## State and fields

No simple top-level field declarations matched the source scan.

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
