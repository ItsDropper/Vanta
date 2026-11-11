package dev.itsdropper.vanta.launcher.repair;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CrashCaseRepositoryTest {
    @TempDir Path temporaryDirectory;

    @Test
    void storesOnlySanitizedCasesAndGroupsDuplicates() throws Exception {
        Path store = temporaryDirectory.resolve("cases.json");
        CrashCaseRepository repository = new CrashCaseRepository(store);
        String log = "java.lang.RuntimeException: failed for user.name=Alice "
                + "at C:\\Users\\Alice\\AppData\\Vanta\\launcher.log "
                + "access_token=secret-value from 192.168.1.22";

        var first = repository.recordForReview(log, FailureCategory.MOD_CRASH);
        var duplicate = repository.recordForReview(log, FailureCategory.MOD_CRASH);

        assertEquals(first.id(), duplicate.id());
        assertEquals(2, duplicate.occurrences());
        assertFalse(duplicate.sanitizedLog().contains("Alice"));
        assertFalse(duplicate.sanitizedLog().contains("secret-value"));
        assertFalse(duplicate.sanitizedLog().contains("192.168.1.22"));
        assertEquals(1, repository.pendingCount());
        assertTrue(Files.exists(store));
    }

    @Test
    void onlyConfirmedCasesAreExportedAsTrainingRows() throws Exception {
        CrashCaseRepository repository = new CrashCaseRepository(temporaryDirectory.resolve("cases.json"));
        var pending = repository.recordForReview(
                "org.spongepowered.asm.mixin.transformer.throwables.MixinApplyError target method missing",
                FailureCategory.MOD_CRASH);
        var rejected = repository.recordForReview(
                "java.lang.IllegalStateException: unrelated application text",
                FailureCategory.UNKNOWN);
        repository.confirm(pending.id(), FailureCategory.MOD_MIXIN_FAILURE);
        repository.reject(rejected.id());

        Path export = temporaryDirectory.resolve("reviewed-cases.tsv");
        assertEquals(1, repository.exportConfirmedCorpus(export));
        String content = Files.readString(export);
        assertTrue(content.startsWith("# Reviewed Vanta crash cases"));
        assertTrue(content.contains("MOD_MIXIN_FAILURE\t"));
        assertFalse(content.contains("UNKNOWN\t"));
    }

    @Test
    void rejectsInvalidTransitionsAndUnknownLabels() throws Exception {
        CrashCaseRepository repository = new CrashCaseRepository(temporaryDirectory.resolve("cases.json"));
        var item = repository.recordForReview("java.lang.IllegalStateException: broken startup", null);
        assertThrows(IllegalArgumentException.class,
                () -> repository.confirm(item.id(), FailureCategory.UNKNOWN));
        repository.reject(item.id());
        assertThrows(IllegalStateException.class,
                () -> repository.confirm(item.id(), FailureCategory.MOD_CRASH));
        assertThrows(IllegalArgumentException.class,
                () -> repository.reject("missing"));
    }

    @Test
    void rejectsOversizedImportsAndCorruptStores() throws Exception {
        CrashCaseRepository repository = new CrashCaseRepository(temporaryDirectory.resolve("cases.json"));
        Path oversized = temporaryDirectory.resolve("large.log");
        Files.write(oversized, new byte[2 * 1024 * 1024 + 1]);
        assertThrows(IOException.class, () -> repository.importLog(oversized));

        Path corrupt = temporaryDirectory.resolve("corrupt.json");
        Files.writeString(corrupt, "{not-json");
        assertThrows(IOException.class, () -> new CrashCaseRepository(corrupt).listCases());
    }

    @Test
    void fingerprintIgnoresTimestampNoiseButPreservesDifferentCauses() {
        String a = CrashCaseRepository.sanitize("2026-11-11 10:00:00 java.lang.IllegalStateException: broken");
        String b = CrashCaseRepository.sanitize("2026-11-12 11:30:00 java.lang.IllegalStateException: broken");
        String c = CrashCaseRepository.sanitize("2026-11-12 11:30:00 java.lang.IllegalArgumentException: broken");
        assertEquals(CrashCaseRepository.fingerprint(a), CrashCaseRepository.fingerprint(b));
        assertNotEquals(CrashCaseRepository.fingerprint(a), CrashCaseRepository.fingerprint(c));
    }

    @Test
    void sanitizerRedactsTokensIpsUuidsPathsAndEmailAddresses() {
        String sanitized = CrashCaseRepository.sanitize(
                "token=abc123 uuid=123e4567-e89b-12d3-a456-426614174000 "
                        + "192.168.0.1 dev@example.com /home/alice/.minecraft/logs/latest.log");
        assertFalse(sanitized.contains("abc123"));
        assertFalse(sanitized.contains("123e4567"));
        assertFalse(sanitized.contains("192.168.0.1"));
        assertFalse(sanitized.contains("dev@example.com"));
        assertFalse(sanitized.contains("/home/alice"));
    }
}
