# `Account.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/account/Account.java`

## Overview

Account is part of the account subsystem. Account persistence and identity models. The manager owns canonical account IDs, encrypted storage, migration and atomic writes; Account is the MinecraftAuth session wrapper. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.account`
- Role: production implementation
- Top-level declaration: `public class Account`
- Source size: 37 lines

## API surface

- `public StepFullJavaSession.FullJavaSession getSession() {`
- `public void setSession(StepFullJavaSession.FullJavaSession session) {`
- `public String getUsername() {`
- `public String getUuid() {`
- `public String getAccessToken() {`

## State and fields

- `private StepFullJavaSession.FullJavaSession session;`

## Dependencies

Imports: 1 distinct types.

Internal Vanta dependencies:
- No direct Vanta imports.

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
