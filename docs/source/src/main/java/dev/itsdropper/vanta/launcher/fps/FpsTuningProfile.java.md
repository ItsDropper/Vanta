# `FpsTuningProfile.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/fps/FpsTuningProfile.java`

## Overview

FpsTuningProfile is part of the fps subsystem. Hardware profiling, local benchmark learning and option generation. Optimizer changes must remain constrained to owned performance keys. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.fps`
- Role: production implementation
- Top-level declaration: `public record FpsTuningProfile`
- Source size: 64 lines

## API surface

- `public static FpsTuningProfile maxFps() {`
- `public static FpsTuningProfile highFps() {`
- `public static FpsTuningProfile balanced() {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

No explicit imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
