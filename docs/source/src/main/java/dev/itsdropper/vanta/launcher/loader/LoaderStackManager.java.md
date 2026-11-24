# `LoaderStackManager.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/loader/LoaderStackManager.java`

## Overview

LoaderStackManager is part of the loader subsystem. Ordered multi-loader profile configuration, deterministic profile merging and persistence. Validate layer IDs, tweaker names and paths before launch. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.loader`
- Role: production implementation
- Top-level declaration: `public final class LoaderStackManager`
- Source size: 181 lines

## API surface

- `public static Path configurationPath(Instance instance) {`
- `public static LoaderStackConfiguration load(Instance instance) throws IOException {`
- `public static void validateForLaunch(Instance instance) {`
- `public static void apply(Instance instance, LoaderStackConfiguration configuration) throws Exception {`
- `public static void refreshBaseAndApply(Instance instance) throws Exception {`
- `public static void clear(Instance instance) throws IOException {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 13 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.instance.FabricInstaller`
- `dev.itsdropper.vanta.launcher.instance.NativeInstaller`
- `dev.itsdropper.vanta.launcher.instance.MinecraftFileInstaller`
- `dev.itsdropper.vanta.launcher.model.Instance`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
