package dev.itsdropper.vanta.launcher.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageDeduplicationServiceTest {

    private static final int SIZE = 8 * 1024;

    @TempDir
    Path directory;

    private final StorageDeduplicationService service = new StorageDeduplicationService();

    @Test
    void reportsIdenticalFilesAndIgnoresDifferentOnes() throws IOException {
        byte[] shared = bytes(1);
        file("a/mods/one.jar", shared);
        file("b/mods/one.jar", shared);
        file("b/mods/other.jar", bytes(2));

        StorageDeduplicationService.Report report = service.scan(List.of(directory), null, null);

        assertEquals(1, report.groups().size());
        assertEquals(1, report.duplicateFiles());
        assertEquals(SIZE, report.reclaimableBytes());
    }

    @Test
    void sameSizeAndSamePrefixButDifferentTailAreNotDuplicates() throws IOException {
        byte[] first = new byte[SIZE * 20];
        byte[] second = first.clone();
        second[second.length - 1] = 9;
        file("a/big.jar", first);
        file("b/big.jar", second);

        assertTrue(service.scan(List.of(directory), null, null).groups().isEmpty());
    }

    @Test
    void alreadyLinkedFilesAreNotReportedAgain() throws IOException {
        Path original = file("a/mods/one.jar", bytes(1));
        Path linked = directory.resolve("b/mods/one.jar");
        Files.createDirectories(linked.getParent());
        Files.createLink(linked, original);

        assertTrue(service.scan(List.of(directory), null, null).groups().isEmpty());
    }

    @Test
    void smallAndTemporaryFilesAreSkipped() throws IOException {
        file("a/tiny.jar", new byte[16]);
        file("b/tiny.jar", new byte[16]);
        file("a/x.jar.tmp", bytes(3));
        file("b/x.jar.tmp", bytes(3));

        assertEquals(0, service.scan(List.of(directory), null, null).filesScanned());
    }

    @Test
    void reclaimMergesDuplicatesIntoLinks() throws IOException {
        byte[] shared = bytes(1);
        Path first = file("a/one.jar", shared);
        Path second = file("b/one.jar", shared);
        Path third = file("c/one.jar", shared);

        StorageDeduplicationService.Report report = service.scan(List.of(directory), null, null);
        StorageDeduplicationService.Outcome outcome = service.reclaim(report, null, null);

        assertEquals(2, outcome.linked());
        assertEquals(0, outcome.skipped());
        assertEquals(2L * SIZE, outcome.reclaimedBytes());
        assertTrue(Files.isSameFile(first, second));
        assertTrue(Files.isSameFile(first, third));
        assertTrue(service.scan(List.of(directory), null, null).groups().isEmpty());
    }

    @Test
    void reclaimSkipsAFileThatChangedAfterTheScan() throws IOException {
        byte[] shared = bytes(1);
        file("a/one.jar", shared);
        Path changing = file("b/one.jar", shared);

        StorageDeduplicationService.Report report = service.scan(List.of(directory), null, null);
        Files.write(changing, bytes(7));
        StorageDeduplicationService.Outcome outcome = service.reclaim(report, null, null);

        assertEquals(0, outcome.linked());
        assertEquals(1, outcome.skipped());
        assertEquals(0L, outcome.reclaimedBytes());
        assertFalse(Files.isSameFile(directory.resolve("a/one.jar"), changing));
        assertEquals(List.of(), service.scan(List.of(directory.resolve("zzz")), null, null).groups());
    }

    @Test
    void cancellingTheScanReturnsWithoutGroups() throws IOException {
        byte[] shared = bytes(1);
        file("a/one.jar", shared);
        file("b/one.jar", shared);

        assertTrue(service.scan(List.of(directory), null, () -> true).groups().isEmpty());
    }

    private Path file(String name, byte[] content) throws IOException {
        Path path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        Files.write(path, content);
        return path;
    }

    private static byte[] bytes(long seed) {
        byte[] data = new byte[SIZE];
        new Random(seed).nextBytes(data);
        return data;
    }
}
