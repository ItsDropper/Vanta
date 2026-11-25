# `InstanceUsageManager.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/service/InstanceUsageManager.java`

## Overview

InstanceUsageManager is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: production implementation
- Top-level declaration: `public final class InstanceUsageManager`
- Source size: 133 lines

## API surface

- `public static synchronized void markPlayed(Instance instance) {`
- `public static synchronized void addPlaytime(Instance instance, long seconds) {`
- `public static synchronized long getPlaytimeSeconds(Instance instance) {`
- `public static synchronized long getLastPlayed(Instance instance) {`
- `public static synchronized List<Instance> sortByLastPlayed(List<Instance> instances) {`
- `public static String formatPlaytime(long seconds) {`

## State and fields

- `private static final ObjectMapper MAPPER =`
- `private static final Path FILE =`
- `public long lastPlayed;`
- `public long playtimeSeconds;`

## Dependencies

Imports: 12 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`
- `dev.itsdropper.vanta.launcher.model.Instance`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
