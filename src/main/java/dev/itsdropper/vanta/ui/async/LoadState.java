package dev.itsdropper.vanta.ui.async;

import java.util.Objects;

/**
 * Immutable state exposed by asynchronous list and detail presenters.
 */
public sealed interface LoadState<T>
        permits LoadState.Loading, LoadState.Empty, LoadState.Failed, LoadState.Ready {

    record Loading<T>() implements LoadState<T> {
    }

    record Empty<T>() implements LoadState<T> {
    }

    record Failed<T>(String message, Runnable retryAction) implements LoadState<T> {
        public Failed {
            message = Objects.requireNonNull(message, "message");
            retryAction = Objects.requireNonNull(retryAction, "retryAction");
        }
    }

    record Ready<T>(T value) implements LoadState<T> {
        public Ready {
            value = Objects.requireNonNull(value, "value");
        }
    }
}
