# `UpdaterProcessExitTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/update/UpdaterProcessExitTest.java`

## Overview

Runs in a separate JVM because System.exit must never terminate Gradle's test worker. The non-daemon thread models a service thread that would keep the original launcher alive if only JavaFX were shut down.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.update`
- Role: test / verification
- Top-level declaration: `class UpdaterProcessExitTest`
- Source size: 108 lines

## API surface

- `public static void main(String[] args) {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 6 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Test cases

- `updaterHandoffTerminatesJvmEvenWhenNonDaemonThreadIsAlive`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
