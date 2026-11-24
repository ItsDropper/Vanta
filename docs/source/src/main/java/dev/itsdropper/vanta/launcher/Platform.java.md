# `Platform.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/Platform.java`

## Overview

Maps host OS and architecture to Vanta's supported platform identities, Java executable naming, Adoptium distribution identifiers and desktop capability. It deliberately rejects unknown/unvalidated platform combinations instead of guessing.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: production implementation
- Top-level declaration: `public final class Platform`
- Source size: 103 lines

## API surface

- `public static OperatingSystem operatingSystem() {`
- `public static String javaExecutableName() {`
- `public static String adoptiumOs() {`
- `public static String adoptiumArchitecture() {`
- `public static String distributionId() {`
- `public static boolean supportsDesktop() {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 1 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
