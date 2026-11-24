# `UpdateService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/update/UpdateService.java`

## Overview

UpdateService is part of the update subsystem. Signed release metadata, download verification, crash-recoverable payload swap, external updater handoff and safe workspace cleanup. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.update`
- Role: production implementation
- Top-level declaration: `public class UpdateService`
- Source size: 197 lines

## API surface

- `public UpdateInfo checkForUpdate(String currentVersion) throws IOException, InterruptedException {`
- `public UpdateInfo getVersion(String currentVersion, String targetVersion)`
- `public List<UpdateInfo> getAvailableVersions(String currentVersion)`

## State and fields

- `private static final String API_BASE =`
- `private static final String RELEASE_DOWNLOAD_BASE =`
- `private static final String STABLE_MANIFEST_URL =`
- `private static final String STABLE_MANIFEST_SIGNATURE_URL =`
- `private static final Pattern RELEASE_VERSION =`
- `private final HttpClient httpClient;`
- `private final ObjectMapper objectMapper;`
- `private final int statusCode;`

## Dependencies

Imports: 17 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.Platform`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
