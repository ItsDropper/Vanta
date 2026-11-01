package dev.itsdropper.vanta.ui.async;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AsyncActionTest {

    private static final long TIMEOUT_SECONDS = 5;

    private UiExecutor executor;
    private ExecutorService fxExecutor;
    private AsyncAction<?> action;

    @BeforeEach
    void createExecutors() {
        executor = new UiExecutor(2, 16, "async-action-worker");
        fxExecutor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "async-action-fx");
            thread.setDaemon(true);
            return thread;
        });
    }

    @AfterEach
    void closeResources() {
        if (action != null) {
            action.close();
        }
        executor.close();
        fxExecutor.shutdownNow();
    }

    @Test
    void deliversProgressAndResultThroughTheFxDispatcher() throws Exception {
        CountDownLatch progressDelivered = new CountDownLatch(1);
        CountDownLatch resultDelivered = new CountDownLatch(1);
        AtomicReference<String> callbackThread = new AtomicReference<>();
        AtomicReference<Double> progressValue = new AtomicReference<>();
        AtomicReference<String> result = new AtomicReference<>();

        action = new AsyncAction<>(
                executor,
                value -> {
                    callbackThread.set(Thread.currentThread().getName());
                    result.set(value);
                    resultDelivered.countDown();
                },
                failure -> resultDelivered.countDown(),
                (fraction, message) -> {
                    callbackThread.set(Thread.currentThread().getName());
                    progressValue.set(fraction);
                    progressDelivered.countDown();
                },
                task -> fxExecutor.execute(task)
        );

        action.start(progress -> {
            progress.report(0.4, "Downloading");
            return "complete";
        });

        assertTrue(progressDelivered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertTrue(resultDelivered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals(0.4, progressValue.get());
        assertEquals("complete", result.get());
        assertEquals("async-action-fx", callbackThread.get());
    }

    @Test
    void dropsResultWhenANewerRequestStarts() throws Exception {
        CountDownLatch oldStarted = new CountDownLatch(1);
        CountDownLatch releaseOld = new CountDownLatch(1);
        CountDownLatch oldFinished = new CountDownLatch(1);
        CountDownLatch freshDelivered = new CountDownLatch(1);
        AtomicReference<String> delivered = new AtomicReference<>();

        action = new AsyncAction<>(
                executor,
                value -> {
                    delivered.set(value);
                    freshDelivered.countDown();
                },
                failure -> {
                },
                (fraction, message) -> {
                },
                task -> fxExecutor.execute(task)
        );

        RequestToken.Ticket oldTicket = action.start(() -> {
            oldStarted.countDown();
            try {
                boolean released = false;
                while (!released) {
                    try {
                        releaseOld.await();
                        released = true;
                    } catch (InterruptedException ignored) {
                        // Simulate an operation that cannot stop immediately.
                    }
                }
                return "stale";
            } finally {
                oldFinished.countDown();
            }
        });

        assertTrue(oldStarted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        action.start(() -> "current");
        assertFalse(oldTicket.isCurrent());
        assertTrue(freshDelivered.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));

        releaseOld.countDown();
        assertTrue(oldFinished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertEquals("current", delivered.get());
    }

    @Test
    void cancellationInvalidatesThePendingResult() throws Exception {
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch finished = new CountDownLatch(1);
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<RequestToken.Ticket> ticket = new AtomicReference<>();

        action = new AsyncAction<>(
                executor,
                value -> delivered.countDown(),
                failure -> delivered.countDown(),
                (fraction, message) -> {
                },
                task -> fxExecutor.execute(task)
        );

        ticket.set(action.start(() -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
                return "unexpected";
            } finally {
                finished.countDown();
            }
        }));

        assertTrue(started.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        action.cancel();
        assertFalse(ticket.get().isCurrent());
        assertTrue(finished.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertFalse(delivered.await(200, TimeUnit.MILLISECONDS));
    }

    @Test
    void deliversFailuresThroughTheFxDispatcher() throws Exception {
        CountDownLatch failed = new CountDownLatch(1);
        AtomicReference<Throwable> deliveredFailure = new AtomicReference<>();
        AtomicReference<String> callbackThread = new AtomicReference<>();

        action = new AsyncAction<>(
                executor,
                value -> {
                },
                failure -> {
                    deliveredFailure.set(failure);
                    callbackThread.set(Thread.currentThread().getName());
                    failed.countDown();
                },
                (fraction, message) -> {
                },
                task -> fxExecutor.execute(task)
        );

        action.start(() -> {
            throw new IllegalStateException("expected failure");
        });

        assertTrue(failed.await(TIMEOUT_SECONDS, TimeUnit.SECONDS));
        assertNotNull(deliveredFailure.get());
        assertEquals("expected failure", deliveredFailure.get().getMessage());
        assertEquals("async-action-fx", callbackThread.get());
    }
}
