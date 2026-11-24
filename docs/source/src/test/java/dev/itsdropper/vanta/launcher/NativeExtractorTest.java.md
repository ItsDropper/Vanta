# `NativeExtractorTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/NativeExtractorTest.java`

## Overview

NativeExtractorTest.java implements part of the launcher subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: test / verification
- Top-level declaration: `class NativeExtractorTest`
- Source size: 192 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 10 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Library`

## Test cases

- `extractsLinuxSharedObjectsIncludingVersionedNames`
- `extractsMacNativeLibraries`
- `extractsWindowsDllsOnlyOnWindows`
- `publishesNativeDirectoryWithoutStaleFiles`
- `failedNativeExtractionPreservesPreviousDirectory`
- `selectsArm64ModernNativeClassifierOnLinux`
- `selectsMacLegacyNativeClassifier`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
