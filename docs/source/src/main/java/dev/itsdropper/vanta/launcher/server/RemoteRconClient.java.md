# `RemoteRconClient.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/server/RemoteRconClient.java`

## Overview

RemoteRconClient is part of the server subsystem. Local server process lifecycle, RCON protocol and per-server Java runtime management. Process output and backups remain local. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.server`
- Role: production implementation
- Top-level declaration: `public final class RemoteRconClient`
- Source size: 90 lines

## API surface

- `public synchronized String command(String command) throws IOException {`

## State and fields

- `private final DataInputStream input;`
- `private final DataOutputStream output;`
- `private volatile boolean authenticated;`

## Dependencies

Imports: 4 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
