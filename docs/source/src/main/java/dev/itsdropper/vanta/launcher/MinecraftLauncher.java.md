# `MinecraftLauncher.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/MinecraftLauncher.java`

## Overview

Owns final JVM command construction and child-process creation. Combines Java executable, memory/JVM flags, classpath, natives, main class, authentication/game arguments and legacy LaunchWrapper handling; includes Rift tweaker extraction/validation and runtime-specific mitigations. It does not own instance installation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: production implementation
- Top-level declaration: `public class MinecraftLauncher`

## API surface

No public/protected method declarations matched the source scan.

## Dependencies

Imports: 8 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.account.Account`
- `dev.itsdropper.vanta.launcher.java.JavaLocator`
- `dev.itsdropper.vanta.launcher.instance.RiftModMetadataCompatibility`
- `dev.itsdropper.vanta.security.RuntimeSecurityPolicy`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
