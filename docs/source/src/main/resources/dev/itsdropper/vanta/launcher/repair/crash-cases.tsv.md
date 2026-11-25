# `crash-cases.tsv`

**Source:** `src/main/resources/dev/itsdropper/vanta/launcher/repair/crash-cases.tsv`

## Overview

Versioned tab-separated labelled crash corpus loaded by DeterministicCrashModel. Labels/categories and sanitized examples are training data; edits should preserve schema, balance, deduplication and privacy checks enforced by CrashCorpusQualityTest.

## Resource format and structure

Tab-separated data with 67 non-empty rows. Header: `# Label<TAB>labelled crash-log case. Keep examples factual and independently labelled.`.

## Runtime and maintenance considerations

- Treat the repository path and filename as part of the resource contract.
- Keep localization keys aligned across language bundles and preserve Java-properties escaping rules.
- Treat trusted-key material as a security trust-store change requiring security review.
- Preserve font licensing notices and verify resource packaging when replacing binary assets.
