# `FpsHardwareDetector.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/fps/FpsHardwareDetector.java`

## Overview

FpsHardwareDetector is part of the fps subsystem. Hardware profiling, local benchmark learning and option generation. Optimizer changes must remain constrained to owned performance keys. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.fps`
- Role: production implementation
- Top-level declaration: `public final class FpsHardwareDetector`
- Source size: 187 lines

## API surface

- `public static FpsHardwareProfile detect() {`

## State and fields

- `private static final Pattern GPU_NAME =`

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
