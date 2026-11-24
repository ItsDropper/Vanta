# `InstanceRecoveryService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/instance/InstanceRecoveryService.java`

## Overview

InstanceRecoveryService is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: production implementation
- Top-level declaration: `public final class InstanceRecoveryService`
- Source size: 670 lines

## API surface

- `public static RecoveryPoint createRecoveryPoint(Instance instance, String reason) throws IOException {`
- `public static List<RecoveryPoint> listRecoveryPoints(Instance instance) throws IOException {`
- `public static void restoreRecoveryPoint(Instance instance, String recoveryPointId) throws IOException {`
- `public static int recoverInterruptedRestores(Instance instance) throws IOException {`

## State and fields

- `private static final List<String> RECOVERED_DIRECTORIES =`

## Dependencies

Imports: 24 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.launcher.service.LaunchHistoryRepository`
- `dev.itsdropper.vanta.ui.LauncherSettings`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
