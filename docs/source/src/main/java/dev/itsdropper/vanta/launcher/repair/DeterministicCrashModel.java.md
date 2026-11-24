# `DeterministicCrashModel.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/repair/DeterministicCrashModel.java`

## Overview

DeterministicCrashModel is part of the repair subsystem. Local crash evidence, diagnosis, confidence and rollback policy. Classification is advisory; only the coordinator may authorize managed repair. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.repair`
- Role: production implementation
- Top-level declaration: `public final class DeterministicCrashModel`
- Source size: 151 lines

## API surface

- `public static Prediction classify(String log) {`

## State and fields

- `private int documents;`
- `private int tokenCount;`
- `private final Map<FailureCategory, ClassStats> classes;`
- `private final Set<String> vocabulary;`
- `private final int documentCount;`

## Dependencies

Imports: 18 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
