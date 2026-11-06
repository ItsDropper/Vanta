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
    void everySupportedLanguageHasRequiredUiStrings() {
        Set<String> requiredKeys = Set.of(
                "language.title",
                "language.description",
                "language.accessibleLabel",
                "language.tooltip",
                "language.restartRequired",
                "language.restartNotRequired",
                "settings.label",
                "settings.sectionAccessible",
                "settings.searchPrompt",
                "settings.searchAccessible",
                "settings.searchTooltip",
                "settings.group.personalization",
                "settings.group.social",
                "settings.group.launcher",
                "settings.group.system",
                "settings.section.appearance",
                "settings.section.discord",
                "settings.section.general",
                "settings.section.minecraft",
                "settings.section.downloads",
                "settings.section.storage",
                "settings.section.state",
                "settings.section.diagnostics",
                "settings.section.about",
                "home.title",
                "home.accountChecking",
                "home.readyToPlay",
                "home.newInstance",
                "home.createInstanceAccessible",
                "home.createInstanceTooltip",
                "home.featuredEyebrow",
                "home.featuredSubtitle",
                "home.libraryLoading",
                "home.recentTitle",
                "home.recentSubtitle",
                "home.instancesLoading",
                "home.serversTitle",
                "home.serversSubtitle",
                "home.noServerHistory",
                "accountCard.profile",
                "accountCard.none",
                "accountCard.disconnected",
                "accountCard.connected",
                "accountCard.unavailable",
                "accountCard.authError",
                "accountSwitcher.accessible",
                "accountSwitcher.tooltip",
                "accountSwitcher.signedOut",
                "accountSwitcher.menuSignedOut",
                "accountSwitcher.accounts",
                "accountSwitcher.add",
                "accountSwitcher.addAccessible",
                "accountSwitcher.addTooltip",
                "accountSwitcher.active",
                "accountSwitcher.menuSignedIn",
                "accountSwitcher.menuSwitchHint",
                "accountSwitcher.currentPrefix",
                "accountSwitcher.switchPrefix",
                "accountSwitcher.currentSuffix",
                "accountSwitcher.switchToPrefix",
                "commandPalette.empty",
                "commandPalette.eyebrow",
                "commandPalette.title",
                "commandPalette.prompt",
                "commandPalette.accessible",
                "commandPalette.results",
                "loading.initializing",
                "loading.brand",
                "loading.environmentManager",
                "login.close",
                "login.title",
                "login.subtitle",
                "login.step",
                "login.instruction",
                "login.codeAccessible",
                "login.copy",
                "login.copyAccessible",
                "login.copied",
                "login.open",
                "login.openAccessible",
                "login.waiting",
                "login.statusAccessible",
                "titleBar.updateAvailable",
                "titleBar.minimizeAccessible",
                "titleBar.maximizeAccessible",
                "titleBar.exitAccessible",
                "titleBar.minimize",
                "titleBar.maximize",
                "titleBar.update",
                "accounts.add",
                "accounts.refresh",
                "accounts.signOut",
                "accounts.title",
                "accounts.description",
                "accounts.activeHeading",
                "accounts.listHeading",
                "accounts.privacy",
                "accounts.none",
                "accounts.notConnected",
                "accounts.connectPrompt",
                "accounts.connected",
                "accounts.uuidUnavailable",
                "accounts.active",
                "accounts.saved",
                "accounts.use",
                "accounts.selectedForLaunch",
                "accounts.emptyTitle",
                "accounts.emptyDescription",
                "accounts.connect",
                "accounts.connecting",
                "accounts.refreshing",
                "accounts.signingOut",
                "accounts.working",
                "createType.title",
                "common.back",
                "preset.title",
                "preset.description",
                "presetConfig.title",
                "presetConfig.description",
                "presetConfig.environment",
                "presetConfig.minecraftVersion",
                "presetConfig.loader",
                "presetConfig.includedMods",
                "presetConfig.noMods",
                "presetConfig.instanceName",
                "presetConfig.nameExample",
                "common.cancel",
                "common.createInstance",
                "common.browseModpacks",
                "onboarding.next",
                "onboarding.connectMicrosoft",
                "onboarding.usageOptIn",
                "onboarding.crashOptIn",
                "onboarding.changeLater",
                "onboarding.stepPrefix",
                "onboarding.stepSuffix",
                "onboarding.finish",
                "onboarding.welcomeTitle",
                "onboarding.welcomeSubtitle",
                "onboarding.overview",
                "onboarding.organizationTitle",
                "onboarding.organizationBody",
                "onboarding.instanceTip",
                "onboarding.accountTitle",
                "onboarding.accountBody",
                "onboarding.noAccount",
                "onboarding.connectedAs",
                "onboarding.connectPrompt",
                "onboarding.accountConnected",
                "onboarding.skipAccount",
                "onboarding.readyTitle",
                "onboarding.readyBody",
                "onboarding.privacyTitle",
                "onboarding.privacyUsage",
                "onboarding.privacyCrash",
                "onboarding.privacyWarning",
                "onboarding.privacyNote",
                "onboarding.createInstance",
                "onboarding.explore",
                "onboarding.waiting",
                "onboarding.connectErrorPrefix",
                "common.retry"
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
