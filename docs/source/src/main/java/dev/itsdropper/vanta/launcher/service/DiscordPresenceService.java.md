# `DiscordPresenceService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/service/DiscordPresenceService.java`

## Overview

DiscordPresenceService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: production implementation
- Top-level declaration: `public final class DiscordPresenceService`
- Source size: 111 lines

## API surface

- `public synchronized void update(Instance instance) {`
- `public synchronized void clear() {`
- `public synchronized void shutdown() {`

## State and fields

- `private IPCClient client;`
- `private long connectedClientId;`

## Dependencies

Imports: 4 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.ui.LauncherSettings`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
