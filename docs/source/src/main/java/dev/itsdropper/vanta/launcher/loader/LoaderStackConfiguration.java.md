# `LoaderStackConfiguration.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/loader/LoaderStackConfiguration.java`

## Overview

LoaderStackConfiguration is part of the loader subsystem. Ordered multi-loader profile configuration, deterministic profile merging and persistence. Validate layer IDs, tweaker names and paths before launch. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.loader`
- Role: production implementation
- Top-level declaration: `public final class LoaderStackConfiguration`
- Source size: 163 lines

## API surface

- `public int getSchemaVersion() {`
- `public void setSchemaVersion(int schemaVersion) {`
- `public List<Layer> getLayers() {`
- `public void setLayers(List<Layer> layers) {`
- `public List<Layer> getOrderedLayers() {`
- `public List<String> getOrderedTweakers() {`
- `public void validate() {`
- `public void save(Path path) throws IOException {`
- `public static LoaderStackConfiguration load(Path path) throws IOException {`

## State and fields

No simple top-level field declarations matched the source scan.

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
