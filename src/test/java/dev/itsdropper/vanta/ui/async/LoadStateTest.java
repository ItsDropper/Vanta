package dev.itsdropper.vanta.ui.async;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadStateTest {

    @Test
    void representsEachLoadOutcome() {
        assertInstanceOf(LoadState.Loading.class, new LoadState.Loading<String>());
        assertInstanceOf(LoadState.Empty.class, new LoadState.Empty<String>());
        assertEquals("ready", assertInstanceOf(
                LoadState.Ready.class,
                new LoadState.Ready<>("ready")
        ).value());
    }

    @Test
    void failedStateExposesItsRetryAction() {
        AtomicBoolean retried = new AtomicBoolean();
        LoadState.Failed<String> failed = new LoadState.Failed<>(
                "Network unavailable",
                () -> retried.set(true)
        );

        assertEquals("Network unavailable", failed.message());
        assertFalse(retried.get());
        failed.retryAction().run();
        assertTrue(retried.get());
    }

    @Test
    void rejectsMissingReadyValuesAndRetryActions() {
        assertThrows(
                NullPointerException.class,
                () -> new LoadState.Ready<String>(null)
        );
        assertThrows(
                NullPointerException.class,
                () -> new LoadState.Failed<String>("failed", null)
        );
    }
}
