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

    @Test
    void selectsStandaloneMojangNativeArtifactsForTheMatchingPlatformAndArchitecture() {
        assertEquals(true, LibraryResolver.isSeparateNativeClassifierApplicable(
                "natives-windows", "windows", "64"));
        assertEquals(false, LibraryResolver.isSeparateNativeClassifierApplicable(
                "natives-windows", "windows", "arm64"));
        assertEquals(true, LibraryResolver.isSeparateNativeClassifierApplicable(
                "natives-windows-arm64", "windows", "arm64"));
        assertEquals(false, LibraryResolver.isSeparateNativeClassifierApplicable(
                "natives-windows-arm64", "windows", "64"));
        assertEquals(true, LibraryResolver.isSeparateNativeClassifierApplicable(
                "natives-linux", "linux", "64"));
        assertEquals(false, LibraryResolver.isSeparateNativeClassifierApplicable(
                "natives-linux", "windows", "64"));
        assertEquals(false, LibraryResolver.isSeparateNativeClassifierApplicable(
                "natives-windows", "windows", "32"));
    }

    @Test
    void resolvesStandaloneMojangNativeArtifactEntries() {
        String previousOs = System.getProperty("os.name");
        String previousArch = System.getProperty("os.arch");
        try {
            System.setProperty("os.name", "Linux");
            System.setProperty("os.arch", "amd64");

            Library library = new Library();
            library.name = "org.lwjgl:lwjgl-freetype:3.4.3-mojang-2.15.0:natives-linux";
            library.downloads = new Library.Downloads();
            library.downloads.artifact = new Library.Artifact();
            library.downloads.artifact.path = "org/lwjgl/lwjgl-freetype/native-linux.jar";

            assertEquals(library.downloads.artifact, LibraryResolver.findNativeArtifact(library));

            System.setProperty("os.arch", "aarch64");
            assertEquals(null, LibraryResolver.findNativeArtifact(library));

            library.name = "org.lwjgl:lwjgl-freetype:3.4.3-mojang-2.15.0:natives-linux-arm64";
            assertEquals(library.downloads.artifact, LibraryResolver.findNativeArtifact(library));
        } finally {
            restoreProperty("os.name", previousOs);
            restoreProperty("os.arch", previousArch);
        }
    }

    private static void restoreProperty(String name, String value) {
        if (value == null) System.clearProperty(name);
        else System.setProperty(name, value);
    }
}
