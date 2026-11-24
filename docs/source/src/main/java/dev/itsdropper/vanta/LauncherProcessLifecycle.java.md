# `LauncherProcessLifecycle.java`

**Source:** `src/main/java/dev/itsdropper/vanta/LauncherProcessLifecycle.java`

## Overview

Owns JVM shutdown independently of JavaFX implicit-exit behavior. Cleanup registration is thread-safe; a registration racing with exit is either included in the shutdown pass or executed immediately. Cleanup is best-effort and isolated so one failing action does not prevent later cleanup. Child Minecraft processes are deliberately not terminated here.

## Package and type

- Package: `dev.itsdropper.vanta`
- Role: production implementation
- Top-level declaration: `public final class LauncherProcessLifecycle`

## API surface

- `public static void registerCleanup(Runnable cleanup) {`
- `public static void exit(Runnable... additionalCleanupActions) {`

## Dependencies

Imports: 3 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
