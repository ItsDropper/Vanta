# `DownloadCenterService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/download/DownloadCenterService.java`

## Overview

DownloadCenterService is part of the download subsystem. Observable in-memory transfer task lifecycle. Download execution remains in DownloadUtil; this package publishes progress/state to listeners. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.download`
- Role: production implementation
- Top-level declaration: `public final class DownloadCenterService`
- Source size: 161 lines

## API surface

- `public double progress() {`
- `public synchronized List<Task> snapshot() {`
- `public void addListener(Consumer<List<Task>> listener) {`
- `public synchronized long begin(String url, Path target, String sha1) {`
- `public synchronized void progress(long id, long received, long total) {`
- `public synchronized void verifying(long id, long received, long total) {`
- `public synchronized void complete(long id, long size) {`
- `public synchronized void fail(long id, Throwable failure) {`
- `public synchronized void clearFinished() {`
- `public synchronized boolean isRetryRequested(long taskId) {`
- `public synchronized void retry(Task task) {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 14 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.ui.async.UiExecutor`
- `dev.itsdropper.vanta.launcher.instance.DownloadUtil`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
