package dev.itsdropper.vanta.ui.legal;

import dev.itsdropper.vanta.ui.LauncherSettings;

import java.time.Instant;

/** Records which versions of the Terms and Privacy Policy the user has accepted. */
public final class LegalAcceptance {

    private LegalAcceptance() {
    }

    /** True when the stored acceptance matches the versions bundled with this build. */
    public static boolean isCurrent() {
        return isCurrent(
                LauncherSettings.getAcceptedTermsVersion(),
                LauncherSettings.getAcceptedPrivacyVersion(),
                LegalDocuments.terms().version(),
                LegalDocuments.privacy().version());
    }

    static boolean isCurrent(String acceptedTerms, String acceptedPrivacy, String terms, String privacy) {
        return !terms.isBlank() && terms.equals(acceptedTerms)
                && !privacy.isBlank() && privacy.equals(acceptedPrivacy);
    }

    /** True when the user accepted an earlier version, so the prompt should say the documents were updated. */
    public static boolean wasAcceptedBefore() {
        return !LauncherSettings.getAcceptedTermsVersion().isBlank()
                || !LauncherSettings.getAcceptedPrivacyVersion().isBlank();
    }

    public static void accept() {
        LauncherSettings.recordLegalAcceptance(
                LegalDocuments.terms().version(),
                LegalDocuments.privacy().version(),
                Instant.now().toString());
    }
}
