package dev.itsdropper.vanta.launcher.diagnostics;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.itsdropper.vanta.launcher.diagnostics.BisectEngine.Outcome;
import dev.itsdropper.vanta.launcher.diagnostics.ModBisectSession.ModInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ModBisectSessionTest {

    @TempDir
    Path instance;

    private final List<String> renames = new ArrayList<>();

    private Path mods() throws IOException {
        return Files.createDirectories(instance.resolve("mods"));
    }

    private List<ModInfo> install(String... names) throws IOException {
        List<ModInfo> infos = new ArrayList<>();
        for (String name : names) {
            Files.writeString(mods().resolve(name + ".jar"), name);
            infos.add(new ModInfo(name + ".jar", name, Set.of(), Set.of()));
        }
        return infos;
    }

    private Set<String> enabledFiles() throws IOException {
        try (var files = Files.list(mods())) {
            return files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".jar"))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        }
    }

    private Set<String> disabledFiles() throws IOException {
        try (var files = Files.list(mods())) {
            return files.map(path -> path.getFileName().toString()).filter(name -> name.endsWith(".jar.disabled"))
                    .collect(java.util.stream.Collectors.toCollection(TreeSet::new));
        }
    }

    @Test
    void startingSwitchesEveryModOffForTheBaselineLaunch() throws IOException {
        List<ModInfo> infos = install("a", "b", "c");

        ModBisectSession.create(mods(), infos, (from, to) -> renames.add(from + ">" + to));

        assertEquals(Set.of(), enabledFiles());
        assertEquals(Set.of("a.jar.disabled", "b.jar.disabled", "c.jar.disabled"), disabledFiles());
        assertEquals(3, renames.size());
        assertTrue(Files.exists(ModBisectSession.journalFor(mods())));
    }

    @Test
    void aFullSearchFindsTheModAndThenRestoresEverything() throws IOException {
        List<ModInfo> infos = install("a", "b", "c", "d", "e", "f");
        ModBisectSession session = ModBisectSession.create(mods(), infos, null);

        while (!session.engine().isFinished()) {
            Set<String> on = enabledFiles();
            session.answer(on.contains("d.jar") ? Outcome.PROBLEM : Outcome.FINE);
        }

        assertEquals(List.of("d.jar"), session.culpritFilenames());
        assertEquals(Set.of("a.jar", "b.jar", "c.jar", "d.jar", "e.jar", "f.jar"), enabledFiles());
        assertEquals(Set.of(), disabledFiles());

        session.disableSuspects();

        assertEquals(Set.of("d.jar.disabled"), disabledFiles());
        assertEquals(5, enabledFiles().size());
        assertFalse(Files.exists(ModBisectSession.journalFor(mods())));
    }

    @Test
    void stoppingPutsEveryModBack() throws IOException {
        List<ModInfo> infos = install("a", "b", "c", "d");
        ModBisectSession session = ModBisectSession.create(mods(), infos, null);
        session.answer(Outcome.FINE);

        session.stop();

        assertEquals(Set.of("a.jar", "b.jar", "c.jar", "d.jar"), enabledFiles());
        assertEquals(Set.of(), disabledFiles());
        assertFalse(Files.exists(ModBisectSession.journalFor(mods())));
    }

    @Test
    void aSearchResumesFromTheSavedFileAfterARestart() throws IOException {
        List<ModInfo> infos = install("a", "b", "c", "d", "e", "f", "g", "h");
        ModBisectSession first = ModBisectSession.create(mods(), infos, null);
        first.answer(Outcome.FINE);
        Set<String> onBefore = enabledFiles();

        ModBisectSession resumed = ModBisectSession.resume(mods(), null).orElseThrow();

        assertEquals(first.engine().currentTest(), resumed.engine().currentTest());
        assertEquals(onBefore, enabledFiles());
        resumed.undo();
        assertEquals(Set.of(), enabledFiles());
    }

    @Test
    void modsTheUserAlreadySwitchedOffAreNeverTouched() throws IOException {
        List<ModInfo> infos = install("a", "b", "c");
        Files.writeString(mods().resolve("old.jar.disabled"), "old");

        ModBisectSession session = ModBisectSession.create(mods(), infos, null);
        session.stop();

        assertEquals(Set.of("old.jar.disabled"), disabledFiles());
        assertEquals("old", Files.readString(mods().resolve("old.jar.disabled")));
    }

    @Test
    void aNameClashRollsBackWithoutLosingAnyMod() throws IOException {
        List<ModInfo> infos = install("a", "b");
        Files.writeString(mods().resolve("b.jar.disabled"), "an older copy");

        assertThrows(IOException.class, () -> ModBisectSession.create(mods(), infos, null));

        assertEquals(Set.of("a.jar", "b.jar"), enabledFiles());
        assertEquals("b", Files.readString(mods().resolve("b.jar")));
        assertEquals("an older copy", Files.readString(mods().resolve("b.jar.disabled")));
        assertFalse(Files.exists(ModBisectSession.journalFor(mods())));
    }

    @Test
    void dependenciesAreSwitchedOnWithTheModsThatNeedThem() throws IOException {
        Files.writeString(mods().resolve("lib.jar"), "lib");
        Files.writeString(mods().resolve("feature.jar"), "feature");
        Files.writeString(mods().resolve("other.jar"), "other");
        List<ModInfo> infos = List.of(
                new ModInfo("lib.jar", "lib", Set.of("lib-api"), Set.of()),
                new ModInfo("feature.jar", "feature", Set.of(), Set.of("lib-api")),
                new ModInfo("other.jar", "other", Set.of(), Set.of()));
        ModBisectSession session = ModBisectSession.create(mods(), infos, null);

        while (!session.engine().isFinished()) {
            Set<String> on = enabledFiles();
            if (on.contains("feature.jar")) {
                assertTrue(on.contains("lib.jar"), "feature was enabled without its library");
            }
            session.answer(on.contains("feature.jar") ? Outcome.PROBLEM : Outcome.FINE);
        }

        assertEquals(List.of("feature.jar"), session.culpritFilenames());
    }

    @Test
    void tooFewModsAndDuplicateSessionsAreRefused() throws IOException {
        assertThrows(IllegalStateException.class, () -> ModBisectSession.create(mods(), install("only"), null));
        List<ModInfo> infos = install("a", "b");
        ModBisectSession.create(mods(), infos, null);
        assertThrows(IllegalStateException.class, () -> ModBisectSession.create(mods(), infos, null));
    }

    @Test
    void twoJarsWithTheSameModIdAreBothTested() throws IOException {
        Files.writeString(mods().resolve("one.jar"), "1");
        Files.writeString(mods().resolve("two.jar"), "2");
        List<ModInfo> infos = List.of(
                new ModInfo("one.jar", "same", Set.of(), Set.of()),
                new ModInfo("two.jar", "same", Set.of(), Set.of()));

        ModBisectSession session = ModBisectSession.create(mods(), infos, null);
        session.stop();

        assertEquals(Set.of("one.jar", "two.jar"), enabledFiles());
    }
}
