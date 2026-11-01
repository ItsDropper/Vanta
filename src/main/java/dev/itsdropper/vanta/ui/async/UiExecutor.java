package dev.itsdropper.vanta.ui.async;

import javafx.application.Platform;

import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Shared bounded executor for UI-triggered blocking work.
 *
 * <p>Tasks never run on the submitting thread when the queue is full; overload
 * is reported with {@link java.util.concurrent.RejectedExecutionException}.</p>
 */
public final class UiExecutor implements AutoCloseable {

    private static final int DEFAULT_WORKERS = Math.max(
            2,
            Math.min(8, Runtime.getRuntime().availableProcessors())
    );
    private static final UiExecutor SHARED = new UiExecutor(
            DEFAULT_WORKERS,
            256,
            "vanta-ui-io"
    );

    private final ThreadPoolExecutor executor;

    public UiExecutor(int workerCount, int queueCapacity, String threadPrefix) {
        if (workerCount < 1) {
            throw new IllegalArgumentException("workerCount must be positive");
        }
        if (queueCapacity < 1) {
            throw new IllegalArgumentException("queueCapacity must be positive");
        }
        if (threadPrefix == null || threadPrefix.isBlank()) {
            throw new IllegalArgumentException("threadPrefix must not be blank");
        }

        ThreadFactory threadFactory = namedDaemonThreadFactory(threadPrefix);

        executor = new ThreadPoolExecutor(
                workerCount,
                workerCount,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(queueCapacity),
                threadFactory,
                new ThreadPoolExecutor.AbortPolicy()
        );
    }

    public static UiExecutor shared() {
        return SHARED;
    }

    public static ThreadFactory namedDaemonThreadFactory(String threadPrefix) {
        if (threadPrefix == null || threadPrefix.isBlank()) {
            throw new IllegalArgumentException("threadPrefix must not be blank");
        }
        AtomicInteger threadNumber = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(
                    task,
                    threadPrefix + "-" + threadNumber.incrementAndGet()
            );
            thread.setDaemon(true);
            return thread;
        };
    }

    public Future<?> execute(Runnable task) {
        return executor.submit(Objects.requireNonNull(task, "task"));
    }

    public void dispatchToUi(Runnable action) {
        Platform.runLater(Objects.requireNonNull(action, "action"));
    }

    public <T> Future<T> submit(Callable<T> task) {
        return executor.submit(Objects.requireNonNull(task, "task"));
    }

    public boolean isShutdown() {
        return executor.isShutdown();
    }

    @Override
    public void close() {
        executor.shutdown();
    }
}
