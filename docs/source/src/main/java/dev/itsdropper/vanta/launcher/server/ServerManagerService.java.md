# `ServerManagerService.java`

**Source:** `src/main/java/dev/itsdropper/vanta/launcher/server/ServerManagerService.java`

## Overview

ServerManagerService is part of the server subsystem. Local server process lifecycle, RCON protocol and per-server Java runtime management. Process output and backups remain local. Its concrete public operations and data contracts are defined in the adjacent source file; keep this reference aligned with those declarations when changing the implementation.

## Package and type

- Package: `dev.itsdropper.vanta.launcher.server`
- Role: production implementation
- Top-level declaration: `public final class ServerManagerService`
- Source size: 501 lines

## API surface

- `public synchronized List<Profile> profiles() {`
- `public synchronized Optional<Profile> profile(String id) {`
- `public void setConsoleListener(BiConsumer<String, String> listener) {`
- `public void setStateListener(BiConsumer<String, Snapshot> listener) {`
- `public synchronized Profile addProfile(String name, Path directory, Path jar) throws IOException {`
- `public synchronized void updateProfile(Profile edited) throws IOException {`
- `public synchronized void removeProfile(String id) throws IOException {`
- `public Snapshot snapshot(String id) {`
- `public void start(String id) throws IOException {`
- `public void sendCommand(String id, String command) throws IOException {`
- `public void stop(String id) throws IOException {`
- `public void forceStop(String id) {`
- `public Path createBackup(String id) throws IOException {`
- `public Path restoreBackup(String id, Path backup) throws IOException {`
- `public List<Path> crashReports(String id) throws IOException {`
- `public Properties readServerProperties(String id) throws IOException {`
- `public void saveServerProperties(String id, Properties properties) throws IOException {`
- `public String latestCrashSummary(String id) throws IOException {`

## State and fields

- `public String id;`
- `public String name;`
- `public String directory;`
- `public String jar;`
- `public boolean eulaAccepted;`
- `private final Path configFile;`

## Dependencies

Imports: 14 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.launcher.MinecraftLocator`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
