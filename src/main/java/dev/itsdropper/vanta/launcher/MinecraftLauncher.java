package dev.itsdropper.vanta.launcher;

import dev.itsdropper.vanta.launcher.account.Account;
import dev.itsdropper.vanta.launcher.java.JavaLocator;
import dev.itsdropper.vanta.launcher.instance.RiftModMetadataCompatibility;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MinecraftLauncher {

    private static boolean requiresNeoForgeFmlVersion(String minecraftVersion) {
        if (minecraftVersion == null || minecraftVersion.isBlank()) return true;
        String firstComponent = minecraftVersion.split("\\.", 2)[0];
        try {
            return Integer.parseInt(firstComponent) < 26;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    public static Process launch(
            Account account,
            LaunchData data
    ) throws Exception {

        if (account == null) {
            throw new IllegalArgumentException("A Microsoft account is required to launch Minecraft.");
        }
        if (account.getUsername() == null || account.getUsername().isBlank()
                || account.getUuid() == null || account.getUuid().isBlank()
                || account.getAccessToken() == null || account.getAccessToken().isBlank()) {
            throw new IllegalStateException("The active Microsoft account session is incomplete. Sign in again before launching.");
        }
        if (data == null || data.minecraftDirectory == null) {
            throw new IllegalArgumentException("Minecraft launch data or instance directory is missing.");
        }

        Path minecraft =
                data.minecraftDirectory
                        .toAbsolutePath()
                        .normalize();

        if (!Files.isDirectory(minecraft)) {
            throw new IllegalStateException("Minecraft instance directory does not exist: " + minecraft);
        }
        if (!Files.isWritable(minecraft)) {
            throw new IllegalStateException("Minecraft instance directory is not writable: " + minecraft);
        }

        // Rift's Gson metadata parser leaves omitted listener sides null.
        // Normalize instance-local mod metadata before Rift scans the mods folder.
        if ("Rift".equalsIgnoreCase(data.loader)) {
            int normalizedMods = RiftModMetadataCompatibility.normalizeModsDirectory(
                    minecraft.resolve("mods"));
            if (normalizedMods > 0) {
                System.out.println("[Vanta Rift Compatibility] Normalized "
                        + normalizedMods + " mod archive(s).");
            }
        }

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

        /*
         * Match desktop launchers that allow Java networking to use the
         * operating system's configured proxy. Without this property, the
         * JDK normally ignores system proxy settings, so mods making their
         * own HTTP requests can fail even when the same instance works in
         * another launcher. This does not override explicit proxy settings
         * supplied by a mod or the user's JVM arguments.
         */
        command.add("-Djava.net.useSystemProxies=true");

        if ("VantaLoader".equalsIgnoreCase(data.loader)) {
            command.addAll(vantaLoaderJvmArguments(minecraft, data.version, data.minecraftMappings));
        }

        // =============================================================
        // NILLOADER JAVA AGENT
        // =============================================================

        if (data.javaAgent != null) {
            Path agent = data.javaAgent.toAbsolutePath().normalize();
            if (!Files.isRegularFile(agent)) {
                throw new IllegalStateException("Java agent not found: " + agent);
            }
            command.add("-javaagent:" + agent);
        }

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

        // NeoForge publishes mandatory JVM flags in its version profile.
        // Preserve their ordering and resolve profile path placeholders in
        // LaunchDataBuilder rather than maintaining an incomplete local copy.
        if ("NeoForge".equalsIgnoreCase(data.loader)
                && data.neoForgeJvmArguments != null) {
            command.addAll(data.neoForgeJvmArguments);
        }

        if (data.javaVersion == 25 && "26.3".equals(data.version)) {
            command.add("-XX:-TieredCompilation");
        }

        // =============================================================
        // OPTIONAL JDK MODULES
        // =============================================================

        /*
         * Essential's shared resource-pack server uses
         * com.sun.net.httpserver.HttpServer. Since Java 9, the
         * jdk.httpserver module is not guaranteed to be resolved by
         * default for class-path applications. Resolve it explicitly
         * for modern Minecraft runtimes so Essential can initialize.
         * Java 8 predates the module system and must not receive this
         * option.
         */
        if (data.javaVersion >= 9 && !"Stein".equalsIgnoreCase(data.loader)) {
            command.add("--add-modules=jdk.httpserver");
        }

        if ("Stein".equalsIgnoreCase(data.loader)
                && Platform.operatingSystem() == Platform.OperatingSystem.MACOS) {
            command.add("-XstartOnFirstThread");
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

        if (data.minecraftArguments != null && !data.minecraftArguments.isBlank()
                && !"Forge".equalsIgnoreCase(data.loader)
                && !"Rift".equalsIgnoreCase(data.loader)) {
            command.addAll(buildLegacyGameArguments(
                    data.minecraftArguments,
                    account,
                    data,
                    minecraft,
                    assets
            ));

            if (data.serverHost != null && !data.serverHost.isBlank()) {
                int port = data.serverPort > 0 ? data.serverPort : 25565;
                command.add("--server");
                command.add(data.serverHost);
                command.add("--port");
                command.add(String.valueOf(port));
            }

            if (data.gameArguments != null && !data.gameArguments.isBlank()) {
                command.addAll(splitArguments(data.gameArguments));
            }
        } else {
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
            // LOADER-SPECIFIC ARGUMENTS
            // =============================================================
    
            /*
             * Forge uses ModLauncher and requires its launch target plus
             * Forge identity arguments. Vanta keeps instances isolated,
             * so add the equivalent arguments directly to the command.
             */
            if ("Forge".equalsIgnoreCase(data.loader)) {
                if (data.loaderVersion == null
                        || data.loaderVersion.isBlank()) {
                    throw new IllegalStateException(
                            "Forge instance has no Forge version."
                    );
                }
    
                command.add("--launchTarget");
                command.add("forge_client");
    
                command.add("--fml.forgeVersion");
                command.add(data.loaderVersion);
    
                command.add("--fml.mcVersion");
                command.add(data.version);
    
                command.add("--fml.forgeGroup");
                command.add("net.minecraftforge");
            }

            if ("NeoForge".equalsIgnoreCase(data.loader)) {
                boolean requiresFmlVersion = requiresNeoForgeFmlVersion(data.version);
                if (data.loaderVersion == null || data.loaderVersion.isBlank()
                        || (requiresFmlVersion && (data.neoForgeFmlVersion == null || data.neoForgeFmlVersion.isBlank()))
                        || data.neoFormVersion == null || data.neoFormVersion.isBlank()) {
                    throw new IllegalStateException(
                            "NeoForge launch identity is incomplete. Repair this instance before launching.");
                }

                // NeoForge's FML VersionInfo requires all three version values.
                // These must be passed as program arguments, not inferred from
                // the classpath or the Minecraft --version argument.
                command.add("--fml.neoForgeVersion");
                command.add(data.loaderVersion);

                if (requiresFmlVersion) {
                    command.add("--fml.fmlVersion");
                    command.add(data.neoForgeFmlVersion);
                }

                command.add("--fml.mcVersion");
                command.add(data.version);

                command.add("--fml.neoFormVersion");
                command.add(data.neoFormVersion);

                /*
                 * Legacy NeoForge releases are bootstrapped through
                 * ModLauncher and require an explicit launch target.
                 * Minecraft 26.1+ uses NeoForge's dedicated startup.Client
                 * main class from version metadata; passing the legacy
                 * --launchTarget option to that entrypoint causes FML
                 * startup to abort before the game content classloader is
                 * completed.
                 */
                if (requiresFmlVersion) {
                    command.add("--launchTarget");
                    command.add("forgeclient");
                }
            }
    
            // Rift 1.13 stores its tweaker in the legacy profile field,
            // but the inherited Minecraft 1.13 profile uses modern game
            // arguments. Build the standard authenticated arguments above,
            // then forward only Rift tweaker declarations to LaunchWrapper.
            if ("Rift".equalsIgnoreCase(data.loader)) {
                command.addAll(riftTweakerArguments(data.minecraftArguments));
            }

            // =============================================================
            // ASSET INDEX
            // =============================================================
    
            if (data.assetIndex != null && !data.assetIndex.isBlank()) {
                command.add("--assetIndex");
                command.add(data.assetIndex);
            }
    
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
    
    
        }

        // Rift's legacy tweaker dereferences LaunchWrapper's assetsDir
        // unconditionally in acceptOptions(). Some inherited 1.13 argument
        // templates omit one or more LaunchWrapper bootstrap options, so
        // normalize these three values after profile/game arguments are built.
        if ("Rift".equalsIgnoreCase(data.loader)) {
            ensureLaunchWrapperOption(command, data.mainClass, "--gameDir", minecraft.toString());
            ensureLaunchWrapperOption(command, data.mainClass, "--assetsDir", assets.toString());
            ensureLaunchWrapperOption(command, data.mainClass, "--version", data.version);
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

    static List<String> riftTweakerArguments(String template) {
        if (template == null || template.isBlank()) {
            throw new IllegalStateException("Rift profile is missing its launch tweaker.");
        }

        List<String> tokens = splitArguments(template);
        List<String> result = new ArrayList<>();
        for (int i = 0; i < tokens.size(); i++) {
            String token = tokens.get(i);
            String tweaker;
            if ("--tweakClass".equals(token)) {
                if (++i >= tokens.size()) {
                    throw new IllegalStateException("Rift profile has --tweakClass without a class name.");
                }
                tweaker = tokens.get(i);
            } else if (token.startsWith("--tweakClass=")) {
                tweaker = token.substring("--tweakClass=".length());
            } else {
                continue;
            }

            if (!tweaker.matches("[A-Za-z_$][A-Za-z0-9_$.]*")) {
                throw new IllegalStateException("Rift profile contains an invalid launch tweaker class.");
            }
            result.add("--tweakClass");
            result.add(tweaker);
        }

        if (result.isEmpty()) {
            throw new IllegalStateException("Rift profile is missing its launch tweaker.");
        }
        return List.copyOf(result);
    }

    static List<String> vantaLoaderJvmArguments(
            Path instanceDirectory,
            String minecraftVersion,
            Path mappings
    ) {
        if (instanceDirectory == null) {
            throw new IllegalStateException("VantaLoader instance directory is missing.");
        }
        if (minecraftVersion == null || minecraftVersion.isBlank()) {
            throw new IllegalStateException("VantaLoader Minecraft version is missing.");
        }
        if (mappings == null || !Files.isRegularFile(mappings)) {
            throw new IllegalStateException(
                    "Official Minecraft client mappings are missing for this VantaLoader instance.");
        }

        Path normalizedInstance = instanceDirectory.toAbsolutePath().normalize();
        Path normalizedMappings = mappings.toAbsolutePath().normalize();
        return List.of(
                "-Dvanta.instance.directory=" + normalizedInstance,
                "-Dvanta.minecraft.version=" + minecraftVersion,
                "-Dvanta.environment=client",
                "-Dvanta.minecraft.mappings=" + normalizedMappings
        );
    }

    static void ensureLaunchWrapperOption(
            List<String> command,
            String mainClass,
            String option,
            String value
    ) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing required Rift LaunchWrapper value for " + option + ".");
        }
        int mainClassIndex = command.indexOf(mainClass);
        if (mainClassIndex < 0) {
            throw new IllegalStateException("LaunchWrapper main class is missing from the command.");
        }

        for (int i = mainClassIndex + 1; i < command.size();) {
            if (option.equals(command.get(i))) {
                command.remove(i);
                if (i < command.size() && !command.get(i).startsWith("--")) {
                    command.remove(i);
                }
            } else {
                i++;
            }
        }
        command.add(option);
        command.add(value);
    }

    /**
     * Historical Alpha/Beta metadata carries its own complete flat game
     * argument template. Those clients use the legacy auth_session token
     * format and must not receive the modern launcher's hard-coded flags.
     */
    private static List<String> buildLegacyGameArguments(
            String template,
            Account account,
            LaunchData data,
            Path gameDirectory,
            Path assetsDirectory
    ) {
        String uuid = account.getUuid().replace("-", "");
        String session = "token:" + account.getAccessToken() + ":" + uuid;

        java.util.Map<String, String> values = java.util.Map.ofEntries(
                java.util.Map.entry("auth_player_name", account.getUsername()),
                java.util.Map.entry("auth_session", session),
                java.util.Map.entry("auth_uuid", uuid),
                java.util.Map.entry("auth_access_token", account.getAccessToken()),
                java.util.Map.entry("game_directory", gameDirectory.toString()),
                java.util.Map.entry("game_assets",
                        assetsDirectory.resolve("virtual").resolve(
                                data.assetIndex == null || data.assetIndex.isBlank()
                                        ? "legacy" : data.assetIndex
                        ).toString()),
                java.util.Map.entry("assets_root", assetsDirectory.toString()),
                java.util.Map.entry("assets_index_name",
                        data.assetIndex == null || data.assetIndex.isBlank()
                                ? "legacy" : data.assetIndex),
                java.util.Map.entry("version_name", data.version),
                java.util.Map.entry("version_type",
                        data.versionType == null || data.versionType.isBlank()
                                ? "release" : data.versionType),
                java.util.Map.entry("user_type", "msa"),
                java.util.Map.entry("user_properties", "{}")
        );

        return LegacyArgumentResolver.resolve(template, values);
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

    private static List<String> splitArguments(String arguments) {
        return LegacyArgumentResolver.tokenize(arguments);
    }
}
