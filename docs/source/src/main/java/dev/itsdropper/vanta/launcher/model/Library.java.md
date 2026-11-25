# `Library.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/model/Library.java`

## Overview

Library is part of the model subsystem. Persistence/data-transfer models used by installation, launch, server history and version metadata. These classes should remain free of I/O orchestration. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.model`
- Role: production implementation
- Top-level declaration: `public class Library`
- Source size: 56 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

- `public String name;`
- `public Map<String, String> natives;`
- `public List<Rule> rules;`
- `public Downloads downloads;`
- `public String action;`
- `public OperatingSystem os;`
- `public String arch;`
- `public String version;`
- `public Artifact artifact;`
- `public Map<String, Artifact> classifiers;`
- `public String path;`
- `public String url;`
- `public String sha1;`
- `public long size;`

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
