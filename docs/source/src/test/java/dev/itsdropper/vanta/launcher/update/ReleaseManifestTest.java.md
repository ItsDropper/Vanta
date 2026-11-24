# `ReleaseManifestTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/update/ReleaseManifestTest.java`

## Overview

ReleaseManifestTest.java implements part of the update subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.update`
- Role: test / verification
- Top-level declaration: `class ReleaseManifestTest`
- Source size: 143 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 3 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Test cases

- `parsesMatchingPlatformArtifactAndSupportsGitBuildVersionSuffix`
- `returnsNoUpdateArtifactForUnsupportedPlatform`
- `rejectsUnexpectedArtifactHost`
- `rejectsArtifactUrlThatDoesNotMatchDeclaredFile`
- `rejectsUnsupportedSchemaVersion`
- `rejectsUpdatesRequiringANewerLauncher`
- `rejectsNonCanonicalVersionsAndSurroundingWhitespace`
- `rejectsUnknownManifestFields`
- `validatesArtifactsForOtherPlatformsToo`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
