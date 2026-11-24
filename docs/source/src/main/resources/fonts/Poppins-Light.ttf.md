# `Poppins-Light.ttf`

**Source:** `src/main/resources/fonts/Poppins-Light.ttf`

## Overview

Bundled Poppins Light font face used by the launcher font registration layer.

## Resource format and structure

Binary TTF asset; payload was not decoded or modified. Preserve its path and filename.

## Runtime and maintenance considerations

- Treat the repository path and filename as part of the resource contract.
- Keep localization keys aligned across language bundles and preserve Java-properties escaping rules.
- Treat trusted-key material as a security trust-store change requiring security review.
- Preserve font licensing notices and verify resource packaging when replacing binary assets.
