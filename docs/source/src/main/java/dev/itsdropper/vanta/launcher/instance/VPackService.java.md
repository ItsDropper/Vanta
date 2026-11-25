# `VPackService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/instance/VPackService.java`

## Overview

VPackService is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: production implementation
- Top-level declaration: `public final class VPackService`
- Source size: 396 lines

## API surface

- `public static Options defaults() {`
- `public static void createVPack(Instance instance, Path output, Options options) throws IOException {`
- `public static Verification verifyVPack(Path archive) throws IOException {`
- `public static Instance restoreVPack(Path archive, String requestedName) throws IOException {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 21 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
