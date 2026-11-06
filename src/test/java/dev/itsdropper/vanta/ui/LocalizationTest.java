package dev.itsdropper.vanta.ui;

import org.junit.jupiter.api.Test;

import java.util.Locale;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class LocalizationTest {

    private static final String BUNDLE = "dev.itsdropper.vanta.i18n.messages";

    @Test
    void supportedLanguagesHaveUniqueCodesAndNames() {
        var languages = Localization.getSupportedLanguages();

        assertEquals(9, languages.size());
        assertEquals(
                languages.size(),
                languages.stream().map(Localization.Language::code).collect(Collectors.toSet()).size()
        );
        assertTrue(languages.stream().allMatch(language ->
                !language.nativeName().isBlank() && !language.englishName().isBlank()
        ));
    }

    @Test
    void everySupportedLanguageHasTheLanguagePreferenceStrings() {
        Set<String> requiredKeys = Set.of(
                "language.title",
                "language.description",
                "language.accessibleLabel",
                "language.tooltip",
                "language.restartRequired",
                "language.restartNotRequired"
        );

        for (Localization.Language language : Localization.getSupportedLanguages()) {
            Locale requestedLocale = Locale.forLanguageTag(language.code());
            ResourceBundle bundle = ResourceBundle.getBundle(BUNDLE, requestedLocale);
            assertEquals(requestedLocale, bundle.getLocale(),
                    language.code() + " unexpectedly fell back to another locale");
            for (String key : requiredKeys) {
                assertTrue(bundle.containsKey(key), language.code() + " is missing " + key);
                assertFalse(bundle.getString(key).isBlank(), language.code() + " has blank " + key);
            }
        }
    }

    @Test
    void unsupportedLanguageCodesAreRejectedByCatalog() {
        assertFalse(Localization.isSupported("xx"));
        assertFalse(Localization.isSupported("fi-FI"));
        assertTrue(Localization.isSupported("fi"));
        assertTrue(Localization.isSupported("pt-PT"));
    }
}
