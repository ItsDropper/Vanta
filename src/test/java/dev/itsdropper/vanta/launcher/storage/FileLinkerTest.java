package dev.itsdropper.vanta.launcher.storage;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileLinkerTest {

    @TempDir
    Path directory;

    @Test
    void publishCreatesALinkWithIdenticalContent() throws IOException {
        Path source = write("source.jar", "alpha");
        Path target = directory.resolve("nested/target.jar");

        FileLinker.Method method = FileLinker.publish(source, target);

        assertNotEquals(FileLinker.Method.ALREADY_LINKED, method);
        assertArrayEquals(Files.readAllBytes(source), Files.readAllBytes(target));
        assertNoTemporaryFiles();
    }

    @Test
    void publishReplacesAnExistingTargetWithoutTouchingTheOldInode() throws IOException {
        Path source = write("new.jar", "new content");
        Path target = write("target.jar", "old content");
        Path alias = directory.resolve("alias.jar");
        Files.createLink(alias, target);

        FileLinker.publish(source, target);

        assertEquals("new content", Files.readString(target));
        assertEquals("old content", Files.readString(alias));
    }

    @Test
    void publishingTheSameFileTwiceIsANoOp() throws IOException {
        Path source = write("source.jar", "alpha");
        Path target = directory.resolve("target.jar");
        FileLinker.publish(source, target);

        assertEquals(FileLinker.Method.ALREADY_LINKED, FileLinker.publish(source, target));
    }

    @Test
    void replaceWithLinkSharesTheCanonicalFile() throws IOException {
        Path canonical = write("canonical.jar", "same bytes");
        Path duplicate = write("duplicate.jar", "same bytes");

        assertTrue(FileLinker.replaceWithLink(canonical, duplicate));

        assertTrue(Files.isSameFile(canonical, duplicate));
        assertNoTemporaryFiles();
    }

    @Test
    void replaceWithLinkRefusesTheSameFile() throws IOException {
        Path canonical = write("canonical.jar", "same bytes");

        assertFalse(FileLinker.replaceWithLink(canonical, canonical));
        assertEquals("same bytes", Files.readString(canonical));
    }

    @Test
    void replaceWithLinkLeavesTheDuplicateAloneWhenTheCanonicalIsMissing() throws IOException {
        Path duplicate = write("duplicate.jar", "kept");

        assertFalse(FileLinker.replaceWithLink(directory.resolve("missing.jar"), duplicate));

        assertEquals("kept", Files.readString(duplicate));
        assertNoTemporaryFiles();
    }

    private Path write(String name, String content) throws IOException {
        Path path = directory.resolve(name);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
        return path;
    }

    private void assertNoTemporaryFiles() throws IOException {
        try (Stream<Path> files = Files.walk(directory)) {
            assertEquals(0, files.filter(path -> path.getFileName().toString().endsWith(".tmp")).count());
        }
    }
}
