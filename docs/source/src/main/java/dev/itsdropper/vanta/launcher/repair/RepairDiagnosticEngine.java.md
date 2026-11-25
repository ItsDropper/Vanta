# `RepairDiagnosticEngine.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/repair/RepairDiagnosticEngine.java`

## Overview

RepairDiagnosticEngine is part of the repair subsystem. Local crash evidence, diagnosis, confidence and rollback policy. Classification is advisory; only the coordinator may authorize managed repair. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.repair`
- Role: production implementation
- Top-level declaration: `public final class RepairDiagnosticEngine`
- Source size: 814 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

- `private static final Pattern MOD_DEPENDENCY =`
- `private static final Pattern MISSING_FILE =`
- `private static final Pattern CLASS_NOT_FOUND =`
- `private static final Pattern UNSATISFIED_LINK =`
- `private static final Pattern JAVA_VERSION =`
- `private static final Pattern JVM_ARGUMENT =`
- `private static final Pattern OUT_OF_MEMORY =`
- `private static final Pattern DISK_SPACE =`
- `private static final Pattern PERMISSION =`
- `private static final Pattern NETWORK =`
- `private static final Pattern MIXIN_FAILURE =`
- `private static final Pattern DUPLICATE_MOD =`
- `private static final Pattern MOD_INITIALIZATION_FAILURE =`
- `private static final Pattern GRAPHICS_FAILURE =`
- `private static final Pattern AUTHENTICATION_FAILURE =`
- `private static final Pattern CORRUPT_ARCHIVE =`
- `private static final Pattern JAVA_ARCHITECTURE =`
- `private static final Pattern FABRIC_LOADER_ERROR =`
- `private static final Pattern MINECRAFT_VERSION =`
- `private static final Pattern CONFIGURATION =`

## Dependencies

Imports: 4 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
