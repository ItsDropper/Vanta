# `LaunchDataBuilder.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/LaunchDataBuilder.java`

## Overview

Transforms a merged Minecraft version manifest plus instance/account/settings context into LaunchData. Resolves modern structured and legacy string arguments, performs placeholder expansion, derives the required Java major from metadata with historical-version fallback, and accounts for NeoForge FML-version requirements.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: production implementation
- Top-level declaration: `public class LaunchDataBuilder`

## API surface

No public/protected method declarations matched the source scan.

## Dependencies

Imports: 14 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.instance.InstanceManager`
- `dev.itsdropper.vanta.launcher.instance.NilLoaderInstaller`
- `dev.itsdropper.vanta.launcher.instance.SteinInstaller`
- `dev.itsdropper.vanta.launcher.instance.VantaLoaderInstaller`
- `dev.itsdropper.vanta.launcher.loader.LoaderStackManager`
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.launcher.model.InstanceSettings`
- `dev.itsdropper.vanta.launcher.model.VersionManifest`
- `dev.itsdropper.vanta.launcher.service.ServerTarget`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
