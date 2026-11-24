# `LaunchHistoryRepository.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/service/LaunchHistoryRepository.java`

## Overview

LaunchHistoryRepository is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: production implementation
- Top-level declaration: `public final class LaunchHistoryRepository`
- Source size: 472 lines

## API surface

- `public record Event(long timestampEpochMillis, Outcome outcome, String message) {`
- `public long durationMillis() {`
- `public boolean terminal() {`
- `public String begin(Instance instance) throws IOException {`
- `public LaunchRecord transition(String id, Outcome outcome, String message) throws IOException {`
- `public LaunchRecord recordActivity(Instance instance, Outcome outcome, String message) throws IOException {`
- `public List<LaunchRecord> list() throws IOException {`
- `public LaunchRecord find(String id) throws IOException {`
- `public static String sanitizeEvidence(String input) {`

## State and fields

- `private final Path storeFile;`
- `private final Path lockFile;`

## Dependencies

Imports: 29 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.launcher.repair.CrashCaseRepository`
- `dev.itsdropper.vanta.launcher.repair.FailureCategory`
- `dev.itsdropper.vanta.launcher.repair.RepairDiagnosis`
- `dev.itsdropper.vanta.launcher.repair.RepairDiagnosticEngine`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
