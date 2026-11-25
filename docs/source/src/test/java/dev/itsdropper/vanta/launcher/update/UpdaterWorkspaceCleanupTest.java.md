# `UpdaterWorkspaceCleanupTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/update/UpdaterWorkspaceCleanupTest.java`

## Overview

UpdaterWorkspaceCleanupTest.java implements part of the update subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.update`
- Role: test / verification
- Top-level declaration: `class UpdaterWorkspaceCleanupTest`
- Source size: 109 lines

## API surface

- `public static void main(String[] args) throws Exception {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 7 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Test cases

- `onlyAcceptsDirectManagedSessionDirectories`
- `deletesWorkspaceRecursivelyWithoutFollowingExternalLinks`
- `staleSweepRemovesAbandonedWorkspacesButPreservesActiveUpdater`
- `scheduledCleanupWaitsUntilUpdaterProcessHasExited`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
