package dev.itsdropper.vanta.launcher.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;

/**
 * Finds byte-identical files and merges them into hard links to reclaim disk space.
 *
 * <p>Scanning never modifies anything. Candidates are narrowed cheaply, first by size, then by a hash of the first
 * block, and only the survivors are fully hashed with SHA-256. Reclaiming re-verifies every file immediately before
 * replacing it, replaces atomically, and leaves a file alone whenever anything about it looks different from the
 * scan, so a stale report can never overwrite changed content.
 */
public final class StorageDeduplicationService {

    /** Files smaller than this save too little to be worth linking. */
    static final long MIN_FILE_SIZE = 4 * 1024;
    private static final int PREFIX_BYTES = 64 * 1024;
    private static final int BUFFER_BYTES = 256 * 1024;

    /** One physical file and every scanned name that points at it. */
    public record PhysicalFile(Object identity, List<Path> paths) {
        public PhysicalFile {
            paths = List.copyOf(paths);
        }
    }

    /** Identical content found under more than one physical file. */
    public record DuplicateGroup(String sha256, long size, List<PhysicalFile> files) {
        public DuplicateGroup {
            files = List.copyOf(files);
        }

        /** Bytes freed once every copy after the first is replaced by a link. */
        public long reclaimableBytes() {
            return size * Math.max(0, files.size() - 1);
        }
    }

    public record Report(List<DuplicateGroup> groups, long filesScanned) {
        public Report {
            groups = List.copyOf(groups);
        }

        public long reclaimableBytes() {
            return groups.stream().mapToLong(DuplicateGroup::reclaimableBytes).sum();
        }

        public int duplicateFiles() {
            return groups.stream().mapToInt(group -> group.files().size() - 1).sum();
        }
    }

    public record Outcome(int linked, int skipped, long reclaimedBytes, List<String> notes) {
        public Outcome {
            notes = List.copyOf(notes);
        }
    }

    @FunctionalInterface
    public interface Progress {
        void update(String phase, long done, long total);
    }

    /**
     * The places where identical files pile up: each instance's mods and packs, the shared libraries and the
     * download cache. Worlds, configs and saves are never included.
     */
    public static List<Path> defaultRoots() {
        List<Path> roots = new ArrayList<>();
        Path instances = dev.itsdropper.vanta.launcher.MinecraftLocator.getInstancesDirectory();
        if (Files.isDirectory(instances)) {
            try (var children = Files.list(instances)) {
                children.filter(Files::isDirectory).forEach(instance -> {
                    roots.add(instance.resolve("mods"));
                    roots.add(instance.resolve("resourcepacks"));
                    roots.add(instance.resolve("shaderpacks"));
                });
            } catch (IOException ignored) {
                // An unreadable instances folder only means fewer places to scan.
            }
        }
        roots.add(dev.itsdropper.vanta.launcher.MinecraftLocator.getLibrariesDirectoryPath());
        roots.add(dev.itsdropper.vanta.launcher.MinecraftLocator.getVantaDirectory().resolve("download-cache"));
        return roots;
    }

