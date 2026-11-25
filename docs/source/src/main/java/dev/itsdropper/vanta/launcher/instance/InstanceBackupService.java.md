# `InstanceBackupService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/instance/InstanceBackupService.java`

## Overview

InstanceBackupService is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: production implementation
- Top-level declaration: `public final class InstanceBackupService`
- Source size: 359 lines

## API surface

- `public static void createBackup(Instance instance, Path output) throws IOException {`
- `public static BackupVerification verifyBackup(Path archive) throws IOException {`
- `public static Instance restoreBackup(Path archive, String requestedName) throws IOException {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 20 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.launcher.service.LaunchHistoryRepository`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
