# `EnvironmentGuardianService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/instance/EnvironmentGuardianService.java`

## Overview

EnvironmentGuardianService is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: production implementation
- Top-level declaration: `public final class EnvironmentGuardianService`
- Source size: 302 lines

## API surface

- `public void start() {`
- `public void close() {`

## State and fields

- `private final Path backupRoot;`
- `private final Supplier<List<Instance>> instanceSupplier;`
- `private final Predicate<Instance> runningPredicate;`
- `private final BooleanSupplier enabled;`
- `private final IntSupplier intervalHours;`
- `private final IntSupplier retentionCount;`
- `private final BackupCreator backupCreator;`
- `private final ActivityRecorder activityRecorder;`
- `private final ScheduledExecutorService scheduler;`

## Dependencies

Imports: 23 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.launcher.service.MultiLaunchService`
- `dev.itsdropper.vanta.launcher.service.LaunchHistoryRepository`
- `dev.itsdropper.vanta.ui.LauncherSettings`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
