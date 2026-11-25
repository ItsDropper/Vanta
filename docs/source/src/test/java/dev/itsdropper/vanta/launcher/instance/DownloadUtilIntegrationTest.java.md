# `DownloadUtilIntegrationTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/instance/DownloadUtilIntegrationTest.java`

## Overview

DownloadUtilIntegrationTest.java implements part of the instance subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: test / verification
- Top-level declaration: `class DownloadUtilIntegrationTest`
- Source size: 225 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

- `private HttpServer server;`
- `private boolean previousCache;`
- `private boolean previousSegmented;`
- `private boolean previousResumable;`
- `private int previousRetries;`

## Dependencies

Imports: 14 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.ui.LauncherSettings`

## Test cases

- `downloadsAndPublishesVerifiedArtifact`
- `resumesFromExistingPartialFileUsingValidatedRange`
- `restartsFromZeroWhenServerRejectsStaleResumeOffset`
- `rejectsResumeResponseWithWrongStartingOffset`
- `rejectsUnsolicitedPartialResponse`
- `checksumFailureDoesNotReplaceExistingTarget`
- `permanentHttpFailureDoesNotReplaceExistingTarget`
- `truncatedResponseDoesNotReplaceExistingTarget`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
