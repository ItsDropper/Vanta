# `LoaderProfileStackMerger.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/loader/LoaderProfileStackMerger.java`

## Overview

LoaderProfileStackMerger is part of the loader subsystem. Ordered multi-loader profile configuration, deterministic profile merging and persistence. Validate layer IDs, tweaker names and paths before launch. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.loader`
- Role: production implementation
- Top-level declaration: `public final class LoaderProfileStackMerger`
- Source size: 321 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 8 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
