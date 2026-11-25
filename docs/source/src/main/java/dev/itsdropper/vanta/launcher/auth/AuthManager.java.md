# `AuthManager.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/auth/AuthManager.java`

## Overview

AuthManager is part of the auth subsystem. Microsoft device-code OAuth and token-chain refresh. Preserve the distinction between explicit credential rejection, transient network failures and user cancellation. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.auth`
- Role: production implementation
- Top-level declaration: `public class AuthManager`
- Source size: 186 lines

## API surface

- `public static Account refresh(Account account) throws Exception {`
- `public static Account login(Account savedAccount) throws Exception {`
- `public static boolean isRefreshTokenRejected(Throwable failure) {`
- `public static boolean isInterruptedFailure(Throwable failure) {`
- `public static boolean isTransientFailure(Throwable failure) {`

## State and fields

- `private static final AbstractStep<?, StepFullJavaSession.FullJavaSession> LOGIN =`

## Dependencies

Imports: 20 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.account.Account`
- `dev.itsdropper.vanta.launcher.account.AccountManager`
- `dev.itsdropper.vanta.ui.LoginPopup`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
