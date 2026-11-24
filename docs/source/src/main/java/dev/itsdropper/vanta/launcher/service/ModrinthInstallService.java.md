# `ModrinthInstallService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/service/ModrinthInstallService.java`

## Overview

ModrinthInstallService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: production implementation
- Top-level declaration: `public final class ModrinthInstallService`
- Source size: 800 lines

## API surface

- `public static void recoverPendingTransactions(Instance instance) throws IOException {`
- `public Path installMod(Instance instance, ModrinthProject project, Consumer<String> progress)`

## State and fields

- `private final ModrinthClient client;`
- `private final InstalledModScanner installedModScanner;`
- `private final ModrinthCatalogService catalog;`
- `private final ModrinthDependencyResolver dependencies;`
- `private final ModrinthArtifactDownloader downloader;`

## Dependencies

Imports: 3 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
