# `LoaderCompatibilityServiceTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/instance/LoaderCompatibilityServiceTest.java`

## Overview

LoaderCompatibilityServiceTest.java implements part of the instance subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: test / verification
- Top-level declaration: `class LoaderCompatibilityServiceTest`
- Source size: 87 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 4 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Test cases

- `successfulLookupReportsAvailableVersion`
- `explicitNoMatchingReleaseReportsUnsupported`
- `emptyMetadataResultReportsUnsupported`
- `transportFailureIsNotMisreportedAsUnsupported`
- `wrappedTransportFailureIsNotMisreportedAsUnsupported`
- `unexpectedRuntimeFailureRemainsRetryable`
- `reportPreservesOrderAndSeparatesFailedChecks`
- `malformedMetadataIsRetryableRatherThanUnsupported`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
