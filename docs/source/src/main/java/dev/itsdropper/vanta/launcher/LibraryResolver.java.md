# `LibraryResolver.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/LibraryResolver.java`

## Overview

Resolves Maven and modern Mojang artifact descriptors to safe local library paths. Selects native classifiers by OS/architecture, evaluates Mojang allow/disallow rules, substitutes architecture tokens, validates paths remain under the managed libraries root, and verifies resolved artifacts are regular files.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: production implementation
- Top-level declaration: `public final class LibraryResolver`

## API surface

- `public static Library.Artifact findNativeArtifact(Library library) {`

## Dependencies

Imports: 6 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Library`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
