# `ModrinthService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/service/ModrinthService.java`

## Overview

ModrinthService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: production implementation
- Top-level declaration: `public class ModrinthService`
- Source size: 277 lines

## API surface

- `public List<ModrinthTag> getGameVersionTags() throws IOException, InterruptedException {`
- `public List<ModrinthTag> getLoaderTags(ModrinthContentType contentType)`
- `public List<ModrinthTag> getCategoryTags(ModrinthContentType contentType)`
- `public List<ModrinthProject> searchModsFast(Instance instance, String query) throws IOException, InterruptedException {`
- `public List<ModrinthProject> mapSearchHitsToProjects(List<ModrinthSearchHit> hits) {`
- `public List<ModrinthProject> searchModsFastBroad(Instance instance, String query) throws IOException, InterruptedException {`
- `public List<ModrinthProject> getMostDownloadedModsFast(Instance instance) throws IOException, InterruptedException {`
- `public List<ModrinthProject> getMostDownloadedModsFastBroad(Instance instance) throws IOException, InterruptedException {`
- `public Path installMod(Instance instance, ModrinthProject project) throws IOException, InterruptedException {`
- `public List<ModUpdate> checkForModUpdates(Instance instance)`
- `public Path installTrackedModUpdate(Instance instance, ModUpdate update)`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 11 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.modrinth.InstalledModScanner`
- `dev.itsdropper.vanta.launcher.instance.InstalledModManager`
- `dev.itsdropper.vanta.launcher.instance.InstalledModRecord`
- `dev.itsdropper.vanta.launcher.model.Instance`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
