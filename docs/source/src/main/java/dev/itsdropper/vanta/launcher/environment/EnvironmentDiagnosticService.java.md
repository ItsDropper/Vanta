# `EnvironmentDiagnosticService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/environment/EnvironmentDiagnosticService.java`

## Overview

EnvironmentDiagnosticService is part of the environment subsystem. Read-only environment inspection and portable lockfile capture/comparison. These services must not mutate instance files. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.environment`
- Role: production implementation
- Top-level declaration: `public final class EnvironmentDiagnosticService`
- Source size: 218 lines

## API surface

- `public record Diagnostic(String code, Severity severity, String message, boolean repairable) {`
- `public record Report(String instanceId, Instant checkedAt, List<Diagnostic> diagnostics) {`
- `public boolean isHealthy() {`
- `public long count(Severity severity) {`
- `public Report inspect(Instance instance) {`
- `public Report inspect(Instance instance, LaunchData launchData) {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 12 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.LaunchData`
- `dev.itsdropper.vanta.launcher.VersionReader`
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.launcher.model.VersionManifest`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
