package org.example.launcher;

import org.example.launcher.account.Account;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MinecraftLauncher {

    public static Process launch(
            Account account,
            LaunchData data
    ) throws Exception {

        Path minecraft =
                data.minecraftDirectory;

        // =============================================================
        // CLASSPATH
        // =============================================================

        System.out.println(
                "Building Minecraft classpath..."
        );

        String cp =
                ClasspathStringBuilder.build(
                        data.classpath
                );

        // =============================================================
        // NATIVES
        // =============================================================

        System.out.println(
                "Extracting natives..."
        );

        NativeExtractor.extractAll(
                data.nativeJars,
                data.nativesDirectory
        );

        // =============================================================
        // JAVA
        // =============================================================

        String javaExecutable;

        if (data.javaPath != null
                && !data.javaPath.isBlank()) {

            javaExecutable =
                    data.javaPath;

        } else {

            javaExecutable =
                    JavaLocator.getJava(
                            data.javaVersion
                    );
        }

        // =============================================================
        // COMMAND
        // =============================================================

        List<String> command =
                new ArrayList<>();

        command.add(
                javaExecutable
        );

        // =============================================================
        // MEMORY
        // =============================================================

        if (data.ramMb > 0) {

            command.add(
                    "-Xmx"
                            + data.ramMb
                            + "M"
            );
        }

        // =============================================================
        // JAVA ARGUMENTS
        // =============================================================

        if (data.javaArguments != null
                && !data.javaArguments.isBlank()) {

            command.addAll(
                    splitArguments(
                            data.javaArguments
                    )
            );
        }

        // =============================================================
        // JAVA LIBRARY PATH
        // =============================================================

        command.add(
                "-Djava.library.path="
                        + data.nativesDirectory
        );

        // =============================================================
        // MINECRAFT ASSETS
        // =============================================================

        Path assets =
                MinecraftLocator
                        .getVantaDirectory()
                        .resolve("assets");

        command.add(
                "-Dminecraft.assets.root="
                        + assets
        );

        // =============================================================
        // CLASSPATH
        // =============================================================

        command.add("-cp");

        command.add(cp);

        // =============================================================
        // MAIN CLASS
        // =============================================================

        command.add(
                data.mainClass
        );

        // =============================================================
        // GAME DIRECTORY
        // =============================================================

        command.add("--gameDir");

        command.add(
                minecraft.toString()
        );

        // =============================================================
        // ASSETS DIRECTORY
        // =============================================================

        command.add("--assetsDir");

        command.add(
                assets.toString()
        );

        // =============================================================
        // VERSION
        // =============================================================

        command.add("--version");

        command.add(
                data.version
        );

        // =============================================================
        // ASSET INDEX
        // =============================================================

        command.add("--assetIndex");

        command.add(
                data.assetIndex
        );

        // =============================================================
        // ACCOUNT
        // =============================================================

        command.add("--username");

        command.add(
                account.getUsername()
        );

        command.add("--uuid");

        command.add(
                account
                        .getUuid()
                        .replace("-", "")
        );

        command.add("--accessToken");

        command.add(
                account.getAccessToken()
        );

        command.add("--userType");

        command.add("msa");

        // =============================================================
        // RESOLUTION
        // =============================================================

        if (data.width > 0
                && data.height > 0) {

            command.add("--width");

            command.add(
                    String.valueOf(
                            data.width
                    )
            );

            command.add("--height");

            command.add(
                    String.valueOf(
                            data.height
                    )
            );
        }

        // =============================================================
        // FULLSCREEN
        // =============================================================

        if (data.fullscreen) {

            command.add(
                    "--fullscreen"
            );
        }

        // =============================================================
        // GAME ARGUMENTS
        // =============================================================

        if (data.gameArguments != null
                && !data.gameArguments.isBlank()) {

            command.addAll(
                    splitArguments(
                            data.gameArguments
                    )
            );
        }

        // =============================================================
        // DEBUG
        // =============================================================

        System.out.println(
                "Starting Minecraft..."
        );

        System.out.println(
                "Java: "
                        + javaExecutable
        );

        System.out.println(
                "RAM: "
                        + data.ramMb
                        + " MB"
        );

        System.out.println(
                "Resolution: "
                        + data.width
                        + "x"
                        + data.height
        );

        System.out.println(
                "Fullscreen: "
                        + data.fullscreen
        );

        // =============================================================
        // PROCESS
        // =============================================================

        ProcessBuilder processBuilder =
                new ProcessBuilder(
                        command
                );

        processBuilder
                .redirectErrorStream(true);

        processBuilder.inheritIO();

        Process process =
                processBuilder.start();

        System.out.println(
                "Started Minecraft with PID: "
                        + process.pid()
        );

        return process;
    }

    // =============================================================
    // ARGUMENT PARSER
    // =============================================================

    private static List<String> splitArguments(
            String arguments
    ) {

        if (arguments == null
                || arguments.isBlank()) {

            return List.of();
        }

        return Arrays.asList(
                arguments.trim().split("\\s+")
        );
    }
}