# Localization

Vanta uses Java `ResourceBundle` property files for UI localization. The system is
intentionally independent of JavaFX controls so views do not own locale parsing,
fallback rules, or bundle loading.

## Supported locales

The initial catalog includes:

- English (`en`)
- Finnish (`fi`)
- Swedish (`sv`)
- German (`de`)
- French (`fr`)
- Spanish (`es`)
- Italian (`it`)
- Dutch (`nl`)
- Portuguese (Portugal, `pt-PT`)

Locale identifiers use BCP 47 language tags. ResourceBundle file names use Java's
locale suffix convention; for example, `pt-PT` maps to
`messages_pt_PT.properties`.

## Architecture

- `LauncherSettings` persists the user's preferred language in the existing local
  `settings.properties` file.
- `Localization.initializeFromSettings()` loads the selected bundle once during
  application startup, before launcher views are constructed.
- `Localization.text(key)` is the single lookup entry point for UI code.
- `LanguagePreferenceControl` is shared by Overview and Settings → General.
- Settings navigation labels, category headings, compact navigation, and search
  affordances use localized resource keys while retaining stable internal section IDs.
- Changing the preference never mutates the active bundle in a running scene.
  The preference is saved immediately and the UI clearly reports that a restart
  is required.
- Missing keys fall back through the root English bundle. Unknown keys are returned
  as their key names, making missing translations visible during development.

## Adding a language

1. Add a `Localization.Language` entry with native and English names. 
3. Add `messages_<locale>.properties` under
   `src/main/resources/dev/itsdropper/vanta/i18n/`.
4. Add the locale to `LocalizationTest` coverage through the supported-language
   catalog; the test verifies all required language-preference and Settings-navigation
   strings exist.
5. Translate UI strings by stable semantic keys, not by using English UI text as
   a lookup key.

## Translation rules

- Use semantic keys such as `settings.general.language.title`, not component
  names or screen coordinates.
- Keep user-facing text out of business logic.
- Use `MessageFormat` for parameterized messages; do not concatenate translated
  sentence fragments.
- Preserve placeholders and formatting tokens across every locale.
- Keep product names, Minecraft loader names, file paths, command names, and
  user-provided content unchanged unless a product-specific translation is
  officially defined.
- Avoid machine-translating technical terms without review by a fluent speaker.

The initial increments localize the language preference component and Settings
navigation, and establish the catalog and persistence lifecycle. Settings page
content and the remaining launcher surfaces are still English and must be migrated
in separate, reviewable batches; this is not yet full-launcher localization.
