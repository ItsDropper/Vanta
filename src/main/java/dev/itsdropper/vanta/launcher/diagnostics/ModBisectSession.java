package dev.itsdropper.vanta.launcher.diagnostics;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.itsdropper.vanta.launcher.diagnostics.BisectEngine.Outcome;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Runs a {@link BisectEngine} against the real mods folder of one instance.
 *
 * <p>A mod is switched off by renaming {@code name.jar} to {@code name.jar.disabled}, the same convention the Mods
 * page uses, so nothing is deleted or rewritten. Mods the user had already switched off are never touched. The
 * whole session is saved next to the instance before any file moves, every move is idempotent, and finishing,
 * stopping or undoing always brings the folder back to a state the saved session describes, so closing the
 * launcher or crashing mid-search can never lose a mod.
 */
public final class ModBisectSession {

    /** What the session needs to know about one enabled mod file. */
    public record ModInfo(String filename, String modId, Set<String> provides, Set<String> requires) {
        public ModInfo {
            provides = provides == null ? Set.of() : Set.copyOf(provides);
            requires = requires == null ? Set.of() : Set.copyOf(requires);
        }
    }

    /** Called after a file was renamed so the instance's mod records can follow. */
    @FunctionalInterface
    public interface Renamer {
        void renamed(String fromFilename, String toFilename) throws IOException;
    }

    private record Saved(String engine, Map<String, String> units, List<String> originallyEnabled) {
    }

    private static final String DISABLED = ".disabled";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private final Path modsDirectory;
    private final Path journal;
    private final Renamer renamer;
    private final Map<String, String> units;
    private final List<String> originallyEnabled;
    private final BisectEngine engine;

    private ModBisectSession(Path modsDirectory, Renamer renamer, Map<String, String> units,
                             List<String> originallyEnabled, BisectEngine engine) {
        this.modsDirectory = modsDirectory;
        this.journal = journalFor(modsDirectory);
        this.renamer = renamer;
        this.units = units;
        this.originallyEnabled = originallyEnabled;
        this.engine = engine;
    }

    public static Path journalFor(Path modsDirectory) {
        Path instance = modsDirectory.toAbsolutePath().getParent();
        return instance.resolve(".vanta").resolve("mod-search.json");
    }

    /**
     * Starts a search over the enabled mods and switches them all off for the first test. Fails without changing
     * anything when there are fewer than two mods to search or a search is already saved for this instance.
     */
    public static ModBisectSession create(Path modsDirectory, List<ModInfo> mods, Renamer renamer)
            throws IOException {
        if (mods.size() < 2) {
            throw new IllegalStateException("At least two enabled mods are needed to search for a problem");
        }
        if (Files.exists(journalFor(modsDirectory))) {
            throw new IllegalStateException("A mod search is already in progress for this instance");
        }

        Map<String, String> units = new LinkedHashMap<>();
        Map<String, String> providedBy = new LinkedHashMap<>();
        for (ModInfo mod : mods) {
            String unit = unitId(mod);
            if (units.containsKey(unit)) {
                // Two jars claiming one mod id are tested as separate mods rather than one hiding the other.
                unit = unit + "#" + mod.filename().toLowerCase(Locale.ROOT);
            }
            units.put(unit, mod.filename());
            providedBy.putIfAbsent(unit, unit);
            for (String id : mod.provides()) {
                providedBy.putIfAbsent(normalize(id), unit);
            }
        }
        // Fabric's old "fabric" id is provided by the Fabric API bundle.
        if (providedBy.containsKey("fabric-api")) {
            providedBy.putIfAbsent("fabric", providedBy.get("fabric-api"));
        }

        Map<String, Set<String>> requires = new LinkedHashMap<>();
        Map<String, String> unitOfFile = new LinkedHashMap<>();
        units.forEach((unit, filename) -> unitOfFile.put(filename, unit));
        for (ModInfo mod : mods) {
            Set<String> needed = new LinkedHashSet<>();
            for (String id : mod.requires()) {
                String provider = providedBy.get(normalize(id));
                if (provider != null) {
                    needed.add(provider);
                }
            }
            requires.put(unitOfFile.get(mod.filename()), needed);
        }

        BisectEngine engine = BisectEngine.start(units.keySet(), requires);
        ModBisectSession session = new ModBisectSession(modsDirectory, renamer, units,
                List.copyOf(units.values()), engine);
        session.save();
        try {
            session.applyCurrent();
        } catch (IOException failure) {
            try {
                session.restoreAll();
            } catch (IOException rollbackFailure) {
                failure.addSuppressed(rollbackFailure);
            } finally {
                Files.deleteIfExists(session.journal);
            }
            throw failure;
        }
        return session;
    }

