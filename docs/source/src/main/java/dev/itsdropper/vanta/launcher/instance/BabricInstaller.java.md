# `BabricInstaller.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/instance/BabricInstaller.java`

## Overview

BabricInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: production implementation
- Top-level declaration: `public final class BabricInstaller`
- Source size: 288 lines

## API surface

- `public static String findLatestLoaderVersion(String minecraftVersion) throws Exception {`
- `public static JsonNode downloadProfile(String minecraftVersion, String loaderVersion) throws Exception {`
- `public static JsonNode downloadFabricProfile(String minecraftVersion, String loaderVersion)`
- `public static JsonNode downloadOverlay(JsonNode fabricProfile) throws Exception {`
- `public static void installLibraries(JsonNode fabricProfile, JsonNode babricOverlay) throws Exception {`
- `public static JsonNode buildOverlay(JsonNode fabricProfile) throws Exception {`

## State and fields

- `private static final String BABRIC_ARGUMENTS =`

## Dependencies

Imports: 5 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.loader.LoaderProfileStackMerger`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
