# `ModrinthSearchHit.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthSearchHit.java`

## Overview

ModrinthSearchHit is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.modrinth`
- Role: production implementation
- Top-level declaration: `public class ModrinthSearchHit`
- Source size: 125 lines

## API surface

- `public String getProjectId() {`
- `public String getProjectType() {`
- `public String getTitle() {`
- `public String getDescription() {`
- `public String getAuthor() {`
- `public String getSlug() {`
- `public String getLicense() {`
- `public int getDownloads() {`
- `public int getFollows() {`
- `public String getIconUrl() {`
- `public List<String> getCategories() {`
- `public List<String> getVersions() {`
- `public String getDateModified() {`
- `public String getDateCreated() {`
- `public String getClientSide() {`
- `public String getServerSide() {`

## State and fields

- `private String projectId;`
- `private String projectType;`
- `private String title;`
- `private String description;`
- `private String author;`
- `private String slug;`
- `private String license;`
- `private int downloads;`
- `private int follows;`
- `private String iconUrl;`
- `private List<String> categories;`
- `private List<String> versions;`
- `private String dateModified;`
- `private String dateCreated;`
- `private String clientSide;`
- `private String serverSide;`

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
