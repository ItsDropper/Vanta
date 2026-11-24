# `NativeExtractor.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/NativeExtractor.java`

## Overview

Extracts platform native entries from resolved library JARs into a staged flat directory, filters native filenames for the current OS, and publishes the completed directory only after extraction succeeds. Existing native output is preserved on failure.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: production implementation
- Top-level declaration: `public class NativeExtractor`
- Source size: 189 lines

## API surface

- `public static void extractAll(List<Path> jars, Path outputFolder) throws Exception {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 7 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
