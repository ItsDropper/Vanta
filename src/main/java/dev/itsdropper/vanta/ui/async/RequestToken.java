package dev.itsdropper.vanta.ui.async;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Issues request tickets that become stale when a newer request starts or
 * their owning view invalidates its work.
 */
public final class RequestToken {

    private final AtomicLong generation = new AtomicLong();
    private volatile boolean active = true;

    public Ticket next() {
        active = true;
        return new Ticket(this, generation.incrementAndGet());
    }

    public void invalidate() {
        active = false;
        generation.incrementAndGet();
    }

    private boolean isCurrent(long expectedGeneration) {
        return active && generation.get() == expectedGeneration;
    }

    public static final class Ticket {
        private final RequestToken source;
        private final long generation;

        private Ticket(RequestToken source, long generation) {
            this.source = source;
            this.generation = generation;
        }

        public boolean isCurrent() {
            return source.isCurrent(generation);
        }
    }
}
