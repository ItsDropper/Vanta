# `TelemetryServiceTest.java`

**Source:** `src/test/java/dev/itsdropper/vanta/telemetry/TelemetryServiceTest.java`

## Overview

Test class for TelemetryService.

## Test cases

- `onlyExplicitlySupportedUsageEventsCanBeTracked`
- `crashTypeSanitizationRemovesMessagesAndUnsafeCharacters`
- `crashTextSanitizationRemovesCommonPrivateValues`
- `crashTextSanitizationEnforcesLengthLimit`

## Dependencies

- `org.junit.jupiter.api.Test`
- `org.junit.jupiter.api.Assertions.assertEquals`
- `org.junit.jupiter.api.Assertions.assertFalse`
- `org.junit.jupiter.api.Assertions.assertTrue`

## Maintenance

Treat assertions and fixtures in this file as executable regression specifications. Update tests when observable behavior changes; do not weaken security or failure-path assertions merely to accommodate an implementation change.
