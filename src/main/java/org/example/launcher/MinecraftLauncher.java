package org.example.launcher;

import org.example.launcher.account.Account;
import org.example.launcher.java.JavaLocator;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MinecraftLauncher {

    public static Process launch(
            Account account,
            LaunchData data
    ) throws Exception {

        Path minecraft =
                data.minecraftDirectory
                        .toAbsolutePath()
                        .normalize();

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

        if (data.javaVersion == 25 && "26.3".equals(data.version)) {
            command.add("-XX:-TieredCompilation");
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

        if (data.javaVersion >= 22) {
            command.add("--enable-native-access=ALL-UNNAMED");
        }

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
        // USER PROPERTIES
        // =============================================================

        /*
         * Minecraft 1.8 requires the userProperties option.
         *
         * Modern Minecraft versions that accept this argument
         * tolerate an empty JSON object.
         */
        command.add("--userProperties");

        command.add("{}");

        // =============================================================
        // RESOLUTION
        // =============================================================

        /*
         * Old Minecraft versions using LWJGL 2 can behave badly when
         * Vanta injects modern-style resolution arguments.
         *
         * Only pass Vanta's resolution settings to newer Minecraft
         * versions for now.
         */
        if (data.width > 0 && data.height > 0) {

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

        /*
         * Same diagnostic restriction as resolution.
         */
        if (data.javaVersion >= 17
                && data.fullscreen) {

            command.add(
                    "--fullscreen"
            );
        }

        // =============================================================
        // SERVER
        // =============================================================

        if (data.serverHost != null
                && !data.serverHost.isBlank()) {

            int port =
                    data.serverPort > 0
                            ? data.serverPort
                            : 25565;

            /*
             * Minecraft 1.20+ uses Quick Play for direct server
             * launches. The old --server/--port arguments are no
             * longer supported.
             *
             * Quick Play performs the connection after Minecraft
             * finishes its normal startup/resource loading.
             */
            if (supportsQuickPlay(data.version)) {
                command.add("--quickPlayMultiplayer");
                command.add(data.serverHost + ":" + port);
            } else {
                command.add("--server");
                command.add(data.serverHost);

                command.add("--port");
                command.add(String.valueOf(port));
            }
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
                "Java version: "
                        + data.javaVersion
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

        System.out.println(
                "Minecraft game directory: "
                        + minecraft
        );

        // =============================================================
        // PROCESS
        // =============================================================

        ProcessBuilder processBuilder =
                new ProcessBuilder(
                        command
                );

        /*
         * Make the Minecraft instance directory the process working
         * directory as well as the explicit --gameDir. This prevents
         * relative paths such as "config" from resolving beside
         * Vanta.exe or another launcher working directory.
         */
        processBuilder.directory(
                minecraft.toFile()
        );

        /*
         * Do NOT use inheritIO() here.
         *
         * LaunchService reads Minecraft's output.
         */
        processBuilder.redirectErrorStream(
                true
        );

        Process process =
                processBuilder.start();

        System.out.println(
                "Started Minecraft with PID: "
                        + process.pid()
        );

        return process;
    }

    private static boolean supportsQuickPlay(
            String version
    ) {
        if (version == null || version.isBlank()) {
            return false;
        }

        String[] parts = version.split("\\.");

        try {
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1
                    ? Integer.parseInt(parts[1])
                    : 0;

            return major > 1 || (major == 1 && minor >= 20);
        } catch (NumberFormatException ignored) {
            return false;
        }
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

        List<String> result =
                new ArrayList<>();

        StringBuilder current =
                new StringBuilder();

        boolean insideQuotes = false;
        char quote = 0;

        for (int i = 0;
             i < arguments.length();
             i++) {

            char c =
                    arguments.charAt(i);

            if ((c == '"' || c == '\'')
                    && (i == 0
                    || arguments.charAt(i - 1) != '\\')) {

                if (insideQuotes) {

                    if (c == quote) {
                        insideQuotes = false;
                    } else {
                        current.append(c);
                    }

                } else {

                    insideQuotes = true;
                    quote = c;
                }

                continue;
            }

            if (Character.isWhitespace(c)
                    && !insideQuotes) {

                if (!current.isEmpty()) {

                    result.add(
                            current.toString()
                    );

                    current.setLength(0);
                }

                continue;
            }

            current.append(c);
        }

        if (!current.isEmpty()) {

            result.add(
                    current.toString()
            );
        }

        return result;
    }
}
