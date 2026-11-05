package dev.itsdropper.vanta.launcher.instance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.LibraryResolver;
import dev.itsdropper.vanta.launcher.MinecraftLocator;
import dev.itsdropper.vanta.launcher.NativeExtractor;
import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.model.Library;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Extracts only the native archives selected for the current host platform. */
public final class NativeInstaller {
    private static final ObjectMapper JSON = new ObjectMapper();

    private NativeInstaller() {}

    public static Path extract(Instance instance, JsonNode metadata) throws Exception {
        if (instance == null || metadata == null) {
            throw new IllegalArgumentException("Instance and version metadata are required.");
        }

        Path nativesDirectory = instance.getDirectory().resolve("natives");
        Files.createDirectories(nativesDirectory);

        JsonNode libraries = metadata.get("libraries");
        if (libraries == null || !libraries.isArray()) {
            return nativesDirectory;
        }

        List<Path> nativeJars = new ArrayList<>();
        Path minecraftDirectory = MinecraftLocator.getVantaDirectory();
        for (JsonNode libraryNode : libraries) {
            Library library = JSON.treeToValue(libraryNode, Library.class);
            Path nativeJar = LibraryResolver.resolveNative(minecraftDirectory, library);
            if (nativeJar != null) nativeJars.add(nativeJar);
        }

        NativeExtractor.extractAll(nativeJars, nativesDirectory);
        return nativesDirectory;
    }
}
