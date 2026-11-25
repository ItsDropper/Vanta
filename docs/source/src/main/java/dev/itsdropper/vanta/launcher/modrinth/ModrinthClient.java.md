# `ModrinthClient.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/modrinth/ModrinthClient.java`

## Overview

ModrinthClient is part of the modrinth subsystem. Modrinth API DTOs, loader ID normalization, installed-JAR metadata scanning and version constraint evaluation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.modrinth`
- Role: production implementation
- Top-level declaration: `public class ModrinthClient`
- Source size: 687 lines

## API surface

- `public List<ModrinthTag> getGameVersionTags() throws IOException, InterruptedException {`
- `public List<ModrinthTag> getLoaderTags() throws IOException, InterruptedException {`
- `public List<ModrinthTag> getCategoryTags() throws IOException, InterruptedException {`
- `public List<ModrinthTag> getLoaderTags(ModrinthContentType contentType)`
- `public List<ModrinthTag> getCategoryTags(ModrinthContentType contentType)`

## State and fields

- `private static final String API_URL =`
- `private final HttpClient httpClient;`
- `private final ObjectMapper mapper;`
- `private static final java.util.Map<String, VersionFilterCacheEntry> VERSION_FILTER_CACHE =`
- `private static final java.util.Map<String, TagCacheEntry> TAG_CACHE =`

## Dependencies

Imports: 11 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
