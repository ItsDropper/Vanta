# `ModrinthTag.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthTag.java`

## Overview

ModrinthTag is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.modrinth`
- Role: production implementation
- Top-level declaration: `public class ModrinthTag`
- Source size: 52 lines

## API surface

- `public List<String> getSupportedProjectTypes() {`

## State and fields

- `private String name;`
- `private String icon;`
- `private String projectType;`
- `private String header;`
- `private List<String> supportedProjectTypes;`
- `private String version;`
- `private String versionType;`
- `private String date;`
- `private boolean major;`

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
