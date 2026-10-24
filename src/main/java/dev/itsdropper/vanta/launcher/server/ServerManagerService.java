package dev.itsdropper.vanta.launcher.server;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.itsdropper.vanta.launcher.MinecraftLocator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Local Java server process management. Profiles and console output stay on-device.
 */
public final class ServerManagerService implements AutoCloseable {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter BACKUP_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ExecutorService IO = Executors.newCachedThreadPool(r -> {
        Thread thread = new Thread(r, "vanta-server-manager");
        thread.setDaemon(true);
        return thread;
    });

    public enum State { STOPPED, STARTING, RUNNING, STOPPING, CRASHED }

    public static final class Profile {
        public String id;
        public String name;
        public String directory;
        public String jar;
        public String javaExecutable = "java";
        public int minRamMb = 1024;
        public int maxRamMb = 4096;
        public int port = 25565;
        public int maxPlayers = 20;
        public String motd = "A Minecraft Server";
        public boolean eulaAccepted;

        public Profile() {}

        public Profile(String id, String name, Path directory, Path jar) {
            this.id = id;
            this.name = name;
            this.directory = directory.toAbsolutePath().normalize().toString();
            this.jar = jar.toAbsolutePath().normalize().toString();
        }

        public Path directoryPath() { return Path.of(directory).toAbsolutePath().normalize(); }
        public Path jarPath() { return Path.of(jar).toAbsolutePath().normalize(); }
    }

    public record Snapshot(Profile profile, State state, int exitCode, String lastExitReason) {}

    private static final class RuntimeState {
        volatile State state = State.STOPPED;
        volatile Process process;
        volatile BufferedWriter stdin;
        volatile int exitCode = Integer.MIN_VALUE;
        volatile String lastExitReason = "";
    }

    private final Path configFile;
    private final Map<String, Profile> profiles = new LinkedHashMap<>();
    private final Map<String, RuntimeState> runtime = new ConcurrentHashMap<>();
    private volatile BiConsumer<String, String> consoleListener = (id, line) -> {};
    private volatile BiConsumer<String, Snapshot> stateListener = (id, snapshot) -> {};

    public ServerManagerService() {
        configFile = MinecraftLocator.getVantaDirectory().resolve("server-manager.json");
        load();
    }

    public synchronized List<Profile> profiles() {
        return profiles.values().stream().map(ServerManagerService::copy).toList();
    }

    public synchronized Optional<Profile> profile(String id) {
        Profile p = profiles.get(id);
        return p == null ? Optional.empty() : Optional.of(copy(p));
    }

    public void setConsoleListener(BiConsumer<String, String> listener) {
        consoleListener = listener == null ? (id, line) -> {} : listener;
    }

    public void setStateListener(BiConsumer<String, Snapshot> listener) {
        stateListener = listener == null ? (id, snapshot) -> {} : listener;
    }

    public synchronized Profile addProfile(String name, Path directory, Path jar) throws IOException {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("Server name is required.");
        if (directory == null || !Files.isDirectory(directory)) throw new IllegalArgumentException("Choose an existing server directory.");
        if (jar == null || !Files.isRegularFile(jar) || !jar.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jar")) {
            throw new IllegalArgumentException("Choose an existing server .jar file.");
        }
        Path normalizedDirectory = directory.toAbsolutePath().normalize();
        Path normalizedJar = jar.toAbsolutePath().normalize();
        if (!normalizedJar.startsWith(normalizedDirectory)) {
            throw new IllegalArgumentException("The server JAR must be inside the selected server directory.");
        }
        String id = UUID.randomUUID().toString();
        Profile profile = new Profile(id, name.trim(), normalizedDirectory, normalizedJar);
        Path properties = normalizedDirectory.resolve("server.properties");
        if (Files.isRegularFile(properties)) {
            Properties loaded = new Properties();
            try (var input = Files.newInputStream(properties)) { loaded.load(input); }
            profile.port = parseInt(loaded.getProperty("server-port"), 25565, 1, 65535);
            profile.maxPlayers = parseInt(loaded.getProperty("max-players"), 20, 1, 1000);
            profile.motd = loaded.getProperty("motd", profile.motd);
        }
        Path eula = normalizedDirectory.resolve("eula.txt");
        profile.eulaAccepted = Files.isRegularFile(eula) && Files.readString(eula).matches("(?s).*?eula\\s*=\\s*true.*");
        profiles.put(id, profile);
        runtime.put(id, new RuntimeState());
        save();
        return copy(profile);
    }

