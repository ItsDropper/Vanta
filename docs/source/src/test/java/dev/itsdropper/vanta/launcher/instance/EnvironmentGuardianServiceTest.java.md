# `EnvironmentGuardianServiceTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/instance/EnvironmentGuardianServiceTest.java`

## Overview

EnvironmentGuardianServiceTest.java implements part of the instance subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: test / verification
- Top-level declaration: `class EnvironmentGuardianServiceTest`
- Source size: 226 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 11 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.launcher.service.LaunchHistoryRepository`

## Test cases

- `createsVerifiedBackupsForStoppedInstancesAndSkipsRunningOnes`
- `skipsInstancesWithARecentAutomaticBackup`
- `prunesOldestArchivesOnlyAfterTheNewBackupHasBeenCreated`
- `preservesExistingArchivesWhenANewBackupFails`
- `skipsInstancesThatAreBeingInstalledOrRepaired`
- `doesNothingWhenAutomaticBackupsAreDisabled`
- `rejectsUnsafeInstanceIdsWithoutEscapingTheBackupRoot`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
