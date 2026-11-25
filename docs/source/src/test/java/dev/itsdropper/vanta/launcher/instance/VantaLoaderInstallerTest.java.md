# `VantaLoaderInstallerTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/instance/VantaLoaderInstallerTest.java`

## Overview

VantaLoaderInstallerTest.java implements part of the instance subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: test / verification
- Top-level declaration: `class VantaLoaderInstallerTest`
- Source size: 161 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 11 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`

## Test cases

- `supportsOnlyMinecraftVersionsWithTheJava21RuntimeBaseline`
- `selectsNewestPublishedNightlyWithBothRequiredAssets`
- `rejectsReleaseListsWithoutAUsableNightly`
- `acceptsActualNightlyReleaseTagsAndMigratesLegacyCommitTags`
- `resolvesAgentInsideTheInstanceAndRejectsPathLikeReleaseIdentifiers`
- `validatesAgentManifestAndRequiredBundledClasses`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
