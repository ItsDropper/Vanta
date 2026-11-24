# `RepairCoordinatorTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/repair/RepairCoordinatorTest.java`

## Overview

RepairCoordinatorTest.java implements part of the repair subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.repair`
- Role: test / verification
- Top-level declaration: `class RepairCoordinatorTest`
- Source size: 67 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 5 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.ui.views.RepairView`

## Test cases

- `knownInstallationDamageUsesManagedRepair`
- `unrelatedFailuresDoNotTriggerPointlessReinstall`
- `unknownFailureGetsNonDestructiveIntegrityPass`
- `explicitUserRequestOverridesNonInstallationDiagnosis`
- `explicitUserRequestAlsoAllowsRepairForUnknownFailures`
- `emptyInputCanStillRunBaselineIntegrityPass`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
