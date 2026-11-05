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

        /*
         * Legacy Minecraft metadata can include asm-all (for example,
         * ASM 4.1 on b1.7.3). Modern Fabric-family loaders also provide
         * modular ASM artifacts. If asm-all is placed first, its older
         * org.objectweb.asm classes shadow the loader's newer ASM and
         * can make ClassVisitor reject the loader's ASM API constant.
         *
         * Keep asm-all on the classpath for legacy launch components,
         * but append it after the loader's modular ASM libraries.
         */
        LinkedHashSet<Path> legacyAsmAll = new LinkedHashSet<>();

        if (manifest.libraries != null) {

            for (Library library :
                    manifest.libraries) {

                Path jar =
                        LibraryResolver.resolve(
                                vantaDirectory,
                                library
                        );

                if (jar != null) {
                    if (library.name != null
                            && library.name.startsWith("org.ow2.asm:asm-all:")) {
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