    public synchronized void updateProfile(Profile edited) throws IOException {
        Profile current = profiles.get(edited.id);
        if (current == null) throw new IllegalArgumentException("Server profile no longer exists.");
        RuntimeState rt = runtime.computeIfAbsent(edited.id, ignored -> new RuntimeState());
        if (rt.process != null && rt.process.isAlive()) throw new IllegalStateException("Stop the server before changing its configuration.");
        if (edited.name == null || edited.name.isBlank()) throw new IllegalArgumentException("Server name is required.");
        if (edited.minRamMb < 256 || edited.maxRamMb < edited.minRamMb || edited.maxRamMb > 262144) {
            throw new IllegalArgumentException("RAM must be 256 MB or more, with maximum RAM at least the minimum and no more than 256 GB.");
        }
        if (edited.port < 1 || edited.port > 65535) throw new IllegalArgumentException("Port must be between 1 and 65535.");
        if (edited.maxPlayers < 1 || edited.maxPlayers > 1000) throw new IllegalArgumentException("Player limit must be between 1 and 1000.");
        if (!Files.isDirectory(edited.directoryPath()) || !Files.isRegularFile(edited.jarPath())
                || !edited.jarPath().startsWith(edited.directoryPath())) {
            throw new IllegalArgumentException("Server directory or JAR path is invalid.");
        }
        if (edited.eulaAccepted && !current.eulaAccepted) {
            Path eula = edited.directoryPath().resolve("eula.txt");
            Files.writeString(eula,
                    "# By changing the setting below to TRUE you indicate your agreement to the Minecraft EULA (https://aka.ms/MinecraftEULA).\neula=true\n",
                    StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }
        profiles.put(edited.id, copy(edited));
        save();
        publish(edited.id);
    }

    public synchronized void removeProfile(String id) throws IOException {
        RuntimeState rt = runtime.get(id);
        if (rt != null && rt.process != null && rt.process.isAlive()) throw new IllegalStateException("Stop the server before removing its profile.");
        profiles.remove(id);
        runtime.remove(id);
        save();
    }

    public Snapshot snapshot(String id) {
        Profile p;
        synchronized (this) { p = profiles.get(id); }
        if (p == null) throw new IllegalArgumentException("Unknown server profile.");
        RuntimeState rt = runtime.computeIfAbsent(id, ignored -> new RuntimeState());
        if (rt.process != null && !rt.process.isAlive()
                && (rt.state == State.RUNNING || rt.state == State.STARTING || rt.state == State.STOPPING)) {
            rt.state = rt.exitCode == 0 ? State.STOPPED : State.CRASHED;
        }
        return new Snapshot(copy(p), rt.state, rt.exitCode, rt.lastExitReason);
    }

    public void start(String id) throws IOException {
        Profile p = profile(id).orElseThrow(() -> new IllegalArgumentException("Unknown server profile."));
        RuntimeState rt = runtime.computeIfAbsent(id, ignored -> new RuntimeState());
        synchronized (rt) {
            if (rt.process != null && rt.process.isAlive()) throw new IllegalStateException("Server is already running.");
            if (!Files.isRegularFile(p.jarPath())) throw new IOException("Server JAR is missing: " + p.jarPath());
            if (!Files.isDirectory(p.directoryPath())) throw new IOException("Server directory is missing.");
            if (!p.eulaAccepted) throw new IllegalStateException("You must accept the Minecraft EULA in the server profile before starting.");
            Path eula = p.directoryPath().resolve("eula.txt");
            if (!Files.exists(eula) || !Files.readString(eula).matches("(?s).*?eula\\s*=\\s*true.*")) {
                throw new IllegalStateException("The server's eula.txt does not contain eula=true. Review and accept the EULA first.");
            }
            rt.state = State.STARTING;
            rt.exitCode = Integer.MIN_VALUE;
            rt.lastExitReason = "";
            publish(id);
            List<String> command = new ArrayList<>();
            command.add(p.javaExecutable == null || p.javaExecutable.isBlank() ? "java" : p.javaExecutable.trim());
            command.add("-Xms" + p.minRamMb + "M");
            command.add("-Xmx" + p.maxRamMb + "M");
            command.add("-jar");
            command.add(p.jarPath().toString());
            command.add("nogui");
            try {
                Process process = new ProcessBuilder(command).directory(p.directoryPath().toFile())
                        .redirectErrorStream(true).start();
                rt.process = process;
                rt.stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
                emit(id, "Vanta: started server process; waiting for server startup.");
                IO.execute(() -> readConsole(id, rt, process));
                IO.execute(() -> {
                    try {
                        int code = process.waitFor();
                        rt.exitCode = code;
                        rt.state = code == 0 ? State.STOPPED : State.CRASHED;
                        rt.lastExitReason = code == 0 ? "Server stopped normally." : "Server exited with code " + code + ". Check the console and crash-reports folder.";
                        emit(id, "Vanta: process exited with code " + code + ".");
                        publish(id);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            } catch (IOException e) {
                rt.state = State.CRASHED;
                rt.lastExitReason = "Could not start Java: " + safeMessage(e);
                emit(id, "Vanta: " + rt.lastExitReason);
                publish(id);
                throw e;
            }
        }
    }

    private void readConsole(String id, RuntimeState rt, Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                emit(id, line);
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.contains("done (") && lower.contains("for help")) {
                    rt.state = State.RUNNING;
                    publish(id);
                } else if (lower.contains("starting minecraft server on")) {
                    rt.state = State.STARTING;
                    publish(id);
                }
            }
        } catch (IOException e) {
            if (process.isAlive()) emit(id, "Vanta: console stream ended: " + safeMessage(e));
        }
    }

    public void sendCommand(String id, String command) throws IOException {
        if (command == null || command.isBlank()) return;
        String trimmed = command.trim();
        if (trimmed.length() > 2048 || trimmed.indexOf('\n') >= 0 || trimmed.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("Enter one server console command at a time (maximum 2048 characters).");
        }
        RuntimeState rt = runtime.get(id);
        if (rt == null || rt.process == null || !rt.process.isAlive() || rt.stdin == null) {
            throw new IllegalStateException("Server console is not connected.");
        }
        synchronized (rt.stdin) {
            rt.stdin.write(trimmed);
            rt.stdin.newLine();
            rt.stdin.flush();
        }
        emit(id, "> " + trimmed);
    }

    public void stop(String id) throws IOException {
        Snapshot snapshot = snapshot(id);
        if (snapshot.state() != State.RUNNING && snapshot.state() != State.STARTING) {
            throw new IllegalStateException("Server is not running.");
        }
        RuntimeState rt = runtime.get(id);
        rt.state = State.STOPPING;
        publish(id);
        sendCommand(id, "stop");
    }

    public void forceStop(String id) {
        RuntimeState rt = runtime.get(id);
        if (rt == null || rt.process == null || !rt.process.isAlive()) return;
        rt.process.destroy();
        IO.execute(() -> {
            try {
                if (!rt.process.waitFor(5, TimeUnit.SECONDS)) rt.process.destroyForcibly();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                rt.process.destroyForcibly();
            }
        });
    }

    public Path createBackup(String id) throws IOException {
        Profile p = profile(id).orElseThrow(() -> new IllegalArgumentException("Unknown server profile."));
        Snapshot state = snapshot(id);
        if (state.state() == State.RUNNING || state.state() == State.STARTING || state.state() == State.STOPPING) {
            throw new IllegalStateException("Stop the server before creating a consistent backup.");
        }
        Path root = p.directoryPath();
        Path backupDir = root.resolve("backups");
        Files.createDirectories(backupDir);
        Path target = backupDir.resolve(p.name.replaceAll("[^a-zA-Z0-9._-]+", "-") + "-" + BACKUP_TIME.format(LocalDateTime.now()) + ".zip");
        Path normalizedTarget = target.toAbsolutePath().normalize();
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(normalizedTarget, StandardOpenOption.CREATE_NEW))) {
            try (var paths = Files.walk(root)) {
                for (Path path : paths.sorted().toList()) {
                    if (path.equals(backupDir) || path.startsWith(backupDir)) continue;
                    if (Files.isDirectory(path)) continue;
                    Path relative = root.relativize(path);
                    if (relative.getNameCount() > 0) {
                        String first = relative.getName(0).toString().toLowerCase(Locale.ROOT);
                        if (Set.of("logs", "crash-reports", "cache").contains(first)) continue;
                    }
                    zip.putNextEntry(new ZipEntry(relative.toString().replace('\\', '/')));
                    Files.copy(path, zip);
                    zip.closeEntry();
                }
            }
        } catch (IOException e) {
            Files.deleteIfExists(normalizedTarget);
            throw e;
        }
        emit(id, "Vanta: backup created at " + normalizedTarget);
        return normalizedTarget;
    }

    public List<Path> crashReports(String id) throws IOException {
        Profile p = profile(id).orElseThrow(() -> new IllegalArgumentException("Unknown server profile."));
        Path dir = p.directoryPath().resolve("crash-reports");
        if (!Files.isDirectory(dir)) return List.of();
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile).sorted(Comparator.comparingLong((Path path) -> path.toFile().lastModified()).reversed()).limit(20).toList();
        }
    }

    public Properties readServerProperties(String id) throws IOException {
        Profile p = profile(id).orElseThrow(() -> new IllegalArgumentException("Unknown server profile."));
        Properties properties = new Properties();
        Path file = p.directoryPath().resolve("server.properties");
        if (Files.isRegularFile(file)) try (var input = Files.newInputStream(file)) { properties.load(input); }
        return properties;
    }

    public void saveServerProperties(String id, Properties properties) throws IOException {
        Snapshot snapshot = snapshot(id);
        if (snapshot.state() == State.RUNNING || snapshot.state() == State.STARTING || snapshot.state() == State.STOPPING) {
            throw new IllegalStateException("Stop the server before editing server.properties.");
        }
        Profile p = snapshot.profile();
        Path file = p.directoryPath().resolve("server.properties");
        Properties current = readServerProperties(id);
        for (String key : List.of("server-port", "max-players", "motd")) {
            if (properties.containsKey(key)) current.setProperty(key, properties.getProperty(key));
        }
        int port = parseInt(current.getProperty("server-port"), p.port, 1, 65535);
        int players = parseInt(current.getProperty("max-players"), p.maxPlayers, 1, 1000);
        current.setProperty("server-port", Integer.toString(port));
        current.setProperty("max-players", Integer.toString(players));
        current.setProperty("motd", current.getProperty("motd", p.motd));
        try (var output = Files.newOutputStream(file, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            current.store(output, "Minecraft server properties managed by Vanta");
        }
        synchronized (this) {
            Profile stored = profiles.get(id);
            stored.port = port;
            stored.maxPlayers = players;
            stored.motd = current.getProperty("motd");
            save();
        }
    }

    public String latestCrashSummary(String id) throws IOException {
        List<Path> reports = crashReports(id);
        if (reports.isEmpty()) return "No crash reports found. Inspect the live console output and latest.log if the server exits unexpectedly.";
        Path report = reports.get(0);
        List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);
        StringBuilder summary = new StringBuilder("Latest report: ").append(report.getFileName()).append('\n');
        for (int i = 0; i < Math.min(lines.size(), 120); i++) summary.append(lines.get(i)).append('\n');
        return summary.toString();
    }

    private void emit(String id, String line) {
        try { consoleListener.accept(id, line); } catch (RuntimeException ignored) {}
    }

    private void publish(String id) {
        try { stateListener.accept(id, snapshot(id)); } catch (RuntimeException ignored) {}
    }

    private synchronized void load() {
        try {
            if (!Files.isRegularFile(configFile)) return;
            List<Profile> loaded = MAPPER.readValue(configFile.toFile(), new TypeReference<>() {});
            for (Profile p : loaded) {
                if (p == null || p.id == null || p.name == null || p.directory == null || p.jar == null) continue;
                profiles.put(p.id, p);
                runtime.put(p.id, new RuntimeState());
            }
        } catch (Exception e) {
            System.err.println("Vanta server profiles could not be loaded: " + safeMessage(e));
        }
    }

    private synchronized void save() throws IOException {
        Files.createDirectories(configFile.getParent());
        Path temp = configFile.resolveSibling(configFile.getFileName() + ".tmp");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), profiles.values());
        try {
            Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(temp, configFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static Profile copy(Profile source) {
        Profile p = new Profile();
        p.id = source.id; p.name = source.name; p.directory = source.directory; p.jar = source.jar;
        p.javaExecutable = source.javaExecutable; p.minRamMb = source.minRamMb; p.maxRamMb = source.maxRamMb;
        p.port = source.port; p.maxPlayers = source.maxPlayers; p.motd = source.motd; p.eulaAccepted = source.eulaAccepted;
        return p;
    }

    private static int parseInt(String value, int fallback, int min, int max) {
        try { int parsed = Integer.parseInt(value); return parsed >= min && parsed <= max ? parsed : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static String safeMessage(Throwable e) {
        return e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage().replaceAll("(?i)(?:[a-z]:\\\\|/)[^\\s]+", "[local path]");
    }

    @Override public void close() {
        for (RuntimeState rt : runtime.values()) {
            if (rt.process != null && rt.process.isAlive()) {
                try {
                    if (rt.stdin != null) { rt.stdin.write("stop"); rt.stdin.newLine(); rt.stdin.flush(); }
                } catch (IOException ignored) {}
            }
        }
    }
}
