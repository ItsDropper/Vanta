# `EnvironmentLockfileService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/environment/EnvironmentLockfileService.java`

## Overview

EnvironmentLockfileService is part of the environment subsystem. Read-only environment inspection and portable lockfile capture/comparison. These services must not mutate instance files. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.environment`
- Role: production implementation
- Top-level declaration: `public final class EnvironmentLockfileService`
- Source size: 737 lines

## API surface

- `public EnvironmentLockfile capture(Instance instance) throws IOException {`
- `public Path write(Instance instance) throws IOException {`
- `public Path getLockfilePath(Instance instance) {`
- `public boolean hasBaseline(Instance instance) {`
- `public EnvironmentLockfile readBaseline(Instance instance) throws IOException {`
- `public EnvironmentLockfile read(Path lockfilePath) throws IOException {`

## State and fields

- `private final InstalledModScanner modScanner;`

## Dependencies

Imports: 34 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`
- `dev.itsdropper.vanta.launcher.LibraryResolver`
- `dev.itsdropper.vanta.launcher.VersionReader`
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.launcher.model.Library`
- `dev.itsdropper.vanta.launcher.model.VersionManifest`
- `dev.itsdropper.vanta.launcher.modrinth.InstalledMod`
- `dev.itsdropper.vanta.launcher.modrinth.InstalledModScanner`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
