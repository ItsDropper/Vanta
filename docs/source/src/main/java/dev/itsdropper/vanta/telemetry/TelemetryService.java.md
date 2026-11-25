# `TelemetryService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/telemetry/TelemetryService.java`

## Overview

Optional telemetry and crash-report delivery boundary. It controls event allowlisting, crash extraction, sanitization and bounded HTTP submission.

## Package and type

- Package: `dev.itsdropper.vanta.telemetry`
- Role: production implementation
- Top-level declaration: `public final class TelemetryService`
- Source size: 355 lines

## API surface

- `public static void track(String eventName) {`
- `public static CompletableFuture<Integer> testUsageDelivery() {`
- `public static CompletableFuture<Integer> testCrashDelivery() {`
- `public static void reportCrash(Throwable error) {`
- `public static void installUncaughtExceptionHandler() {`

## State and fields

- `private static volatile Thread.UncaughtExceptionHandler telemetryDefaultHandler;`

## Dependencies

Imports: 23 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.ui.LauncherSettings`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
