# `InstalledModScanner.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/modrinth/InstalledModScanner.java`

## Overview

InstalledModScanner is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.modrinth`
- Role: production implementation
- Top-level declaration: `public class InstalledModScanner`
- Source size: 581 lines

## API surface

- `public InstalledMod scanFile(Path file) {`

## State and fields

- `private final ObjectMapper objectMapper =`

## Dependencies

Imports: 12 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
