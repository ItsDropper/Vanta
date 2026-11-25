# `ModrinthGraphInstallTransactionTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/service/ModrinthGraphInstallTransactionTest.java`

## Overview

ModrinthGraphInstallTransactionTest.java implements part of the service subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: test / verification
- Top-level declaration: `class ModrinthGraphInstallTransactionTest`
- Source size: 351 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 17 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.instance.InstalledModManager`
- `dev.itsdropper.vanta.launcher.instance.InstalledModRecord`
- `dev.itsdropper.vanta.launcher.model.Instance`

## Test cases

- `failureRestoresEveryTrackedArtifactAndRegistryWithoutTouchingUntrackedFiles`
- `successfulOperationKeepsAllChangesAndCleansItsSnapshot`
- `rollbackRestoresAbsenceOfRegistryWhenInstanceWasPreviouslyUntracked`
- `interruptionRollsBackEarlierGraphChangesAndPropagatesInterruption`
- `rejectsRegistryPathTraversalBeforeRunningOperation`
- `nextInstallRecoversAnInterruptedPublishBeforeRegistryCommit`
- `recoveryRefusesAnIncompletePreparedJournalInsteadOfDeletingUserData`
- `recoveryRetainsJournalWhenAnOriginalBackupIsMissing`
- `serializesConcurrentTransactionsForTheSameInstance`
- `nestedInstallOperationsJoinTheOuterTransactionAndRollbackTogether`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
