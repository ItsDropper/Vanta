package dev.itsdropper.vanta.launcher.repair;

import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class CrashCorpusQualityTest {
    @Test
    void corpusHasBalancedLabelsUniqueCasesAndNoUnredactedPersonalData() throws Exception {
        InputStream stream = getClass().getResourceAsStream(
                "/dev/itsdropper/vanta/launcher/repair/crash-cases.tsv");
        assertNotNull(stream, "Crash corpus resource must be packaged");
        Set<String> rows = new HashSet<>();
        Set<FailureCategory> categories = new HashSet<>();
        int count = 0;
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] fields = line.split("\\t", 2);
                assertEquals(2, fields.length, "Every row must have exactly one label and one case");
                FailureCategory label = assertDoesNotThrow(() -> FailureCategory.valueOf(fields[0]));
                assertNotEquals(FailureCategory.UNKNOWN, label);
                assertTrue(fields[1].length() >= 20 && fields[1].length() <= 8_192);
                assertTrue(rows.add(line), "Duplicate training case: " + line);
                assertEquals(fields[1], CrashCaseRepository.sanitize(fields[1]),
                        "Training cases must already satisfy the local redaction policy");
                categories.add(label);
                count++;
            }
        }
        assertEquals(66, count, "Unexpected corpus size; update the reviewed corpus intentionally");
        Set<FailureCategory> expectedCategories = EnumSet.allOf(FailureCategory.class);
        expectedCategories.remove(FailureCategory.UNKNOWN);
        assertEquals(expectedCategories, categories,
                "Every actionable diagnosis category must have labelled training coverage");
    }

    @Test
    void modelProducesStablePredictionsForHeldOutFullyQualifiedExceptions() {
        assertEquals(FailureCategory.NETWORK,
                DeterministicCrashModel.classify(
                        "java.net.UnknownHostException: failed to resolve resources download host").category());
        assertEquals(FailureCategory.MOD_MIXIN_FAILURE,
                DeterministicCrashModel.classify(
                        "org.spongepowered.asm.mixin.transformer.throwables.MixinApplyError while transforming target method")
                        .category());
    }
}
