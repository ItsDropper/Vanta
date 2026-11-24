# `MinecraftLauncherTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/MinecraftLauncherTest.java`

## Overview

MinecraftLauncherTest.java implements part of the launcher subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher`
- Role: test / verification
- Top-level declaration: `class MinecraftLauncherTest`
- Source size: 90 lines

## API surface

No public/protected method declarations matched the source scan.

## State and fields

No simple top-level field declarations matched the source scan.

## Dependencies

Imports: 7 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Test cases

- `ensuresLaunchWrapperOptionExistsExactlyOnceAfterMainClass`
- `extractsRiftTweakersWithoutDroppingAuthenticationArgumentsFromNormalLaunchPath`
- `rejectsRiftProfileWithoutTweaker`
- `rejectsRiftTweakerWithoutClassName`
- `disablesVulnerableBase64IntrinsicForJava17AndNewerOnX64`
- `doesNotPassJava4ShellMitigationToJava8OrArmRuntimes`
- `rejectsMissingLaunchWrapperOptionValue`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
