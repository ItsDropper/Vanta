# `version.properties`

**Source:** `src/main/resources/version.properties`

## Overview

Packaged application version resource read by update/version services; keep synchronized with release/build versioning.

## Resource format and structure

Java properties bundle with 1 entries. Key sample: `version`.

## Runtime and maintenance considerations

- Treat the repository path and filename as part of the resource contract.
- Keep localization keys aligned across language bundles and preserve Java-properties escaping rules.
- Treat trusted-key material as a security trust-store change requiring security review.
- Preserve font licensing notices and verify resource packaging when replacing binary assets.
