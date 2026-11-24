# `AccountService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/service/AccountService.java`

## Overview

AccountService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: production implementation
- Top-level declaration: `public class AccountService`
- Source size: 669 lines

## API surface

- `public Account loadAccount() throws Exception {`
- `public Account login() throws Exception {`
- `public List<Account> getAccounts() {`
- `public Account getCurrentAccount() {`
- `public Account ensureFreshSession(String expectedAccountId) throws Exception {`
- `public void close() {`
- `public SessionHealth getSessionHealth() {`
- `public String getSessionHealthMessage() {`
- `public long getLastSuccessfulRefreshAt() {`
- `public void addSessionHealthListener(Runnable listener) {`
- `public void removeSessionHealthListener(Runnable listener) {`
- `public Account refreshSessionNow() throws Exception {`
- `public void logout() throws IOException {`

## State and fields

- `private volatile long lastSuccessfulRefreshAt;`
- `private volatile Account currentAccount;`
- `private final List<Account> accounts =`
- `private final ReentrantLock accountLock =`
- `private final List<AccountListener> listeners =`
- `private final ScheduledExecutorService sessionMaintenance =`
- `private ScheduledFuture<?> maintenanceTask;`
- `private String maintenancePausedAccountId;`
- `private volatile boolean closed;`
- `private long maintenanceRetryAfterMillis;`

## Dependencies

Imports: 12 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.account.Account`
- `dev.itsdropper.vanta.launcher.account.AccountManager`
- `dev.itsdropper.vanta.launcher.auth.AuthManager`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
