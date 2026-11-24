# `Instance.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/model/Instance.java`

## Overview

Instance is part of the model subsystem. Persistence/data-transfer models used by installation, launch, server history and version metadata. These classes should remain free of I/O orchestration. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.model`
- Role: production implementation
- Top-level declaration: `public class Instance`
- Source size: 114 lines

## API surface

- `public String getId() {`
- `public String getName() {`
- `public String getMinecraftVersion() {`
- `public String getLoader() {`
- `public String getLoaderVersion() {`
- `public Path getDirectory() {`
- `public String getIcon() {`
- `public Instance withIcon(String newIcon) {`
- `public String getDisplayLoader() {`
- `public String toString() {`

## State and fields

- `private final String id;`
- `private final String name;`
- `private final String minecraftVersion;`
- `private final String loader;`
- `private final String loaderVersion;`
- `private final Path directory;`
- `private final String icon;`

## Dependencies

Imports: 4 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
