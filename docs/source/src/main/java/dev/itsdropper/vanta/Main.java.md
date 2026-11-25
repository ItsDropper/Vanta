# `Main.java`

**Source:** `src/main/java/dev/itsdropper/vanta/Main.java`

## Overview

JavaFX application entry point and process-mode dispatcher. Startup performs update transaction recovery before JavaFX, routes updater and cleanup arguments to dedicated entry points, initializes security policy and localization, then transitions from a minimum-duration loading screen to LauncherView only after asynchronous view readiness. Screenshot capture is a separate undecorated path and skips normal telemetry setup.

## Package and type

- Package: `dev.itsdropper.vanta`
- Role: production implementation
- Top-level declaration: `public class Main`

## API surface

- `public void start(Stage stage) {`
- `public static void main(String[] args) {`

## Dependencies

Imports: 15 distinct types.

Internal Vanta dependencies:
- `dev.itsdropper.vanta.ui.LauncherView`
- `dev.itsdropper.vanta.ui.LoadingScreen`
- `dev.itsdropper.vanta.ui.ThemeManager`
- `dev.itsdropper.vanta.ui.Localization`
- `dev.itsdropper.vanta.telemetry.TelemetryService`
- `dev.itsdropper.vanta.security.RuntimeSecurityPolicy`

## Implementation considerations

- Keep side effects and validation within the existing component boundary.
- Preserve observable return values, exception behavior, nullability and ordering relied on by callers.
- Update the closest tests when changing externally observable behavior.

## Documentation maintenance

The checked-in source and tests are authoritative. Update this document when responsibility, API, invariants or dependency direction changes.
