# `ModrinthInstallServiceIntegrationTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/service/ModrinthInstallServiceIntegrationTest.java`

## Overview

ModrinthInstallServiceIntegrationTest.java implements part of the service subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: test / verification
- Top-level declaration: `class ModrinthInstallServiceIntegrationTest`
- Source size: 284 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 18 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.instance.InstalledModManager`
- `dev.itsdropper.vanta.launcher.instance.InstalledModRecord`
- `dev.itsdropper.vanta.launcher.modrinth.InstalledModScanner`
- `dev.itsdropper.vanta.launcher.modrinth.ModrinthClient`
- `dev.itsdropper.vanta.launcher.modrinth.ModrinthVersion`
- `dev.itsdropper.vanta.launcher.model.Instance`

## Test cases

- `installsResolvedDependencyGraphThroughRealFilesystemAndRegistryPath`
- `upgradesTrackedModAndRemovesPreviousFilenameOnlyAfterCommit`
- `failureOnSecondGraphArtifactRestoresPriorInstallAndRemovesFirstNewArtifact`
- `cancellationDuringSecondArtifactRestoresEarlierGraphChanges`
- `registryUpdateFailureAfterArtifactPublishRestoresOldArtifactAndRegistry`
- `rejectsConflictingVersionsForSameProjectBeforeStartingDownloads`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
