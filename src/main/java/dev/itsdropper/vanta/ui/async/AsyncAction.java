package dev.itsdropper.vanta.ui.async;

import javafx.application.Platform;

import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Runs one replaceable background action and delivers its events on the FX
 * Application Thread. Starting a new request or cancelling invalidates all
 * queued callbacks from the previous request.
 */
public final class AsyncAction<T> implements AutoCloseable {

    private final UiExecutor executor;
    private final Consumer<T> onSuccess;
    private final Consumer<Throwable> onFailure;
    private final BiConsumer<Double, String> onProgress;
    private final Consumer<Runnable> fxDispatcher;
    private final RequestToken requestToken = new RequestToken();
    private final AtomicBoolean running = new AtomicBoolean();

    private volatile Future<?> task;

    public AsyncAction(
            UiExecutor executor,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure,
            BiConsumer<Double, String> onProgress
    ) {
        this(executor, onSuccess, onFailure, onProgress, Platform::runLater);
    }

    public AsyncAction(
            UiExecutor executor,
            Consumer<T> onSuccess,
            Consumer<Throwable> onFailure,
            BiConsumer<Double, String> onProgress,
            Consumer<Runnable> fxDispatcher
    ) {
        this.executor = Objects.requireNonNull(executor, "executor");
        this.onSuccess = Objects.requireNonNull(onSuccess, "onSuccess");
        this.onFailure = Objects.requireNonNull(onFailure, "onFailure");
        this.onProgress = Objects.requireNonNull(onProgress, "onProgress");
        this.fxDispatcher = Objects.requireNonNull(fxDispatcher, "fxDispatcher");
    }

    public RequestToken.Ticket start(Callable<T> work) {
        Objects.requireNonNull(work, "work");
        return start(progress -> work.call());
    }

    public synchronized RequestToken.Ticket start(Work<T> work) {
        Objects.requireNonNull(work, "work");

        Future<?> previous = task;
        if (previous != null) {
            previous.cancel(true);
        }

        RequestToken.Ticket ticket = requestToken.next();
        running.set(true);

        try {
            task = executor.execute(() -> execute(work, ticket));
        } catch (RejectedExecutionException rejected) {
            running.set(false);
            dispatch(ticket, () -> onFailure.accept(rejected));
        }

        return ticket;
    }

    public synchronized void cancel() {
        requestToken.invalidate();
        running.set(false);

        Future<?> current = task;
        task = null;
        if (current != null) {
            current.cancel(true);
        }
    }

    public boolean isRunning() {
        return running.get();
    }

    private void execute(Work<T> work, RequestToken.Ticket ticket) {
        try {
            T result = work.run((fraction, message) -> {
                double boundedFraction = Double.isNaN(fraction)
                        ? 0.0
                        : Math.max(0.0, Math.min(1.0, fraction));
                dispatch(ticket, () -> onProgress.accept(boundedFraction, message));
            });
            dispatch(ticket, () -> onSuccess.accept(result));
        } catch (Throwable failure) {
            dispatch(ticket, () -> onFailure.accept(failure));
        } finally {
            if (ticket.isCurrent()) {
                running.set(false);
                task = null;
            }
        }
    }

    private void dispatch(RequestToken.Ticket ticket, Runnable callback) {
        try {
            fxDispatcher.accept(() -> {
                if (ticket.isCurrent()) {
                    callback.run();
                }
            });
        } catch (IllegalStateException toolkitUnavailable) {
            // The JavaFX toolkit may already be shutting down during application exit.
        }
    }

    @Override
    public void close() {
        cancel();
    }

    @FunctionalInterface
    public interface Work<T> {
        T run(ProgressReporter progress) throws Exception;
    }

    @FunctionalInterface
    public interface ProgressReporter {
        void report(double fraction, String message);
    }
}
