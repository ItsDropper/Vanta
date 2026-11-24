# `InstanceManager.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/instance/InstanceManager.java`

## Overview

InstanceManager is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: production implementation
- Top-level declaration: `public class InstanceManager`
- Source size: 729 lines

## API surface

- `public static List<Instance> discoverInstances() {`
- `public static List<Instance> discoverRepairableInstances() {`
- `public static List<Instance> discoverRepairableInstances(Consumer<String> skippedInstanceReporter) {`
- `public static Instance renameInstance(Instance instance, String newName) throws IOException {`
- `public static Instance setInstanceIcon(Instance instance, String icon) throws IOException {`
- `public static Instance duplicateInstance(Instance source, String requestedName) throws IOException {`

## State and fields

- `private static final ObjectMapper MAPPER =`
- `private static final String INSTALLING_MARKER =`

## Dependencies

Imports: 16 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.launcher.service.LaunchHistoryRepository`
- `dev.itsdropper.vanta.launcher.model.InstanceSettings`
- `dev.itsdropper.vanta.launcher.service.ModrinthInstallService`
- `dev.itsdropper.vanta.ui.LauncherSettings`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
