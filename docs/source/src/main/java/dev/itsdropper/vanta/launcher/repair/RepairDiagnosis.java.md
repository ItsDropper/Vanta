# `RepairDiagnosis.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/repair/RepairDiagnosis.java`

## Overview

RepairDiagnosis is part of the repair subsystem. Local crash evidence, diagnosis, confidence and rollback policy. Classification is advisory; only the coordinator may authorize managed repair. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.repair`
- Role: production implementation
- Top-level declaration: `public record RepairDiagnosis`
- Source size: 45 lines

## API surface

No public/protected method declarations matched the source scan.

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