    /** Scans {@code roots} recursively without following symbolic links. */
    public Report scan(Collection<Path> roots, Progress progress, BooleanSupplier cancelled) throws IOException {
        Progress listener = progress == null ? (phase, done, total) -> { } : progress;
        BooleanSupplier stop = cancelled == null ? () -> false : cancelled;

        Map<Long, List<Candidate>> bySize = new LinkedHashMap<>();
        long[] scanned = {0};
        for (Path root : roots) {
            if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) {
                continue;
            }
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (stop.getAsBoolean()) {
                        return FileVisitResult.TERMINATE;
                    }
                    if (attributes.isRegularFile() && !attributes.isSymbolicLink()
                            && attributes.size() >= MIN_FILE_SIZE && !isTransient(file)) {
                        bySize.computeIfAbsent(attributes.size(), key -> new ArrayList<>())
                                .add(new Candidate(file, attributes.size(), attributes.fileKey()));
                        scanned[0]++;
                        if (scanned[0] % 256 == 0) {
                            listener.update("Listing files", scanned[0], -1);
                        }
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException failure) {
                    return FileVisitResult.CONTINUE;
                }
            });
        }

        List<List<Candidate>> sizeGroups = bySize.values().stream()
                .filter(group -> physicalCount(group) > 1)
                .toList();
        long totalToHash = sizeGroups.stream().mapToLong(List::size).sum();
        long hashed = 0;
        List<DuplicateGroup> groups = new ArrayList<>();

        for (List<Candidate> sizeGroup : sizeGroups) {
            if (stop.getAsBoolean()) {
                break;
            }
            Map<String, List<Candidate>> byPrefix = new LinkedHashMap<>();
            for (Candidate candidate : sizeGroup) {
                String prefix = hashOrNull(candidate.path(), PREFIX_BYTES);
                if (prefix != null) {
                    byPrefix.computeIfAbsent(prefix, key -> new ArrayList<>()).add(candidate);
                }
            }
            for (List<Candidate> prefixGroup : byPrefix.values()) {
                if (physicalCount(prefixGroup) < 2) {
                    continue;
                }
                Map<String, List<Candidate>> byContent = new LinkedHashMap<>();
                for (Candidate candidate : prefixGroup) {
                    if (stop.getAsBoolean()) {
                        break;
                    }
                    String digest = hashOrNull(candidate.path(), Long.MAX_VALUE);
                    if (digest != null) {
                        byContent.computeIfAbsent(digest, key -> new ArrayList<>()).add(candidate);
                    }
                    hashed++;
                    listener.update("Comparing files", hashed, totalToHash);
                }
                byContent.forEach((digest, matches) -> {
                    List<PhysicalFile> physical = toPhysical(matches);
                    if (physical.size() > 1) {
                        groups.add(new DuplicateGroup(digest, matches.get(0).size(), physical));
                    }
                });
            }
        }
        groups.sort((a, b) -> Long.compare(b.reclaimableBytes(), a.reclaimableBytes()));
        return new Report(groups, scanned[0]);
    }

    /** Replaces the duplicates in {@code report} with hard links, verifying each file again first. */
    public Outcome reclaim(Report report, Progress progress, BooleanSupplier cancelled) {
        Progress listener = progress == null ? (phase, done, total) -> { } : progress;
        BooleanSupplier stop = cancelled == null ? () -> false : cancelled;
        int linked = 0;
        int skipped = 0;
        long reclaimed = 0;
        List<String> notes = new ArrayList<>();
        long total = report.duplicateFiles();
        long done = 0;

        for (DuplicateGroup group : report.groups()) {
            if (stop.getAsBoolean()) {
                notes.add("Stopped before finishing. Files already merged stay merged.");
                break;
            }
            Path canonical = firstUsable(group.files().get(0), group);
            if (canonical == null) {
                skipped += group.files().size() - 1;
                done += group.files().size() - 1;
                notes.add("Skipped a group because its first file changed or is gone.");
                continue;
            }
            for (PhysicalFile other : group.files().subList(1, group.files().size())) {
                boolean allReplaced = true;
                for (Path duplicate : other.paths()) {
                    if (stop.getAsBoolean()) {
                        allReplaced = false;
                        break;
                    }
                    if (matches(duplicate, group) && FileLinker.replaceWithLink(canonical, duplicate)) {
                        linked++;
                    } else {
                        skipped++;
                        allReplaced = false;
                    }
                }
                // Space is only freed once the last name of the old copy has been replaced.
                if (allReplaced) {
                    reclaimed += group.size();
                }
                done++;
                listener.update("Merging duplicates", done, total);
            }
        }
        return new Outcome(linked, skipped, reclaimed, notes);
    }

    private static Path firstUsable(PhysicalFile file, DuplicateGroup group) {
        for (Path path : file.paths()) {
            if (matches(path, group)) {
                return path;
            }
        }
        return null;
    }

    /** True when the file still has exactly the size and SHA-256 recorded by the scan. */
    private static boolean matches(Path path, DuplicateGroup group) {
        try {
            BasicFileAttributes attributes = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
            return attributes.isRegularFile() && attributes.size() == group.size()
                    && group.sha256().equals(hashOrNull(path, Long.MAX_VALUE));
        } catch (IOException failure) {
            return false;
        }
    }

    private static List<PhysicalFile> toPhysical(List<Candidate> matches) {
        Map<Object, List<Path>> byIdentity = new LinkedHashMap<>();
        for (Candidate candidate : matches) {
            Object identity = candidate.fileKey() != null ? candidate.fileKey() : candidate.path();
            byIdentity.computeIfAbsent(identity, key -> new ArrayList<>()).add(candidate.path());
        }
        if (matches.get(0).fileKey() == null) {
            mergeByFileIdentity(byIdentity);
        }
        List<PhysicalFile> result = new ArrayList<>();
        byIdentity.forEach((identity, paths) -> result.add(new PhysicalFile(identity, paths)));
        return result;
    }

    /** File systems without file keys (some Windows setups) are compared pairwise with isSameFile. */
    private static void mergeByFileIdentity(Map<Object, List<Path>> byIdentity) {
        List<Object> keys = new ArrayList<>(byIdentity.keySet());
        for (int i = 0; i < keys.size(); i++) {
            Object keep = keys.get(i);
            if (!byIdentity.containsKey(keep)) {
                continue;
            }
            for (int j = i + 1; j < keys.size(); j++) {
                Object other = keys.get(j);
                List<Path> otherPaths = byIdentity.get(other);
                if (otherPaths == null) {
                    continue;
                }
                try {
                    if (Files.isSameFile(byIdentity.get(keep).get(0), otherPaths.get(0))) {
                        byIdentity.get(keep).addAll(otherPaths);
                        byIdentity.remove(other);
                    }
                } catch (IOException ignored) {
                    // Treated as distinct files, which only means a missed saving.
                }
            }
        }
    }

    private static int physicalCount(List<Candidate> candidates) {
        if (candidates.size() < 2) {
            return candidates.size();
        }
        return toPhysical(candidates).size();
    }

    private static boolean isTransient(Path file) {
        String name = file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        return name.endsWith(".tmp") || name.endsWith(".part") || name.endsWith(".lock")
                || name.endsWith(".partial") || name.endsWith(".download");
    }

    /** SHA-256 of up to {@code limit} bytes, or null when the file cannot be read. */
    static String hashOrNull(Path path, long limit) {
        try (InputStream input = Files.newInputStream(path)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_BYTES];
            long remaining = limit;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    break;
                }
                digest.update(buffer, 0, read);
                remaining -= read;
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException failure) {
            return null;
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required by the Java platform", impossible);
        }
    }

    private record Candidate(Path path, long size, Object fileKey) {
        Candidate {
            Objects.requireNonNull(path);
        }
    }
}
