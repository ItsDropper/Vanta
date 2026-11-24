# `ModrinthDependencyResolverGraphTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/service/ModrinthDependencyResolverGraphTest.java`

## Overview

ModrinthDependencyResolverGraphTest.java implements part of the service subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: test / verification
- Top-level declaration: `class ModrinthDependencyResolverGraphTest`
- Source size: 178 lines

## API surface

- `public List<ModrinthVersion> getVersions(String projectId) {`

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 7 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`

## Test cases

- `backtracksRootCandidateWhenItsDependencyViolatesAnotherRequiredRoot`
- `requiredDependencyVersionIsNotSilentlyReplacedByAnotherVersion`
- `unsatisfiableRequiredDependencyFailsInsteadOfReturningPartialGraph`
- `circularRequiredDependenciesTerminateAndReturnOneVersionPerProject`
- `unavailableOptionalDependencyDoesNotBlockRootInstall`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
