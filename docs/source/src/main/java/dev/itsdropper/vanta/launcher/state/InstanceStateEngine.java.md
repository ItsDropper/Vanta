# `InstanceStateEngine.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/state/InstanceStateEngine.java`

## Overview

InstanceStateEngine is part of the state subsystem. Read-only aggregate health snapshots and filesystem fingerprinting for instance/shared-resource status. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.state`
- Role: production implementation
- Top-level declaration: `public final class InstanceStateEngine`
- Source size: 185 lines

## API surface

- `public static List<SharedState> inspectSharedResources() {`
- `public static SharedState inspectShared(String name, Path root, boolean validateArchives) {`
- `public static SharedState inspectShared(String name, Path root, boolean validateArchives, int workers) {`
- `public static InstanceState inspect(Instance instance) {`
- `public static InstanceState inspect(Instance instance, int workers) {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 13 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.launcher.MinecraftLocator`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
