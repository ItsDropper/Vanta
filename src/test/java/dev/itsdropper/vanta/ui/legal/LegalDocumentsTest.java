package dev.itsdropper.vanta.ui.legal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LegalDocumentsTest {

    @Test
    void bundledDocumentsHaveVersions() {
        assertFalse(LegalDocuments.terms().version().isBlank());
        assertFalse(LegalDocuments.privacy().version().isBlank());
    }

    @Test
    void bundledCopiesMatchTheFilesInTheRepositoryRoot() throws IOException {
        assertEquals(Files.readString(Path.of("TERMS.md"), StandardCharsets.UTF_8),
                LegalDocuments.terms().markdown());
        assertEquals(Files.readString(Path.of("PRIVACY.md"), StandardCharsets.UTF_8),
                LegalDocuments.privacy().markdown());
    }

    @Test
    void versionIsTheLastUpdatedDate() {
        assertEquals("1 January 2030", LegalDocuments.parse("# T\n\n**Last updated:** 1 January 2030\n").version());
    }

    @Test
    void aDocumentWithoutADateIsRejected() {
        assertThrows(IllegalStateException.class, () -> LegalDocuments.parse("# No date here"));
    }

    @Test
    void acceptanceMustMatchBothVersions() {
        assertTrue(LegalAcceptance.isCurrent("a", "b", "a", "b"));
        assertFalse(LegalAcceptance.isCurrent("a", "old", "a", "b"));
        assertFalse(LegalAcceptance.isCurrent("old", "b", "a", "b"));
        assertFalse(LegalAcceptance.isCurrent("", "", "a", "b"));
    }
}
