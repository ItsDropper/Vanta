# `LegacyArgumentResolver.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/LegacyArgumentResolver.java`

## Overview

Tokenizes legacy argument templates before placeholder substitution so paths containing spaces remain a single argument. Handles quoting and rejects malformed quoting or unresolved placeholders rather than passing ambiguous strings to the JVM.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: production implementation
- Top-level declaration: `public final class LegacyArgumentResolver`

## API surface

- `public static List<String> resolve(String template, Map<String, String> placeholders) {`
- `public static List<String> tokenize(String arguments) {`

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
