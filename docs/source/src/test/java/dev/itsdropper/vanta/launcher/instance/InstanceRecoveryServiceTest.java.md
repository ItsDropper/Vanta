# `InstanceRecoveryServiceTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/instance/InstanceRecoveryServiceTest.java`

## Overview

InstanceRecoveryServiceTest.java implements part of the instance subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: test / verification
- Top-level declaration: `class InstanceRecoveryServiceTest`
- Source size: 208 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 9 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`

## Test cases

- `recoveryPointIncludesManagedFoldersButExcludesWorlds`
- `restoreReplacesOnlyManagedDirectoriesAndPreservesWorlds`
- `restoringLegacyPointPreservesImportedLoaderProfiles`
- `interruptedRestoreIsRolledBackFromJournal`
- `committedRestoreIsNotRolledBackDuringCleanupRecovery`
- `keepsOnlyFiveMostRecentRecoveryPoints`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
