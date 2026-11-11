package dev.itsdropper.vanta.launcher.repair;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DeterministicCrashModelTest {
    @Test
    void trainsFromVersionedCorpusAndClassifiesHeldOutCases() {
        assertTrue(DeterministicCrashModel.trainingExampleCount() >= 50);
        assertCategory("UnsupportedClassVersionError: class file version 69, runtime only recognizes up to 65",
                FailureCategory.JAVA_VERSION);
        assertCategory("java.net.UnknownHostException: failed to resolve resources download host", FailureCategory.NETWORK);
        assertCategory("org.spongepowered.asm.mixin.transformer.throwables.MixinApplyError: critical injection failure",
                FailureCategory.MOD_MIXIN_FAILURE);
        assertCategory("java.util.zip.ZipException: unexpected end of zlib input stream", FailureCategory.CORRUPT_LIBRARY);
    }

    @Test
    void doesNotClassifyGenericOrUnrelatedText() {
        assertNull(DeterministicCrashModel.classify("Minecraft is starting normally."));
        assertNull(DeterministicCrashModel.classify("hello world"));
    }

    @Test
    void trainingIsDeterministic() {
        var first = DeterministicCrashModel.classify("UnsupportedClassVersionError class file version 69 runtime recognizes up to 65");
        var second = DeterministicCrashModel.classify("UnsupportedClassVersionError class file version 69 runtime recognizes up to 65");
        assertNotNull(first);
        assertEquals(first.category(), second.category());
        assertEquals(first.confidence(), second.confidence());
    }

    private static void assertCategory(String log, FailureCategory expected) {
        var prediction = DeterministicCrashModel.classify(log);
        assertNotNull(prediction, "Expected a model prediction for: " + log);
        assertEquals(expected, prediction.category());
    }
}
