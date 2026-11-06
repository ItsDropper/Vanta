package dev.itsdropper.vanta.ui;

import java.util.List;
import java.util.Locale;
import java.util.ResourceBundle;

/**
 * Central localization entry point.
 *
 * The configured locale is loaded once during application startup. UI code
 * should resolve user-facing strings through this class rather than reading
 * resource bundles or the process-wide default locale directly.
 */
public final class Localization {

    private static final String BUNDLE_BASE_NAME = "dev.itsdropper.vanta.i18n.messages";

    public record Language(String code, String nativeName, String englishName) {
        @Override
        public String toString() {
            return nativeName + " (" + englishName + ")";
        }
    }

    private static final List<Language> SUPPORTED_LANGUAGES = List.of(
            new Language("en", "English", "English"),
            new Language("fi", "Suomi", "Finnish"),
            new Language("sv", "Svenska", "Swedish"),
            new Language("de", "Deutsch", "German"),
            new Language("fr", "Français", "French"),
            new Language("es", "Español", "Spanish"),
            new Language("it", "Italiano", "Italian"),
            new Language("nl", "Nederlands", "Dutch"),
            new Language("pt-PT", "Português", "Portuguese")
    );

    private static volatile Locale activeLocale = Locale.ENGLISH;
    private static volatile ResourceBundle activeBundle =
            ResourceBundle.getBundle(BUNDLE_BASE_NAME, Locale.ENGLISH);

    private Localization() {
    }

    /** Loads the saved language before any launcher views are constructed. */
    public static void initializeFromSettings() {
        String code = LauncherSettings.getPreferredLanguageCode();
        Locale requested = Locale.forLanguageTag(code);
        if (!isSupported(code)) {
            requested = Locale.ENGLISH;
        }

        activeLocale = requested;
        activeBundle = ResourceBundle.getBundle(BUNDLE_BASE_NAME, requested);
    }

    public static String text(String key) {
        if (key == null || key.isBlank()) {
            return "";
        }
        try {
            return activeBundle.getString(key);
        } catch (java.util.MissingResourceException ignored) {
            return key;
        }
    }

    public static Locale getActiveLocale() {
        return activeLocale;
    }

    public static String getActiveLanguageCode() {
        return activeLocale.toLanguageTag();
    }

    public static List<Language> getSupportedLanguages() {
        return SUPPORTED_LANGUAGES;
    }

    public static boolean isSupported(String code) {
        return code != null && SUPPORTED_LANGUAGES.stream()
                .anyMatch(language -> language.code().equals(code));
    }
}
