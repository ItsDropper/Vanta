# `ModrinthVersion.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthVersion.java`

## Overview

ModrinthVersion is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.modrinth`
- Role: production implementation
- Top-level declaration: `public class ModrinthVersion`
- Source size: 91 lines

## API surface

- `public String getId() {`
- `public String getProjectId() {`
- `public String getName() {`
- `public String getVersionNumber() {`
- `public String getVersionType() {`
- `public List<String> getGameVersions() {`
- `public List<String> getLoaders() {`
- `public boolean isFeatured() {`
- `public int getDownloads() {`
- `public List<ModrinthFile> getFiles() {`
- `public List<ModrinthDependency> getDependencies() {`

## State and fields

- `private String id;`
- `private String projectId;`
- `private String name;`
- `private String versionNumber;`
- `private String versionType;`
- `private List<String> gameVersions;`
- `private List<String> loaders;`
- `private boolean featured;`
- `private int downloads;`
- `private List<ModrinthFile> files;`
- `private List<ModrinthDependency> dependencies;`

## Dependencies

Imports: 3 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
