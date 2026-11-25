# `LaunchPerformanceTracker.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/service/LaunchPerformanceTracker.java`

## Overview

LaunchPerformanceTracker is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: production implementation
- Top-level declaration: `public final class LaunchPerformanceTracker`
- Source size: 116 lines

## API surface

- `public static synchronized void record(Instance instance, boolean optimized, long durationNanos) {`
- `public static synchronized void recordProcessStartup(Instance instance, boolean optimized, long durationNanos) {`
- `public static synchronized Summary summaryForMostRecent(boolean modded) {`
- `public static synchronized Summary summaryForInstance(String instanceId) {`
- `public static synchronized void clear() {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 6 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
