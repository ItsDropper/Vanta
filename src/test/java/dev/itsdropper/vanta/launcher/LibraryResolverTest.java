package dev.itsdropper.vanta.launcher;

import dev.itsdropper.vanta.launcher.model.Library;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LibraryResolverTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void fallsBackToMavenCoordinateWhenArtifactMetadataHasNoPath() throws Exception {
        String previousOs = System.getProperty("os.name");
        String previousHome = System.getProperty("user.home");
        try {
            System.setProperty("os.name", "Linux");
            System.setProperty("user.home", temporaryDirectory.toString());

            Path expected = MinecraftLocator.getLibrariesDirectoryPath().resolve(
                    "net/java/jinput/jinput/2.0.5/jinput-2.0.5.jar");
            Files.createDirectories(expected.getParent());
            Files.write(expected, new byte[]{1, 2, 3});

            Library library = new Library();
            library.name = "net.java.jinput:jinput:2.0.5";
            library.downloads = new Library.Downloads();
            library.downloads.artifact = new Library.Artifact();

            assertEquals(expected, LibraryResolver.resolve(null, library));
        } finally {
            restoreProperty("os.name", previousOs);
            restoreProperty("user.home", previousHome);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
    }
}
