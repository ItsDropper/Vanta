package dev.itsdropper.vanta.launcher.download;

import javafx.application.Platform;
import dev.itsdropper.vanta.launcher.instance.DownloadUtil;

import java.net.URI;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * In-memory activity registry for real launcher file downloads.
 * It deliberately stores no response bodies or credentials and is local-only.
 */
public final class DownloadCenterService {
    public enum Status { DOWNLOADING, VERIFYING, COMPLETED, FAILED }

    public record Task(long id, String name, String source, Path target, String expectedSha1,
                       Status status, long receivedBytes, long totalBytes, Instant startedAt,
                       Instant finishedAt, String error) {
        public double progress() {
            return totalBytes > 0 ? Math.min(1.0, (double) receivedBytes / totalBytes) : 0.0;
        }
        public boolean active() { return status == Status.DOWNLOADING || status == Status.VERIFYING; }
    }

    private static final DownloadCenterService INSTANCE = new DownloadCenterService();
    private static final int MAX_HISTORY = 100;
    private final AtomicLong sequence = new AtomicLong();
    private final List<Task> tasks = new ArrayList<>();
    private final CopyOnWriteArrayList<Consumer<List<Task>>> listeners = new CopyOnWriteArrayList<>();
    private final ExecutorService retryExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "vanta-download-retry");
        thread.setDaemon(true);
        return thread;
    });

    private DownloadCenterService() {}
    public static DownloadCenterService getInstance() { return INSTANCE; }

    public synchronized List<Task> snapshot() {
        return tasks.stream().sorted(Comparator.comparing(Task::startedAt).reversed()).toList();
    }

    public void addListener(Consumer<List<Task>> listener) {
        listeners.add(listener);
        notifyListener(listener);
    }
    public void removeListener(Consumer<List<Task>> listener) { listeners.remove(listener); }

    public synchronized long begin(String url, Path target, String sha1) {
        long id = sequence.incrementAndGet();
        String name = target == null || target.getFileName() == null ? "Download" : target.getFileName().toString();
        tasks.add(new Task(id, name, safeSource(url), target, sha1, Status.DOWNLOADING,
                0, -1, Instant.now(), null, null));
        trimHistory();
        publish();
        return id;
    }

    public synchronized void progress(long id, long received, long total) {
        update(id, old -> new Task(old.id(), old.name(), old.source(), old.target(), old.expectedSha1(),
                old.status(), Math.max(0, received), total > 0 ? total : -1,
                old.startedAt(), old.finishedAt(), old.error()));
    }

    public synchronized void verifying(long id, long received, long total) {
        update(id, old -> new Task(old.id(), old.name(), old.source(), old.target(), old.expectedSha1(),
                Status.VERIFYING, received, total, old.startedAt(), null, null));
    }

    public synchronized void complete(long id, long size) {
        update(id, old -> new Task(old.id(), old.name(), old.source(), old.target(), old.expectedSha1(),
                Status.COMPLETED, size, size, old.startedAt(), Instant.now(), null));
    }

    public synchronized void fail(long id, Throwable failure) {
        String message = failure == null || failure.getMessage() == null
                ? "The download failed. Check the connection and try again."
                : failure.getMessage();
        if (message.length() > 280) message = message.substring(0, 277) + "...";
        final String reason = message;
        update(id, old -> new Task(old.id(), old.name(), old.source(), old.target(), old.expectedSha1(),
                Status.FAILED, old.receivedBytes(), old.totalBytes(), old.startedAt(), Instant.now(), reason));
    }

    public synchronized void clearFinished() {
        tasks.removeIf(task -> !task.active());
        publish();
    }

    public void retry(Task task) {
        if (task == null || task.status() != Status.FAILED || task.target() == null || task.source() == null) return;
        retryExecutor.submit(() -> {
            try {
                DownloadUtil.downloadFile(task.source(), task.target(), task.expectedSha1());
            } catch (Exception ignored) {
                // The new task records the actionable error; avoid a second global error surface.
            }
        });
    }

    private void update(long id, java.util.function.UnaryOperator<Task> mapper) {
        for (int i = 0; i < tasks.size(); i++) {
            if (tasks.get(i).id() == id) {
                tasks.set(i, mapper.apply(tasks.get(i)));
                publish();
                return;
            }
        }
    }

    private void trimHistory() {
        while (tasks.size() > MAX_HISTORY) {
            int removable = -1;
            for (int i = tasks.size() - 1; i >= 0; i--) {
                if (!tasks.get(i).active()) { removable = i; break; }
            }
            if (removable < 0) break;
            tasks.remove(removable);
        }
    }

    private void publish() {
        List<Task> copy = snapshot();
        for (Consumer<List<Task>> listener : listeners) {
            Platform.runLater(() -> listener.accept(copy));
        }
    }

    private void notifyListener(Consumer<List<Task>> listener) {
        List<Task> copy = snapshot();
        Platform.runLater(() -> listener.accept(copy));
    }

    private static String safeSource(String raw) {
        try {
            URI uri = URI.create(raw);
            String host = uri.getHost();
            return host == null ? "Remote source" : host;
        } catch (RuntimeException ignored) {
            return "Remote source";
        }
    }
}
