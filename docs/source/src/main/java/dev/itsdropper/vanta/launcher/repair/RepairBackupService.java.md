# `RepairBackupService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/repair/RepairBackupService.java`

## Overview

RepairBackupService is part of the repair subsystem. Local crash evidence, diagnosis, confidence and rollback policy. Classification is advisory; only the coordinator may authorize managed repair. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.repair`
- Role: production implementation
- Top-level declaration: `public final class RepairBackupService`
- Source size: 110 lines

## API surface

- `public static Path createSnapshot(Path instanceDirectory) throws IOException {`
- `public static boolean restoreLatest(Path instanceDirectory) throws IOException {`

## State and fields

- `private static final List<String> PROTECTED_FILES =`

## Dependencies

Imports: 11 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
