# `ServerHistoryManager.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/service/ServerHistoryManager.java`

## Overview

ServerHistoryManager is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: production implementation
- Top-level declaration: `public final class ServerHistoryManager`
- Source size: 215 lines

## API surface

- `public static void addListener(Runnable listener) {`
- `public static void removeListener(Runnable listener) {`
- `public static synchronized List<ServerHistoryEntry> getRecent() {`

## State and fields

- `private static final ObjectMapper MAPPER =`
- `private static final Path FILE =`
- `private static final CopyOnWriteArrayList<Runnable> listeners =`

## Dependencies

Imports: 13 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.launcher.model.ServerHistoryEntry`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
