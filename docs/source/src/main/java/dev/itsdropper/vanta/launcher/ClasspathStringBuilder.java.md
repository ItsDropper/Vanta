# `ClasspathStringBuilder.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/ClasspathStringBuilder.java`

## Overview

Converts an ordered Path collection into the platform-specific JVM classpath representation. It is intentionally a formatting boundary: dependency selection and ordering belong to ClasspathBuilder.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: production implementation
- Top-level declaration: `public class ClasspathStringBuilder`

## API surface

- `public static String build(List<Path> paths) {`

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
