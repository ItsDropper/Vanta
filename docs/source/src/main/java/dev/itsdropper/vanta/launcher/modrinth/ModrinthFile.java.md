# `ModrinthFile.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthFile.java`

## Overview

ModrinthFile is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.modrinth`
- Role: production implementation
- Top-level declaration: `public class ModrinthFile`
- Source size: 36 lines

## API surface

- `public String getUrl() {`
- `public String getFilename() {`
- `public boolean isPrimary() {`
- `public long getSize() {`

## State and fields

- `private String url;`
- `private String filename;`
- `private boolean primary;`
- `private long size;`

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
