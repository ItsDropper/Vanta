# `MultiLaunchService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/service/MultiLaunchService.java`

## Overview

MultiLaunchService is part of the service subsystem. Application services coordinating accounts, launch, Modrinth, usage, history, Discord presence and multi-instance lifecycle. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.service`
- Role: production implementation
- Top-level declaration: `public final class MultiLaunchService`
- Source size: 207 lines

## API surface

- `public void addStateListener(Consumer<LaunchService.LaunchState> listener) {`
- `public void removeStateListener(Consumer<LaunchService.LaunchState> listener) {`
- `public Process launch(Instance instance) throws Exception {`
- `public void close() {`
- `public void close(Instance instance) {`
- `public long getSessionPlaytimeSeconds(Instance instance) {`
- `public boolean isRunning(Instance instance) {`
- `public LaunchService.LaunchState getState(Instance instance) {`
- `public synchronized LaunchService.LaunchState getState() {`
- `public Instance getRunningInstance() {`
- `public ServerTarget getRunningServerTarget() {`
- `public LaunchFailure getLastFailure() {`
- `public Instance getFailedInstance() {`
- `public String getRecentOutput() {`
- `public Process getProcess() {`

## State and fields

- `private final AccountService accountService;`
- `private final CopyOnWriteArrayList<Consumer<LaunchService.LaunchState>> listeners =`
- `private volatile Instance latestInstance;`
- `private volatile LaunchFailure lastFailure;`
- `private volatile Instance failedInstance;`

## Dependencies

Imports: 5 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.model.Instance`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
