# `PlatformTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/PlatformTest.java`

## Overview

PlatformTest.java implements part of the launcher subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: test / verification
- Top-level declaration: `class PlatformTest`
- Source size: 113 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 2 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Test cases

- `detectsSupportedOperatingSystems`
- `doesNotMisclassifyOtherUnixFamiliesAsLinux`
- `resolvesWindowsRoamingDataDirectory`
- `resolvesMacApplicationSupportDirectory`
- `resolvesLinuxXdgDataDirectory`
- `fallsBackToLinuxLocalShareAndIgnoresRelativeXdgOverride`
- `rejectsUnknownOperatingSystemsAndMissingRequiredHomes`
- `selectsNativeJavaExecutableName`
- `mapsSupportedReleaseTargetsAndRejectsUntestedCombinations`
- `mapsSupportedAdoptiumPlatformsAndArchitectures`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
