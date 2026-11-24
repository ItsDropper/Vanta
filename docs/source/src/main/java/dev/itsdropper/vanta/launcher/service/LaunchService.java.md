# `LaunchService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/service/LaunchService.java`

## Overview

LaunchService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: production implementation
- Top-level declaration: `public class LaunchService`
- Source size: 995 lines

## API surface

- `public synchronized LaunchFailure getLastFailure() {`
- `public synchronized Instance getFailedInstance() {`
- `public String getRecentOutput() {`
- `public synchronized void close() {`
- `public synchronized boolean isRunning() {`
- `public synchronized Process getProcess() {`
- `public synchronized Instance getRunningInstance() {`
- `public synchronized ServerTarget getRunningServerTarget() {`
- `public synchronized LaunchState getState() {`

## State and fields

- `private static final Pattern SERVER_CONNECTION_PATTERN =`
- `private final AccountService accountService;`
- `private final List<Consumer<LaunchState>> stateListeners =`
- `private Process minecraftProcess;`
- `private Instance runningInstance;`
- `private ServerTarget runningServerTarget;`
- `private Instance failedInstance;`
- `private LaunchState state =`
- `private final StringBuilder outputBuffer =`
- `private LaunchFailure lastFailure;`
- `private long launchStartedAtMillis;`
- `private final LaunchHistoryRepository launchHistoryRepository;`
- `private String launchHistoryId;`

## Dependencies

Imports: 26 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.LaunchData`
- `dev.itsdropper.vanta.launcher.LaunchDataBuilder`
- `dev.itsdropper.vanta.launcher.MinecraftLauncher`
- `dev.itsdropper.vanta.launcher.account.Account`
- `dev.itsdropper.vanta.launcher.model.Instance`
- `dev.itsdropper.vanta.launcher.repair.FailureCategory`
- `dev.itsdropper.vanta.launcher.repair.RepairDiagnosis`
- `dev.itsdropper.vanta.launcher.repair.RepairDiagnosticEngine`
- `dev.itsdropper.vanta.ui.views.RepairView`
- `dev.itsdropper.vanta.telemetry.TelemetryService`
- `dev.itsdropper.vanta.ui.LauncherSettings`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
