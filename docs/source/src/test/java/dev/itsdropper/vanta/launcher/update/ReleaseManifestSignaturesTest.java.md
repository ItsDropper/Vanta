# `ReleaseManifestSignaturesTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/update/ReleaseManifestSignaturesTest.java`

## Overview

ReleaseManifestSignaturesTest.java implements part of the update subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.update`
- Role: test / verification
- Top-level declaration: `class ReleaseManifestSignaturesTest`
- Source size: 111 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

- `private static final byte[] DOMAIN =`

## Dependencies

Imports: 8 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Test cases

- `verifiesExactManifestBytesUsingPinnedKeyId`
- `rejectsModifiedManifestBytes`
- `rejectsUnknownAndMalformedKeyIds`
- `rejectsDuplicateFieldsUnknownFieldsAndInvalidSignatureLengths`
- `pinnedTrustStoreNeverAcceptsAnUnknownSigner`
- `parsesKeyringAndRejectsDuplicateIds`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
