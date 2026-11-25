# `InstanceSettings.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/model/InstanceSettings.java`

## Overview

InstanceSettings is part of the model subsystem. Persistence/data-transfer models used by installation, launch, server history and version metadata. These classes should remain free of I/O orchestration. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.model`
- Role: production implementation
- Top-level declaration: `public class InstanceSettings`
- Source size: 151 lines

## API surface

- `public int getRamMb() {`
- `public void setRamMb(int ramMb) {`
- `public String getJavaArguments() {`
- `public void setJavaArguments(String javaArguments) {`
- `public String getGameArguments() {`
- `public void setGameArguments(String gameArguments) {`
- `public int getWidth() {`
- `public void setWidth(int width) {`
- `public int getHeight() {`
- `public void setHeight(int height) {`
- `public boolean isFullscreen() {`
- `public void setFullscreen(boolean fullscreen) {`
- `public String getJavaPath() {`
- `public void setJavaPath(String javaPath) {`

## State and fields

- `private int ramMb;`
- `private String javaArguments;`
- `private String gameArguments;`
- `private int width;`
- `private int height;`
- `private boolean fullscreen;`
- `private String javaPath;`

## Dependencies

Imports: 2 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
