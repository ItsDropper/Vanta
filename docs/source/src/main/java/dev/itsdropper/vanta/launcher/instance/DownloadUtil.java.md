# `DownloadUtil.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/instance/DownloadUtil.java`

## Overview

DownloadUtil is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: production implementation
- Top-level declaration: `public class DownloadUtil`
- Source size: 909 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

- `private final int statusCode;`
- `private final String retryAfter;`
- `private static final ObjectMapper MAPPER =`
- `private static final HttpClient HTTP =`
- `private static final HttpClient HTTP_HTTP1 =`
- `private static final Duration REQUEST_TIMEOUT =`
- `private int users;`

## Dependencies

Imports: 28 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`
- `dev.itsdropper.vanta.launcher.Platform`
- `dev.itsdropper.vanta.ui.LauncherSettings`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
