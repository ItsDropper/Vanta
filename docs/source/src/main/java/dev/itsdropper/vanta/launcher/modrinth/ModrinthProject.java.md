# `ModrinthProject.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthProject.java`

## Overview

ModrinthProject is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.modrinth`
- Role: production implementation
- Top-level declaration: `public class ModrinthProject`
- Source size: 206 lines

## API surface

- `public static ModrinthProject fromSearchHit(ModrinthSearchHit hit) {`
- `public String getId() {`
- `public String getProjectId() {`
- `public String getSlug() {`
- `public String getProjectType() {`
- `public String getTeam() {`
- `public String getTitle() {`
- `public String getDescription() {`
- `public String getBody() {`
- `public String getIconUrl() {`
- `public int getDownloads() {`
- `public int getFollows() {`
- `public List<String> getCategories() {`
- `public List<String> getVersions() {`
- `public List<String> getLoaders() {`
- `public String getDateCreated() {`
- `public String getDateModified() {`
- `public String getClientSide() {`
- `public String getServerSide() {`
- `public ModrinthLicense getLicense() {`
- `public String getSourceUrl() {`
- `public String getIssuesUrl() {`
- `public String getWikiUrl() {`
- `public String getDiscordUrl() {`

## State and fields

- `private String id;`
- `private String projectId;`
- `private String slug;`
- `private String projectType;`
- `private String team;`
- `private String title;`
- `private String description;`
- `private String body;`
- `private String iconUrl;`
- `private int downloads;`
- `private int followers;`
- `private List<String> categories;`
- `private List<String> versions;`
- `private List<String> loaders;`
- `private String dateCreated;`
- `private String dateModified;`
- `private String clientSide;`
- `private String serverSide;`
- `private ModrinthLicense license;`
- `private String sourceUrl;`
- `private String issuesUrl;`
- `private String wikiUrl;`
- `private String discordUrl;`

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
