# `release-trusted-keys.json`

**Source:** `src/main/resources/release-trusted-keys.json`

## Overview

Pinned release-signing trust store consumed by release manifest signature verification. Key IDs must map to trusted public keys; unknown signers are rejected.

## Resource format and structure

JSON configuration (163 characters). Parse and validate its exact schema before changing security-sensitive key material.

## Runtime and maintenance considerations

- Treat the repository path and filename as part of the resource contract.
- Keep localization keys aligned across language bundles and preserve Java-properties escaping rules.
- Treat trusted-key material as a security trust-store change requiring security review.
- Preserve font licensing notices and verify resource packaging when replacing binary assets.
