# `SteinInstaller.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/instance/SteinInstaller.java`

## Overview

SteinInstaller is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: production implementation
- Top-level declaration: `public final class SteinInstaller`
- Source size: 496 lines

## API surface

- `public static boolean supportsCurrentPlatform() {`
- `public static boolean supportsMinecraftVersion(String minecraftVersion) {`
- `public static String findLatestLoaderVersion() {`
- `public static Path javaExecutable(Instance instance) {`
- `public static String install(Instance instance, JsonNode vanillaMetadata) throws Exception {`
- `public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes)`
- `public FileVisitResult visitFile(Path file, BasicFileAttributes attributes)`
- `public FileVisitResult postVisitDirectory(Path dir, IOException failure)`

## State and fields

- `private static final String INSTALLER_URL =`
- `private static final String INSTALLER_SHA256 =`
- `private static final String PAULSCODE_PATH =`

## Dependencies

Imports: 25 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`
- `dev.itsdropper.vanta.launcher.Platform`
- `dev.itsdropper.vanta.launcher.java.JavaLocator`
- `dev.itsdropper.vanta.launcher.model.Instance`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
