package dev.itsdropper.vanta.ui.async;

import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiExecutorTest {

    @Test
    void createsNamedDaemonThreadsAndRejectsWorkWhenItsBoundedQueueIsFull()
            throws Exception {
        UiExecutor executor = new UiExecutor(1, 1, "vanta-ui-test");
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch secondFinished = new CountDownLatch(1);
        AtomicReference<String> workerName = new AtomicReference<>();
        AtomicReference<Boolean> daemon = new AtomicReference<>();

        try {
            executor.execute(() -> {
                workerName.set(Thread.currentThread().getName());
                daemon.set(Thread.currentThread().isDaemon());
                firstStarted.countDown();
                try {
                    releaseFirst.await();
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                }
            });

            assertTrue(firstStarted.await(5, TimeUnit.SECONDS));
            executor.execute(secondFinished::countDown);

            assertThrows(
                    RejectedExecutionException.class,
                    () -> executor.execute(() -> {
                    })
            );

            releaseFirst.countDown();
            assertTrue(secondFinished.await(5, TimeUnit.SECONDS));
            assertTrue(workerName.get().startsWith("vanta-ui-test-"));
            assertEquals(Boolean.TRUE, daemon.get());
        } finally {
            releaseFirst.countDown();
            executor.close();
        }
    }
}
