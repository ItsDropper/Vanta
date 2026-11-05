package dev.itsdropper.vanta.launcher;

import dev.itsdropper.vanta.launcher.model.Instance;
import dev.itsdropper.vanta.launcher.model.Library;
import dev.itsdropper.vanta.launcher.model.VersionManifest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

public class ClasspathBuilder {

    public static List<Path> build(
            Instance instance
    ) {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        LinkedHashSet<Path> classpath =
                new LinkedHashSet<>();

        Path instanceDirectory =
                instance.getDirectory();

        Path vantaDirectory =
                MinecraftLocator.getVantaDirectory();

        VersionManifest manifest =
                VersionReader.readVersion(
                        instanceDirectory
                );

        if (manifest == null) {
            throw new IllegalStateException(
                    "Instance version metadata not found: "
                            + instance.getName()
            );
        }

        // ModLoader 1.2.5 calls File(URI) on its class resource URL.
        // Load its bootstrap from an exploded directory so the URL is file:
        // rather than jar:file:...!/ (which is not a hierarchical URI).
        if ("ModLoader".equalsIgnoreCase(instance.getLoader())) {
            Path bootstrapDirectory = instanceDirectory.resolve("modloader-bootstrap");
            if (!Files.isRegularFile(bootstrapDirectory.resolve("ModLoader.class"))) {
                throw new IllegalStateException(
                        "ModLoader bootstrap class missing. Repair this instance before launching.");
            }
            classpath.add(bootstrapDirectory);
        }

        /*
         * Legacy Minecraft metadata can include asm-all (for example,
         * ASM 4.1 on b1.7.3). Babric uses Fabric Loader, which supplies
         * modern modular ASM. Fabric Loader scans the entire classpath
         * and rejects duplicate org.objectweb.asm classes regardless
         * of JAR ordering, so Babric must omit the legacy aggregate.
         * Other legacy loaders retain asm-all for compatibility.
         */
        LinkedHashSet<Path> legacyAsmAll = new LinkedHashSet<>();
        boolean fabricLoaderProvidesAsm =
                "Babric".equalsIgnoreCase(instance.getLoader());

        if (manifest.libraries != null) {

            for (Library library :
                    manifest.libraries) {

                boolean legacyAsmAllLibrary =
                        library.name != null
                                && library.name.startsWith("org.ow2.asm:asm-all:");

                // Babric runs on Fabric Loader, which brings a modern,
                // modular ASM stack. Keeping Beta 1.7.3's asm-all 4.1
                // alongside ASM 9 duplicates org.objectweb.asm classes;
                // Fabric Loader rejects the classpath before game startup.
                // The legacy aggregate is only retained for non-Fabric
                // legacy launchers that may still depend on it.
                if (fabricLoaderProvidesAsm && legacyAsmAllLibrary) {
                    continue;
                }

                Path jar =
                        LibraryResolver.resolve(
                                vantaDirectory,
                                library
                        );

                if (jar != null) {
                    if (legacyAsmAllLibrary) {
                        legacyAsmAll.add(jar);
                    } else {
                        classpath.add(jar);
                    }
                }
            }
        }

        classpath.addAll(legacyAsmAll);

        Path gameJar =
                instanceDirectory
                        .resolve("minecraft")
                        .resolve("client.jar");

        if (!Files.exists(gameJar)) {
            throw new IllegalStateException(
                    "Minecraft client JAR not found: "
                            + gameJar
            );
        }

        classpath.add(gameJar);

        return new ArrayList<>(
                classpath
        );
    }

    public static List<Path> buildNativeJars(
            Instance instance
    ) {

        if (instance == null) {
            throw new IllegalArgumentException(
                    "Instance cannot be null."
            );
        }

        LinkedHashSet<Path> nativeJars =
                new LinkedHashSet<>();

        Path vantaDirectory =
                MinecraftLocator.getVantaDirectory();

        VersionManifest manifest =
                VersionReader.readVersion(
                        instance.getDirectory()
                );

        if (manifest == null) {
            throw new IllegalStateException(
                    "Instance version metadata not found: "
                            + instance.getName()
            );
        }

        if (manifest.libraries != null) {

            for (Library library :
                    manifest.libraries) {

                Path nativeJar =
                        LibraryResolver.resolveNative(
                                vantaDirectory,
                                library
                        );

                if (nativeJar != null) {
                    nativeJars.add(nativeJar);
                }
            }
        }

        return new ArrayList<>(
                nativeJars
        );
    }
}

