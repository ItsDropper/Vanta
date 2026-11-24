# `LaunchHistoryRepositoryTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/service/LaunchHistoryRepositoryTest.java`

## Overview

LaunchHistoryRepositoryTest.java implements part of the service subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: test / verification
- Top-level declaration: `class LaunchHistoryRepositoryTest`
- Source size: 236 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 10 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.launcher.repair.FailureCategory`

## Test cases

- `persistsLifecycleAndDiagnosticEvidenceAcrossRepositoryInstances`
- `doesNotRunCrashDiagnosisWhenLauncherFailsBeforeAChildProcessExists`
- `recordsSuccessfulAndUserStoppedRunsAsDifferentOutcomes`
- `rejectsIllegalTransitionsAndMutationAfterCompletion`
- `concurrentWritersDoNotLoseIndependentLaunches`
- `rotatesOldCompletedRecordsAtTheConfiguredLimit`
- `refusesCorruptStoresWithoutSilentlyOverwritingThem`
- `recordsInstanceOperationsAlongsideLaunchesInOneTimeline`
- `recordsEnvironmentBaselineAndDriftChecksAsTimelineActivities`
- `preservesUuidShapedStableInstanceIds`
- `truncatesDiagnosticEvidenceInsideTheConfiguredRecordLimit`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
