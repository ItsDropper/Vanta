# `InstanceRepairServiceTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/instance/InstanceRepairServiceTest.java`

## Overview

InstanceRepairServiceTest.java implements part of the instance subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: test / verification
- Top-level declaration: `class InstanceRepairServiceTest`
- Source size: 121 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 4 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Test cases

- `restoresOriginalPatchedClientAndVersionMetadataAfterFailedRepair`
- `restoresRuntimeAndNativeDirectoryTreesWithoutLeavingNewFiles`
- `removesFilesAndDirectoriesThatDidNotExistBeforeRepair`
- `identifiesMissingLoaderProfilesAndNativeExtractionAsRepairable`
- `doesNotAutomaticallyRepairTransientNetworkOrUserModFailures`
- `identifiesMissingSteinRuntimeAsRepairable`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
