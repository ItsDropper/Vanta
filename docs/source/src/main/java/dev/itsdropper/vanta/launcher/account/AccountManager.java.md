# `AccountManager.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/account/AccountManager.java`

## Overview

AccountManager is part of the account subsystem. Account persistence and identity models. The manager owns canonical account IDs, encrypted storage, migration and atomic writes; Account is the MinecraftAuth session wrapper. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.account`
- Role: production implementation
- Top-level declaration: `public class AccountManager`
- Source size: 556 lines

## API surface

- `public static List<Account> loadAccounts()`
- `public static String getActiveAccountId()`
- `public static void clearAccounts()`

## State and fields

- `private static final Path ACCOUNTS_DIRECTORY =`
- `private static final Path ACTIVE_ACCOUNT_FILE =`
- `private static final Path LEGACY_FILE =`

## Dependencies

Imports: 17 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`
- `dev.itsdropper.vanta.launcher.security.CredentialManager`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
