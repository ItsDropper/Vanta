# `messages_it.properties`

**Source:** `src/main/resources/dev/itsdropper/vanta/i18n/messages_it.properties`

## Overview

Italian localization bundle.

## Resource format and structure

Java properties bundle with 800 entries. Key sample: `language.title`, `language.description`, `language.accessibleLabel`, `language.tooltip`, `language.restartRequired`, `language.restartNotRequired`, `settings.label`, `settings.sectionAccessible`, `settings.searchPrompt`, `settings.searchAccessible`, `settings.searchTooltip`, `settings.group.personalization`, `settings.group.social`, `settings.group.launcher`, `settings.group.system`, `settings.section.appearance`, `settings.section.discord`, `settings.section.general`, `settings.section.minecraft`, `settings.section.downloads`, `settings.section.storage`, `settings.section.state`, `settings.section.diagnostics`, `settings.section.about`, `home.title`, `home.accountChecking`, `home.readyToPlay`, `home.newInstance`, ….

## Runtime and maintenance considerations

- Treat the repository path and filename as part of the resource contract.
- Keep localization keys aligned across language bundles and preserve Java-properties escaping rules.
- Treat trusted-key material as a security trust-store change requiring security review.
- Preserve font licensing notices and verify resource packaging when replacing binary assets.
