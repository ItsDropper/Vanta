# `CrashCaseRepositoryTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/repair/CrashCaseRepositoryTest.java`

## Overview

CrashCaseRepositoryTest.java implements part of the repair subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.repair`
- Role: test / verification
- Top-level declaration: `class CrashCaseRepositoryTest`
- Source size: 250 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 6 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Test cases

- `storesOnlySanitizedCasesAndGroupsDuplicates`
- `onlyConfirmedCasesAreExportedAsTrainingRows`
- `verifiedFixesAreSanitizedAndExportedSeparately`
- `confirmedLabelsCanBeCorrectedAndRejectedCasesCanBeReopened`
- `verifiedFixLookupIsCategoryScopedAndBounded`
- `readsPreviousStoreSchemaAndUpgradesOnNextWrite`
- `rejectsInvalidTransitionsAndUnknownLabels`
- `rejectsOversizedImportsAndCorruptStores`
- `concurrentRepositoryInstancesDoNotLoseUpdates`
- `localStoreUsesOwnerOnlyPosixPermissionsWhereSupported`
- `exportCannotOverwriteItsOwnReviewDatabase`
- `fingerprintIgnoresTimestampNoiseButPreservesDifferentCauses`
- `largeLogsRetainBothBeginningAndRootCauseTail`
- `importNeverMutatesTheSourceCrashLog`
- `sanitizerRedactsTokensIpsUuidsPathsAndEmailAddresses`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
