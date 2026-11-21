package dev.itsdropper.vanta.launcher.instance;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Resolves loader compatibility independently from loader installation.
 *
 * <p>A loader is only marked unsupported when its metadata explicitly has no
 * matching release (or its local compatibility predicate rejects the game
 * version). Transport and unexpected metadata failures remain a separate
 * state so a temporary outage never masquerades as an incompatibility.</p>
 */
public final class LoaderCompatibilityService {
    public enum Status {
        AVAILABLE,
        UNSUPPORTED,
        CHECK_FAILED
    }

    public record Result(Status status, String loaderVersion) {
        public Result {
            if (status == null) throw new IllegalArgumentException("A compatibility status is required.");
            if (status == Status.AVAILABLE && (loaderVersion == null || loaderVersion.isBlank())) {
                throw new IllegalArgumentException("Available loaders must provide a version.");
            }
        }

        public boolean isAvailable() {
            return status == Status.AVAILABLE;
        }
    }

    public record Report(String minecraftVersion, Map<String, Result> results) {
        public Report {
            if (minecraftVersion == null || minecraftVersion.isBlank()) {
                throw new IllegalArgumentException("Minecraft version is required.");
            }
            results = Collections.unmodifiableMap(new LinkedHashMap<>(results));
        }

        public List<String> availableLoaders() {
            return results.entrySet().stream()
                    .filter(entry -> entry.getValue().isAvailable())
                    .map(Map.Entry::getKey)
                    .toList();
        }

        public List<String> failedLoaders() {
            return results.entrySet().stream()
                    .filter(entry -> entry.getValue().status() == Status.CHECK_FAILED)
                    .map(Map.Entry::getKey)
                    .toList();
        }

        public boolean hasFailures() {
            return results.values().stream().anyMatch(result -> result.status() == Status.CHECK_FAILED);
        }
    }

    @FunctionalInterface
    interface VersionLookup {
        String find() throws Exception;
    }

    private record Probe(String loader, VersionLookup lookup) { }

    private LoaderCompatibilityService() { }

    /**
     * Checks independent loader metadata endpoints concurrently with bounded
     * parallelism. Results retain declaration order for deterministic UI.
     */
    public static Report checkMinecraftVersion(String minecraftVersion) throws InterruptedException {
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IllegalArgumentException("Minecraft version is required.");
        }

        List<Probe> probes = List.of(
                new Probe("Fabric", () -> FabricInstaller.findLatestLoaderVersion(minecraftVersion)),
                new Probe("Quilt", () -> QuiltInstaller.findLatestLoaderVersion(minecraftVersion)),
                new Probe("Forge", () -> ForgeInstaller.findLatestLoaderVersion(minecraftVersion)),
                new Probe("NeoForge", () -> NeoForgeInstaller.findLatestLoaderVersion(minecraftVersion)),
                new Probe("Ornithe", () -> OrnitheInstaller.findLatestLoaderVersion(minecraftVersion)),
                new Probe("Legacy Fabric", () -> LegacyFabricInstaller.findLatestLoaderVersion(minecraftVersion)),
                new Probe("Babric", () -> BabricInstaller.findLatestLoaderVersion(minecraftVersion)),
                new Probe("NilLoader", () -> NilLoaderInstaller.findLatestLoaderVersion(minecraftVersion)),
                new Probe("LiteLoader", () -> LiteLoaderInstaller.findLatestLoaderVersion(minecraftVersion)),
                new Probe("ModLoader", () -> ModLoaderInstaller.findLatestLoaderVersion(minecraftVersion)),
                new Probe("Rift", () -> RiftInstaller.findLatestLoaderVersion(minecraftVersion)),
                new Probe("Stein", () -> SteinInstaller.supportsMinecraftVersion(minecraftVersion)
                        ? SteinInstaller.findLatestLoaderVersion() : null),
                // Match the launcher UI's existing policy: compatibility is
                // determined locally. The immutable GitHub release is resolved
                // during installation, so a GitHub outage must not hide VantaLoader.
                new Probe("VantaLoader", () -> VantaLoaderInstaller.supportsMinecraftVersion(minecraftVersion)
                        ? "nightly" : null)
        );

        int workers = Math.min(6, probes.size());
        ExecutorService executor = Executors.newFixedThreadPool(
                workers, UiExecutorThreadFactory.create());
        List<Future<Result>> futures = new ArrayList<>(probes.size());
        try {
            for (Probe probe : probes) {
                futures.add(executor.submit(() -> evaluate(probe.lookup())));
            }

            Map<String, Result> results = new LinkedHashMap<>();
            for (int i = 0; i < probes.size(); i++) {
                try {
                    results.put(probes.get(i).loader(), futures.get(i).get());
                } catch (ExecutionException unexpected) {
                    results.put(probes.get(i).loader(), new Result(Status.CHECK_FAILED, null));
                }
            }
            results.put("Vanilla", new Result(Status.AVAILABLE, "vanilla"));
            return new Report(minecraftVersion, results);
        } catch (InterruptedException interrupted) {
            futures.forEach(future -> future.cancel(true));
            Thread.currentThread().interrupt();
            throw interrupted;
        } finally {
            executor.shutdownNow();
        }
    }

    static Result evaluate(VersionLookup lookup) {
        try {
            String version = lookup.find();
            return version == null || version.isBlank()
                    ? new Result(Status.UNSUPPORTED, null)
                    : new Result(Status.AVAILABLE, version);
        } catch (IllegalArgumentException | IllegalStateException unsupported) {
            if (hasIOExceptionCause(unsupported)) {
                return new Result(Status.CHECK_FAILED, null);
            }
            return new Result(Status.UNSUPPORTED, null);
        } catch (Exception failure) {
            return new Result(Status.CHECK_FAILED, null);
        }
    }

    private static boolean hasIOExceptionCause(Throwable failure) {
        for (Throwable cause = failure; cause != null && cause.getCause() != cause; cause = cause.getCause()) {
            if (cause instanceof IOException) return true;
        }
        return false;
    }

    private static final class UiExecutorThreadFactory {
        private UiExecutorThreadFactory() { }

        private static java.util.concurrent.ThreadFactory create() {
            return dev.itsdropper.vanta.ui.async.UiExecutor.namedDaemonThreadFactory(
                    "vanta-loader-compatibility");
        }
    }
}
