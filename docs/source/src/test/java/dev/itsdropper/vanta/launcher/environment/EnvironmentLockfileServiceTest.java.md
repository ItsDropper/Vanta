# `EnvironmentLockfileServiceTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/environment/EnvironmentLockfileServiceTest.java`

## Overview

EnvironmentLockfileServiceTest.java implements part of the environment subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.environment`
- Role: test / verification
- Top-level declaration: `class EnvironmentLockfileServiceTest`
- Source size: 272 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 8 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`

## Test cases

- `capturesEveryRegularModFileWithStableSha256AndPortableMetadata`
- `writesAndReadsLockfileWithoutMachineSpecificPaths`
- `capturesResourcePacksShadersAndNestedConfigFilesDeterministically`
- `capturesMinecraftRuntimeMetadataAndClientJar`
- `rejectsSymbolicMinecraftDirectoryEvenWhenVersionMetadataIsAbsent`
- `readsSchemaTwoBaselinesAfterSchemaThreeUpgrade`
- `readsPreviousSchemaOneLockfilesWithoutEnvironmentFileMetadata`
- `doesNotFollowSymbolicLinksInsideTrackedDirectories`
- `rejectsEnvironmentArtifactPathTraversalInSavedLockfile`
- `rejectsDuplicateArtifactFilenamesWhenReading`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
