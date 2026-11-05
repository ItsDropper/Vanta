package dev.itsdropper.vanta.launcher;

import dev.itsdropper.vanta.launcher.model.Library;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

class NativeExtractorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void extractsLinuxSharedObjectsIncludingVersionedNames() throws Exception {
        String previous = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Linux");
            Path archive = createArchive("linux-natives.jar",
                    "liblwjgl.so", "libglfw.so.3", "lwjgl.dll", "libmac.dylib");
            Path output = temporaryDirectory.resolve("linux-natives");

            NativeExtractor.extract(archive, output);

            assertTrue(Files.isRegularFile(output.resolve("liblwjgl.so")));
            assertTrue(Files.isRegularFile(output.resolve("libglfw.so.3")));
            assertFalse(Files.exists(output.resolve("lwjgl.dll")));
            assertFalse(Files.exists(output.resolve("libmac.dylib")));
        } finally {
            restoreProperty("os.name", previous);
        }
    }

    @Test
    void extractsMacNativeLibraries() throws Exception {
        String previous = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Mac OS X");
            Path archive = createArchive("mac-natives.jar",
                    "liblwjgl.dylib", "liblwjgl.jnilib", "libcompat.so", "lwjgl.dll");
            Path output = temporaryDirectory.resolve("mac-natives");

            NativeExtractor.extract(archive, output);

            assertTrue(Files.isRegularFile(output.resolve("liblwjgl.dylib")));
            assertTrue(Files.isRegularFile(output.resolve("liblwjgl.jnilib")));
            assertTrue(Files.isRegularFile(output.resolve("libcompat.so")));
            assertFalse(Files.exists(output.resolve("lwjgl.dll")));
        } finally {
            restoreProperty("os.name", previous);
        }
    }

    @Test
    void extractsWindowsDllsOnlyOnWindows() throws Exception {
        String previous = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Windows 11");
            Path archive = createArchive("windows-natives.jar",
                    "lwjgl.dll", "liblwjgl.so", "liblwjgl.dylib");
            Path output = temporaryDirectory.resolve("windows-natives");

            NativeExtractor.extract(archive, output);

            assertTrue(Files.isRegularFile(output.resolve("lwjgl.dll")));
            assertFalse(Files.exists(output.resolve("liblwjgl.so")));
            assertFalse(Files.exists(output.resolve("liblwjgl.dylib")));
        } finally {
            restoreProperty("os.name", previous);
        }
    }

    @Test
    void publishesNativeDirectoryWithoutStaleFiles() throws Exception {
        String previous = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Linux");
            Path output = temporaryDirectory.resolve("instance/natives");
            Files.createDirectories(output);
            Files.writeString(output.resolve("old-library.so"), "stale");
            Files.writeString(output.resolve("old-windows.dll"), "stale");
            Path archive = createArchive("replacement.jar", "libcurrent.so");

            NativeExtractor.extractAll(java.util.List.of(archive), output);

            assertTrue(Files.isRegularFile(output.resolve("libcurrent.so")));
            assertFalse(Files.exists(output.resolve("old-library.so")));
            assertFalse(Files.exists(output.resolve("old-windows.dll")));
        } finally {
            restoreProperty("os.name", previous);
        }
    }

    @Test
    void failedNativeExtractionPreservesPreviousDirectory() throws Exception {
        String previous = System.getProperty("os.name");
        try {
            System.setProperty("os.name", "Linux");
            Path output = temporaryDirectory.resolve("instance/natives");
            Files.createDirectories(output);
            Path previousNative = output.resolve("known-good.so");
            Files.writeString(previousNative, "known-good");
            Path invalidArchive = temporaryDirectory.resolve("missing.jar");

            assertThrows(Exception.class,
                    () -> NativeExtractor.extractAll(java.util.List.of(invalidArchive), output));

            assertEquals("known-good", Files.readString(previousNative));
            try (var children = Files.list(output.getParent())) {
                assertFalse(children.anyMatch(path ->
                        path.getFileName().toString().contains(".staging-")));
            }
        } finally {
            restoreProperty("os.name", previous);
        }
    }

    @Test
    void selectsArm64ModernNativeClassifierOnLinux() {
        String previousOs = System.getProperty("os.name");
        String previousArch = System.getProperty("os.arch");
        try {
            System.setProperty("os.name", "Linux");
            System.setProperty("os.arch", "aarch64");
            Library library = new Library();
            library.downloads = new Library.Downloads();
            library.downloads.classifiers = new LinkedHashMap<>();
            Library.Artifact x64 = artifact("natives-linux-64.jar");
            Library.Artifact arm64 = artifact("natives-linux-arm64.jar");
            library.downloads.classifiers.put("natives-linux-64", x64);
            library.downloads.classifiers.put("natives-linux-arm64", arm64);

            assertSame(arm64, LibraryResolver.findNativeArtifact(library));
        } finally {
            restoreProperty("os.name", previousOs);
            restoreProperty("os.arch", previousArch);
        }
    }

    @Test
    void selectsMacLegacyNativeClassifier() {
        String previousOs = System.getProperty("os.name");
        String previousArch = System.getProperty("os.arch");
        try {
            System.setProperty("os.name", "Darwin");
            System.setProperty("os.arch", "x86_64");
            Library library = new Library();
            library.natives = Map.of("osx", "natives-macos-${arch}");
            library.downloads = new Library.Downloads();
            library.downloads.classifiers = Map.of(
                    "natives-macos-64", artifact("natives-macos-64.jar"));

            assertEquals("natives-macos-64.jar",
                    LibraryResolver.findNativeArtifact(library).path);
        } finally {
            restoreProperty("os.name", previousOs);
            restoreProperty("os.arch", previousArch);
        }
    }

    private Path createArchive(String name, String... entries) throws Exception {
        Path archive = temporaryDirectory.resolve(name);
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            for (String entry : entries) {
                zip.putNextEntry(new ZipEntry(entry));
                zip.write(new byte[]{1, 2, 3});
                zip.closeEntry();
            }
        }
        return archive;
    }

    private static Library.Artifact artifact(String path) {
        Library.Artifact artifact = new Library.Artifact();
        artifact.path = path;
        artifact.url = "https://example.invalid/" + path;
        return artifact;
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
    }
}