    /** The saved session for an instance, if the last one was not finished or stopped. */
    public static Optional<ModBisectSession> resume(Path modsDirectory, Renamer renamer) throws IOException {
        Path journal = journalFor(modsDirectory);
        if (!Files.isRegularFile(journal)) {
            return Optional.empty();
        }
        Saved saved = GSON.fromJson(Files.readString(journal, StandardCharsets.UTF_8), Saved.class);
        if (saved == null || saved.engine() == null || saved.units() == null) {
            return Optional.empty();
        }
        return Optional.of(new ModBisectSession(modsDirectory, renamer, new LinkedHashMap<>(saved.units()),
                List.copyOf(saved.originallyEnabled()), BisectEngine.fromJson(saved.engine())));
    }

    public BisectEngine engine() {
        return engine;
    }

    /** Filenames (as they were before the search) of the suspected mods. */
    public List<String> culpritFilenames() {
        return engine.culprits().stream().map(units::get).toList();
    }

    /** Mod filenames in the current test, for showing the user what is on. */
    public Map<String, String> unitFilenames() {
        return Map.copyOf(units);
    }

    /** Records the outcome, saves, and switches mods for the next test, or restores them all when finished. */
    public void answer(Outcome outcome) throws IOException {
        engine.answer(outcome);
        save();
        applyCurrent();
    }

    public void undo() throws IOException {
        engine.undo();
        save();
        applyCurrent();
    }

    /** Switches every mod from the search back on and forgets the search. */
    public void stop() throws IOException {
        restoreAll();
        Files.deleteIfExists(journal);
    }

    /** Switches every mod back on, then turns the suspects off, and forgets the search. */
    public void disableSuspects() throws IOException {
        restoreAll();
        List<String> failures = new ArrayList<>();
        for (String filename : culpritFilenames()) {
            try {
                move(filename, filename + DISABLED);
            } catch (IOException failure) {
                failures.add(filename + ": " + failure.getMessage());
            }
        }
        if (!failures.isEmpty()) {
            throw new IOException("Could not switch off " + String.join("; ", failures));
        }
        Files.deleteIfExists(journal);
    }

    /** Applies whatever the engine wants on for the current launch. Finished searches restore every mod. */
    private void applyCurrent() throws IOException {
        if (engine.isFinished()) {
            restoreAll();
            return;
        }
        Set<String> wanted = engine.currentTest();
        List<String> failures = new ArrayList<>();
        for (Map.Entry<String, String> unit : units.entrySet()) {
            try {
                if (wanted.contains(unit.getKey())) {
                    enable(unit.getValue());
                } else {
                    move(unit.getValue(), unit.getValue() + DISABLED);
                }
            } catch (IOException failure) {
                failures.add(unit.getValue() + " (" + failure.getMessage() + ")");
            }
        }
        if (!failures.isEmpty()) {
            throw new IOException("Some mods could not be switched. Close Minecraft and try again: "
                    + String.join(", ", failures));
        }
    }

    private void restoreAll() throws IOException {
        List<String> failures = new ArrayList<>();
        for (String filename : originallyEnabled) {
            try {
                enable(filename);
            } catch (IOException failure) {
                failures.add(filename + " (" + failure.getMessage() + ")");
            }
        }
        if (!failures.isEmpty()) {
            throw new IOException("Some mods could not be switched back on. Close Minecraft and try again: "
                    + String.join(", ", failures));
        }
    }

    private void enable(String filename) throws IOException {
        // Already on means nothing to bring back, even if an unrelated older ".disabled" copy sits beside it.
        if (Files.exists(modsDirectory.resolve(filename))) {
            return;
        }
        move(filename + DISABLED, filename);
    }

    /** Renames {@code from} to {@code to} if {@code from} exists and {@code to} does not. Safe to repeat. */
    private void move(String from, String to) throws IOException {
        Path source = modsDirectory.resolve(from);
        Path target = modsDirectory.resolve(to);
        if (!Files.exists(source)) {
            return;
        }
        if (Files.exists(target)) {
            throw new IOException(to + " already exists");
        }
        try {
            Files.move(source, target, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(source, target);
        }
        if (renamer != null) {
            renamer.renamed(from, to);
        }
    }

    private void save() throws IOException {
        Files.createDirectories(journal.getParent());
        Path temporary = journal.resolveSibling(journal.getFileName() + ".tmp");
        Files.writeString(temporary, GSON.toJson(new Saved(engine.toJson(), units, originallyEnabled)),
                StandardCharsets.UTF_8);
        try {
            Files.move(temporary, journal, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, journal, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String unitId(ModInfo mod) {
        String id = mod.modId();
        return id == null || id.isBlank() ? "file:" + mod.filename().toLowerCase(Locale.ROOT) : normalize(id);
    }

    private static String normalize(String id) {
        return id.trim().toLowerCase(Locale.ROOT);
    }
}
