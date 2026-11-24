# `AccountSkinService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/service/AccountSkinService.java`

## Overview

AccountSkinService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: production implementation
- Top-level declaration: `public class AccountSkinService`
- Source size: 350 lines

## API surface

- `public static void clearAll() {`

## State and fields

- `private static final String PROFILE_URL =`
- `private static final HttpClient HTTP =`
- `private static final Map<UUID, Image> SKIN_CACHE =`
- `private static final Map<UUID, String> URL_CACHE =`

## Dependencies

Imports: 16 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.ui.async.UiExecutor`
- `dev.itsdropper.vanta.launcher.account.Account`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
