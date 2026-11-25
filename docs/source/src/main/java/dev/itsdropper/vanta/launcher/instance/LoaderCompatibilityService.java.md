# `LoaderCompatibilityService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/instance/LoaderCompatibilityService.java`

## Overview

LoaderCompatibilityService is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: production implementation
- Top-level declaration: `public final class LoaderCompatibilityService`
- Source size: 168 lines

## API surface

- `public record Result(Status status, String loaderVersion) {`
- `public boolean isAvailable() {`
- `public record Report(String minecraftVersion, Map<String, Result> results) {`
- `public List<String> availableLoaders() {`
- `public List<String> failedLoaders() {`
- `public boolean hasFailures() {`
- `public static Report checkMinecraftVersion(String minecraftVersion) throws InterruptedException {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 10 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
