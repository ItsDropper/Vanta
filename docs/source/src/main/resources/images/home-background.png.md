# `home-background.png`

**Source:** `src/main/resources/images/home-background.png`

## Overview

Raster home-screen background resource. It is a visual asset rather than executable logic.

## Resource format and structure

Binary PNG asset; payload was not decoded or modified. Preserve its path and filename.

## Runtime and maintenance considerations

- Treat the repository path and filename as part of the resource contract.
- Keep localization keys aligned across language bundles and preserve Java-properties escaping rules.
- Treat trusted-key material as a security trust-store change requiring security review.
- Preserve font licensing notices and verify resource packaging when replacing binary assets.
