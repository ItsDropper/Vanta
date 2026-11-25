# `InstalledModRecord.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/instance/InstalledModRecord.java`

## Overview

InstalledModRecord is part of the instance subsystem. Minecraft installation, loader overlays, import/export, repair, recovery, backups and shared artifact transfer. Loader-specific installers validate upstream metadata; transaction services protect existing user data. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.instance`
- Role: production implementation
- Top-level declaration: `public class InstalledModRecord`
- Source size: 47 lines

## API surface

- `public String getProjectId() {`
- `public String getVersionId() {`
- `public String getModId() {`
- `public String getVersion() {`
- `public String getFilename() {`

## State and fields

- `private String projectId;`
- `private String versionId;`
- `private String modId;`
- `private String version;`
- `private String filename;`

## Dependencies

No explicit imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
