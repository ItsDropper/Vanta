# `CrashCaseRepository.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/repair/CrashCaseRepository.java`

## Overview

CrashCaseRepository is part of the repair subsystem. Local crash evidence, diagnosis, confidence and rollback policy. Classification is advisory; only the coordinator may authorize managed repair. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.repair`
- Role: production implementation
- Top-level declaration: `class performs`
- Source size: 502 lines

## API surface

- `public List<CrashCase> listCases() throws IOException {`
- `public int pendingCount() throws IOException {`
- `public List<VerifiedFix> findVerifiedFixes(FailureCategory category, int limit) throws IOException {`
- `public CrashCase recordForReview(String log, FailureCategory proposedCategory) throws IOException {`
- `public CrashCase importLog(Path source) throws IOException {`
- `public CrashCase confirm(String id, FailureCategory category) throws IOException {`
- `public CrashCase confirm(String id, FailureCategory category, String verifiedFix) throws IOException {`
- `public CrashCase reject(String id) throws IOException {`
- `public CrashCase correctConfirmed(String id, FailureCategory category, String verifiedFix) throws IOException {`
- `public CrashCase reopenRejected(String id) throws IOException {`
- `public int exportConfirmedCorpus(Path destination) throws IOException {`
- `public static String sanitize(String source) {`
- `public static String fingerprint(String sanitizedLog) {`
- `public int exportVerifiedFixes(Path destination) throws IOException {`

## State and fields

- `private final Path storeFile;`
- `private final Path lockFile;`

## Dependencies

Imports: 25 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
