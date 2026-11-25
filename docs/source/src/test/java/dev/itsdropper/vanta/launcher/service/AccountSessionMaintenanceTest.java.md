# `AccountSessionMaintenanceTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/launcher/service/AccountSessionMaintenanceTest.java`

## Overview

AccountSessionMaintenanceTest.java implements part of the service subsystem. Its responsibility and behavior are defined by the declarations and implementation summarized in this document.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: test / verification
- Top-level declaration: `class AccountSessionMaintenanceTest`
- Source size: 99 lines

## API surface

- `public long getExpireTimeMs() {`
- `public String getToken() {`
- `public String getUserHash() {`
- `public Map<String, String> getDisplayClaims() {`
- `public StepFullXblSession.FullXblSession getFullXblSession() {`
- `protected StepFullXblSession.FullXblSession prevResult() {`
- `public boolean isExpired() {`

## State and fields

- `private final boolean expired;`

## Dependencies

Imports: 10 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.account.Account`

## Test cases

- `missingSessionDoesNotTriggerBackgroundRefresh`
- `detectsExpiredUpstreamXboxTokenEvenWhenMinecraftTokenHasNotExpired`
- `doesNotRefreshWhenEntireTokenChainIsValid`

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
