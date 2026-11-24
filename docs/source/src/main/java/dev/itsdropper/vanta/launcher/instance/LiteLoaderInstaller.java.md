# `LiteLoaderInstaller.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/instance/LiteLoaderInstaller.java`

## Overview

LiteLoaderInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: production implementation
- Top-level declaration: `public final class LiteLoaderInstaller`
- Source size: 213 lines

## API surface

- `public static String findLatestLoaderVersion(String minecraftVersion) throws Exception {`
- `public static JsonNode buildOverlay(String minecraftVersion, JsonNode versionMetadata) {`
- `public static String install(Instance instance) throws Exception {`
- `public static String install(Instance instance, String minecraftVersion) throws Exception {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 13 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`
- `dev.itsdropper.vanta.launcher.loader.LoaderStackConfiguration`
- `dev.itsdropper.vanta.launcher.loader.LoaderStackManager`
- `dev.itsdropper.vanta.launcher.model.Instance`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
